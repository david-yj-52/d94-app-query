# src/test — 테스트 작성 규칙 (요약)

상세: [docs/testing.md](../../docs/testing.md) · 최근 결과와 결함 목록: [docs/test-report.md](../../docs/test-report.md)

- **위치**: 선언 패키지 기준 `src/test/java/tsh/d94/app/server/<하위 경로>/`. 파일 폴더(`tsh/d94/query`)가 아니라 `package` 선언을 따른다.
- **이름**: 단위 `XxxTest` (surefire), 통합 `XxxIT` (failsafe, Testcontainers `postgres:16`).
- **단위**: `@ExtendWith(MockitoExtension.class)` + `@Mock`/`@InjectMocks`. `objId` 는 `ReflectionTestUtils.setField(m, "objId", id)`.
- **통합**: `@IntegrationTest`. 테스트 메서드에 `@Transactional` 을 걸지 않는다(서비스가 직접 커밋). 결과는 `JdbcTemplate` 으로 DB 에서 다시 읽는다.
- **컨텍스트**: `TestContexts.login(email[, tenant])` / `TestContexts.clear()`. 데이터는 `CiraFixtures` (고유 이메일·프로젝트 키로 테스트끼리 안 겹치게).
- **결함 테스트**: `@DisplayName("[Dn] ...")` + 올바른 동작을 기대. 결함이 고쳐질 때까지 실패하는 게 정상이다. 고치면 `docs/test-report.md` 를 갱신한다.
- **실행**: Docker 필요. `mvn verify -Dmaven.test.failure.ignore=true` 로 리포트(JaCoCo·SpotBugs)까지 만든다.
