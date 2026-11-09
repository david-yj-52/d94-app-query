package tsh.d94.app.server.interfaces.controller.cira;

import java.io.InputStream;
import java.util.List;

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
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

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

	@GetMapping("/attachments/{attachmentId}/download")
	public ResponseEntity<StreamingResponseBody> download(@PathVariable String attachmentId) {
		SnCiraAttachmentModel attachment = attachmentService.getAttachmentForDownload(attachmentId);

		StreamingResponseBody body = outputStream -> {
			try (InputStream is = attachmentService.getObjectStream(attachment.getS3Key())) {
				is.transferTo(outputStream);
			}
		};

		MediaType mediaType;
		try {
			mediaType = MediaType.parseMediaType(attachment.getMimeType());
		} catch (Exception e) {
			mediaType = MediaType.APPLICATION_OCTET_STREAM;
		}

		HttpHeaders headers = new HttpHeaders();
		headers.setContentDisposition(
			ContentDisposition.inline().filename(attachment.getFileNm()).build()
		);

		return ResponseEntity.ok()
			.headers(headers)
			.contentType(mediaType)
			.body(body);
	}

	@DeleteMapping("/attachments/{attachmentId}")
	public ApiResponse<Void> delete(@PathVariable String attachmentId) {
		attachmentService.delete(attachmentId);
		return ApiResponse.noContent();
	}

}
