package org.example.userservice;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class UserController {

    @GetMapping("/api/test")
    public String getTest(@RequestHeader(value = "X-User-Name", defaultValue = "anonymous") String username) {
        return "Hej " + username;
    }
}