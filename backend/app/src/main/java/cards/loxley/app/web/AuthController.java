package cards.loxley.app.web;

import cards.loxley.app.security.JwtTokenProvider;
import cards.loxley.app.web.dto.AuthRequest;
import cards.loxley.app.web.dto.AuthResponse;
import cards.loxley.db.User;
import cards.loxley.db.UserRepository;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[a-zA-Z][a-zA-Z0-9_-]{2,29}$");
    private static final int MIN_PASSWORD_LENGTH = 8;
    private static final Duration JWT_COOKIE_MAX_AGE = Duration.ofDays(7);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final boolean cookieSecure;

    public AuthController(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          JwtTokenProvider tokenProvider,
                          @Value("${app.cookie.secure}") boolean cookieSecure) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.cookieSecure = cookieSecure;
    }

    @GetMapping("/me")
    public ResponseEntity<AuthResponse> me() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        return ResponseEntity.ok(new AuthResponse(authentication.getName()));
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@RequestBody AuthRequest request,
                                                  HttpServletResponse response) {
        validateCredentials(request);

        if (userRepository.findByUsername(request.username()).isPresent()) {
            throw new UsernameAlreadyExistsException(request.username());
        }

        var user = new User(request.username(), passwordEncoder.encode(request.password()));
        userRepository.save(user);

        addJwtCookie(response, tokenProvider.generateToken(request.username()));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new AuthResponse(request.username()));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody AuthRequest request,
                                               HttpServletResponse response) {
        var user = userRepository.findByUsername(request.username())
                .orElseThrow(() -> new BadCredentialsException("Bad credentials"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadCredentialsException("Bad credentials");
        }

        addJwtCookie(response, tokenProvider.generateToken(request.username()));
        return ResponseEntity.ok(new AuthResponse(request.username()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response) {
        var cookie = ResponseCookie.from("jwt", "")
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok().build();
    }

    private void validateCredentials(AuthRequest request) {
        if (request.username() == null || !USERNAME_PATTERN.matcher(request.username()).matches()) {
            throw new IllegalArgumentException(
                    "Username must be 3-30 characters: letters, digits, underscore, or hyphen; must start with a letter.");
        }
        if (request.password() == null || request.password().length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalArgumentException(
                    "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");
        }
    }

    private void addJwtCookie(HttpServletResponse response, String token) {
        var cookie = ResponseCookie.from("jwt", token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Lax")
                .path("/")
                .maxAge(JWT_COOKIE_MAX_AGE)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
