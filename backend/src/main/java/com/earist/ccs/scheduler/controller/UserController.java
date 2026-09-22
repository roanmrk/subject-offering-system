package com.earist.ccs.scheduler.controller;

import com.earist.ccs.scheduler.model.User;
import com.earist.ccs.scheduler.repository.UserRepository;
import com.earist.ccs.scheduler.util.JwtUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/admin/users")
@Slf4j
public class UserController {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(@RequestBody Map<String, String> credentials) {
        String email = credentials.get("email");
        String password = credentials.get("password");

        log.info("Login attempt: {}", email);

        Map<String, Object> response = new HashMap<>();
        Optional<User> userOpt = userRepository.findByEmail(email);

        if (userOpt.isPresent()) {
            User user = userOpt.get();
            String storedPassword = user.getPassword();

            boolean matches = false;

            if (storedPassword != null && storedPassword.startsWith("$2")) {
                matches = passwordEncoder.matches(password, storedPassword);
                log.info("Checked BCrypt hash for {}: {}", email, matches);
            } else {
                matches = password != null && password.equals(storedPassword);
                log.info("Checked plaintext password for {}: {}", email, matches);
                if (matches) {
                    user.setPassword(passwordEncoder.encode(password));
                    userRepository.save(user);
                    log.info("🔐 Upgraded password to BCrypt: {}", email);
                }
            }

            if (matches) {
                String token = jwtUtil.generateToken(user.getEmail(), user.getRole());

                response.put("success", true);
                response.put("message", "Login successful");
                response.put("token", token);

                Map<String, Object> userData = new HashMap<>();
                userData.put("id", user.getId());
                userData.put("email", user.getEmail());
                userData.put("name", user.getName());
                userData.put("role", user.getRole());
                userData.put("status", user.getStatus());
                response.put("user", userData);

                log.info("✅ Login successful: {}", email);
                return ResponseEntity.ok(response);
            } else {
                try { Thread.sleep(800); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                log.warn("❌ Password mismatch for {}", email);
            }
        } else {
            try { Thread.sleep(800); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
            log.warn("❌ No user found with email: {}", email);
        }

        response.put("success", false);
        response.put("message", "Invalid email or password");
        return ResponseEntity.status(401).body(response);
    }

    @GetMapping
    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    @PostMapping
    public ResponseEntity<?> addUser(@RequestBody User user) {
        try {
            if (userRepository.findByEmail(user.getEmail()).isPresent()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Email already exists"));
            }
            if (user.getStatus() == null) {
                user.setStatus("active");
            }
            if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                user.setPassword(passwordEncoder.encode(user.getPassword()));
            }
            User saved = userRepository.save(user);
            log.info("User created: {}", saved.getEmail());
            return ResponseEntity.ok(saved);
        } catch (Exception e) {
            log.error("Error creating user: {}", e.getMessage());
            return ResponseEntity.status(500).body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateUser(@PathVariable Long id, @RequestBody User user) {
        return userRepository.findById(id)
            .map(existing -> {
                existing.setName(user.getName());
                existing.setEmail(user.getEmail());
                existing.setRole(user.getRole());
                if (user.getStatus() != null) {
                    existing.setStatus(user.getStatus());
                }
                if (user.getPassword() != null && !user.getPassword().isEmpty()) {
                    existing.setPassword(passwordEncoder.encode(user.getPassword()));
                }
                User updated = userRepository.save(existing);
                log.info("User updated: {}", updated.getEmail());
                return ResponseEntity.ok(updated);
            })
            .orElse(ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        if (userRepository.existsById(id)) {
            userRepository.deleteById(id);
            log.info("User deleted: {}", id);
            return ResponseEntity.ok(Map.of("message", "User deleted"));
        }
        return ResponseEntity.notFound().build();
    }
}