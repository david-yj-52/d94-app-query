package tsh.d94.app.server.service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import tsh.d94.app.server.config.StorageProperties;

import io.minio.BucketExistsArgs;
import io.minio.ListObjectsArgs;
import io.minio.Result;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;
import io.minio.messages.Item;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class StorageService {

	private final MinioClient minioClient;
	private final StorageProperties storageProperties;

	public String upload(MultipartFile file, String path) {
		try {
			ensureBucketExists();

			try (InputStream is = file.getInputStream()) {
				minioClient.putObject(
					PutObjectArgs.builder()
						.bucket(storageProperties.getBucket())
						.object(path)
						.stream(is, file.getSize(), -1)
						.contentType(file.getContentType())
						.build()
				);
			}
			return path;
		} catch (Exception e) {
			log.error("MinIO upload failed: path={}", path, e);
			throw new RuntimeException("파일 업로드에 실패했습니다: " + e.getMessage(), e);
		}
	}

	public String generatePresignedUrl(String s3Key) {
		try {
			return minioClient.getPresignedObjectUrl(
				GetPresignedObjectUrlArgs.builder()
					.bucket(storageProperties.getBucket())
					.object(s3Key)
					.method(Method.GET)
					.expiry(15, TimeUnit.MINUTES)
					.build()
			);
		} catch (Exception e) {
			log.error("MinIO presigned URL generation failed: key={}", s3Key, e);
			throw new RuntimeException("다운로드 URL 생성에 실패했습니다: " + e.getMessage(), e);
		}
	}

	public InputStream getObjectStream(String s3Key) {
		try {
			return minioClient.getObject(
				GetObjectArgs.builder()
					.bucket(storageProperties.getBucket())
					.object(s3Key)
					.build()
			);
		} catch (Exception e) {
			log.error("MinIO getObject failed: key={}", s3Key, e);
			throw new RuntimeException("파일 조회에 실패했습니다: " + e.getMessage(), e);
		}
	}

	public void delete(String s3Key) {
		try {
			minioClient.removeObject(
				RemoveObjectArgs.builder()
					.bucket(storageProperties.getBucket())
					.object(s3Key)
					.build()
			);
		} catch (Exception e) {
			log.error("MinIO delete failed: key={}", s3Key, e);
			throw new RuntimeException("파일 삭제에 실패했습니다: " + e.getMessage(), e);
		}
	}

	public void putBytes(String key, byte[] data, String contentType) {
		putStream(key, new ByteArrayInputStream(data), data.length, contentType);
	}

	public void putStream(String key, InputStream in, long size, String contentType) {
		try {
			ensureBucketExists();
			minioClient.putObject(PutObjectArgs.builder()
				.bucket(storageProperties.getBucket()).object(key)
				.stream(in, size, -1).contentType(contentType).build());
		} catch (Exception e) {
			log.error("MinIO put failed: key={}", key, e);
			throw new RuntimeException("파일 저장에 실패했습니다: " + e.getMessage(), e);
		}
	}

	/** 객체 크기. 없으면 -1 */
	public long objectSize(String key) {
		try {
			return minioClient.statObject(StatObjectArgs.builder()
				.bucket(storageProperties.getBucket()).object(key).build()).size();
		} catch (ErrorResponseException e) {
			return -1;
		} catch (Exception e) {
			throw new RuntimeException("파일 조회에 실패했습니다: " + e.getMessage(), e);
		}
	}

	public void deletePrefix(String prefix) {
		try {
			for (Result<Item> r : minioClient.listObjects(ListObjectsArgs.builder()
				.bucket(storageProperties.getBucket()).prefix(prefix).recursive(true).build())) {
				delete(r.get().objectName());
			}
		} catch (Exception e) {
			log.error("MinIO prefix delete failed: prefix={}", prefix, e);
			throw new RuntimeException("파일 삭제에 실패했습니다: " + e.getMessage(), e);
		}
	}

	private void ensureBucketExists() throws Exception {
		boolean exists = minioClient.bucketExists(
			BucketExistsArgs.builder().bucket(storageProperties.getBucket()).build()
		);
		if (!exists) {
			minioClient.makeBucket(
				MakeBucketArgs.builder().bucket(storageProperties.getBucket()).build()
			);
			log.info("MinIO bucket created: {}", storageProperties.getBucket());
		}
	}

}
