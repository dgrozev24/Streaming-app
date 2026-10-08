package com.StreamingApp.StreamingBE.audiobooks;

import com.StreamingApp.StreamingBE.accounts.AccountService;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:audiobooktest;MODE=PostgreSQL;DB_CLOSE_DELAY=-1")
@AutoConfigureMockMvc
class AudiobookApiTests {
    static final Path MEDIA;
    static { try { MEDIA = Files.createTempDirectory("audiobook-test-"); } catch (Exception e) { throw new RuntimeException(e); } }
    @DynamicPropertySource static void media(DynamicPropertyRegistry registry) {
        registry.add("audiobooks.media-root", () -> MEDIA.toString());
    }
    @Autowired MockMvc mvc;
    @Autowired AudiobookRepository books;
    @Autowired ListeningProgressRepository progress;
    @Autowired AccountService accounts;
    @Autowired AudiobookMedia media;

    @BeforeEach void setup() throws Exception {
        progress.deleteAll(); books.deleteAll();
        var book = new Audiobook(); book.setId("sample"); book.setTitle("Sample book");
        book.setAuthor("Author"); book.setNarrator("Narrator");
        for (int i = 0; i < 2; i++) {
            var chapter = new Audiobook.Chapter(); chapter.setTitle("Chapter " + (i + 1));
            chapter.setSource("sample.wav"); chapter.setDurationSeconds(60); book.getChapters().add(chapter);
        }
        books.save(book);
        byte[] bytes = new byte[256]; for (int i = 0; i < bytes.length; i++) bytes[i] = (byte) i;
        Files.write(MEDIA.resolve("sample.wav"), bytes);
    }
    String token() { return accounts.register(UUID.randomUUID() + "@example.com", "correct-horse-123").token(); }

    @Test void catalogueIsPublicAndHidesStoragePaths() throws Exception {
        mvc.perform(get("/api/audiobooks")).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("Sample book"))
                .andExpect(jsonPath("$[0].durationSeconds").value(120))
                .andExpect(jsonPath("$[0].chapters[0].number").value(1))
                .andExpect(jsonPath("$[0].chapters[0].source").doesNotExist());
        mvc.perform(get("/api/audiobooks/missing")).andExpect(status().isNotFound());
        mvc.perform(get("/api/audiobooks/sample/chapters/3/audio")).andExpect(status().isNotFound());
    }
    @Test void seekingAndHeadRequestsPreserveByteRanges() throws Exception {
        mvc.perform(get("/api/audiobooks/sample/chapters/1/audio").header("Range", "bytes=10-19"))
                .andExpect(status().isPartialContent()).andExpect(header().string("Content-Range", "bytes 10-19/256"))
                .andExpect(content().bytes(new byte[] {10,11,12,13,14,15,16,17,18,19}));
        mvc.perform(get("/api/audiobooks/sample/chapters/1/audio").header("Range", "bytes=999-"))
                .andExpect(status().isRequestedRangeNotSatisfiable());
        mvc.perform(head("/api/audiobooks/sample/chapters/1/audio")).andExpect(status().isOk())
                .andExpect(header().longValue("Content-Length", 256)).andExpect(content().bytes(new byte[0]));
    }
    @Test void progressIsPrivateAndSurvivesUpdates() throws Exception {
        String alice = token(), bob = token();
        for (int position : List.of(12, 25)) {
            mvc.perform(put("/api/account/audiobooks/sample/progress").header("Authorization", "Bearer " + alice)
                    .contentType("application/json").content("{\"chapterNumber\":2,\"positionSeconds\":" + position + "}"))
                    .andExpect(status().isOk());
        }
        mvc.perform(get("/api/account/audiobooks/sample/progress").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.chapterNumber").value(2)).andExpect(jsonPath("$.positionSeconds").value(25));
        mvc.perform(get("/api/account/audiobooks/sample/progress").header("Authorization", "Bearer " + bob))
                .andExpect(jsonPath("$.chapterNumber").value(1)).andExpect(jsonPath("$.positionSeconds").value(0));
        assertEquals(1, progress.count());
    }
    @Test void rejectsAnonymousAndInvalidProgress() throws Exception {
        mvc.perform(get("/api/account/audiobooks/sample/progress")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/account/audiobooks/sample/progress").contentType("application/json")
                .content("{\"chapterNumber\":1,\"positionSeconds\":10}")).andExpect(status().isUnauthorized());
        String token = token();
        for (String body : List.of("{}", "{\"chapterNumber\":1,\"positionSeconds\":-1}",
                "{\"chapterNumber\":3,\"positionSeconds\":1}", "{\"chapterNumber\":1,\"positionSeconds\":1000}")) {
            mvc.perform(put("/api/account/audiobooks/sample/progress").header("Authorization", "Bearer " + token)
                    .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        assertEquals(0, progress.count());
    }
    @Test void preventsArbitraryFilesystemReads() {
        assertThrows(IllegalArgumentException.class, () -> media.validateSource("../outside.wav"));
        assertThrows(IllegalArgumentException.class, () -> media.validateSource(MEDIA.resolve("sample.wav").toString()));
        assertThrows(IllegalArgumentException.class, () -> media.validateSource("http://localhost/private"));
    }
    @Test void missingAudioReturnsNotFound() throws Exception {
        Files.delete(MEDIA.resolve("sample.wav"));
        mvc.perform(get("/api/audiobooks/sample/chapters/1/audio")).andExpect(status().isNotFound());
    }
    @AfterAll static void cleanup() throws Exception {
        Files.deleteIfExists(MEDIA.resolve("sample.wav")); Files.deleteIfExists(MEDIA);
    }
}
