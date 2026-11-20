package com.example.demo.storage;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriUtils;

import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.paginators.ListObjectsV2Iterable;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Service
@RequiredArgsConstructor
public class StorageService {

	private final S3Client s3;
	private final S3Presigner presigner;
	private final StorageProperties properties;

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

	public List<S3Object> list(Bucket bucket, String prefix) {
		ListObjectsV2Iterable pages = s3.listObjectsV2Paginator(
				request -> request.bucket(name(bucket)).prefix(normalizeKey(prefix)));
		List<S3Object> objects = new ArrayList<>();
		for (ListObjectsV2Response page : pages) {
			objects.addAll(page.contents());
		}
		return objects;
	}

	public int deleteMany(Bucket bucket, Collection<String> keys) {
		int deleted = 0;
		List<String> allKeys = List.copyOf(keys);
		for (int start = 0; start < allKeys.size(); start += 1000) {
			List<ObjectIdentifier> identifiers = allKeys
					.subList(start, Math.min(start + 1000, allKeys.size()))
					.stream()
					.map(key -> ObjectIdentifier.builder().key(normalizeKey(key)).build())
					.toList();
			s3.deleteObjects(DeleteObjectsRequest.builder()
					.bucket(name(bucket))
					.delete(Delete.builder().objects(identifiers).build())
					.build());
			deleted += identifiers.size();
		}
		return deleted;
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
		return presignPut(bucket, key, contentType, expiry, null);
	}

	public URI presignPut(Bucket bucket, String key, String contentType, Duration expiry, Long contentLength) {
		PutObjectPresignRequest request = PutObjectPresignRequest.builder()
				.signatureDuration(expiry)
				.putObjectRequest(putObjectRequest(bucket, key, contentType, contentLength))
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
		return putObjectRequest(bucket, key, contentType, null);
	}

	private PutObjectRequest putObjectRequest(Bucket bucket, String key, String contentType, Long contentLength) {
		PutObjectRequest.Builder builder = PutObjectRequest.builder()
				.bucket(name(bucket))
				.key(normalizeKey(key));
		if (contentType != null && !contentType.isBlank()) {
			builder.contentType(contentType);
		}
		if (contentLength != null) {
			builder.contentLength(contentLength);
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
