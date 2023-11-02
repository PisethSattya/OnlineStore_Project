package co.devkh.onlinestore.reviewonlinestore.api.auth.web;

import jakarta.validation.constraints.NotBlank;

public record LoginDto(@NotBlank
                       String username,
                       @NotBlank
                       String password) {
}
