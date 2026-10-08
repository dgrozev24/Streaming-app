package com.StreamingApp.StreamingBE.movies;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class MovieApiTests {
    @Autowired MockMvc mvc;
    @MockitoBean MovieRepository repository;

    @Test
    void anonymousUsersCanListMoviesWithPlaybackReferences() throws Exception {
        MovieModel movie = new MovieModel();
        movie.setMovieId(7);
        movie.setMovie_name("Test movie");
        movie.setHls_reference("https://media.example/movies/test/index.m3u8");
        when(repository.findAll(any(Sort.class))).thenReturn(List.of(movie));
        mvc.perform(get("/api/movies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].title").value("Test movie"))
                .andExpect(jsonPath("$[0].hlsReference").value("https://media.example/movies/test/index.m3u8"));
    }

    @Test
    void missingMovieReturns404() throws Exception {
        when(repository.findById(99)).thenReturn(Optional.empty());
        mvc.perform(get("/api/movies/99")).andExpect(status().isNotFound());
    }

    @Test
    void movieDetailsAreAvailable() throws Exception {
        MovieModel movie = new MovieModel();
        movie.setMovieId(7);
        movie.setMovie_name("Test movie");
        when(repository.findById(7)).thenReturn(Optional.of(movie));
        mvc.perform(get("/api/movies/7")).andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Test movie"));
    }

    @Test
    void developmentWebOriginCanReadMovies() throws Exception {
        when(repository.findAll(any(Sort.class))).thenReturn(List.of());
        mvc.perform(get("/api/movies").header("Origin", "http://localhost:8090"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "http://localhost:8090"));
    }

    @Test
    void anonymousWritesAreForbidden() throws Exception {
        mvc.perform(post("/api/movies").contentType("application/json").content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
