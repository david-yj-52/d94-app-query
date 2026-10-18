package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;
import java.util.Map;

import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import tsh.d94.app.server.apService.cira.NotificationService;
import tsh.d94.app.server.apService.cira.SseEmitterService;
import tsh.d94.app.server.apService.cira.dto.NotificationPreferenceRequest;
import tsh.d94.app.server.apService.cira.dto.NotificationPreferenceResponse;
import tsh.d94.app.server.apService.cira.dto.NotificationResponse;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

	private final NotificationService notificationService;
	private final SseEmitterService sseEmitterService;
	private final GsUserAccess userAccess;

	@GetMapping(value = "/subscribe", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public SseEmitter subscribe() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		String userId = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email))
			.getObjId();
		return sseEmitterService.connect(userId);
	}

	@GetMapping
	public ApiResponse<List<NotificationResponse>> getNotifications() {
		return ApiResponse.ok(notificationService.getNotifications());
	}

	@PutMapping("/{id}/read")
	public ApiResponse<NotificationResponse> markRead(@PathVariable String id) {
		return ApiResponse.ok(notificationService.markRead(id));
	}

	@PutMapping("/read-all")
	public ApiResponse<Void> markAllRead() {
		notificationService.markAllRead();
		return ApiResponse.noContent();
	}

	@GetMapping("/unread-count")
	public ApiResponse<Map<String, Long>> getUnreadCount() {
		return ApiResponse.ok(Map.of("count", notificationService.getUnreadCount()));
	}

	@GetMapping("/preferences")
	public ApiResponse<List<NotificationPreferenceResponse>> getPreferences() {
		return ApiResponse.ok(notificationService.getPreferences());
	}

	@PutMapping("/preferences")
	public ApiResponse<List<NotificationPreferenceResponse>> updatePreferences(
		@RequestBody NotificationPreferenceRequest request
	) {
		return ApiResponse.ok(notificationService.updatePreferences(request));
	}

}
