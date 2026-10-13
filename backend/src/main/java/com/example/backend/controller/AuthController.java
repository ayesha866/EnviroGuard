package com.example.backend.controller;

import com.example.backend.config.JwtUtil;
import com.example.backend.model.Factory;
import com.example.backend.model.User;
import com.example.backend.repository.FactoryRepository;
import com.example.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;
    private final FactoryRepository factoryRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthController(JwtUtil jwtUtil, UserRepository userRepository,
                          FactoryRepository factoryRepository, PasswordEncoder passwordEncoder) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
        this.factoryRepository = factoryRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/signup")
    public ResponseEntity<String> signup(@RequestBody SignupRequest request) {
        // Strict Validation: Reject if any field is missing or empty
        if (request.username() == null || request.username().isBlank() ||
                request.password() == null || request.password().isBlank() ||
                request.email() == null || request.email().isBlank() ||
                request.factoryName() == null || request.factoryName().isBlank()) {
            return ResponseEntity.status(400).body("Error: Username, Email, Password, and Factory Name are all required.");
        }

        if (userRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(400).body("Error: Username is already taken.");
        }

        // 1. Create a new Factory entry in the database
        Factory factory = new Factory();
        factory.setName(request.factoryName());
        Factory savedFactory = factoryRepository.save(factory); // Database auto-generates unique factoryId

        // 2. Create the Factory Admin (Manager)
        User adminUser = new User();
        adminUser.setUsername(request.username());
        adminUser.setPassword(passwordEncoder.encode(request.password()));
        adminUser.setEmail(request.email());
        adminUser.setFactoryId(savedFactory.getId());
        adminUser.setRole("ROLE_ADMIN");

        userRepository.save(adminUser);

        return ResponseEntity.ok("Factory registered successfully! Your assigned Factory ID is: " + savedFactory.getId() + ". Save this ID for logging in.");
    }

    @PostMapping("/login")
    public ResponseEntity<String> login(@RequestBody LoginRequest request) {
        if (request.username() == null || request.username().isBlank() ||
                request.password() == null || request.password().isBlank() ||
                request.factoryId() == null) {
            return ResponseEntity.status(400).body("Error: Username, Password, and Factory ID are required.");
        }

        Optional<User> userOptional = userRepository.findByUsername(request.username());

        if (userOptional.isPresent()) {
            User user = userOptional.get();
            // Validate password and verify the user belongs to the specified Factory ID
            if (passwordEncoder.matches(request.password(), user.getPassword()) &&
                    request.factoryId().equals(user.getFactoryId())) {

                String token = jwtUtil.generateToken(user.getUsername(), user.getRole(), user.getFactoryId());
                return ResponseEntity.ok(token);
            }
        }
        return ResponseEntity.status(401).body("Invalid credentials or incorrect Factory ID.");
    }
}

record SignupRequest(String username, String password, String email, String factoryName) {}
record LoginRequest(String username, String password, Long factoryId) {}