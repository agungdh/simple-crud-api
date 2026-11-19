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
		String publicBucket) {

	public StorageProperties {
		if (region == null || region.isBlank()) {
			region = "us-east-1";
		}
		if (presignExpiry == null) {
			presignExpiry = Duration.ofMinutes(15);
		}
	}

}
