package tsh.d94.app.server.interfaces.controller.cira;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import tsh.d94.app.server.apService.cira.IssueService;
import tsh.d94.app.server.apService.cira.dto.CreateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.IssueFilterRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.interfaces.controller.GlobalExceptionHandler;

/** 경로·메서드 → 서비스 호출, 응답 래퍼({success,data}), 요청 검증, 예외 → 상태코드 */
@ExtendWith(MockitoExtension.class)
class IssueControllerTest {

	@Mock
	private IssueService issueService;
	@InjectMocks
	private IssueController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = MockMvcBuilders.standaloneSetup(controller)
			.setControllerAdvice(new GlobalExceptionHandler())
			.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
			.build();
	}

	private static IssueResponse issue(String key) {
		return IssueResponse.builder().id("i-1").issueKey(key).title("t").build();
	}

	@Test
	@DisplayName("POST /api/v1/projects/{projectId}/issues → createIssue, {success:true,data}")
	void create() throws Exception {
		given(issueService.createIssue(eq("p-1"), any())).willReturn(issue("CIRA-1"));

		mvc.perform(post("/api/v1/projects/p-1/issues").contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"이슈 1\",\"priority\":\"MEDIUM\",\"dueDt\":\"2026-12-30\",\"storyPnt\":1}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.data.issueKey").value("CIRA-1"));

		ArgumentCaptor<CreateIssueRequest> req = ArgumentCaptor.forClass(CreateIssueRequest.class);
		then(issueService).should().createIssue(eq("p-1"), req.capture());
		org.assertj.core.api.Assertions.assertThat(req.getValue().getDueDt()).hasToString("2026-12-30");
	}

	@Test
	@DisplayName("[D25] 제목 없는 이슈 생성은 400 VALIDATION_ERROR (CreateIssueRequest 에 검증 없음)")
	void create_withoutTitle_isBadRequest() throws Exception {
		mvc.perform(post("/api/v1/projects/p-1/issues").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest());
		then(issueService).shouldHaveNoInteractions();
	}

	@Test
	@DisplayName("GET /projects/{projectId}/issues: 필터·페이지 파라미터가 서비스로 전달된다")
	void list_passesFilterAndPage() throws Exception {
		given(issueService.listIssues(eq("p-1"), any(), any())).willReturn(new PageImpl<>(List.of(issue("CIRA-2")), org.springframework.data.domain.PageRequest.of(2, 5), 11));

		mvc.perform(get("/api/v1/projects/p-1/issues").param("priority", "HIGH").param("keyword", "로그인")
				.param("page", "2").param("size", "5"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data.content[0].issueKey").value("CIRA-2"));

		ArgumentCaptor<IssueFilterRequest> filter = ArgumentCaptor.forClass(IssueFilterRequest.class);
		ArgumentCaptor<Pageable> page = ArgumentCaptor.forClass(Pageable.class);
		then(issueService).should().listIssues(eq("p-1"), filter.capture(), page.capture());
		org.assertj.core.api.Assertions.assertThat(filter.getValue().getPriority()).isEqualTo("HIGH");
		org.assertj.core.api.Assertions.assertThat(filter.getValue().getKeyword()).isEqualTo("로그인");
		org.assertj.core.api.Assertions.assertThat(page.getValue().getPageNumber()).isEqualTo(2);
		org.assertj.core.api.Assertions.assertThat(page.getValue().getPageSize()).isEqualTo(5);
	}

	@Test
	@DisplayName("GET·PUT·DELETE /issues/{issueId} 라우팅")
	void getUpdateDelete() throws Exception {
		given(issueService.getIssue("i-1")).willReturn(issue("CIRA-1"));
		given(issueService.updateIssue(eq("i-1"), any())).willReturn(issue("CIRA-1"));

		mvc.perform(get("/api/v1/issues/i-1")).andExpect(jsonPath("$.data.id").value("i-1"));
		mvc.perform(put("/api/v1/issues/i-1").contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"x\"}"))
			.andExpect(status().isOk());
		mvc.perform(delete("/api/v1/issues/i-1")).andExpect(status().isOk());

		then(issueService).should().deleteIssue("i-1");
	}

	@Test
	@DisplayName("PUT /issues/{id}/status: statusId 가 없으면 400, 있으면 changeStatus")
	void changeStatus_validated() throws Exception {
		mvc.perform(put("/api/v1/issues/i-1/status").contentType(MediaType.APPLICATION_JSON).content("{}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
		then(issueService).shouldHaveNoInteractions();

		given(issueService.changeStatus(eq("i-1"), any())).willReturn(issue("CIRA-1"));
		mvc.perform(put("/api/v1/issues/i-1/status").contentType(MediaType.APPLICATION_JSON)
			.content("{\"statusId\":\"s-done\"}")).andExpect(status().isOk());
	}

	@Test
	@DisplayName("허용되지 않은 전이(CiraException) → 400 + 도메인 코드")
	void changeStatus_domainError() throws Exception {
		given(issueService.changeStatus(eq("i-1"), any()))
			.willThrow(new CiraException(ErrorCode.ISSUE_INVALID_TRANSITION));

		mvc.perform(put("/api/v1/issues/i-1/status").contentType(MediaType.APPLICATION_JSON)
				.content("{\"statusId\":\"s-done\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value(ErrorCode.ISSUE_INVALID_TRANSITION.getCode()));
	}

	@Test
	@DisplayName("GET /issues/{id}/available-transitions")
	void availableTransitions() throws Exception {
		given(issueService.getAvailableTransitions("i-1")).willReturn(List.of());

		mvc.perform(get("/api/v1/issues/i-1/available-transitions"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.data").isArray());
	}

}
