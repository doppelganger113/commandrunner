package com.doppelganger113.commandrunner;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("users")
public class UsersController {

    @GetMapping
    public List<User> getHello() {
        return List.of(
                new User("John", 32),
                new User("Ani", 34)
        );
    }
}
