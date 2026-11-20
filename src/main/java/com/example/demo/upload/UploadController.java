package com.example.demo.upload;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/uploads")
@RequiredArgsConstructor
public class UploadController {

	private final TempUploadService tempUploads;

	@PostMapping("/presign")
	public PresignUploadResponse presign(@Valid @RequestBody PresignUploadRequest request) {
		return tempUploads.presign(request.size());
	}

}
