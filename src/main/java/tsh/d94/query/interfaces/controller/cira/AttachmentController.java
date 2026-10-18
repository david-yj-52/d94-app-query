package tsh.d94.app.server.interfaces.controller.cira;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.view.RedirectView;

import tsh.d94.app.server.apService.cira.AttachmentService;
import tsh.d94.app.server.apService.cira.dto.AttachmentResponse;
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
	public RedirectView download(@PathVariable String attachmentId) {
		String downloadUrl = attachmentService.getDownloadUrl(attachmentId);
		RedirectView redirect = new RedirectView(downloadUrl);
		redirect.setStatusCode(HttpStatus.FOUND);
		return redirect;
	}

	@DeleteMapping("/attachments/{attachmentId}")
	public ApiResponse<Void> delete(@PathVariable String attachmentId) {
		attachmentService.delete(attachmentId);
		return ApiResponse.noContent();
	}

}
