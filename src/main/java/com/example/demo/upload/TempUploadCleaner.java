package com.example.demo.upload;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.storage.StorageProperties;

@Slf4j
@Component
@RequiredArgsConstructor
public class TempUploadCleaner {

	private final TempUploadService tempUploads;
	private final StorageProperties properties;

	@Scheduled(fixedDelayString = "${app.storage.temp-cleanup-interval:1m}",
			initialDelayString = "${app.storage.temp-cleanup-interval:1m}")
	public void cleanup() {
		if (!Boolean.TRUE.equals(properties.tempCleanupEnabled())) {
			return;
		}
		try {
			int deleted = tempUploads.deleteExpired();
			if (deleted > 0) {
				log.info("Deleted {} expired temporary upload(s)", deleted);
			}
		}
		catch (RuntimeException e) {
			log.warn("Failed to clean up temporary uploads", e);
		}
	}

}
