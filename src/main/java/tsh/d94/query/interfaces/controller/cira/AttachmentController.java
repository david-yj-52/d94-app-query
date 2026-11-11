package tsh.d94.app.server.interfaces.controller.cira;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import tsh.d94.app.server.apService.cira.AttachmentService;
import tsh.d94.app.server.apService.cira.dto.AttachmentResponse;
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

	/**
	 * 첨부 파일 본문. 요청 스레드에서 동기로 흘려보낸다.
	 * StreamingResponseBody(비동기)는 ASYNC 재디스패치에서 JWT 필터가 다시 돌지 않아
	 * anyRequest().authenticated() 에 막혀 파일 대신 오류가 내려갔다.
	 * inline + UTF-8 파일명(RFC 5987) → 브라우저가 열 수 있는 형식(이미지·PDF·텍스트)은 바로 보여 준다.
	 */
	@GetMapping("/attachments/{attachmentId}/download")
	public ResponseEntity<InputStreamResource> download(@PathVariable String attachmentId) {
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

		InputStreamResource body = new InputStreamResource(attachmentService.getObjectStream(attachment.getS3Key()));
		ResponseEntity.BodyBuilder res = ResponseEntity.ok().headers(headers).contentType(mediaType);
		if (attachment.getFileSize() != null) {
			res.contentLength(attachment.getFileSize());
		}
		return res.body(body);
	}

	@DeleteMapping("/attachments/{attachmentId}")
	public ApiResponse<Void> delete(@PathVariable String attachmentId) {
		attachmentService.delete(attachmentId);
		return ApiResponse.noContent();
	}

}
