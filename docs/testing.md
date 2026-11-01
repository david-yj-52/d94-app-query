# 테스트 가이드 (d94-query)

> 테스트를 쓰거나 고치거나 돌릴 때 읽는 문서입니다. 최근 실행 결과와 테스트가 찾은 결함은 [test-report.md](test-report.md) 에 있습니다.

## 1. 테스트 계층

| 계층 | 이름 규칙 | 실행 | 도구 | 무엇을 보나 |
|---|---|---|---|---|
| 단위 | `XxxTest.java` | `mvn test` (surefire) | JUnit 5 + Mockito + AssertJ | BIZ 로직 분기: 권한, 상태 전이, 계산, 검증. DB 없이 Access 를 mock |
| 통합 | `XxxIT.java` | `mvn verify` (failsafe) | `@IntegrationTest` + Testcontainers `postgres:16` | 실제 PostgreSQL 에서 SQL 이 실행되는지, 트랜잭션이 커밋되는지, DB 에 남은 행이 맞는지 |
| 정적 분석 | — | `mvn verify` | SpotBugs + FindSecBugs | 버그 패턴, 보안 패턴. 지금은 리포트만 만들고 빌드를 막지 않음 (`failOnError=false`) |
| 커버리지 | — | `mvn verify` | JaCoCo 0.8.12 | 단위 + 통합 결과를 합친 리포트 |

**대상 범위**: 로직이 있는 클래스(apService, automation, cql, controller, 커스텀 쿼리가 있는 Access)에만 1:1 테스트를 둡니다. DTO, Model, Repo 인터페이스는 통합 테스트가 대신 다룹니다.

## 2. 위치 규칙

- 파일은 `src/main/java/tsh/d94/query/**` 폴더에 있지만 선언 패키지는 `tsh.d94.app.server.*` 입니다(CLAUDE.md 미정의 #12).
- 테스트는 **선언 패키지 기준**으로 둡니다: `src/test/java/tsh/d94/app/server/<같은 하위 경로>/XxxTest.java`.
  - 예: `apService/cira/IssueService.java` → `src/test/java/tsh/d94/app/server/apService/cira/IssueServiceTest.java`, `IssueServiceIT.java`
- 공용 테스트 코드는 `src/test/java/tsh/d94/app/server/support/` 에 있습니다.

| 파일 | 역할 |
|---|---|
| `D94QueryTestApplication` | 테스트용 부트 진입점. `QueryApplication` 이 package-private 이고 테스트 패키지의 상위가 아니라 `@SpringBootTest` 가 못 찾기 때문에 있다. 하는 일은 `QueryApplication` 하나만 불러오는 것 |
| `support/IntegrationTest` | `@SpringBootTest` + `test` 프로필 + `PostgresTestContainer` 를 묶은 어노테이션 |
| `support/PostgresTestContainer` | `postgres:16` 컨테이너. JVM 당 1개를 모든 컨텍스트가 공유하고, `@ServiceConnection` 으로 datasource 를 채운다 |
| `support/TestContexts` | 로그인 사용자(email)와 tenant 를 `SecurityContextHolder`·`TenantContext` 에 넣고 뺀다 (`login`/`clear`) |
| `support/CiraFixtures` | IT 데이터 준비: `user`, `project`(실제 `ProjectService` 사용), `issueType`, `statusId`, `issue` |
| `src/test/resources/application-test.yml` | 테스트 프로필. JWT 테스트 키, 평문 로그인 허용, SQL 로그 끔 |
| `src/test/resources/docker-java.properties` | `api.version=1.44`. Testcontainers 1.20 + Docker Engine 29 호환용 (없으면 "Could not find a valid Docker environment") |

## 3. 통합 테스트 작성 규칙

- 테스트 메서드에 `@Transactional` 을 **걸지 않습니다**. 서비스가 운영처럼 직접 커밋해야 커밋 시점 오류(rollback-only, 제약 위반, 비동기 리스너)가 드러납니다.
- 결과는 서비스 반환값뿐 아니라 `JdbcTemplate` 으로 **DB 에서 다시 읽어** 확인합니다.
- 데이터가 테스트끼리 겹치지 않게 `CiraFixtures.uniqueEmail(...)`, 무작위 프로젝트 키, 검색 표식(marker)을 씁니다. 컨테이너는 공유되고 테스트 후 데이터를 지우지 않습니다.
- `@BeforeEach` 에서 `TestContexts.login(...)`, `@AfterEach` 에서 `TestContexts.clear()`.
- 비동기(`automationTaskExecutor`) 결과는 행이 생길 때까지 짧게 폴링합니다 (`AutomationEventListenerIT.waitForRows`).
- 스키마 검사(`SchemaDriftIT`)는 **전용 컨테이너**에 Flyway 만 적용해 비교합니다. 공용 컨테이너는 `ddl-auto=update` 로 이미 바뀌어 있어 비교 기준이 될 수 없습니다.

## 4. 단위 테스트 작성 규칙

- `@ExtendWith(MockitoExtension.class)` + `@Mock` + `@InjectMocks`. 기존 예: `SprintServiceTest`, `IssueServiceTest`.
- `objId` 는 lib-core `BasicAudit` 에 setter 가 없으니 `ReflectionTestUtils.setField(model, "objId", id)` 로 넣습니다.
- getter 만 있는 요청 DTO(예: `ChangeStatusRequest`)는 `new ObjectMapper().convertValue(Map.of(...), X.class)` 로 만듭니다(요청 본문과 같은 경로).
- 서비스가 `SecurityContextHolder`·`TenantContext` 를 읽으면 단위 테스트에서도 `TestContexts.login(...)` 을 씁니다.
- 공통 stub 을 `@BeforeEach` 에 모을 때는 `@MockitoSettings(strictness = Strictness.LENIENT)` (그렇지 않으면 UnnecessaryStubbing).

## 5. 결함 재현 테스트

- 결함을 재현하는 테스트는 이름(`@DisplayName`) 앞에 `[Dn]` 을 붙이고, **올바른 동작을 기대**하도록 씁니다. 결함이 고쳐지기 전까지 실패하는 것이 정상입니다.
- 결함 번호·원인·영향·수정안은 [test-report.md](test-report.md) 의 결함 목록에 있습니다. 결함을 고치면 그 테스트가 통과하는지 확인하고 리포트에서 상태를 바꿉니다.
- CI(`deploy.yml`)는 `-DskipTests` 라 결함 테스트가 배포를 막지 않습니다.

## 6. 실행

Docker(Desktop)가 떠 있어야 통합 테스트가 돕니다. `postgres:16` 이미지를 미리 받아 두면 빠릅니다.

```bash
mvn test                                   # 단위만
mvn verify -Dmaven.test.failure.ignore=true # 단위 + 통합 + JaCoCo + SpotBugs (결함 테스트가 있어도 리포트까지 진행)
mvn verify -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false -Dit.test=IssueServiceIT  # 통합 하나만
mvn test -Dtest='Cql*Test'                 # 단위 일부
```

## 7. 리포트 위치 (`mvn verify` 후)

| 리포트 | 경로 |
|---|---|
| 커버리지 (단위+통합 합산) | `target/site/jacoco-merged/index.html` |
| 커버리지 (단위만) | `target/site/jacoco/index.html` |
| SpotBugs | `target/spotbugsXml.xml` (요약은 test-report.md, 상세 보기는 `mvn spotbugs:gui`) |
| 단위·통합 결과 | `target/surefire-reports/`, `target/failsafe-reports/` |
| 스키마 차이 (Flyway vs 엔티티) | `target/schema-drift.txt` (`SchemaDriftIT`) |
| 리포지토리 쿼리 실행 결과 | `target/repository-queries.txt` (`CiraRepositoryQueryIT`) |

## 8. 새 코드를 추가할 때

- 로직이 있는 클래스를 추가하면 같은 패키지에 `XxxTest` 를 함께 만듭니다.
- 새 리포지토리 메서드는 `CiraRepositoryQueryIT` 가 자동으로 실행합니다. 인자 타입이 새로우면 `sample(...)` 에 추가하세요(없으면 SKIP 으로 표시).
- 새 엔티티·컬럼은 Flyway 마이그레이션과 함께 추가합니다. `SchemaDriftIT` 가 빠진 것을 잡습니다.
