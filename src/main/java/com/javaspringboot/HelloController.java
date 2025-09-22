package com.javaspringboot.first.appl;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class HelloController {

    @GetMapping("/")
    public String home() {
        return "✅ Spring Boot App is Running!";
    }

    @GetMapping("/status")
    public String status() {
        return "App deployed and working fine.";
    }
}
