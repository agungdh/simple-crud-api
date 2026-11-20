package com.example.demo.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @NotBlank
        @Size(min = 3, max = 100)
        String username,

        @Size(min = 6, max = 255)
        String password,

        @NotBlank
        @Size(max = 255)
        String name
) {}
