package com.StreamingApp.StreamingBE.accounts;

import com.StreamingApp.StreamingBE.movies.MovieModel;
import com.StreamingApp.StreamingBE.movies.MovieRepository;
import com.jayway.jsonpath.JsonPath;
import java.time.Instant;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AccountApiTests {
    @Autowired MockMvc mvc;
    @Autowired AccountRepository accounts;
    @Autowired SessionRepository sessions;
    @Autowired MovieRepository movies;

    @BeforeEach
    void reset() {
        sessions.deleteAll();
        accounts.deleteAll();
        movies.deleteAll();
        MovieModel movie = new MovieModel();
        movie.setMovieId(1);
        movie.setMovie_name("Test movie");
        movies.save(movie);
    }

    private String register(String email) throws Exception {
        String response = mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"" + email + "\",\"password\":\"correct-horse-123\"}"))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.token");
    }

    @Test
    void registrationHashesCredentialsAndRejectsDuplicateEmail() throws Exception {
        String token = register("Test@Example.com");
        Account account = accounts.findByEmail("test@example.com").orElseThrow();
        assertNotEquals("correct-horse-123", account.getPasswordHash());
        assertTrue(sessions.existsById(AccountService.hash(token)));
        assertFalse(sessions.existsById(token));
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"test@example.com\",\"password\":\"correct-horse-123\"}"))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/account").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.email").value("test@example.com"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    void savedMoviesArePrivateAndSavesAreIdempotent() throws Exception {
        String alice = register("alice@example.com");
        String bob = register("bob@example.com");
        for (int i = 0; i < 2; i++) {
            mvc.perform(put("/api/account/saved/1").header("Authorization", "Bearer " + alice))
                    .andExpect(status().isNoContent());
        }
        mvc.perform(get("/api/account/saved").header("Authorization", "Bearer " + alice))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1));
        mvc.perform(get("/api/account/saved").header("Authorization", "Bearer " + bob))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(delete("/api/account/saved/1").header("Authorization", "Bearer " + bob))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/account/saved").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.length()").value(1));
        mvc.perform(delete("/api/account/saved/1").header("Authorization", "Bearer " + alice))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/account/saved").header("Authorization", "Bearer " + alice))
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(put("/api/account/saved/999").header("Authorization", "Bearer " + alice))
                .andExpect(status().isNotFound());
    }

    @Test
    void loginLogoutAndExpirationEnforceSessions() throws Exception {
        String original = register("user@example.com");
        mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"user@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized());
        String body = mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"USER@example.com\",\"password\":\"correct-horse-123\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.token");
        mvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/account").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        LoginSession expired = sessions.findById(AccountService.hash(original)).orElseThrow();
        expired.setExpiresAt(Instant.now().minusSeconds(1));
        sessions.save(expired);
        mvc.perform(get("/api/account").header("Authorization", "Bearer " + original))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsInvalidRegistrationAndAnonymousWatchlistRequests() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"bad\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/register").contentType("application/json")
                .content("{\"email\":\"valid@example.com\",\"password\":\"short\"}")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/account/saved")).andExpect(status().isUnauthorized());
        mvc.perform(put("/api/account/saved/1")).andExpect(status().isUnauthorized());
    }
}
