package tsh.d94.app.server.apService.cira.automation;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import tsh.d94.app.server.apService.cira.NotificationService;
import tsh.d94.app.server.data.orm.cira.ciraAutoRule.SnCiraAutoRuleModel;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;

@ExtendWith(MockitoExtension.class)
class ActionExecutorTest {

	private static final String ISSUE_ID = "issue-1";

	@Mock
	private NotificationService notificationService;
	@Mock
	private SnCiraIssueAccess issueAccess;
	@Mock
	private RestTemplate restTemplate;

	private ActionExecutor executor;

	@BeforeEach
	void setUp() {
		executor = new ActionExecutor(notificationService, issueAccess, new ObjectMapper(), restTemplate);
	}

	private static SnCiraAutoRuleModel rule(String actionType, String config) {
		SnCiraAutoRuleModel rule = new SnCiraAutoRuleModel();
		rule.setActionType(actionType);
		rule.setActionConfig(config);
		return rule;
	}

	private static SnCiraIssueModel issue() {
		SnCiraIssueModel issue = new SnCiraIssueModel();
		issue.setEvtNm("CreateIssue");
		return issue;
	}

	@Test
	@DisplayName("SEND_NOTIFICATION: userId 가 있으면 그 사용자, 제목·메시지 기본값")
	void sendNotification_toConfiguredUser_withDefaults() {
		executor.execute(rule("SEND_NOTIFICATION", "{\"userId\":\"u-9\"}"), ISSUE_ID, Map.of());

		then(notificationService).should()
			.send("u-9", "AUTOMATION", "자동화 알림", "자동화 규칙이 실행되었습니다.", "ISSUE", ISSUE_ID);
	}

	@Test
	@DisplayName("SEND_NOTIFICATION: userId 가 없으면 컨텍스트의 담당자에게")
	void sendNotification_fallsBackToAssignee() {
		executor.execute(rule("SEND_NOTIFICATION", "{\"title\":\"T\",\"message\":\"M\"}"), ISSUE_ID,
			Map.of("assigneeId", "assignee-1"));

		then(notificationService).should().send("assignee-1", "AUTOMATION", "T", "M", "ISSUE", ISSUE_ID);
	}

	@Test
	@DisplayName("SEND_NOTIFICATION: 대상이 전혀 없으면 보내지 않는다")
	void sendNotification_noTarget_skips() {
		executor.execute(rule("SEND_NOTIFICATION", "{}"), ISSUE_ID, Map.of());

		then(notificationService).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("CHANGE_STATUS: 상태와 이벤트명을 바꿔 저장")
	void changeStatus_updatesIssue() {
		SnCiraIssueModel issue = issue();
		given(issueAccess.findById(ISSUE_ID)).willReturn(issue);

		executor.execute(rule("CHANGE_STATUS", "{\"statusId\":\"s-done\"}"), ISSUE_ID, Map.of());

		assertThat(issue.getStatusId()).isEqualTo("s-done");
		assertThat(issue.getEvtNm()).isEqualTo("AutoChangeStatus");
		assertThat(issue.getPrevEvntNm()).isEqualTo("CreateIssue");
		then(issueAccess).should().save(issue);
	}

	@Test
	@DisplayName("CHANGE_STATUS / ASSIGN_USER: 필수 값이 없으면 아무것도 하지 않는다")
	void missingConfig_skips() {
		executor.execute(rule("CHANGE_STATUS", "{}"), ISSUE_ID, Map.of());
		executor.execute(rule("ASSIGN_USER", null), ISSUE_ID, Map.of());

		then(issueAccess).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("ASSIGN_USER: 담당자를 바꿔 저장, 조회 실패는 다시 던져 실행 이력을 FAILED 로 남기게 한다")
	void assignUser_updatesAndPropagatesFailure() {
		SnCiraIssueModel issue = issue();
		given(issueAccess.findById(ISSUE_ID)).willReturn(issue);
		executor.execute(rule("ASSIGN_USER", "{\"userId\":\"u-2\"}"), ISSUE_ID, Map.of());
		assertThat(issue.getAssigneeId()).isEqualTo("u-2");

		given(issueAccess.findById("missing")).willThrow(new RuntimeException("NOT_FOUND"));
		assertThatThrownBy(() -> executor.execute(rule("ASSIGN_USER", "{\"userId\":\"u-2\"}"), "missing", Map.of()))
			.hasMessageContaining("NOT_FOUND");
	}

	@Test
	@DisplayName("SEND_WEBHOOK: 설정 body 에 issueId·context 를 더해 POST")
	@SuppressWarnings("unchecked")
	void sendWebhook_postsBodyWithIssue() {
		executor.execute(rule("SEND_WEBHOOK", "{\"url\":\"https://hooks.example.test/x\",\"body\":{\"a\":1}}"),
			ISSUE_ID, Map.of("priority", "HIGH"));

		ArgumentCaptor<HttpEntity<Map<String, Object>>> captor = ArgumentCaptor.forClass(HttpEntity.class);
		then(restTemplate).should().postForObject(eq("https://hooks.example.test/x"), captor.capture(), eq(String.class));
		assertThat(captor.getValue().getBody())
			.containsEntry("a", 1)
			.containsEntry("issueId", ISSUE_ID)
			.containsEntry("context", Map.of("priority", "HIGH"));
	}

	@Test
	@DisplayName("[D16] SEND_WEBHOOK 은 내부 주소로 요청하지 않는다 (SSRF)")
	void sendWebhook_internalAddress_isBlocked() {
		executor.execute(rule("SEND_WEBHOOK", "{\"url\":\"http://localhost:8888/actuator/env\"}"), ISSUE_ID, Map.of());
		executor.execute(rule("SEND_WEBHOOK", "{\"url\":\"http://169.254.169.254/latest/meta-data\"}"), ISSUE_ID,
			Map.of());

		then(restTemplate).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("깨진 설정 JSON·모르는 액션은 예외 없이 무시")
	void invalidConfigOrType_ignored() {
		assertThatCode(() -> {
			executor.execute(rule("SEND_NOTIFICATION", "{oops"), ISSUE_ID, Map.of());
			executor.execute(rule("UNKNOWN", "{}"), ISSUE_ID, Map.of());
			executor.execute(rule("ADD_LABEL", "{\"label\":\"x\"}"), ISSUE_ID, Map.of());
		}).doesNotThrowAnyException();
		then(notificationService).shouldHaveNoInteractions();
		then(restTemplate).shouldHaveNoInteractions();
	}

}
