package com.nikit.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HomeController {
    @GetMapping
    public String home() {

        return "Welcome to trading platform";
    }

    @GetMapping("/api")
    public String secure() {
        return "welcome to treading platform Secure";
    }
}