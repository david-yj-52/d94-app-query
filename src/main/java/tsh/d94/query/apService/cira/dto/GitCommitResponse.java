package tsh.d94.app.server.apService.cira.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GitCommitResponse {

	private String objId;
	private String repoId;
	private String issueId;
	private String commitHash;
	private String msg;
	private String authorNm;
	private String authorEmail;
	private LocalDateTime commitDt;
}
