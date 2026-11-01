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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import tsh.d94.app.server.apService.cira.ProjectService;
import tsh.d94.app.server.apService.cira.dto.ProjectMemberResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.support.ControllerTestSupport;

@ExtendWith(MockitoExtension.class)
class ProjectControllerTest {

	@Mock
	private ProjectService projectService;
	@InjectMocks
	private ProjectController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = ControllerTestSupport.mvc(controller);
	}

	@Test
	@DisplayName("POST·GET /api/v1/projects, GET·PUT /projects/{id}")
	void projectRoutes() throws Exception {
		ProjectResponse p = ProjectResponse.builder().id("p-1").key("CIRA").name("n").build();
		given(projectService.createProject(any())).willReturn(p);
		given(projectService.getMyProjects()).willReturn(List.of(p));
		given(projectService.getProject("p-1")).willReturn(p);
		given(projectService.updateProject(eq("p-1"), any())).willReturn(p);

		mvc.perform(post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
				.content("{\"key\":\"CIRA\",\"name\":\"n\",\"projectType\":\"SCRUM\"}"))
			.andExpect(status().isOk()).andExpect(jsonPath("$.data.key").value("CIRA"));
		mvc.perform(get("/api/v1/projects")).andExpect(jsonPath("$.data[0].id").value("p-1"));
		mvc.perform(get("/api/v1/projects/p-1")).andExpect(jsonPath("$.data.id").value("p-1"));
		mvc.perform(put("/api/v1/projects/p-1").contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"m\"}"))
			.andExpect(status().isOk());
	}

	@Test
	@DisplayName("멤버 추가·역할 변경·제거 경로")
	void memberRoutes() throws Exception {
		ProjectMemberResponse m = ProjectMemberResponse.builder().userId("u-2").role("DEVELOPER").build();
		given(projectService.addMember(eq("p-1"), any())).willReturn(m);
		given(projectService.updateMemberRole(eq("p-1"), eq("u-2"), any())).willReturn(m);

		mvc.perform(post("/api/v1/projects/p-1/members").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"a@b.c\"}")).andExpect(jsonPath("$.data.userId").value("u-2"));
		mvc.perform(put("/api/v1/projects/p-1/members/u-2/role").contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"ADMIN\"}")).andExpect(status().isOk());
		mvc.perform(delete("/api/v1/projects/p-1/members/u-2")).andExpect(status().isOk())
			.andExpect(jsonPath("$.code").value("204"));

		then(projectService).should().removeMember("p-1", "u-2");
	}

	@Test
	@DisplayName("워크플로우 상태·전이 경로")
	void workflowRoutes() throws Exception {
		given(projectService.getWorkflowStatuses("p-1")).willReturn(List.of());
		given(projectService.getWorkflowTransitions("p-1")).willReturn(List.of());

		mvc.perform(get("/api/v1/projects/p-1/workflow/statuses")).andExpect(jsonPath("$.data").isArray());
		mvc.perform(get("/api/v1/projects/p-1/workflow/transitions")).andExpect(jsonPath("$.data").isArray());
		mvc.perform(delete("/api/v1/projects/p-1/workflow/statuses/s-1")).andExpect(status().isOk());

		then(projectService).should().deleteWorkflowStatus("p-1", "s-1");
	}

	@Test
	@DisplayName("[D19] ADMIN 이 아닌 사용자의 멤버 추가(SecurityException)는 403 이어야 한다 — 지금은 500")
	void notAdmin_isForbidden() throws Exception {
		given(projectService.addMember(eq("p-1"), any()))
			.willThrow(new SecurityException("Access denied: ADMIN role required"));

		mvc.perform(post("/api/v1/projects/p-1/members").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"a@b.c\"}")).andExpect(status().isForbidden());
	}

	@Test
	@DisplayName("[D19] 이미 있는 멤버 추가(IllegalStateException)는 409 이어야 한다 — 지금은 500")
	void duplicateMember_isConflict() throws Exception {
		given(projectService.addMember(eq("p-1"), any()))
			.willThrow(new IllegalStateException("PROJECT_MEMBER_ALREADY_EXISTS"));

		mvc.perform(post("/api/v1/projects/p-1/members").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"a@b.c\"}")).andExpect(status().isConflict());
	}

}
