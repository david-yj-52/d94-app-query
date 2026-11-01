package tsh.d94.app.server.support;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

/**
 * 전체 컨텍스트 + 실제 PostgreSQL(Testcontainers) 통합 테스트. 클래스 이름은 {@code *IT} (failsafe 가 실행).
 * 스키마는 운영과 같게 Flyway 적용 후 {@code ddl-auto=update}. 설정: {@code application-test.yml}.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@SpringBootTest
@ActiveProfiles("test")
@Import(PostgresTestContainer.class)
public @interface IntegrationTest {
}
