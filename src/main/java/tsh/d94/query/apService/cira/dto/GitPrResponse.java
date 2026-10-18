package tsh.d94.app.server.apService.cira.dto;

import java.time.LocalDateTime;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GitPrResponse {

	private String objId;
	private String repoId;
	private String issueId;
	private Integer prNo;
	private String title;
	private String prStat;
	private String srcBranch;
	private String tgtBranch;
	private String authorNm;
	private LocalDateTime mergedAt;
	private LocalDateTime closedAt;
}
