package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.cira.CiraDocsService;
import tsh.d94.app.server.apService.cira.CiraDocsService.DeriveFromIssueRequest;
import tsh.d94.app.server.apService.cira.CiraDocsService.DocRuleDto;
import tsh.d94.app.server.apService.cira.CiraDocsService.IssueDocsResponse;
import tsh.d94.app.server.apService.cira.CiraDocsService.PromoteRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteResponse;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

/** CIRA Task ↔ Docs: 문서 관리 기준정보, Task 연결 문서·계보, 파생, Docs 노트 → Task 승격. 프로젝트 멤버가 아니면 404 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}")
@RequiredArgsConstructor
public class CiraDocsController {

	private final CiraDocsService service;

	@GetMapping("/doc-rules")
	public ApiResponse<List<DocRuleDto>> rules(@PathVariable String projectId) {
		return ApiResponse.ok(service.rules(projectId));
	}

	/** 그 Task 타입의 슬롯 목록을 통째로 저장 (프로젝트 ADMIN) */
	@PutMapping("/doc-rules/{issueTypeId}")
	public ApiResponse<List<DocRuleDto>> saveRules(@PathVariable String projectId, @PathVariable String issueTypeId,
		@RequestBody List<DocRuleDto> body) {
		return ApiResponse.ok(service.saveRules(projectId, issueTypeId, body));
	}

	@GetMapping("/issues/{issueId}/docs")
	public ApiResponse<IssueDocsResponse> docs(@PathVariable String projectId, @PathVariable String issueId) {
		return ApiResponse.ok(service.docs(projectId, issueId));
	}

	/** 메인 노트가 없는 Task 에 메인 노트 생성·연결 (있으면 그대로) */
	@PostMapping("/issues/{issueId}/docs")
	public ApiResponse<IssueDocsResponse> ensureMain(@PathVariable String projectId, @PathVariable String issueId) {
		return ApiResponse.ok(service.ensureMain(projectId, issueId));
	}

	@PostMapping("/issues/{issueId}/docs/derive")
	public ApiResponse<NoteResponse> derive(@PathVariable String projectId, @PathVariable String issueId,
		@RequestBody DeriveFromIssueRequest req) {
		return ApiResponse.ok(service.derive(projectId, issueId, req));
	}

	@PostMapping("/docs/notes/{noteId}/promote-to-cira")
	public ApiResponse<IssueResponse> promote(@PathVariable String projectId, @PathVariable String noteId,
		@RequestBody PromoteRequest req) {
		return ApiResponse.ok(service.promote(projectId, noteId, req));
	}
}
