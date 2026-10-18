package tsh.d94.app.server.apService.cira.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GitRepositoryRequest {

	/** Git 저장소 이름 (예: my-repo) */
	private String repoNm;

	/** Git 저장소 URL */
	private String repoUrl;

	/** Git 공급자: GITHUB | GITLAB */
	private String provider;

	/** 기본 브랜치 (기본값: main) */
	private String defaultBranch = "main";

	/** 개인 액세스 토큰 (평문, 서버 내에서 AES-256 암호화됨) */
	private String accessToken;
}
