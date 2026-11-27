package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.cira.BoardService;
import tsh.d94.app.server.apService.cira.CiraDocsService;
import tsh.d94.app.server.apService.cira.CiraDocsService.DocRuleDto;
import tsh.d94.app.server.apService.cira.CiraFieldService;
import tsh.d94.app.server.apService.cira.ProjectService;
import tsh.d94.app.server.apService.cira.SpaceGuard;
import tsh.d94.app.server.apService.cira.SpaceService;
import tsh.d94.app.server.apService.cira.dto.AddStatusRequest;
import tsh.d94.app.server.apService.cira.dto.BoardResponse;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Field;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.FieldKeys;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.ImportField;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.SaveCustomField;
import tsh.d94.app.server.apService.cira.dto.CreateBoardRequest;
import tsh.d94.app.server.apService.cira.dto.SpaceDto.SaveSpace;
import tsh.d94.app.server.apService.cira.dto.SpaceDto.Space;
import tsh.d94.app.server.apService.cira.dto.UpdateTransitionsRequest;
import tsh.d94.app.server.apService.cira.dto.WorkflowStatusResponse;
import tsh.d94.app.server.apService.cira.dto.WorkflowTransitionResponse;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

/**
 * 작업 공간(V630) CRUD 와 공간별 자율 설정. /spaces/{spaceId}/... 는 기존 /projects/{id}/...?spaceId= 와 같은 서비스를 탄다.
 * 권한: 조회 = 프로젝트 멤버, 공간 설정 수정 = 프로젝트 ADMIN 또는 공간 관리자, 공간 생성·삭제·관리자 임명 = 프로젝트 ADMIN.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SpaceController {

	private final SpaceService spaceService;
	private final SpaceGuard guard;
	private final ProjectService projectService;
	private final BoardService boardService;
	private final CiraFieldService fieldService;
	private final CiraDocsService docsService;

	// ── 공간 ──

	@GetMapping("/projects/{projectId}/spaces")
	public ApiResponse<List<Space>> list(@PathVariable String projectId) {
		return ApiResponse.ok(spaceService.list(projectId));
	}

	@PostMapping("/projects/{projectId}/spaces")
	public ApiResponse<Space> create(@PathVariable String projectId, @RequestBody SaveSpace req) {
		return ApiResponse.created(spaceService.create(projectId, req));
	}

	@GetMapping("/spaces/{spaceId}")
	public ApiResponse<Space> get(@PathVariable String spaceId) {
		return ApiResponse.ok(spaceService.get(spaceId));
	}

	@PutMapping("/spaces/{spaceId}")
	public ApiResponse<Space> update(@PathVariable String spaceId, @RequestBody SaveSpace req) {
		return ApiResponse.ok(spaceService.update(spaceId, req));
	}

	@DeleteMapping("/spaces/{spaceId}")
	public ApiResponse<Void> delete(@PathVariable String spaceId) {
		spaceService.delete(spaceId);
		return ApiResponse.noContent();
	}

	// ── 공간별 워크플로 ──

	@GetMapping("/spaces/{spaceId}/statuses")
	public ApiResponse<List<WorkflowStatusResponse>> statuses(@PathVariable String spaceId) {
		return ApiResponse.ok(projectService.getWorkflowStatuses(project(spaceId), spaceId));
	}

	@PostMapping("/spaces/{spaceId}/statuses")
	public ApiResponse<WorkflowStatusResponse> addStatus(@PathVariable String spaceId, @RequestBody AddStatusRequest req) {
		return ApiResponse.ok(projectService.addWorkflowStatus(project(spaceId), spaceId, req));
	}

	@DeleteMapping("/spaces/{spaceId}/statuses/{statusId}")
	public ApiResponse<Void> deleteStatus(@PathVariable String spaceId, @PathVariable String statusId) {
		projectService.deleteWorkflowStatus(project(spaceId), statusId);
		return ApiResponse.noContent();
	}

	@GetMapping("/spaces/{spaceId}/transitions")
	public ApiResponse<List<WorkflowTransitionResponse>> transitions(@PathVariable String spaceId) {
		return ApiResponse.ok(projectService.getWorkflowTransitions(project(spaceId), spaceId));
	}

	@PutMapping("/spaces/{spaceId}/transitions")
	public ApiResponse<List<WorkflowTransitionResponse>> saveTransitions(@PathVariable String spaceId,
		@RequestBody UpdateTransitionsRequest req) {
		return ApiResponse.ok(projectService.updateWorkflowTransitions(project(spaceId), spaceId, req));
	}

	// ── 공간별 보드 ──

	@GetMapping("/spaces/{spaceId}/boards")
	public ApiResponse<List<BoardResponse>> boards(@PathVariable String spaceId) {
		return ApiResponse.ok(boardService.listBoards(project(spaceId), spaceId));
	}

	@PostMapping("/spaces/{spaceId}/boards")
	public ApiResponse<BoardResponse> createBoard(@PathVariable String spaceId, @Valid @RequestBody CreateBoardRequest req) {
		req.setSpaceId(spaceId);
		return ApiResponse.created(boardService.createBoard(project(spaceId), req));
	}

	// ── 공간별 필드 세트·컬럼 ──

	@GetMapping("/spaces/{spaceId}/fields")
	public ApiResponse<List<Field>> fields(@PathVariable String spaceId) {
		return ApiResponse.ok(fieldService.projectFields(project(spaceId), spaceId));
	}

	@PutMapping("/spaces/{spaceId}/fields")
	public ApiResponse<List<Field>> saveFields(@PathVariable String spaceId, @RequestBody FieldKeys req) {
		return ApiResponse.ok(fieldService.replaceProjectFields(project(spaceId), spaceId,
			req.fieldKeys() == null ? List.of() : req.fieldKeys()));
	}

	@PostMapping("/spaces/{spaceId}/custom-fields")
	public ApiResponse<Field> createField(@PathVariable String spaceId, @RequestBody SaveCustomField req) {
		return ApiResponse.ok(fieldService.createProjectField(project(spaceId), spaceId, req));
	}

	@PostMapping("/spaces/{spaceId}/custom-fields/import")
	public ApiResponse<Field> importField(@PathVariable String spaceId, @RequestBody ImportField req) {
		return ApiResponse.ok(fieldService.importToProject(project(spaceId), spaceId, req.sourceFieldId()));
	}

	@GetMapping("/spaces/{spaceId}/column-pref")
	public ApiResponse<FieldKeys> columnPref(@PathVariable String spaceId) {
		return ApiResponse.ok(fieldService.columnPref(project(spaceId), spaceId));
	}

	@PutMapping("/spaces/{spaceId}/column-pref")
	public ApiResponse<FieldKeys> saveColumnPref(@PathVariable String spaceId, @RequestBody FieldKeys req) {
		return ApiResponse.ok(fieldService.saveColumnPref(project(spaceId), spaceId, req.fieldKeys()));
	}

	@DeleteMapping("/spaces/{spaceId}/column-pref")
	public ApiResponse<FieldKeys> resetColumnPref(@PathVariable String spaceId) {
		return ApiResponse.ok(fieldService.resetColumnPref(project(spaceId), spaceId));
	}

	// ── 산출물 규칙 ──

	/** 공간 규칙 + 그 공간에 적용되는 프로젝트 공통 규칙(locked 는 기준정보에서 가져온 것) */
	@GetMapping("/spaces/{spaceId}/doc-rules")
	public ApiResponse<List<DocRuleDto>> spaceRules(@PathVariable String spaceId) {
		return ApiResponse.ok(docsService.spaceRules(spaceId));
	}

	@PutMapping("/spaces/{spaceId}/doc-rules/{issueTypeId}")
	public ApiResponse<List<DocRuleDto>> saveSpaceRules(@PathVariable String spaceId, @PathVariable String issueTypeId,
		@RequestBody List<DocRuleDto> body) {
		return ApiResponse.ok(docsService.saveSpaceRules(spaceId, issueTypeId, body));
	}

	/** 전사 기준정보 공통 산출물 규칙 (조회는 로그인 사용자 — 프로젝트 ADMIN 이 가져올 목록을 본다) */
	@GetMapping("/cira/doc-rules")
	public ApiResponse<List<DocRuleDto>> globalRules() {
		return ApiResponse.ok(docsService.globalRules());
	}

	@PreAuthorize("hasRole('ADMIN')")
	@PutMapping("/cira/doc-rules/{issueTypeId}")
	public ApiResponse<List<DocRuleDto>> saveGlobalRules(@PathVariable String issueTypeId, @RequestBody List<DocRuleDto> body) {
		return ApiResponse.ok(docsService.saveGlobalRules(issueTypeId, body));
	}

	private String project(String spaceId) {
		return guard.get(spaceId).getProjectId();
	}
}
