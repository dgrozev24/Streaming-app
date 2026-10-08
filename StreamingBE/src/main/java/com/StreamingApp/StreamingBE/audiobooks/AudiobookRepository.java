package com.StreamingApp.StreamingBE.audiobooks;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AudiobookRepository extends JpaRepository<Audiobook, String> {}
