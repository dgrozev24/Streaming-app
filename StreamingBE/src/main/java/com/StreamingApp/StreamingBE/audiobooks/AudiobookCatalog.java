package com.StreamingApp.StreamingBE.audiobooks;

import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.constraints.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/** Local operator-managed catalogue import. No unauthenticated catalogue write endpoint. */
@Component
public class AudiobookCatalog implements ApplicationRunner {
    public record ChapterInput(@NotBlank @Size(max = 255) String title,
                               @NotBlank @Size(max = 2048) String source,
                               @Positive double durationSeconds) {}
    public record BookInput(@NotBlank @Pattern(regexp = "[a-z0-9-]{1,100}") String id,
                            @NotBlank @Size(max = 255) String title,
                            @NotBlank @Size(max = 255) String author,
                            @NotBlank @Size(max = 255) String narrator,
                            @Size(max = 4000) String description,
                            @Size(max = 2048) String coverUrl,
                            @NotEmpty @Size(max = 1000) List<@Valid ChapterInput> chapters) {}
    private final String catalog;
    private final ObjectMapper json;
    private final Validator validator;
    private final AudiobookRepository books;
    private final AudiobookMedia media;
    public AudiobookCatalog(@Value("${audiobooks.catalog:./media/audiobooks/catalog.json}") String catalog,
                            ObjectMapper json, Validator validator, AudiobookRepository books, AudiobookMedia media) {
        this.catalog = catalog; this.json = json; this.validator = validator; this.books = books; this.media = media;
    }
    @Override @Transactional
    public void run(ApplicationArguments args) throws Exception {
        if (catalog.isBlank() || !Files.exists(Path.of(catalog))) return;
        BookInput[] entries = json.readValue(Files.readString(Path.of(catalog)), BookInput[].class);
        Set<String> ids = new HashSet<>();
        for (BookInput entry : entries) {
            if (!validator.validate(entry).isEmpty() || !ids.add(entry.id())) {
                throw new IllegalArgumentException("Invalid or duplicate audiobook in " + catalog);
            }
            var book = books.findById(entry.id()).orElseGet(Audiobook::new);
            book.setId(entry.id()); book.setTitle(entry.title()); book.setAuthor(entry.author());
            book.setNarrator(entry.narrator()); book.setDescription(entry.description()); book.setCoverUrl(entry.coverUrl());
            book.getChapters().clear();
            for (ChapterInput input : entry.chapters()) {
                if (!Double.isFinite(input.durationSeconds())) throw new IllegalArgumentException("Invalid chapter duration");
                media.validateSource(input.source());
                var chapter = new Audiobook.Chapter();
                chapter.setTitle(input.title()); chapter.setSource(input.source()); chapter.setDurationSeconds(input.durationSeconds());
                book.getChapters().add(chapter);
            }
            books.save(book);
        }
    }
}
