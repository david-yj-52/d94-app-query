package tsh.d94.app.server;

import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * 테스트 전용 부트 설정 진입점. 운영 진입점 {@code tsh.d94.query.QueryApplication} 은 package-private 이고
 * 테스트 패키지({@code tsh.d94.app.server.*})의 상위가 아니어서 {@code @SpringBootTest} 가 직접 찾지 못한다.
 * 이 클래스는 {@code tsh.d94.app.server} 에 있어 모든 테스트가 찾을 수 있고, 하는 일은 QueryApplication
 * 하나만 불러오는 것이다 → 스캔·엔티티·리포지토리 설정은 운영과 같은 곳(QueryApplication)에만 있다.
 */
@SpringBootConfiguration
@ComponentScan(
	basePackages = "tsh.d94.query",
	useDefaultFilters = false,
	includeFilters = @ComponentScan.Filter(type = FilterType.REGEX, pattern = "tsh\\.d94\\.query\\.QueryApplication")
)
public class D94QueryTestApplication {
}
