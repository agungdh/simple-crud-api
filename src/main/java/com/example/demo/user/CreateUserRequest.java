package com.example.demo.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequest(
        @NotBlank
        @Size(min = 3, max = 100)
        String username,

        @NotBlank
        @Size(min = 6, max = 255)
        String password,

        @NotBlank
        @Size(max = 255)
        String name
) {}
