package com.example.demo.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;

import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
public class StorageService {

	private final S3Client s3;
	private final S3Presigner presigner;
	private final StorageProperties properties;

	public StorageService(S3Client s3, S3Presigner presigner, StorageProperties properties) {
		this.s3 = s3;
		this.presigner = presigner;
		this.properties = properties;
	}

	public void put(Bucket bucket, String key, Path file, String contentType) {
		s3.putObject(putObjectRequest(bucket, key, contentType), RequestBody.fromFile(file));
	}

	public void putBytes(Bucket bucket, String key, byte[] content, String contentType) {
		s3.putObject(putObjectRequest(bucket, key, contentType), RequestBody.fromBytes(content));
	}

	public ResponseInputStream<GetObjectResponse> get(Bucket bucket, String key) {
		return s3.getObject(getObjectRequest(bucket, key));
	}

	public byte[] getBytes(Bucket bucket, String key) {
		try (ResponseInputStream<GetObjectResponse> stream = get(bucket, key)) {
			return stream.readAllBytes();
		}
		catch (IOException e) {
			throw new UncheckedIOException("failed to read s3://" + name(bucket) + "/" + key, e);
		}
	}

	public boolean exists(Bucket bucket, String key) {
		try {
			s3.headObject(request -> request.bucket(name(bucket)).key(normalizeKey(key)));
			return true;
		}
		catch (NoSuchKeyException e) {
			return false;
		}
	}

	public void delete(Bucket bucket, String key) {
		s3.deleteObject(request -> request.bucket(name(bucket)).key(normalizeKey(key)));
	}

	public URI presignGet(Bucket bucket, String key) {
		return presignGet(bucket, key, properties.presignExpiry());
	}

	public URI presignGet(Bucket bucket, String key, Duration expiry) {
		GetObjectPresignRequest request = GetObjectPresignRequest.builder()
				.signatureDuration(expiry)
				.getObjectRequest(getObjectRequest(bucket, key))
				.build();
		return URI.create(presigner.presignGetObject(request).url().toString());
	}

	public URI presignPut(Bucket bucket, String key, String contentType) {
		return presignPut(bucket, key, contentType, properties.presignExpiry());
	}

	public URI presignPut(Bucket bucket, String key, String contentType, Duration expiry) {
		PutObjectPresignRequest request = PutObjectPresignRequest.builder()
				.signatureDuration(expiry)
				.putObjectRequest(putObjectRequest(bucket, key, contentType))
				.build();
		return URI.create(presigner.presignPutObject(request).url().toString());
	}

	/**
	 * Direct URL for an object in the public bucket.
	 */
	public URI publicUrl(String key) {
		String base = properties.publicBaseUrl().toString().replaceAll("/+$", "");
		return URI.create(base + "/" + UriUtils.encodePath(normalizeKey(key), StandardCharsets.UTF_8));
	}

	private GetObjectRequest getObjectRequest(Bucket bucket, String key) {
		return GetObjectRequest.builder()
				.bucket(name(bucket))
				.key(normalizeKey(key))
				.build();
	}

	private PutObjectRequest putObjectRequest(Bucket bucket, String key, String contentType) {
		PutObjectRequest.Builder builder = PutObjectRequest.builder()
				.bucket(name(bucket))
				.key(normalizeKey(key));
		if (contentType != null && !contentType.isBlank()) {
			builder.contentType(contentType);
		}
		return builder.build();
	}

	private String name(Bucket bucket) {
		return switch (bucket) {
			case PRIVATE -> properties.privateBucket();
			case PUBLIC -> properties.publicBucket();
		};
	}

	private static String normalizeKey(String key) {
		if (key == null || key.isBlank()) {
			throw new IllegalArgumentException("key must not be blank");
		}
		String normalized = key.replace('\\', '/');
		while (normalized.startsWith("/")) {
			normalized = normalized.substring(1);
		}
		boolean invalid = normalized.isEmpty()
				|| Arrays.stream(normalized.split("/")).anyMatch(segment -> segment.equals(".."));
		if (invalid) {
			throw new IllegalArgumentException("invalid key: " + key);
		}
		return normalized;
	}

}
