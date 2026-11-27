package tsh.d94.app.server.apService.cira.dto;

/** 작업 공간 요청·응답 (V650) */
public final class SpaceDto {

	private SpaceDto() {
	}

	/** presetTypeId null = 빈 공간(To Do → In Progress → Done + 칸반 1개). adminUserId null = 만든 사람 */
	public record SaveSpace(String name, String key, String description, String presetTypeId, String adminUserId,
		Short sortOrd) {
	}

	public record Space(String id, String projectId, String name, String key, String description, String presetTypeId,
		String adminUserId, String adminNm, boolean isDefault, short sortOrd, boolean canManage) {
	}
}
