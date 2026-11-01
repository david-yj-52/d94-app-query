package tsh.d94.app.server.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합 테스트용 PostgreSQL. 운영(delap tsh-postgres)과 같은 {@code postgres:16}.
 * 컨테이너는 JVM 당 한 번만 띄우고 모든 Spring 컨텍스트가 공유한다 (static 필드).
 * {@code @ServiceConnection} 이 spring.datasource.* 를 컨테이너 값으로 채운다.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestContainer {

	static final PostgreSQLContainer<?> POSTGRES =
		new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"))
			.withDatabaseName("d94_test")
			.withUsername("d94")
			.withPassword("d94");

	static {
		POSTGRES.start();
	}

	@Bean
	@ServiceConnection
	PostgreSQLContainer<?> postgresContainer() {
		return POSTGRES;
	}

}
