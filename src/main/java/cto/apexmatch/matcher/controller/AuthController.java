package cto.apexmatch.matcher.controller;

import cto.apexmatch.matcher.config.JwtUtil;
import cto.apexmatch.matcher.dto.AuthResponse;
import cto.apexmatch.matcher.dto.LoginRequest;
import cto.apexmatch.matcher.dto.RegisterRequest;
import cto.apexmatch.matcher.model.User;
import cto.apexmatch.matcher.service.UserService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@CrossOrigin(origins = "*", maxAge = 3600)
public class AuthController {

    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);

    private final UserService userService;
    private final JwtUtil jwtUtil;

    public AuthController(UserService userService, JwtUtil jwtUtil) {
        this.userService = userService;
        this.jwtUtil = jwtUtil;
    }

    /**
     * Register a new user
     * POST /api/auth/register
     */
    @PostMapping("/register")
    public ResponseEntity<?> register(@Valid @RequestBody RegisterRequest registerRequest) {
        try {
            // Validate passwords match
            if (!registerRequest.getPassword().equals(registerRequest.getConfirmPassword())) {
                return ResponseEntity.badRequest().body("Passwords do not match");
            }

            User user = userService.registerUser(registerRequest);
            String token = jwtUtil.generateToken(user.getEmail());

            AuthResponse authResponse = new AuthResponse(token, user.getId(), user.getEmail());
            logger.info("User registered successfully: {}", user.getEmail());
            return ResponseEntity.status(HttpStatus.CREATED).body(authResponse);

        } catch (IllegalArgumentException e) {
            logger.warn("Registration validation error: {}", e.getMessage());
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            logger.error("Registration failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Registration failed: " + e.getMessage());
        }
    }

    /**
     * Login user
     * POST /api/auth/login
     */
    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody LoginRequest loginRequest) {
        try {
            User user = userService.authenticateUser(loginRequest);
            String token = jwtUtil.generateToken(user.getEmail());

            AuthResponse authResponse = new AuthResponse(token, user.getId(), user.getEmail());
            logger.info("User logged in successfully: {}", user.getEmail());
            return ResponseEntity.ok(authResponse);

        } catch (Exception e) {
            logger.warn("Login failed for email: {}", loginRequest.getEmail());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body("Invalid credentials");
        }
    }

    /**
     * Health check endpoint
     * GET /api/auth/health
     */
    @GetMapping("/health")
    public ResponseEntity<?> health() {
        return ResponseEntity.ok().body("Auth service is running");
    }
}
