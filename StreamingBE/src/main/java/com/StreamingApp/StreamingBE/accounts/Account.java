package com.StreamingApp.StreamingBE.accounts;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.util.HashSet;
import java.util.Set;
import com.StreamingApp.StreamingBE.movies.MovieModel;

@Entity
@Table(name = "app_account")
@Getter @Setter
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true, length = 254)
    private String email;
    @Column(nullable = false)
    private String passwordHash;
    @ManyToMany
    @JoinTable(name = "saved_movie", joinColumns = @JoinColumn(name = "account_id"),
            inverseJoinColumns = @JoinColumn(name = "movie_id"),
            uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "movie_id"}))
    private Set<MovieModel> savedMovies = new HashSet<>();
}
