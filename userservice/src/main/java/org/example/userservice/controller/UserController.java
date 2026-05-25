package org.example.userservice.controller;

import lombok.RequiredArgsConstructor;
import org.example.userservice.model.UserEntity;
import org.example.userservice.service.UserService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @PostMapping("/users")
    public UserEntity createUser(@RequestBody UserEntity user) {
        return userService.saveUser(user);
    }

    @GetMapping("/users/{id}")
    public UserEntity getUser(@PathVariable UUID id) {
        return userService.getUserById(id);
    }

    @GetMapping("/users")
    public List<UserEntity> getAllUsers() {
        return userService.getAllUsers();
    }

}