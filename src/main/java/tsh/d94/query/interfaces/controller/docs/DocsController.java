package tsh.d94.app.server.interfaces.controller.docs;

import java.time.LocalDate;
import java.util.List;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.docs.DocsDtos.DeriveRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.FolderRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.FolderResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.KindRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.LineageNode;
import tsh.d94.app.server.apService.docs.DocsDtos.MoveFolderRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.MoveNoteRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteSummary;
import tsh.d94.app.server.apService.docs.DocsDtos.TaskResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.TaskStatusRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.VersionResponse;
import tsh.d94.app.server.apService.docs.DocsEvolveService;
import tsh.d94.app.server.apService.docs.DocsFolderService;
import tsh.d94.app.server.apService.docs.DocsNoteService;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

/** Docs — 로그인 사용자의 개인 vault. 남의 폴더·노트는 404. */
@RestController
@RequestMapping("/api/v1/docs")
@RequiredArgsConstructor
public class DocsController {

	private final DocsFolderService folders;
	private final DocsNoteService notes;
	private final DocsEvolveService evolve;

	// ── 폴더 ──

	@GetMapping("/folders")
	public ApiResponse<List<FolderResponse>> listFolders() {
		return ApiResponse.ok(folders.list());
	}

	@PostMapping("/folders")
	public ApiResponse<FolderResponse> createFolder(@RequestBody FolderRequest req) {
		return ApiResponse.ok(folders.create(req));
	}

	@PutMapping("/folders/{id}")
	public ApiResponse<FolderResponse> renameFolder(@PathVariable String id, @RequestBody FolderRequest req) {
		return ApiResponse.ok(folders.rename(id, req));
	}

	@PutMapping("/folders/{id}/move")
	public ApiResponse<FolderResponse> moveFolder(@PathVariable String id, @RequestBody MoveFolderRequest req) {
		return ApiResponse.ok(folders.move(id, req));
	}

	@DeleteMapping("/folders/{id}")
	public ApiResponse<Void> deleteFolder(@PathVariable String id) {
		folders.delete(id);
		return ApiResponse.noContent();
	}

	// ── 노트 ──

	/** folderId="" 이면 최상위 노트만, 생략하면 전체 */
	@GetMapping("/notes")
	public ApiResponse<List<NoteSummary>> listNotes(@RequestParam(required = false) String folderId,
		@RequestParam(required = false) String q, @RequestParam(required = false) String kind,
		@RequestParam(required = false) String stream, @RequestParam(required = false) String tag,
		@RequestParam(required = false) String origin) {
		return ApiResponse.ok(notes.list(folderId, q, kind, stream, tag, origin));
	}

	@GetMapping("/notes/titles")
	public ApiResponse<List<String>> titles(@RequestParam(required = false) String prefix) {
		return ApiResponse.ok(notes.titles(prefix));
	}

	@GetMapping("/notes/by-title")
	public ApiResponse<NoteResponse> byTitle(@RequestParam String title) {
		return ApiResponse.ok(notes.getByTitle(title));
	}

	@PostMapping("/notes")
	public ApiResponse<NoteResponse> createNote(@RequestBody NoteRequest req) {
		return ApiResponse.ok(notes.create(req));
	}

	@GetMapping("/notes/{id}")
	public ApiResponse<NoteResponse> getNote(@PathVariable String id) {
		return ApiResponse.ok(notes.get(id));
	}

	@PutMapping("/notes/{id}")
	public ApiResponse<NoteResponse> updateNote(@PathVariable String id, @RequestBody NoteRequest req) {
		return ApiResponse.ok(notes.update(id, req));
	}

	@PutMapping("/notes/{id}/move")
	public ApiResponse<NoteResponse> moveNote(@PathVariable String id, @RequestBody MoveNoteRequest req) {
		return ApiResponse.ok(notes.move(id, req));
	}

	@DeleteMapping("/notes/{id}")
	public ApiResponse<Void> deleteNote(@PathVariable String id) {
		notes.delete(id);
		return ApiResponse.noContent();
	}

	@GetMapping("/notes/{id}/versions")
	public ApiResponse<List<VersionResponse>> versions(@PathVariable String id) {
		return ApiResponse.ok(notes.versions(id));
	}

	@GetMapping("/notes/{id}/backlinks")
	public ApiResponse<List<NoteSummary>> backlinks(@PathVariable String id) {
		return ApiResponse.ok(notes.backlinks(id));
	}

	// ── 진화 ──

	@PostMapping("/notes/{id}/derive")
	public ApiResponse<NoteResponse> derive(@PathVariable String id, @RequestBody DeriveRequest req) {
		return ApiResponse.ok(evolve.derive(id, req));
	}

	@PutMapping("/notes/{id}/kind")
	public ApiResponse<NoteResponse> changeKind(@PathVariable String id, @RequestBody KindRequest req) {
		return ApiResponse.ok(evolve.changeKind(id, req.kind()));
	}

	@GetMapping("/notes/{id}/lineage")
	public ApiResponse<List<LineageNode>> lineage(@PathVariable String id) {
		return ApiResponse.ok(evolve.lineage(id));
	}

	@GetMapping("/tasks")
	public ApiResponse<List<TaskResponse>> tasks(@RequestParam(required = false) String status,
		@RequestParam(required = false) String tag, @RequestParam(required = false) String stream,
		@RequestParam(required = false) LocalDate dueBefore) {
		return ApiResponse.ok(evolve.tasks(status, tag, stream, dueBefore));
	}

	@PutMapping("/tasks/{noteId}/{lineNo}")
	public ApiResponse<NoteResponse> setTaskStatus(@PathVariable String noteId, @PathVariable int lineNo,
		@RequestBody TaskStatusRequest req) {
		return ApiResponse.ok(evolve.setTaskStatus(noteId, lineNo, req.status()));
	}

	/** 체크박스 줄을 노트로 승격, 새 노트를 돌려준다 */
	@PostMapping("/tasks/{noteId}/{lineNo}/promote")
	public ApiResponse<NoteResponse> promoteTask(@PathVariable String noteId, @PathVariable int lineNo) {
		return ApiResponse.ok(evolve.promoteTask(noteId, lineNo));
	}
}
