package tsh.d94.app.server.apService.cira.dto;

import java.util.List;

import lombok.Data;

@Data
public class NotificationPreferenceRequest {

	private List<PrefItem> preferences;

	@Data
	public static class PrefItem {
		private String eventType;
		private String channel;
		private boolean enabled;
	}

}
