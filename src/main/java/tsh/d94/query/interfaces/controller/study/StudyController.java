package tsh.d94.app.server.interfaces.controller.study;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.study.StudyAnnotationService;
import tsh.d94.app.server.apService.study.StudyDocumentService;
import tsh.d94.app.server.apService.study.StudyPageService;
import tsh.d94.app.server.apService.study.StudyTermService;
import tsh.d94.app.server.apService.study.StudyUploadService;
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

	@GetMapping("/documents/{id}/figures/{blockKey}")
	public ResponseEntity<StreamingResponseBody> figure(@PathVariable String id, @PathVariable String blockKey) {
		InputStream in = documents.figure(id, blockKey);
		StreamingResponseBody body = out -> {
			try (in) {
				in.transferTo(out);
			}
		};
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_PNG)
			.cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
			.body(body);
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
