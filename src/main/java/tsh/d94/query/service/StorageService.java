package tsh.d94.app.server.service;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import tsh.d94.app.server.config.StorageProperties;

import io.minio.BucketExistsArgs;
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
