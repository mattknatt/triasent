package org.example.userservice.service;

import lombok.RequiredArgsConstructor;
import org.example.userservice.model.UserEntity;
import org.example.userservice.repository.UserRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserEntity saveUser(UserEntity user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public UserEntity getUserById(UUID id) {
        return userRepository.findById(id).orElse(null);
    }

    public List<UserEntity> getAllUsers() {
        return userRepository.findAll();
    }

    public UserEntity findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    /** Partial update: only the provided (non-null) fields are changed. Returns null if not found. */
    public UserEntity updateUser(UUID id, UserEntity changes) {
        UserEntity existing = userRepository.findById(id).orElse(null);
        if (existing == null) {
            return null;
        }
        if (changes.getUsername() != null) {
            existing.setUsername(changes.getUsername());
        }
        if (changes.getRole() != null) {
            existing.setRole(changes.getRole());
        }
        if (changes.getPassword() != null && !changes.getPassword().isBlank()) {
            existing.setPassword(passwordEncoder.encode(changes.getPassword()));
        }
        return userRepository.save(existing);
    }

    /** Returns false if no such user existed. */
    public boolean deleteUser(UUID id) {
        if (!userRepository.existsById(id)) {
            return false;
        }
        userRepository.deleteById(id);
        return true;
    }
}
