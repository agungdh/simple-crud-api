package com.example.demo.upload;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.example.demo.storage.Bucket;
import com.example.demo.storage.StorageProperties;
import com.example.demo.storage.StorageService;

import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
class TempUploadServiceTest {

	@Mock
	private StorageService storage;

	private TempUploadService service;

	@BeforeEach
	void setUp() {
		service = new TempUploadService(storage, properties());
	}

	@Test
	void presignReturnsTemporaryKeyAndSignedUrl() {
		when(storage.presignPut(eq(Bucket.PRIVATE), anyString(), isNull(), eq(Duration.ofMinutes(15)), eq(123L)))
				.thenReturn(URI.create("http://localhost:9000/priv/tmp/upload?signature"));

		PresignUploadResponse response = service.presign(123L);

		assertThat(response.key())
				.matches("tmp/[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
		assertThat(response.uploadUrl()).isEqualTo("http://localhost:9000/priv/tmp/upload?signature");
		assertThat(response.expiresIn()).isEqualTo(900);
		verify(storage).presignPut(eq(Bucket.PRIVATE), eq(response.key()), isNull(),
				eq(Duration.ofMinutes(15)), eq(123L));
	}

	@Test
	void deleteExpiredDeletesOnlyObjectsOlderThanTtlPlusGrace() {
		Instant now = Instant.now();
		when(storage.list(Bucket.PRIVATE, "tmp/")).thenReturn(List.of(
				S3Object.builder().key("tmp/expired").lastModified(now.minus(Duration.ofMinutes(16))).build(),
				S3Object.builder().key("tmp/recent").lastModified(now.minus(Duration.ofMinutes(14))).build(),
				S3Object.builder().key("tmp/fresh").lastModified(now.minusSeconds(5)).build()));
		when(storage.deleteMany(Bucket.PRIVATE, List.of("tmp/expired"))).thenReturn(1);

		assertThat(service.deleteExpired()).isEqualTo(1);

		verify(storage).deleteMany(Bucket.PRIVATE, List.of("tmp/expired"));
	}

	@Test
	void deleteExpiredDoesNothingWhenAllObjectsAreFresh() {
		when(storage.list(Bucket.PRIVATE, "tmp/")).thenReturn(List.of(
				S3Object.builder().key("tmp/fresh").lastModified(Instant.now()).build()));

		assertThat(service.deleteExpired()).isZero();

		verify(storage, never()).deleteMany(Bucket.PRIVATE, List.of("tmp/fresh"));
	}

	private static StorageProperties properties() {
		return new StorageProperties(
				URI.create("http://localhost:9000"),
				"us-east-1",
				"access-key",
				"secret-key",
				true,
				Duration.ofMinutes(15),
				URI.create("http://localhost:9000/pub"),
				"priv",
				"pub",
				"tmp/",
				Duration.ofMinutes(15),
				Duration.ofMinutes(1),
				Duration.ofSeconds(30),
				true);
	}

}
