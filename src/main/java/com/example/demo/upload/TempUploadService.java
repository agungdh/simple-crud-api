package com.example.demo.upload;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.example.demo.storage.Bucket;
import com.example.demo.storage.StorageProperties;
import com.example.demo.storage.StorageService;

import software.amazon.awssdk.services.s3.model.S3Object;

@Service
public class TempUploadService {

	private final StorageService storage;
	private final StorageProperties properties;

	public TempUploadService(StorageService storage, StorageProperties properties) {
		this.storage = storage;
		this.properties = properties;
	}

	public PresignUploadResponse presign(long size) {
		String key = properties.tempPrefix() + UUID.randomUUID();
		Duration expiry = properties.presignExpiry();
		String uploadUrl = storage.presignPut(Bucket.PRIVATE, key, null, expiry, size).toString();
		return new PresignUploadResponse(key, uploadUrl, expiry.toSeconds());
	}

	public int deleteExpired() {
		Instant cutoff = Instant.now()
				.minus(properties.tempTtl())
				.minus(properties.tempCleanupGrace());
		List<String> expired = storage.list(Bucket.PRIVATE, properties.tempPrefix())
				.stream()
				.filter(object -> object.lastModified() != null && object.lastModified().isBefore(cutoff))
				.map(S3Object::key)
				.toList();
		if (expired.isEmpty()) {
			return 0;
		}
		return storage.deleteMany(Bucket.PRIVATE, expired);
	}

}
