package com.StreamingApp.StreamingBE.audiobooks;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.Setter;

@Entity
@Getter @Setter
public class Audiobook {
    @Id @Column(length = 100)
    private String id;
    @Column(nullable = false)
    private String title;
    private String author;
    private String narrator;
    @Column(length = 4000)
    private String description;
    @Column(length = 2048)
    private String coverUrl;
    @ElementCollection
    @CollectionTable(name = "audiobook_chapter", joinColumns = @JoinColumn(name = "book_id"))
    @OrderColumn(name = "chapter_index")
    private List<Chapter> chapters = new ArrayList<>();

    @Embeddable @Getter @Setter
    public static class Chapter {
        @Column(nullable = false)
        private String title;
        @Column(nullable = false, length = 2048)
        private String source;
        private double durationSeconds;
    }
}
