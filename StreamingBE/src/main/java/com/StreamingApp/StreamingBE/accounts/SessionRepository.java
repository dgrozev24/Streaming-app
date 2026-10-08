package com.StreamingApp.StreamingBE.accounts;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionRepository extends JpaRepository<LoginSession, String> {}
