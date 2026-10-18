package tsh.d94.app.server.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.minio.MinioClient;
import lombok.RequiredArgsConstructor;

@Configuration
@RequiredArgsConstructor
public class StorageConfig {

	private final StorageProperties props;   // cira.storage 속성 주입

	@Bean
	public MinioClient minioClient() {
		return MinioClient.builder()
			.endpoint(props.getEndpoint())
			.credentials(props.getAccessKey(), props.getSecretKey())
			.build();
	}
}