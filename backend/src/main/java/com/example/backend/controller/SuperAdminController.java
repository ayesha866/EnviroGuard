package com.example.backend.controller;
import com.example.backend.model.User;
import com.example.backend.repository.UserRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

@CrossOrigin(origins = "http://localhost:5173")
@RestController
@RequestMapping("/api/superadmin")
public class SuperAdminController {private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SuperAdminController(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // Endpoint to create a new Factory Admin
    @PostMapping("/create-admin")
    public ResponseEntity<String> createFactoryAdmin(@RequestBody AdminRequest request) {

        // Note: In JwtFilter, you would ensure only ROLE_SUPER_ADMIN can hit this endpoint.

        if (userRepository.findByUsername(request.username()).isPresent()) {
            return ResponseEntity.status(400).body("Username already exists");
        }

        User newAdmin = new User();
        newAdmin.setUsername(request.username());
        newAdmin.setPassword(passwordEncoder.encode(request.password()));
        newAdmin.setRole("ROLE_ADMIN"); // Elevate to Admin immediately
        newAdmin.setFactoryId(request.factoryId()); // Assign to specific factory

        userRepository.save(newAdmin);

        return ResponseEntity.ok("Factory Admin created successfully for Factory ID: " + request.factoryId());
    }



}
record AdminRequest(String username, String password, Long factoryId) {}
