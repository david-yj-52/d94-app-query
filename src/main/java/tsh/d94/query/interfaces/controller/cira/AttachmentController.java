package tsh.d94.app.server.interfaces.controller.cira;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import tsh.d94.app.server.apService.cira.AttachmentService;
import tsh.d94.app.server.apService.cira.dto.AttachmentResponse;
import tsh.d94.app.server.apService.cira.dto.StartAttachmentUploadResponse;
import tsh.d94.app.server.data.orm.cira.ciraAttachment.SnCiraAttachmentModel;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AttachmentController {

	private final AttachmentService attachmentService;

	@PostMapping("/issues/{issueId}/attachments")
	public ApiResponse<AttachmentResponse> upload(
		@PathVariable String issueId,
		@RequestParam("file") MultipartFile file
	) {
		return ApiResponse.ok(attachmentService.upload(issueId, file));
	}

	@GetMapping("/issues/{issueId}/attachments")
	public ApiResponse<List<AttachmentResponse>> listAttachments(@PathVariable String issueId) {
		return ApiResponse.ok(attachmentService.listAttachments(issueId));
	}

	// ── 조각 업로드 (UMD 요청 본문 1MB 상한 우회). 순서: start → parts/{1..partCount} → complete

	public record StartUploadRequest(long fileSize) {
	}

	public record CompleteUploadRequest(String fileName, long fileSize, String mimeType) {
	}

	@PostMapping("/issues/{issueId}/attachments/uploads")
	public ApiResponse<StartAttachmentUploadResponse> startUpload(@PathVariable String issueId,
		@RequestBody StartUploadRequest req) {
		return ApiResponse.ok(attachmentService.startUpload(issueId, req.fileSize()));
	}

	@PutMapping(value = "/issues/{issueId}/attachments/uploads/{uploadId}/parts/{n}",
		consumes = MediaType.APPLICATION_OCTET_STREAM_VALUE)
	public ApiResponse<Void> putPart(@PathVariable String issueId, @PathVariable String uploadId,
		@PathVariable int n, @RequestBody byte[] body) {
		attachmentService.putPart(issueId, uploadId, n, body);
		return ApiResponse.noContent();
	}

	@PostMapping("/issues/{issueId}/attachments/uploads/{uploadId}/complete")
	public ApiResponse<AttachmentResponse> completeUpload(@PathVariable String issueId, @PathVariable String uploadId,
		@RequestBody CompleteUploadRequest req) {
		return ApiResponse.ok(
			attachmentService.completeUpload(issueId, uploadId, req.fileName(), req.fileSize(), req.mimeType()));
	}

	/**
	 * 첨부 파일 본문. 요청 스레드에서 동기로 흘려보낸다.
	 * StreamingResponseBody(비동기)는 ASYNC 재디스패치에서 JWT 필터가 다시 돌지 않아
	 * anyRequest().authenticated() 에 막혀 파일 대신 오류가 내려갔다.
	 * inline + UTF-8 파일명(RFC 5987) → 브라우저가 열 수 있는 형식(이미지·PDF·텍스트)은 바로 보여 준다.
	 * Range: bytes=a-b 를 받으면 그 구간만 206 으로 준다 — UMD 응답 상한(5MB)보다 큰 파일은 웹이 나눠 받아 잇는다.
	 */
	@GetMapping("/attachments/{attachmentId}/download")
	public ResponseEntity<InputStreamResource> download(@PathVariable String attachmentId,
		@RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
		SnCiraAttachmentModel attachment = attachmentService.getAttachmentForDownload(attachmentId);

		MediaType mediaType;
		try {
			mediaType = MediaType.parseMediaType(attachment.getMimeType());
		} catch (Exception e) {
			mediaType = MediaType.APPLICATION_OCTET_STREAM;
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentDisposition(
			ContentDisposition.inline().filename(attachment.getFileNm(), StandardCharsets.UTF_8).build()
		);
		headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");

		Long total = attachment.getFileSize();
		if (range != null && total != null && total > 0) {
			long start;
			long end;
			try {
				HttpRange r = HttpRange.parseRanges(range).get(0); // ponytail: 첫 구간만 (multipart/byteranges 미지원)
				start = r.getRangeStart(total);
				end = r.getRangeEnd(total);
				if (start >= total || start > end) {
					throw new IllegalArgumentException("범위 밖");
				}
			} catch (IllegalArgumentException | IndexOutOfBoundsException e) {
				headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + total);
				return ResponseEntity.status(HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE).headers(headers).build();
			}
			long length = end - start + 1;
			headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + total);
			return ResponseEntity.status(HttpStatus.PARTIAL_CONTENT).headers(headers).contentType(mediaType)
				.contentLength(length)
				.body(new InputStreamResource(attachmentService.getObjectStream(attachment.getS3Key(), start, length)));
		}

		InputStreamResource body = new InputStreamResource(attachmentService.getObjectStream(attachment.getS3Key()));
		ResponseEntity.BodyBuilder res = ResponseEntity.ok().headers(headers).contentType(mediaType);
		if (total != null) {
			res.contentLength(total);
		}
		return res.body(body);
	}

	@DeleteMapping("/attachments/{attachmentId}")
	public ApiResponse<Void> delete(@PathVariable String attachmentId) {
		attachmentService.delete(attachmentId);
		return ApiResponse.noContent();
	}

}
