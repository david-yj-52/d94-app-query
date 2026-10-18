package tsh.d94.app.server.apService.cira.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GitRepositoryResponse {

	private String objId;
	private String projectId;
	private String repoNm;
	private String repoUrl;
	private String provider;
	private String defaultBranch;
	/** 등록 완료 직후 한 번만 노출되는 Webhook Secret (이후 조회 시 null) */
	private String webhookSecret;
	/** Webhook 수신 URL */
	private String webhookUrl;
}
