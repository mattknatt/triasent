package org.example.userservice.service;

import lombok.RequiredArgsConstructor;
import org.example.userservice.model.UserEntity;
import org.example.userservice.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    public void saveUser(UserEntity user) {
        userRepository.save(user);
    }

    public UserEntity getUserById(UUID id) {
        return userRepository.findById(id).orElse(null);
    }

    public List<UserEntity> getAllUsers() {
        return userRepository.findAll();
    }
}
