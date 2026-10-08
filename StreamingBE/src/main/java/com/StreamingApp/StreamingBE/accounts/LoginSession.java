package com.StreamingApp.StreamingBE.accounts;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.Instant;

@Entity
@Getter @Setter
public class LoginSession {
    @Id @Column(length = 64)
    private String tokenHash;
    @ManyToOne(optional = false)
    private Account account;
    @Column(nullable = false)
    private Instant expiresAt;
}
