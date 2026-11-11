# 테스트 리포트 — CIRA (d94-query)

> 규칙·실행 방법: [testing.md](testing.md). 결함은 **보고만** 했고 운영 코드는 고치지 않았습니다. 결함 재현 테스트(`[Dn]`)는 결함이 고쳐질 때까지 실패하는 것이 정상입니다.

## 0. 수정 이력

| 일시 | 해결한 결함 | 변경 | 확인한 테스트 |
|---|---|---|---|
| 2026-11-01 | **D3** 이슈 생성 500 | lib-core `AbstractCrudService.findByIdOptional(null)` → 리포지토리를 부르지 않고 empty (`AbstractCrudServiceTest`). `createIssue` 가 상태 미지정 시 프로젝트 첫 상태(sortOrd 최소)를 쓰고, 다른 프로젝트의 상태는 거절 | `IssueServiceIT` › `[D3 재현]…`, `IssueServiceTest` › `[D3]…` |
| 2026-11-01 | **D7** 이슈 유형 없음 | Flyway `V320` 이 기본 유형(Task·Bug·Story·Epic) 시드, 유형 미지정 시 Task. 목록 API `GET /api/v1/issue-types` 추가 | `IssueServiceIT` › `[D7]…` |
| 2026-11-01 | **D1** (백엔드 쪽) 이메일이 담당자로 저장 | `createIssue` 가 담당자를 프로젝트의 Usable 멤버 objId 인지 확인, 아니면 400 | `IssueServiceIT` › `[D1 재현]…`, `IssueServiceTest` › `[D1]…` |
| 2026-11-01 | **D5** 255자 제한 | `V320` 이 이슈·댓글 내용, 알림 메시지, 프로젝트·PR 설명, 커밋 메시지(+ `*_HIST`)를 `text` 로, 엔티티에 `columnDefinition = "text"` | `IssueServiceIT`·`CommentServiceIT` › `[D5 재현]…` (스키마 자료형 차이 54 → 48) |
| 2026-11-01 | **D25** 제목 검증 없음 | `CreateIssueRequest.title` `@NotBlank` + 컨트롤러 `@Valid` | `IssueControllerTest` › `[D25]…` |

웹 쪽 확인: d94-web 상태 필터 우려(d94-web D12)는 결함이 아니다. Spring 은 `status[]=a` 와 `status=a` 를 모두 목록으로 받는다 (`IssueControllerTest` › `list_statusFilterBinding`).

## 1. 실행 정보

- 일시: 2026-10-31 22:59 (America/New_York), 명령: `mvn clean verify -Dmaven.test.failure.ignore=true`
- 환경: JDK 21 (jbr-21.0.11), Maven 3.9.9, Docker 29.8.2, Testcontainers `postgres:16` (운영 delap 과 같은 버전)

## 2. 테스트 수

| 종류 | 실행 | 통과 | 실패·오류 |
|---|---:|---:|---:|
| 단위 (surefire) | 162 | 149 | 13 — 모두 `[Dn]` 결함 재현 |
| 통합 (failsafe) | 45 | 20 | 25 — 모두 `[Dn]` 결함 재현 |

데이터 검증 결과: CIRA 리포지토리 커스텀 쿼리 **71개 모두 PostgreSQL 에서 실행 성공** (`CiraRepositoryQueryIT`). 스키마 차이는 D6.

## 3. 커버리지 (JaCoCo, 단위+통합 합산, `target/site/jacoco-merged`)

| 범위 | Line | Branch |
|---|---:|---:|
| 전체 | 46.4% | 37.5% |
| CIRA 로직 (apService·automation·cql·controller) | 46.0% | 37.2% |

| 클래스 | Line | Branch |
|---|---:|---:|
| WorkflowService | 100.0% | 87.5% |
| CqlLexer | 99.2% | 87.4% |
| ConditionEvaluator | 94.1% | 85.7% |
| CqlParser | 93.9% | 86.8% |
| NotificationService | 93.5% | 77.8% |
| ActionExecutor | 89.5% | 90.9% |
| IssueService | 86.2% | 63.6% |
| AutomationEventListener | 79.0% | 62.5% |
| SprintService | 65.7% | 47.4% |
| ProjectService | 65.0% | 34.1% |
| CommentService | 63.6% | 54.0% |
| BoardService | 56.0% | 51.9% |
| CqlQueryBuilder | 49.5% | 37.3% |
| AutomationRuleService | 39.5% | 15.0% |
| WikiService | 32.7% | 11.1% |
| CqlSearchService | 25.0% | 3.7% |
| MilestoneService | 22.6% | 4.5% |
| VersionService | 18.5% | 4.2% |
| **아직 전용 테스트 없음**: DashboardService 0.4%, SearchService 0.5%, GitWebhookService 1.5%, GitRepositoryService 0.8%, SseEmitterService 16.2%, 컨트롤러 10개 (Sprint·Comment·Cql·Search·AutomationRule·Version·Milestone·Wiki·Dashboard·Git). AttachmentService(조각 업로드)·AttachmentController(동기 다운로드·Range) 는 단위 테스트 있음(2026-11-10) | | |

## 4. SpotBugs (`target/spotbugsXml.xml`, 전체 255건)

| 범주 | 건수 | 주요 유형 |
|---|---:|---|
| MALICIOUS_CODE | 238 | `EI_EXPOSE_REP2` 195, `EI_EXPOSE_REP` 42 — Lombok 엔티티·DTO 가 가변 객체를 그대로 노출. 대부분 의도된 패턴 |
| SECURITY | 20 | `CRLF_INJECTION_LOGS` 13 (사용자 입력을 로그에 그대로), `PADDING_ORACLE` 3 |
| STYLE / BAD_PRACTICE | 8 | `DLS_DEAD_LOCAL_STORE` 3, `CT_CONSTRUCTOR_THROW` 3 |

## 4. 결함 목록

심각도: **치명** = 기능이 아예 동작하지 않거나 데이터·권한이 새어 나감, **높음** = 주요 기능이 틀리게 동작, **중간** = 일부 경우만 오동작, **낮음** = 품질·일관성.
"운영 확인" = delap 운영 로그·DB(`information_schema`, 읽기 전용)에서 같은 현상을 확인함.

### 4.1 기능이 동작하지 않음

| # | 심각도 | 재현 테스트 | 원인 | 영향 | 수정안 |
|---|---|---|---|---|---|
| D3 | 치명 · 운영 확인 | `IssueServiceIT` › `[D3 재현] 웹과 같은 요청…` | 요청에 `statusId`·`issueTypeId` 가 없으면 `IssueService.mapToResponse` 가 `findByIdOptional(null)` 을 호출 → `SimpleJpaRepository.findById(null)` 이 트랜잭션 안에서 `IllegalArgumentException` → 트랜잭션 rollback-only → lib-core `AbstractCrudService.findByIdOptional` 이 예외를 삼킴 → 커밋 시 `UnexpectedRollbackException` → 500 | **웹에서 이슈를 하나도 만들 수 없다** (운영 2026-10-31 21:26:30 오류의 실제 원인). 같은 패턴이 CIRA 서비스에 `findByIdOptional(x.getYId())` 26곳 (Board·Comment·Search·Attachment 등) | lib-core `findByIdOptional`: `id == null` 이면 리포지토리를 부르지 않고 `Optional.empty()`, catch 범위를 좁힌다. `createIssue` 는 상태 미지정 시 프로젝트 첫 상태를 기본값으로 |
| D7 | 높음 | (D3 테스트 준비 과정에서 확인) | 이슈 유형 테이블 `SN_CIRA_CIRA_ISSUE_TYPE` 은 전역이고 Flyway 시드도, 프로젝트 생성 시 기본 유형도 없다 | 클라이언트가 보낼 `issueTypeId` 가 존재하지 않음 → 모든 생성이 D3 경로로 감, 이슈에 유형이 없음 | Flyway 시드(Task·Bug·Story 등) 또는 `createProject` 에서 기본 유형 생성 |
| D2 | 치명 | `AutomationEventListenerIT` › `issueCreated_ruleExecutes`, `[D2 재현]…` | `AutomationEventListener` 가 `@Async @EventListener` 라 이슈가 **커밋되기 전에** 실행 → 이슈 조회 `NOT_FOUND` → 컨텍스트가 비고, 실행 이력 저장도 FK(`fk_ciraautoexecution_issue`) 위반 → 비동기 트랜잭션 실패 | `ISSUE_CREATED` 자동화가 전혀 동작하지 않고 실행 이력도 남지 않는다 | `@TransactionalEventListener(phase = AFTER_COMMIT)` + `@Async` |
| D11 | 치명 · 운영 확인 | `AutomationEventListenerIT` › `[D11 재현]…` | `jsonb` 컬럼 7개(`sn_cira_auto_rule.trigger/cond/action_config`, `custom_field.options`, `issue_cf_value.val_json`, `audit_log.old/new_snapshot`)를 엔티티가 `String` 으로만 매핑(`@JdbcTypeCode(SqlTypes.JSON)` 없음) | **자동화 규칙을 만들 수 없다** (`column is of type jsonb but expression is of type character varying`) | 해당 필드에 `@JdbcTypeCode(SqlTypes.JSON)` |
| D5 | 높음 · 운영 확인 | `IssueServiceIT` › `[D5 재현]…`, `CommentServiceIT` › `[D5 재현]…` | Flyway 는 `text` 로 만들지만 엔티티에 길이·`columnDefinition` 이 없어 `ddl-auto=update` 가 `varchar(255)` 로 바꿈. 운영 DB 확인: `sn_cira_issue.content`, `sn_cira_comment.content`, `sn_cira_notification.msg`, `sn_cira_project.descr`, `git_commit.msg`, `git_pr.descr` 등 | **255자를 넘는 이슈 설명·댓글이 저장되지 않는다** (500) | 엔티티에 `@Column(columnDefinition = "text")`, 운영 DB 는 `ALTER COLUMN ... TYPE text` 마이그레이션 |
| D17 | 높음 | `IssueServiceTest` › `[D17]…` | `updateIssue` 는 `title`·`content`·`assigneeId` 만 반영 | 우선순위·스토리 포인트·마감일·스프린트 수정이 **조용히 무시**된다 | 나머지 필드도 반영 + 변경 이력 |
| D8 | 중간 | `CommentServiceIT` › `[D8 재현]…` | 멘션 정규식 `@([\w.-]+)` 이 이메일 안의 `@` 에서 끊기는데 조회는 `findByEmail` | `@이메일` 멘션 알림이 가지 않는다 | 정규식을 이메일 전체로(`@([\w.+-]+@[\w.-]+)`) 또는 사용자 id 멘션 |
| D23 | 중간 | `NotificationServiceTest` › `[D23]…` | `NotificationService.send` 가 알림 설정(`sn_cira_notif_pref`)을 읽지 않음 | 알림 유형을 꺼도 계속 온다 | `send` 에서 (사용자, 채널, 유형) 설정 확인 |

### 4.2 보안 (권한·데이터 노출)

| # | 심각도 | 재현 테스트 | 원인 | 영향 | 수정안 |
|---|---|---|---|---|---|
| D13 | 치명 | `CqlSearchServiceIT` › `[D13]…` 3건 | CQL 검색이 `issueAccess.findAll(spec, pageable)` 로 바로 조회: tenant 필터(`enableTenantFilter`) 미적용, 프로젝트 멤버 범위·`deletedAt` 조건 없음 | **다른 tenant·멤버가 아닌 프로젝트·삭제된 이슈까지 검색된다**. `SearchService.searchIssues` 도 `projectId` 를 안 주면 같은 문제(코드 확인) | Specification 에 내 프로젝트 id 목록·`deletedAt is null` 을 항상 추가, Access 의 `findAll(spec, …)` 에도 tenant 필터 |
| D24 | 치명 | `CiraAuthorizationIT` 5건 | `AutomationRuleService`·`CommentService`·`GitRepositoryService`·`MilestoneService`·`VersionService`·`WikiService` 에 프로젝트 멤버 검사가 **하나도 없음** | 같은 tenant 의 아무 사용자나 남의 프로젝트에 댓글·버전·마일스톤·위키·Git 연결·자동화 규칙을 만들고 바꾸고 지울 수 있다 (테스트에서 실제로 생성됨) | 공통 `requireProjectMember(projectId)` 를 모든 쓰기·읽기 진입점에 |
| D18 | 높음 | `IssueServiceTest` › `[D18]…`, `ProjectServiceIT` › `[D18]…` | `IssueService.getIssue/listIssues/getBacklog`, `ProjectService.getProject/getMembers/getWorkflowStatuses` 에 멤버 검사 없음 | 남의 프로젝트 이슈·멤버 목록을 id 로 읽을 수 있다 | 위와 같음 |
| D20 | 높음 | `ProjectServiceIT` › `[D20]…` | 멤버 제거는 soft delete(`use_stat_cd=Delete`)인데 멤버 확인은 모두 `findAllByUserId(...)` 로 상태를 거르지 않음 | **제거된 멤버가 계속 접근하고 이슈를 만든다** | `findAllByUserIdAndUseStatCd(…, Usable)` 로 바꾸거나 Access 에서 거름 |
| D16 | 높음 | `ActionExecutorTest` › `[D16]…` | 자동화 `SEND_WEBHOOK` 이 규칙에 적힌 아무 URL 로 서버가 POST (허용 목록·내부 주소 차단 없음) | SSRF: 규칙을 만들 수 있는 사용자가 서버로 내부 주소(Config 서버, Eureka, 클라우드 메타데이터)를 호출. D24 와 합치면 **비멤버도** 가능 | https 만, 내부·사설 IP 차단, 허용 도메인 목록 |

### 4.3 데이터·스키마

| # | 심각도 | 재현 테스트 | 원인 | 영향 | 수정안 |
|---|---|---|---|---|---|
| D6 | 중간 | `SchemaDriftIT` (`target/schema-drift.txt`) | Flyway 에 없는 테이블 66개(실제 12 + Envers `*_hist` 54), 자료형 차이 54건 — 모두 `ddl-auto=update` 가 만들거나 바꿈 | 운영은 동작하지만, 새 DB 를 Flyway 로만 만들거나 `ddl-auto` 를 끄면 깨진다. D5 의 근본 원인 | 누락 테이블을 마이그레이션으로 옮기고, 안정화 후 `ddl-auto=validate` |
| D21 | 중간 | `ProjectServiceIT` › `[D21]…` | soft delete 된 멤버 행이 `UNIQUE(project_id, user_id)` 를 차지 | 제거했던 멤버를 다시 추가하면 500(`uk_ciraprojectmember_01`) | 재추가 시 기존 행을 되살리기(Usable) |
| D4 | 낮음 | (IT 기동 로그) | `StOrgWrkrRelModel` 컬럼 주석의 `it's` 따옴표가 이스케이프되지 않음 | `ddl-auto=update` 의 `comment on column` DDL 이 실패(로그만) | 주석 문구 수정 |

### 4.4 오류 처리·입력 검증

| # | 심각도 | 재현 테스트 | 원인 | 영향 | 수정안 |
|---|---|---|---|---|---|
| D19 | 중간 | `GlobalExceptionHandlerTest` › `[D19]…` 2건, `ProjectControllerTest` › `[D19]…` 2건, `ProjectServiceIT` › `[D19]…` | `ProjectService` 가 `IllegalStateException`(중복 멤버·마지막 ADMIN)·`java.lang.SecurityException`(권한)을 던지는데 처리기가 없음. `CiraException` 은 코드와 상관없이 모두 400 | 권한 없음·중복이 **500** 으로 보이고, 없는 이슈·비멤버도 400 | `IllegalStateException`→409, `SecurityException`·`AccessDeniedException`→403, `ErrorCode` 에 HTTP 상태를 넣어 404/403 구분 |
| D25 | 중간 | `IssueControllerTest` › `[D25]…` | `CreateIssueRequest` 에 검증 어노테이션이 없고 컨트롤러에 `@Valid` 도 없음 | 제목 없는 생성이 서비스·DB 까지 감 | `@NotBlank title` + `@Valid` |
| D22 | 중간 | `BoardServiceTest` › `[D22]…` | 상태가 없는 컬럼으로 이동 시 `targetColumn.getStatusId().equals(…)` NPE. 상태 없는 컬럼 추가는 D3 패턴. 컬럼 삭제 시 그 컬럼의 이슈 위치가 남음 | 500, 보드에서 이슈가 사라짐 | null 비교 순서 변경, 컬럼 삭제 시 위치 이동/삭제 |
| D14 | 낮음 | `CqlSearchServiceIT` › `[D14]…`, `CqlLexerTest` › `[D14]…` | CQL 값 변환 오류(`DateTimeParseException`, `NumberFormatException`, 모르는 필드 `IllegalArgumentException`)가 `CqlParseException` 으로 바뀌지 않음. 닫히지 않은 문자열을 그대로 받음 | 사용자 입력 실수가 400 이 아니라 500. `created > "2026-01-01"`(날짜만) 도 500 | 빌더에서 변환 예외를 `CqlParseException` 으로 감싸고, 날짜만 오면 그날 0시로 |

### 4.5 동작 불일치

| # | 심각도 | 재현 테스트 | 원인 | 영향 | 수정안 |
|---|---|---|---|---|---|
| D9 | 중간 | `BoardServiceIT` › `[D9]…` 2건 | `BoardService.moveIssue` 는 상태를 바꾸지만 `IssueService.changeStatus` 가 하는 워크플로우 전이 검사·변경 이력·`IssueStatusChangedEvent` 를 하지 않음 | 보드에서는 허용되지 않은 전이가 되고, 이력·알림·자동화가 빠진다 | 컬럼 상태가 바뀌면 `changeStatus` 경로를 재사용 |
| D12 | 중간 | `CqlSearchServiceIT` › `[D12]…` | CQL `currentUser()` 가 로그인 이름(email)으로 바뀌는데 `assignee`·`reporter` 컬럼은 사용자 objId | `assignee = currentUser()` 가 항상 빈 결과 | `currentUser()` 를 사용자 objId 로 해석 |
| D15 | 낮음 | `CqlParserTest` › `[D15]…` | 조건 없이 `ORDER BY` 만 있으면 파서가 `ORDER` 를 필드명으로 읽음 | `ORDER BY created DESC` 단독 쿼리 오류 | `parse()` 에서 `ORDER` 토큰이면 조건 생략 |
| D1 | 높음 · 운영 확인 | `IssueServiceIT` › `[D1 재현]…`, `IssueServiceTest` › `[D1]…` | 웹은 담당자 **이메일**을 `assigneeId` 로 보내고, 백엔드는 검증 없이 `assignee_id` 에 저장 | 담당자가 비어 보이고 필터·배정 알림이 엉뚱한 곳으로 | 웹: 사용자 검색 후 objId 전송. 백엔드: `assigneeId` 가 프로젝트 멤버인지 검사 |
| D10 | 높음 | (d94-web 계약 테스트) | 스프린트 상태: 백엔드 `Planned`/`Active`/`Completed`, 웹 `CREATED`/`ACTIVE`/`COMPLETED` (필드 이름도 `sprintStat`↔`status`) | 웹의 스프린트 상태 비교가 항상 거짓 (시작·완료 버튼, 활성 스프린트) | 한쪽으로 통일 (d94-web 보고서 D4) |

### 4.6 기타 관찰 (결함으로 세지 않음)

- `ApiResponse.created(...)`·`noContent()` 는 본문 `code` 만 `"201"`/`"204"` 이고 HTTP 상태는 200 이다. 웹은 본문만 보므로 지금은 문제 없음.
- CQL 에서 하이픈이 든 값(이슈 키 `CIRA-1`)은 따옴표가 필요하다(`CqlLexerTest` › `[관찰]…`).
- `ActionExecutor` 의 `CHANGE_STATUS`·`ASSIGN_USER` 도 워크플로우·멤버 검사 없이 값을 넣는다(D9·D24 와 같은 계열).
- `SnCiraAutoRuleModel` 의 필드 초기값(`isActive = true`, `sortOrd = 0`)은 `@SuperBuilder` 가 무시하지만 서비스가 값을 직접 넣어 실제 문제는 없다.
