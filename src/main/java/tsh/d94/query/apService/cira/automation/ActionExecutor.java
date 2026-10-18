package tsh.d94.app.server.apService.cira.automation;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import tsh.d94.app.server.apService.cira.NotificationService;
import tsh.d94.app.server.data.orm.cira.ciraAutoRule.SnCiraAutoRuleModel;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 자동화 규칙 액션 실행기
 *
 * actionConfig JSON 형식:
 * SEND_NOTIFICATION : { "userId": "...", "title": "...", "message": "..." }
 * CHANGE_STATUS     : { "statusId": "..." }
 * ASSIGN_USER       : { "userId": "..." }
 * ADD_LABEL         : { "label": "..." }
 * SEND_WEBHOOK      : { "url": "https://...", "body": { ... } }
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ActionExecutor {

	private final NotificationService notificationService;
	private final SnCiraIssueAccess issueAccess;
	private final ObjectMapper objectMapper;
	private final RestTemplate restTemplate;

	public void execute(SnCiraAutoRuleModel rule, String issueId, Map<String, Object> eventContext) {
		String actionType = rule.getActionType();
		Map<String, Object> config = parseConfig(rule.getActionConfig());

		switch (actionType) {
			case "SEND_NOTIFICATION" -> executeSendNotification(issueId, config, eventContext);
			case "CHANGE_STATUS" -> executeChangeStatus(issueId, config);
			case "ASSIGN_USER" -> executeAssignUser(issueId, config);
			case "ADD_LABEL" -> log.info("[자동화] ADD_LABEL 액션 - issueId={}, config={}", issueId, config);
			case "SEND_WEBHOOK" -> executeSendWebhook(issueId, config, eventContext);
			default -> log.warn("[자동화] 알 수 없는 액션 타입: {}", actionType);
		}
	}

	private void executeSendNotification(String issueId, Map<String, Object> config, Map<String, Object> ctx) {
		String userId = (String)config.get("userId");
		String title = (String)config.getOrDefault("title", "자동화 알림");
		String message = (String)config.getOrDefault("message", "자동화 규칙이 실행되었습니다.");

		// userId 미지정 시 assignee에게 발송
		if (userId == null) {
			userId = (String)ctx.get("assigneeId");
		}
		if (userId == null) {
			log.warn("[자동화] SEND_NOTIFICATION 대상 userId 없음 - issueId={}", issueId);
			return;
		}
		notificationService.send(userId, "AUTOMATION", title, message, "ISSUE", issueId);
		log.info("[자동화] 알림 발송 완료 - userId={}, issueId={}", userId, issueId);
	}

	private void executeChangeStatus(String issueId, Map<String, Object> config) {
		String statusId = (String)config.get("statusId");
		if (statusId == null) {
			log.warn("[자동화] CHANGE_STATUS - statusId 누락 - issueId={}", issueId);
			return;
		}
		try {
			SnCiraIssueModel issue = issueAccess.findById(issueId);
			issue.setStatusId(statusId);
			issue.setPrevEvntNm(issue.getEvtNm());
			issue.setEvtNm("AutoChangeStatus");
			issueAccess.save(issue);
			log.info("[자동화] 상태 변경 완료 - issueId={}, statusId={}", issueId, statusId);
		} catch (Exception e) {
			log.error("[자동화] CHANGE_STATUS 실패 - issueId={}: {}", issueId, e.getMessage());
			throw e;
		}
	}

	private void executeAssignUser(String issueId, Map<String, Object> config) {
		String userId = (String)config.get("userId");
		if (userId == null) {
			log.warn("[자동화] ASSIGN_USER - userId 누락 - issueId={}", issueId);
			return;
		}
		try {
			SnCiraIssueModel issue = issueAccess.findById(issueId);
			issue.setAssigneeId(userId);
			issue.setPrevEvntNm(issue.getEvtNm());
			issue.setEvtNm("AutoAssignUser");
			issueAccess.save(issue);
			log.info("[자동화] 담당자 변경 완료 - issueId={}, userId={}", issueId, userId);
		} catch (Exception e) {
			log.error("[자동화] ASSIGN_USER 실패 - issueId={}: {}", issueId, e.getMessage());
			throw e;
		}
	}

	private void executeSendWebhook(String issueId, Map<String, Object> config, Map<String, Object> ctx) {
		String url = (String)config.get("url");
		if (url == null) {
			log.warn("[자동화] SEND_WEBHOOK - url 누락 - issueId={}", issueId);
			return;
		}
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> configBody = (Map<String, Object>)config.get("body");
			Map<String, Object> body = configBody != null ? new HashMap<>(configBody) : new HashMap<>();
			body.put("issueId", issueId);
			body.put("context", ctx);

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);

			restTemplate.postForObject(url, entity, String.class);
			log.info("[자동화] 웹훅 발송 완료 - issueId={}, url={}", issueId, url);
		} catch (Exception e) {
			log.error("[자동화] SEND_WEBHOOK 실패 - issueId={}, url={}: {}", issueId, url, e.getMessage());
			throw e;
		}
	}

	@SuppressWarnings("unchecked")
	private Map<String, Object> parseConfig(String json) {
		if (json == null || json.isBlank())
			return Map.of();
		try {
			return objectMapper.readValue(json, new TypeReference<>() {
			});
		} catch (Exception e) {
			log.warn("actionConfig 파싱 실패: {}", e.getMessage());
			return Map.of();
		}
	}

}
