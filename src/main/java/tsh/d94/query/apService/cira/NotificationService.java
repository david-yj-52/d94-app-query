package tsh.d94.app.server.apService.cira;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.dto.NotificationPreferenceRequest;
import tsh.d94.app.server.apService.cira.dto.NotificationPreferenceResponse;
import tsh.d94.app.server.apService.cira.dto.NotificationResponse;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraNotifPref.SnCiraNotifPrefAccess;
import tsh.d94.app.server.data.orm.cira.ciraNotifPref.SnCiraNotifPrefModel;
import tsh.d94.app.server.data.orm.cira.ciraNotification.SnCiraNotificationAccess;
import tsh.d94.app.server.data.orm.cira.ciraNotification.SnCiraNotificationModel;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

	private static final String RESOURCE_TYPE_ISSUE = "ISSUE";

	private final SnCiraNotificationAccess notificationAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraNotifPrefAccess prefAccess;
	private final SseEmitterService sseEmitterService;
	private final GsUserAccess userAccess;

	@Transactional
	public void send(String userId, String type, String title, String message,
		String resourceType, String resourceId) {
		SnCiraNotificationModel notification = SnCiraNotificationModel.builder()
			.userId(userId)
			.notifType(type)
			.title(title)
			.msg(message)
			.resourceType(resourceType)
			.resourceId(resourceId)
			.readYn("N")
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(TenantContext.get())
			.traceId("NOTIFICATION")
			.useStatCd(UseStatCd.Usable)
			.evtNm("SendNotification")
			.prevEvntNm("None")
			.build();
		notificationAccess.save(notification);

		NotificationResponse response = mapToResponse(notification);
		sseEmitterService.send(userId, "notification", response);
	}

	public List<NotificationResponse> getNotifications() {
		String userId = currentUserId();
		List<SnCiraNotificationModel> notifications = notificationAccess.findRecentByUserId(userId);
		Map<String, String> issueKeys = issueKeysOf(notifications);
		return notifications.stream()
			.map(n -> mapToResponse(n, issueKeys))
			.collect(Collectors.toList());
	}

	@Transactional
	public NotificationResponse markRead(String notificationId) {
		String userId = currentUserId();
		SnCiraNotificationModel notification = notificationAccess.findById(notificationId);
		if (!userId.equals(notification.getUserId())) {
			throw new IllegalArgumentException("접근 권한이 없습니다.");
		}
		notification.setReadYn("Y");
		notification.setEvtNm("MarkRead");
		notification.setPrevEvntNm("SendNotification");
		notificationAccess.save(notification);
		return mapToResponse(notification);
	}

	@Transactional
	public void markAllRead() {
		String userId = currentUserId();
		notificationAccess.findAllUnreadByUserId(userId).stream()
			.forEach(n -> {
				n.setReadYn("Y");
				n.setEvtNm("MarkAllRead");
				n.setPrevEvntNm("SendNotification");
				notificationAccess.save(n);
			});
	}

	public long getUnreadCount() {
		return notificationAccess.countUnread(currentUserId());
	}

	public List<NotificationPreferenceResponse> getPreferences() {
		String userId = currentUserId();
		return prefAccess.findByUserId(userId)
			.stream()
			.map(this::mapPrefToResponse)
			.collect(Collectors.toList());
	}

	@Transactional
	public List<NotificationPreferenceResponse> updatePreferences(NotificationPreferenceRequest request) {
		String userId = currentUserId();
		for (NotificationPreferenceRequest.PrefItem item : request.getPreferences()) {
			prefAccess.findByUserIdAndChannelAndEventType(userId, item.getChannel(), item.getEventType())
				.ifPresentOrElse(pref -> {
					pref.setEnabledYn(item.isEnabled() ? "Y" : "N");
					pref.setEvtNm("UpdatePref");
					pref.setPrevEvntNm("None");
					prefAccess.save(pref);
				}, () -> {
					SnCiraNotifPrefModel newPref = SnCiraNotifPrefModel.builder()
						.userId(userId)
						.channel(item.getChannel())
						.eventType(item.getEventType())
						.enabledYn(item.isEnabled() ? "Y" : "N")
						.srvId(ApplicationProperties.getApplicationServiceName())
						.tenant(TenantContext.get())
						.traceId("NOTIF-PREF")
						.useStatCd(UseStatCd.Usable)
						.evtNm("CreatePref")
						.prevEvntNm("None")
						.build();
					prefAccess.save(newPref);
				});
		}
		return getPreferences();
	}

	private String currentUserId() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
		return user.getObjId();
	}

	/** ISSUE 알림의 resourceId(이슈 objId) → issueKey. 목록 조회는 한 번의 쿼리로 모아서 찾는다 */
	private Map<String, String> issueKeysOf(List<SnCiraNotificationModel> notifications) {
		List<String> issueIds = notifications.stream()
			.filter(n -> RESOURCE_TYPE_ISSUE.equals(n.getResourceType()) && n.getResourceId() != null)
			.map(SnCiraNotificationModel::getResourceId)
			.distinct()
			.collect(Collectors.toList());
		if (issueIds.isEmpty()) {
			return Map.of();
		}
		return issueAccess.findByIds(issueIds).stream()
			.filter(i -> i.getIssueKey() != null)
			.collect(Collectors.toMap(SnCiraIssueModel::getObjId, SnCiraIssueModel::getIssueKey));
	}

	private NotificationResponse mapToResponse(SnCiraNotificationModel model) {
		return mapToResponse(model, issueKeysOf(List.of(model)));
	}

	private NotificationResponse mapToResponse(SnCiraNotificationModel model, Map<String, String> issueKeys) {
		return NotificationResponse.builder()
			.id(model.getObjId())
			.userId(model.getUserId())
			.type(model.getNotifType())
			.title(model.getTitle())
			.message(model.getMsg())
			.resourceType(model.getResourceType())
			.resourceId(model.getResourceId())
			.issueKey(RESOURCE_TYPE_ISSUE.equals(model.getResourceType()) ? issueKeys.get(model.getResourceId()) : null)
			.read("Y".equals(model.getReadYn()))
			.createdAt(model.getCreatedAt())
			.build();
	}

	private NotificationPreferenceResponse mapPrefToResponse(SnCiraNotifPrefModel model) {
		return NotificationPreferenceResponse.builder()
			.id(model.getObjId())
			.eventType(model.getEventType())
			.channel(model.getChannel())
			.enabled("Y".equals(model.getEnabledYn()))
			.build();
	}

}
