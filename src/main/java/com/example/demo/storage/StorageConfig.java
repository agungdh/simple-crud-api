package com.example.demo.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

	@Bean
	S3Client s3Client(StorageProperties properties) {
		return S3Client.builder()
				.endpointOverride(properties.endpoint())
				.region(Region.of(properties.region()))
				.credentialsProvider(credentialsProvider(properties))
				.serviceConfiguration(serviceConfiguration(properties))
				.build();
	}

	@Bean
	S3Presigner s3Presigner(StorageProperties properties) {
		return S3Presigner.builder()
				.endpointOverride(properties.endpoint())
				.region(Region.of(properties.region()))
				.credentialsProvider(credentialsProvider(properties))
				.serviceConfiguration(serviceConfiguration(properties))
				.build();
	}

	private static StaticCredentialsProvider credentialsProvider(StorageProperties properties) {
		return StaticCredentialsProvider.create(
				AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
	}

	private static S3Configuration serviceConfiguration(StorageProperties properties) {
		return S3Configuration.builder()
				.pathStyleAccessEnabled(properties.pathStyleAccess())
				.build();
	}

}
