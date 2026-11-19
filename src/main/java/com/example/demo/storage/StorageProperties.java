package com.example.demo.storage;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.storage")
public record StorageProperties(
		URI endpoint,
		String region,
		String accessKey,
		String secretKey,
		boolean pathStyleAccess,
		Duration presignExpiry,
		URI publicBaseUrl,
		String privateBucket,
		String publicBucket,
		String tempPrefix,
		Duration tempTtl,
		Duration tempCleanupInterval,
		Duration tempCleanupGrace,
		Boolean tempCleanupEnabled) {

	public StorageProperties {
		if (region == null || region.isBlank()) {
			region = "us-east-1";
		}
		if (presignExpiry == null) {
			presignExpiry = Duration.ofMinutes(15);
		}
		if (tempPrefix == null || tempPrefix.isBlank()) {
			tempPrefix = "tmp/";
		}
		tempPrefix = tempPrefix.replace('\\', '/');
		while (tempPrefix.startsWith("/")) {
			tempPrefix = tempPrefix.substring(1);
		}
		if (!tempPrefix.endsWith("/")) {
			tempPrefix = tempPrefix + "/";
		}
		if (tempTtl == null) {
			tempTtl = Duration.ofMinutes(15);
		}
		if (tempCleanupInterval == null) {
			tempCleanupInterval = Duration.ofMinutes(1);
		}
		if (tempCleanupGrace == null) {
			tempCleanupGrace = Duration.ofSeconds(30);
		}
		if (tempCleanupEnabled == null) {
			tempCleanupEnabled = Boolean.TRUE;
		}
	}

}
