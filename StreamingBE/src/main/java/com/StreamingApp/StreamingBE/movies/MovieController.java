package com.StreamingApp.StreamingBE.movies;

import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/movies")
public class MovieController {
    private final MovieService service;
    private final PlaybackService playback;

    public MovieController(MovieService service, PlaybackService playback) {
        this.service = service;
        this.playback = playback;
    }

    @GetMapping
    public List<MovieResponse> findAll() {
        return service.findAll();
    }

    @GetMapping("/{id}")
    public MovieResponse findById(@PathVariable int id) {
        return service.findById(id);
    }

    @GetMapping(value = "/{id}/playlist.m3u8", produces = "application/vnd.apple.mpegurl")
    public org.springframework.http.ResponseEntity<String> playlist(@PathVariable int id) {
        return org.springframework.http.ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore()).body(playback.playlist(id));
    }
}
