package com.StreamingApp.StreamingBE.audiobooks;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AudiobookController {
    public record ProgressInput(@NotNull @Min(1) Integer chapterNumber,
                                @NotNull @DecimalMin("0") Double positionSeconds) {}
    private final AudiobookService books;
    private final AudiobookMedia media;
    public AudiobookController(AudiobookService books, AudiobookMedia media) { this.books = books; this.media = media; }

    @GetMapping("/audiobooks")
    public List<AudiobookService.BookView> list() { return books.list(); }
    @GetMapping("/audiobooks/{id}")
    public AudiobookService.BookView detail(@PathVariable String id) { return books.detail(id); }
    @GetMapping("/audiobooks/{id}/chapters/{number}/audio")
    public ResponseEntity<Resource> audio(@PathVariable String id, @PathVariable int number) {
        return media.audio(books.chapter(id, number).getSource());
    }
    @RequestMapping(value = "/audiobooks/{id}/chapters/{number}/audio", method = RequestMethod.HEAD)
    public ResponseEntity<Void> audioHead(@PathVariable String id, @PathVariable int number) throws java.io.IOException {
        var response = media.audio(books.chapter(id, number).getSource());
        var headers = new HttpHeaders();
        headers.putAll(response.getHeaders());
        if (response.getBody() != null) headers.setContentLength(response.getBody().contentLength());
        return new ResponseEntity<>(null, headers, response.getStatusCode());
    }
    @GetMapping("/account/audiobooks/{id}/progress")
    public AudiobookService.ProgressView progress(@AuthenticationPrincipal Long accountId, @PathVariable String id) {
        return books.progress(accountId, id);
    }
    @PutMapping("/account/audiobooks/{id}/progress")
    public AudiobookService.ProgressView save(@AuthenticationPrincipal Long accountId, @PathVariable String id,
                                            @Valid @RequestBody ProgressInput input) {
        return books.saveProgress(accountId, id, input.chapterNumber(), input.positionSeconds());
    }
}
