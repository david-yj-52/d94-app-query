package tsh.d94.app.server.interfaces.controller.study;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.study.StudyAnnotationService;
import tsh.d94.app.server.apService.study.StudyDocumentService;
import tsh.d94.app.server.apService.study.StudyPageService;
import tsh.d94.app.server.apService.study.StudyTermService;
import tsh.d94.app.server.apService.study.StudyUploadService;
import tsh.d94.app.server.apService.study.StudySyntaxService;
import tsh.d94.app.server.apService.study.dictionary.StudyDictionaryService;
import tsh.d94.app.server.apService.study.dto.StudyDtos.*;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

/** PDF 학습 뷰어. 설계: d94-note docs/superpowers/specs/2026-11-09-pdf-study-viewer-design.md §7.1 */
@RestController
@RequestMapping("/api/v1/study")
@RequiredArgsConstructor
public class StudyController {

	private final StudyUploadService uploads;
	private final StudyDocumentService documents;
	private final StudyPageService pages;
	private final StudyTermService terms;
	private final StudyAnnotationService annotations;
	private final StudyDictionaryService dictionary;
	private final StudySyntaxService syntax;

	@PostMapping("/documents/uploads")
	public ApiResponse<StartUploadResponse> startUpload(@RequestBody StartUploadRequest req) {
		return ApiResponse.created(uploads.start(req));
	}

	@PutMapping(value = "/documents/uploads/{uploadId}/parts/{n}", consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ApiResponse<Void> putPart(@PathVariable String uploadId, @PathVariable int n, @RequestBody byte[] body) {
		uploads.putPart(uploadId, n, body);
		return ApiResponse.ok(null);
	}

	@PostMapping("/documents/uploads/{uploadId}/complete")
	public ApiResponse<DocumentResponse> complete(@PathVariable String uploadId) {
		return ApiResponse.created(uploads.complete(uploadId));
	}

	@GetMapping("/documents")
	public ApiResponse<Page<DocumentResponse>> documents(@PageableDefault(size = 20) Pageable pageable) {
		return ApiResponse.ok(documents.list(pageable));
	}

	@GetMapping("/documents/{id}")
	public ApiResponse<DocumentResponse> document(@PathVariable String id) {
		return ApiResponse.ok(documents.get(id));
	}

	@DeleteMapping("/documents/{id}")
	public ApiResponse<Void> deleteDocument(@PathVariable String id) {
		documents.delete(id);
		return ApiResponse.ok(null);
	}

	@GetMapping("/documents/{id}/toc")
	public ApiResponse<List<TocNode>> toc(@PathVariable String id) {
		return ApiResponse.ok(documents.toc(id));
	}

	@GetMapping("/documents/{id}/pages/{pageSectionId}")
	public ApiResponse<PageResponse> page(@PathVariable String id, @PathVariable String pageSectionId) {
		return ApiResponse.ok(pages.getPage(id, pageSectionId));
	}

	@GetMapping("/documents/{id}/pages/{pageSectionId}/syntax")
	public ApiResponse<SyntaxResponse> syntax(@PathVariable String id, @PathVariable String pageSectionId) {
		return ApiResponse.ok(syntax.getPageSyntax(id, pageSectionId));
	}

	@GetMapping("/dictionary")
	public ApiResponse<DictionaryResponse> dictionary(@RequestParam String word) { return ApiResponse.ok(dictionary.lookup(word)); }
	@GetMapping("/dictionary/key")
	public ApiResponse<DictionaryKeyStatus> dictionaryKey() { return ApiResponse.ok(dictionary.keyStatus()); }
	@PutMapping("/dictionary/key")
	public ApiResponse<DictionaryKeyStatus> saveDictionaryKey(@RequestBody DictionaryKeyRequest req) { return ApiResponse.ok(dictionary.registerKey(req.apiKey())); }
	@DeleteMapping("/dictionary/key")
	public ApiResponse<Void> deleteDictionaryKey() { dictionary.deleteKey(); return ApiResponse.ok(null); }

	@PutMapping("/documents/{id}/position")
	public ApiResponse<Void> savePosition(@PathVariable String id, @RequestBody PositionRequest req) {
		documents.savePosition(id, req);
		return ApiResponse.ok(null);
	}

	/** 동기로 흘려보낸다. StreamingResponseBody 는 ASYNC 재디스패치에서 JWT 필터가 안 돌아 403 이 났다 (AttachmentController 참고) */
	@GetMapping("/documents/{id}/figures/{blockKey}")
	public ResponseEntity<InputStreamResource> figure(@PathVariable String id, @PathVariable String blockKey) {
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
			.body(new InputStreamResource(documents.figure(id, blockKey)));
	}

	@GetMapping("/terms")
	public ApiResponse<List<TermResponse>> terms(@RequestParam(required = false) String documentId) {
		return ApiResponse.ok(terms.list(documentId));
	}

	@PostMapping("/terms")
	public ApiResponse<TermResponse> createTerm(@RequestBody TermRequest req) {
		return ApiResponse.created(terms.create(req));
	}

	@PutMapping("/terms/{id}")
	public ApiResponse<TermResponse> updateTerm(@PathVariable String id, @RequestBody TermRequest req) {
		return ApiResponse.ok(terms.update(id, req));
	}

	@DeleteMapping("/terms/{id}")
	public ApiResponse<Void> deleteTerm(@PathVariable String id) {
		terms.delete(id);
		return ApiResponse.ok(null);
	}

	@GetMapping("/annotations")
	public ApiResponse<List<AnnotationListItem>> annotations(@RequestParam(required = false) String documentId) {
		return ApiResponse.ok(annotations.list(documentId));
	}

	@PostMapping("/annotations")
	public ApiResponse<AnnotationListItem> createAnnotation(@RequestBody CreateAnnotationRequest req) {
		return ApiResponse.created(annotations.create(req));
	}

	@PatchMapping("/annotations/{id}")
	public ApiResponse<AnnotationListItem> updateAnnotation(@PathVariable String id,
		@RequestBody UpdateAnnotationRequest req) {
		return ApiResponse.ok(annotations.update(id, req));
	}

	@DeleteMapping("/annotations/{id}")
	public ApiResponse<Void> deleteAnnotation(@PathVariable String id) {
		annotations.delete(id);
		return ApiResponse.ok(null);
	}
}
