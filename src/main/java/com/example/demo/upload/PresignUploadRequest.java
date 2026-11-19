package com.example.demo.upload;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record PresignUploadRequest(@NotNull @Positive Long size) {

}
