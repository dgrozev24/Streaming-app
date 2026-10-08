package com.StreamingApp.StreamingBE.movies;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MovieRepository extends JpaRepository<MovieModel, Integer> {
}

