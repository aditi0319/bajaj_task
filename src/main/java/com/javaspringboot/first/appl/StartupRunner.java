package com.javaspringboot.first.appl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

@Component
public class StartupRunner implements ApplicationRunner {

    private final RestTemplate rest;
    private final ObjectMapper mapper = new ObjectMapper();

    public StartupRunner(RestTemplate rest) {
        this.rest = rest;
    }

    // Values from application.properties
    @Value("${app.name}") private String name;
    @Value("${app.regNo}") private String regNo;
    @Value("${app.email}") private String email;

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("=== Starting automatic flow ===");

        // Debug print
        System.out.println("Loaded values -> name=" + name + ", regNo=" + regNo + ", email=" + email);

        // Build request body for generateWebhook
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("regNo", regNo);
        body.put("email", email);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            // Step 1: Call generateWebhook
            ResponseEntity<String> response = rest.exchange(
                    "https://bfhldevapigw.healthrx.co.in/hiring/generateWebhook/JAVA",
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            System.out.println("Response status: " + response.getStatusCode());
            System.out.println("Response body: " + response.getBody());

            if (response.getStatusCode().is2xxSuccessful()) {
                JsonNode root = mapper.readTree(response.getBody());
                String webhook = root.get("webhook").asText();
                String token = root.get("accessToken").asText();

                System.out.println("Received webhook: " + webhook);
                System.out.println("Received accessToken: " + token);

                // Step 2: Load SQL from resources
                String sqlContent;
                try {
                    sqlContent = new String(
                            Objects.requireNonNull(
                                getClass().getClassLoader().getResourceAsStream("final-query.sql")
                            ).readAllBytes(),
                            StandardCharsets.UTF_8
                    );
                    System.out.println("Loaded SQL:\n" + sqlContent);
                } catch (Exception e) {
                    throw new RuntimeException("Could not load final-query.sql from resources!", e);
                }

                // Step 3: Build solution body
                Map<String, Object> solution = new HashMap<>();
                solution.put("answer", sqlContent.trim());

                HttpHeaders solHeaders = new HttpHeaders();
                solHeaders.setContentType(MediaType.APPLICATION_JSON);
                solHeaders.add("Authorization", "Bearer " + token); // Explicit

                HttpEntity<Map<String, Object>> solEntity = new HttpEntity<>(solution, solHeaders);

                // Debug
                System.out.println("Sending to webhook: " + mapper.writeValueAsString(solution));
                System.out.println("Auth Header: Bearer " + token);

                // Step 4: Send solution to webhook
                ResponseEntity<String> solResponse = rest.exchange(
                        webhook,
                        HttpMethod.POST,
                        solEntity,
                        String.class
                );

                System.out.println("Webhook response status: " + solResponse.getStatusCode());
                System.out.println("Webhook response body: " + solResponse.getBody());

            } else {
                System.out.println("generateWebhook failed: " + response.getBody());
            }

        } catch (Exception e) {
            System.out.println("Error during automatic flow: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
