package com.example.backend.controller;

import com.example.backend.config.JwtUtil;
import com.example.backend.model.User;
import com.example.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AdminController(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/create-worker")
    public ResponseEntity<String> createWorker(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody WorkerRequest request) {

        String token = authHeader.replace("Bearer ", "");
        Long adminFactoryId = jwtUtil.extractFactoryId(token);

        // Strict validation for worker creation
        if (request.username() == null || request.username().isBlank() ||
                request.password() == null || request.password().isBlank() ||
                request.email() == null || request.email().isBlank()) {
            return ResponseEntity.status(400).body("Username, Password, and Email are required for worker creation.");
        }

        if (userRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(400).body("Username '" + request.username() + "' is already taken.");
        }

        User worker = new User();
        worker.setUsername(request.username());
        worker.setPassword(passwordEncoder.encode(request.password()));
        worker.setEmail(request.email());
        worker.setFactoryId(adminFactoryId);
        worker.setRole("ROLE_USER"); // Standard worker role

        userRepository.save(worker);

        return ResponseEntity.ok("Worker '" + request.username() + "' created successfully for Factory " + adminFactoryId);
    }
}

record WorkerRequest(String username, String password, String email) {}