package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.CommentService;
import tsh.d94.app.server.apService.cira.dto.AddReactionRequest;
import tsh.d94.app.server.apService.cira.dto.CommentResponse;
import tsh.d94.app.server.apService.cira.dto.CreateCommentRequest;
import tsh.d94.app.server.apService.cira.dto.UpdateCommentRequest;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class CommentController {

	private final CommentService commentService;

	@PostMapping("/issues/{issueId}/comments")
	public ApiResponse<CommentResponse> createComment(
		@PathVariable String issueId,
		@Valid @RequestBody CreateCommentRequest request
	) {
		return ApiResponse.created(commentService.createComment(issueId, request));
	}

	@GetMapping("/issues/{issueId}/comments")
	public ApiResponse<List<CommentResponse>> getComments(@PathVariable String issueId) {
		return ApiResponse.ok(commentService.getComments(issueId));
	}

	@PutMapping("/comments/{commentId}")
	public ApiResponse<CommentResponse> updateComment(
		@PathVariable String commentId,
		@Valid @RequestBody UpdateCommentRequest request
	) {
		return ApiResponse.ok(commentService.updateComment(commentId, request));
	}

	@DeleteMapping("/comments/{commentId}")
	public ApiResponse<Void> deleteComment(@PathVariable String commentId) {
		commentService.deleteComment(commentId);
		return ApiResponse.noContent();
	}

	@PostMapping("/comments/{commentId}/reactions")
	public ApiResponse<CommentResponse> toggleReaction(
		@PathVariable String commentId,
		@Valid @RequestBody AddReactionRequest request
	) {
		return ApiResponse.ok(commentService.toggleReaction(commentId, request));
	}
}
