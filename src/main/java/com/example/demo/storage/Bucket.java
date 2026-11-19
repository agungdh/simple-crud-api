package com.example.demo.storage;

/**
 * Logical buckets used by the application. {@link #PRIVATE} objects are only
 * reachable through presigned URLs, while {@link #PUBLIC} objects are
 * anonymously readable.
 */
public enum Bucket {

	PRIVATE,
	PUBLIC

}
