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

import tsh.d94.app.server.apService.cira.BoardService;
import tsh.d94.app.server.apService.cira.dto.BoardColumnResponse;
import tsh.d94.app.server.apService.cira.dto.BoardDetailResponse;
import tsh.d94.app.server.apService.cira.dto.BoardResponse;
import tsh.d94.app.server.apService.cira.dto.CreateBoardColumnRequest;
import tsh.d94.app.server.apService.cira.dto.CreateBoardRequest;
import tsh.d94.app.server.apService.cira.dto.MoveIssueRequest;
import tsh.d94.app.server.apService.cira.dto.UpdateBoardColumnRequest;
import tsh.d94.app.server.apService.cira.dto.UpdateBoardRequest;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class BoardController {

	private final BoardService boardService;

	@PostMapping("/projects/{projectId}/boards")
	public ApiResponse<BoardResponse> createBoard(
		@PathVariable String projectId,
		@Valid @RequestBody CreateBoardRequest request
	) {
		return ApiResponse.created(boardService.createBoard(projectId, request));
	}

	@GetMapping("/projects/{projectId}/boards")
	public ApiResponse<List<BoardResponse>> listBoards(@PathVariable String projectId) {
		return ApiResponse.ok(boardService.listBoards(projectId));
	}

	@GetMapping("/boards/{boardId}")
	public ApiResponse<BoardDetailResponse> getBoardDetail(@PathVariable String boardId) {
		return ApiResponse.ok(boardService.getBoardDetail(boardId));
	}

	@PutMapping("/boards/{boardId}")
	public ApiResponse<BoardResponse> updateBoard(
		@PathVariable String boardId,
		@RequestBody UpdateBoardRequest request
	) {
		return ApiResponse.ok(boardService.updateBoard(boardId, request));
	}

	@DeleteMapping("/boards/{boardId}")
	public ApiResponse<Void> deleteBoard(@PathVariable String boardId) {
		boardService.deleteBoard(boardId);
		return ApiResponse.noContent();
	}

	@PostMapping("/boards/{boardId}/columns")
	public ApiResponse<BoardColumnResponse> addColumn(
		@PathVariable String boardId,
		@Valid @RequestBody CreateBoardColumnRequest request
	) {
		return ApiResponse.created(boardService.addColumn(boardId, request));
	}

	@PutMapping("/boards/{boardId}/columns/{columnId}")
	public ApiResponse<BoardColumnResponse> updateColumn(
		@PathVariable String boardId,
		@PathVariable String columnId,
		@RequestBody UpdateBoardColumnRequest request
	) {
		return ApiResponse.ok(boardService.updateColumn(boardId, columnId, request));
	}

	@DeleteMapping("/boards/{boardId}/columns/{columnId}")
	public ApiResponse<Void> deleteColumn(
		@PathVariable String boardId,
		@PathVariable String columnId
	) {
		boardService.deleteColumn(boardId, columnId);
		return ApiResponse.noContent();
	}

	@PutMapping("/boards/{boardId}/issues/{issueId}/move")
	public ApiResponse<BoardDetailResponse> moveIssue(
		@PathVariable String boardId,
		@PathVariable String issueId,
		@Valid @RequestBody MoveIssueRequest request
	) {
		return ApiResponse.ok(boardService.moveIssue(boardId, issueId, request));
	}
}
