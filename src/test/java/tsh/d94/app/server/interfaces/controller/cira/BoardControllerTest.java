package tsh.d94.app.server.interfaces.controller.cira;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tsh.d94.app.server.apService.cira.BoardService;
import tsh.d94.app.server.apService.cira.dto.BoardDetailResponse;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.support.ControllerTestSupport;

@ExtendWith(MockitoExtension.class)
class BoardControllerTest {

	@Mock
	private BoardService boardService;
	@InjectMocks
	private BoardController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = ControllerTestSupport.mvc(controller);
	}

	@Test
	@DisplayName("PUT /boards/{boardId}/issues/{issueId}/move → moveIssue, 보드 상세 반환")
	void move() throws Exception {
		given(boardService.moveIssue(eq("b-1"), eq("i-1"), any()))
			.willReturn(BoardDetailResponse.builder().id("b-1").build());

		mvc.perform(put("/api/v1/boards/b-1/issues/i-1/move").contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetColumnId\":\"c-2\",\"afterIssueId\":\"i-0\"}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.id").value("b-1"));
	}

	@Test
	@DisplayName("WIP 초과(CiraException) → 400 + BOARD_WIP_LIMIT_EXCEEDED 코드")
	void move_wipExceeded() throws Exception {
		given(boardService.moveIssue(eq("b-1"), eq("i-1"), any()))
			.willThrow(new CiraException(ErrorCode.BOARD_WIP_LIMIT_EXCEEDED));

		mvc.perform(put("/api/v1/boards/b-1/issues/i-1/move").contentType(MediaType.APPLICATION_JSON)
				.content("{\"targetColumnId\":\"c-2\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.BOARD_WIP_LIMIT_EXCEEDED.getCode()));
	}

	@Test
	@DisplayName("보드·컬럼 조회/삭제 경로")
	void boardAndColumnRoutes() throws Exception {
		given(boardService.getBoardDetail("b-1")).willReturn(BoardDetailResponse.builder().id("b-1").build());

		mvc.perform(get("/api/v1/boards/b-1")).andExpect(jsonPath("$.data.id").value("b-1"));
		mvc.perform(delete("/api/v1/boards/b-1/columns/c-1")).andExpect(status().isOk());
		mvc.perform(delete("/api/v1/boards/b-1")).andExpect(status().isOk());

		then(boardService).should().deleteColumn("b-1", "c-1");
		then(boardService).should().deleteBoard("b-1");
	}

}
