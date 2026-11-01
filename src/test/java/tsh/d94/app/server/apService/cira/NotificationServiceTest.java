package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import tsh.d94.app.server.apService.cira.dto.NotificationPreferenceRequest;
import tsh.d94.app.server.apService.cira.dto.NotificationResponse;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraNotifPref.SnCiraNotifPrefAccess;
import tsh.d94.app.server.data.orm.cira.ciraNotifPref.SnCiraNotifPrefModel;
import tsh.d94.app.server.data.orm.cira.ciraNotification.SnCiraNotificationAccess;
import tsh.d94.app.server.data.orm.cira.ciraNotification.SnCiraNotificationModel;
import tsh.d94.app.server.support.TestContexts;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationServiceTest {

	private static final String EMAIL = "me@test.d94";
	private static final String ME = "user-me";

	@Mock
	private SnCiraNotificationAccess notificationAccess;
	@Mock
	private SnCiraIssueAccess issueAccess;
	@Mock
	private SnCiraNotifPrefAccess prefAccess;
	@Mock
	private SseEmitterService sseEmitterService;
	@Mock
	private GsUserAccess userAccess;
	@InjectMocks
	private NotificationService notificationService;

	private static <T> T withId(T model, String id) {
		ReflectionTestUtils.setField(model, "objId", id);
		return model;
	}

	@BeforeEach
	void setUp() {
		TestContexts.login(EMAIL);
		given(userAccess.findByEmail(EMAIL)).willReturn(Optional.of(withId(new GsUserModel(), ME)));
		given(issueAccess.findByIds(any())).willReturn(List.of());
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private static SnCiraNotificationModel notif(String id, String userId, String resourceType, String resourceId) {
		SnCiraNotificationModel n = withId(new SnCiraNotificationModel(), id);
		n.setUserId(userId);
		n.setNotifType("ISSUE_ASSIGNED");
		n.setResourceType(resourceType);
		n.setResourceId(resourceId);
		n.setReadYn("N");
		return n;
	}

	@Test
	@DisplayName("send: 읽지 않음(N)으로 저장하고 SSE 로 같은 응답을 보낸다")
	void send_savesUnreadAndPushes() {
		notificationService.send("u-1", "ISSUE_ASSIGNED", "t", "m", "ISSUE", "i-1");

		ArgumentCaptor<SnCiraNotificationModel> saved = ArgumentCaptor.forClass(SnCiraNotificationModel.class);
		then(notificationAccess).should().save(saved.capture());
		assertThat(saved.getValue().getReadYn()).isEqualTo("N");
		assertThat(saved.getValue().getTenant()).isEqualTo(TestContexts.TENANT);
		then(sseEmitterService).should().send(eq("u-1"), eq("notification"), any(NotificationResponse.class));
	}

	@Test
	@DisplayName("[D23] 사용자가 끈 알림 유형은 보내지 않는다 (설정을 저장만 하고 읽지 않음)")
	void send_respectsDisabledPreference() {
		SnCiraNotifPrefModel off = new SnCiraNotifPrefModel();
		off.setUserId("u-1");
		off.setEventType("ISSUE_ASSIGNED");
		off.setChannel("IN_APP");
		off.setEnabledYn("N");
		given(prefAccess.findByUserId("u-1")).willReturn(List.of(off));
		given(prefAccess.findByUserIdAndChannelAndEventType("u-1", "IN_APP", "ISSUE_ASSIGNED"))
			.willReturn(Optional.of(off));

		notificationService.send("u-1", "ISSUE_ASSIGNED", "t", "m", "ISSUE", "i-1");

		then(notificationAccess).should(never()).save(any());
	}

	@Test
	@DisplayName("목록: ISSUE 알림에만 issueKey 를 한 번의 조회로 채운다")
	void getNotifications_fillsIssueKeyInOneLookup() {
		given(notificationAccess.findRecentByUserId(ME)).willReturn(List.of(
			notif("n1", ME, "ISSUE", "i-1"), notif("n2", ME, "ISSUE", "i-1"), notif("n3", ME, "SPRINT", "s-1")));
		SnCiraIssueModel issue = withId(new SnCiraIssueModel(), "i-1");
		issue.setIssueKey("CIRA-7");
		given(issueAccess.findByIds(List.of("i-1"))).willReturn(List.of(issue));

		List<NotificationResponse> list = notificationService.getNotifications();

		assertThat(list).extracting(NotificationResponse::getIssueKey).containsExactly("CIRA-7", "CIRA-7", null);
		then(issueAccess).should(times(1)).findByIds(List.of("i-1"));
	}

	@Test
	@DisplayName("목록: ISSUE 알림이 없으면 이슈를 조회하지 않는다")
	void getNotifications_noIssueNotifications_noLookup() {
		given(notificationAccess.findRecentByUserId(ME)).willReturn(List.of(notif("n3", ME, "SPRINT", "s-1")));

		notificationService.getNotifications();

		then(issueAccess).should(never()).findByIds(any());
	}

	@Test
	@DisplayName("markRead: 본인 알림만 읽음 처리, 남의 알림은 거절")
	void markRead_ownershipChecked() {
		SnCiraNotificationModel mine = notif("n1", ME, "ISSUE", "i-1");
		given(notificationAccess.findById("n1")).willReturn(mine);
		given(notificationAccess.findById("n2")).willReturn(notif("n2", "someone-else", "ISSUE", "i-1"));

		assertThat(notificationService.markRead("n1").isRead()).isTrue();
		assertThat(mine.getReadYn()).isEqualTo("Y");
		assertThatThrownBy(() -> notificationService.markRead("n2")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("markAllRead: 안 읽은 알림을 모두 Y 로 저장")
	void markAllRead() {
		SnCiraNotificationModel a = notif("a", ME, "ISSUE", "i");
		SnCiraNotificationModel b = notif("b", ME, "ISSUE", "i");
		given(notificationAccess.findAllUnreadByUserId(ME)).willReturn(List.of(a, b));

		notificationService.markAllRead();

		assertThat(List.of(a, b)).extracting(SnCiraNotificationModel::getReadYn).containsOnly("Y");
		then(notificationAccess).should(times(2)).save(any());
	}

	@Test
	@DisplayName("updatePreferences: 있으면 갱신, 없으면 새로 만든다")
	void updatePreferences_upsert() {
		SnCiraNotifPrefModel existing = new SnCiraNotifPrefModel();
		existing.setEnabledYn("Y");
		given(prefAccess.findByUserIdAndChannelAndEventType(ME, "IN_APP", "ISSUE_ASSIGNED"))
			.willReturn(Optional.of(existing));
		given(prefAccess.findByUserIdAndChannelAndEventType(ME, "EMAIL", "ISSUE_ASSIGNED")).willReturn(Optional.empty());

		NotificationPreferenceRequest req = new NotificationPreferenceRequest();
		NotificationPreferenceRequest.PrefItem off = new NotificationPreferenceRequest.PrefItem();
		off.setChannel("IN_APP");
		off.setEventType("ISSUE_ASSIGNED");
		off.setEnabled(false);
		NotificationPreferenceRequest.PrefItem on = new NotificationPreferenceRequest.PrefItem();
		on.setChannel("EMAIL");
		on.setEventType("ISSUE_ASSIGNED");
		on.setEnabled(true);
		req.setPreferences(List.of(off, on));

		notificationService.updatePreferences(req);

		assertThat(existing.getEnabledYn()).isEqualTo("N");
		ArgumentCaptor<SnCiraNotifPrefModel> saved = ArgumentCaptor.forClass(SnCiraNotifPrefModel.class);
		then(prefAccess).should(times(2)).save(saved.capture());
		assertThat(saved.getAllValues().get(1).getChannel()).isEqualTo("EMAIL");
		assertThat(saved.getAllValues().get(1).getEnabledYn()).isEqualTo("Y");
	}

}
