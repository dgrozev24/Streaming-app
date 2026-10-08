package com.StreamingApp.StreamingBE.accounts;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import com.StreamingApp.StreamingBE.movies.MovieResponse;

@RestController
@RequestMapping("/api")
public class AccountController {
    public record Credentials(@NotBlank @Email @Size(max = 254) String email,
                              @NotBlank @Size(max = 128) String password) {}
    private final AccountService accounts;
    public AccountController(AccountService accounts) { this.accounts = accounts; }

    @PostMapping("/auth/register") @ResponseStatus(HttpStatus.CREATED)
    public AccountService.LoginResult register(@Valid @RequestBody Credentials body) {
        return accounts.register(body.email(), body.password());
    }
    @PostMapping("/auth/login")
    public AccountService.LoginResult login(@Valid @RequestBody Credentials body) {
        return accounts.login(body.email(), body.password());
    }
    @GetMapping("/account")
    public AccountService.UserView profile(@AuthenticationPrincipal Long id) { return accounts.profile(id); }

    @PostMapping("/auth/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader("Authorization") String header) { accounts.logout(header.substring(7)); }

    @GetMapping("/account/saved")
    public List<MovieResponse> saved(@AuthenticationPrincipal Long id) { return accounts.saved(id); }

    @PutMapping("/account/saved/{movieId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void save(@AuthenticationPrincipal Long id, @PathVariable int movieId) {
        accounts.saveMovie(id, movieId, true);
    }
    @DeleteMapping("/account/saved/{movieId}") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@AuthenticationPrincipal Long id, @PathVariable int movieId) {
        accounts.saveMovie(id, movieId, false);
    }
}
