package tsh.d94.app.server.interfaces.controller.cira;

import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tsh.d94.app.server.apService.cira.NotificationService;
import tsh.d94.app.server.apService.cira.SseEmitterService;
import tsh.d94.app.server.apService.cira.dto.NotificationResponse;
import tsh.d94.app.server.support.ControllerTestSupport;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;

/**
 * 응답 모양 계약 (d94-web src/lib/api/notifications.ts 가 이 모양을 변환한다):
 * 목록은 페이지가 아닌 배열, 안 읽은 수는 {count}, 필드는 read·message·resourceType·resourceId·issueKey.
 */
@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

	@Mock
	private NotificationService notificationService;
	@Mock
	private SseEmitterService sseEmitterService;
	@Mock
	private GsUserAccess userAccess;
	@InjectMocks
	private NotificationController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = ControllerTestSupport.mvc(controller);
	}

	@Test
	@DisplayName("GET /notifications → data 는 배열, 필드 read·message·issueKey")
	void list_isPlainArray() throws Exception {
		given(notificationService.getNotifications()).willReturn(List.of(NotificationResponse.builder()
			.id("n-1").type("ISSUE_ASSIGNED").title("t").message("m").resourceType("ISSUE").resourceId("i-1")
			.issueKey("CIRA-1").read(false).build()));

		mvc.perform(get("/api/v1/notifications"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray())
			.andExpect(jsonPath("$.data[0].read").value(false))
			.andExpect(jsonPath("$.data[0].message").value("m"))
			.andExpect(jsonPath("$.data[0].issueKey").value("CIRA-1"))
			.andExpect(jsonPath("$.data.content").doesNotExist());
	}

	@Test
	@DisplayName("GET /notifications/unread-count → data 는 {count: N}")
	void unreadCount_isObject() throws Exception {
		given(notificationService.getUnreadCount()).willReturn(3L);

		mvc.perform(get("/api/v1/notifications/unread-count"))
			.andExpect(jsonPath("$.data.count").value(3));
	}

	@Test
	@DisplayName("PUT /{id}/read, /read-all, GET·PUT /preferences 라우팅")
	void otherRoutes() throws Exception {
		given(notificationService.markRead("n-1")).willReturn(NotificationResponse.builder().id("n-1").read(true).build());
		given(notificationService.getPreferences()).willReturn(List.of());
		given(notificationService.updatePreferences(org.mockito.ArgumentMatchers.any())).willReturn(List.of());

		mvc.perform(put("/api/v1/notifications/n-1/read")).andExpect(jsonPath("$.data.read").value(true));
		mvc.perform(put("/api/v1/notifications/read-all")).andExpect(status().isOk());
		mvc.perform(get("/api/v1/notifications/preferences")).andExpect(jsonPath("$.data").isArray());
		mvc.perform(put("/api/v1/notifications/preferences").contentType(MediaType.APPLICATION_JSON)
			.content("{\"preferences\":[]}")).andExpect(status().isOk());

		then(notificationService).should().markAllRead();
	}

}
