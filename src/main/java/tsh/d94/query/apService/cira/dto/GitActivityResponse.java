package tsh.d94.app.server.apService.cira.dto;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GitActivityResponse {

	private String issueId;
	private List<GitCommitResponse> commits;
	private List<GitPrResponse> pullRequests;
}
