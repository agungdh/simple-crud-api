package com.example.demo.upload;

public record PresignUploadResponse(String key, String uploadUrl, long expiresIn) {

}
