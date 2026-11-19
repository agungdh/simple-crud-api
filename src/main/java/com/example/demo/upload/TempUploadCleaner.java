package com.example.demo.upload;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.example.demo.storage.StorageProperties;

@Component
public class TempUploadCleaner {

	private static final Logger log = LoggerFactory.getLogger(TempUploadCleaner.class);

	private final TempUploadService tempUploads;
	private final StorageProperties properties;

	public TempUploadCleaner(TempUploadService tempUploads, StorageProperties properties) {
		this.tempUploads = tempUploads;
		this.properties = properties;
	}

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
