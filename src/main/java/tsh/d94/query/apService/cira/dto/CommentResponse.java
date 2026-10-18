package tsh.d94.app.server.apService.cira.dto;

import java.time.LocalDateTime;
import java.util.List;

import tsh.d94.lib.core.apService.auth.dto.UserResponse;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CommentResponse {
	private String id;
	private String issueId;
	private String parentId;
	private String content;
	private UserResponse author;
	private List<CommentResponse> replies;
	private List<CommentReactionResponse> reactions;
	private LocalDateTime createdAt;
	private LocalDateTime modifiedAt;
}
