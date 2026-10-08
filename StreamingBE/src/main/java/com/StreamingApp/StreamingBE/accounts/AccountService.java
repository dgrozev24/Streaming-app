package com.StreamingApp.StreamingBE.accounts;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.StreamingApp.StreamingBE.movies.*;

@Service
@Transactional
public class AccountService {
    public record UserView(Long id, String email) {}
    public record LoginResult(String token, Instant expiresAt, UserView user) {}
    private final AccountRepository accounts;
    private final SessionRepository sessions;
    private final MovieRepository movies;
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final String dummyHash = passwords.encode(UUID.randomUUID().toString());
    private final SecureRandom random = new SecureRandom();

    public AccountService(AccountRepository accounts, SessionRepository sessions, MovieRepository movies) {
        this.accounts = accounts;
        this.sessions = sessions;
        this.movies = movies;
    }

    public LoginResult register(String email, String password) {
        if (password.length() < 10 || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a password of at least 10 characters and at most 72 UTF-8 bytes");
        }
        String normalized = email.strip().toLowerCase(Locale.ROOT);
        if (accounts.findByEmail(normalized).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
        }
        Account account = new Account();
        account.setEmail(normalized);
        account.setPasswordHash(passwords.encode(password));
        try {
            accounts.saveAndFlush(account);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with that email already exists");
        }
        return issueSession(account);
    }

    public LoginResult login(String email, String password) {
        Account account = accounts.findByEmail(email.strip().toLowerCase(Locale.ROOT)).orElse(null);
        boolean matches = password.getBytes(StandardCharsets.UTF_8).length <= 72
                && passwords.matches(password, account == null ? dummyHash : account.getPasswordHash());
        if (account == null || !matches) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Incorrect email or password");
        }
        return issueSession(account);
    }

    private LoginResult issueSession(Account account) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LoginSession session = new LoginSession();
        session.setTokenHash(hash(token));
        session.setAccount(account);
        session.setExpiresAt(Instant.now().plus(30, ChronoUnit.DAYS));
        sessions.save(session);
        return new LoginResult(token, session.getExpiresAt(), new UserView(account.getId(), account.getEmail()));
    }

    @Transactional(readOnly = true)
    public Optional<Long> authenticate(String token) {
        return sessions.findById(hash(token)).filter(s -> s.getExpiresAt().isAfter(Instant.now()))
                .map(s -> s.getAccount().getId());
    }

    public void logout(String token) {
        sessions.deleteById(hash(token));
    }

    @Transactional(readOnly = true)
    public UserView profile(Long id) {
        Account account = accounts.findById(id).orElseThrow();
        return new UserView(account.getId(), account.getEmail());
    }

    @Transactional(readOnly = true)
    public List<MovieResponse> saved(Long id) {
        return accounts.findById(id).orElseThrow().getSavedMovies().stream()
                .sorted(Comparator.comparingInt(MovieModel::getMovieId)).map(MovieResponse::from).toList();
    }

    public void saveMovie(Long id, int movieId, boolean save) {
        Account account = accounts.findForUpdate(id).orElseThrow();
        MovieModel movie = movies.findById(movieId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found"));
        if (save) account.getSavedMovies().add(movie);
        else account.getSavedMovies().removeIf(item -> item.getMovieId() == movieId);
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
