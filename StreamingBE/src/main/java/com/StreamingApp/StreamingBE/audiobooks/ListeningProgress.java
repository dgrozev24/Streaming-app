package com.StreamingApp.StreamingBE.audiobooks;

import com.StreamingApp.StreamingBE.accounts.Account;
import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "book_id"}))
@Getter @Setter
public class ListeningProgress {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "account_id")
    private Account account;
    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "book_id")
    private Audiobook book;
    private int chapterNumber;
    private double positionSeconds;
    private Instant updatedAt;
}
