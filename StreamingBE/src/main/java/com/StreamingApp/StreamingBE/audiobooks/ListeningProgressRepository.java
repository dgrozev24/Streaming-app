package com.StreamingApp.StreamingBE.audiobooks;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ListeningProgressRepository extends JpaRepository<ListeningProgress, Long> {
    Optional<ListeningProgress> findByAccountIdAndBookId(Long accountId, String bookId);
}
