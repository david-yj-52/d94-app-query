package tsh.d94.app.server.apService.cira;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tsh.d94.app.server.apService.cira.dto.AttachmentResponse;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraAttachment.SnCiraAttachmentAccess;
import tsh.d94.app.server.data.orm.cira.ciraAttachment.SnCiraAttachmentModel;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueLog.SnCiraIssueLogAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueLog.SnCiraIssueLogModel;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.service.StorageService;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AttachmentService {

	private final SnCiraAttachmentAccess attachmentAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraProjectMemberAccess projectMemberAccess;
	private final SnCiraIssueLogAccess issueLogAccess;
	private final GsUserAccess userAccess;
	private final StorageService storageService;

	@Transactional
	public AttachmentResponse upload(String issueId, MultipartFile file) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel uploader = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		SnCiraIssueModel issue = issueAccess.findById(issueId);
		if (issue.getDeletedAt() != null) {
			throw new CiraException(ErrorCode.ISSUE_NOT_FOUND, "이슈를 찾을 수 없습니다: " + issueId);
		}

		validateMembership(uploader.getObjId(), issue.getProjectId());

		String sanitizedName = sanitizeFileName(file.getOriginalFilename());
		String s3Key = String.format("attachments/%s/%s/%s_%s",
			issue.getProjectId(), issueId, UUID.randomUUID(), sanitizedName);

		storageService.upload(file, s3Key);

		SnCiraAttachmentModel attachment = SnCiraAttachmentModel.builder()
			.issueId(issueId)
			.fileNm(sanitizedName)
			.s3Key(s3Key)
			.fileSize(file.getSize())
			.mimeType(file.getContentType())
			.uploadedBy(uploader.getObjId())
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("UPLOAD-ATTACHMENT")
			.useStatCd(UseStatCd.Usable)
			.evtNm("UploadAttachment")
			.prevEvntNm("None")
			.build();

		attachmentAccess.save(attachment);

		recordLog(issueId, "attachment", null, sanitizedName, uploader.getObjId());

		String downloadUrl = storageService.generatePresignedUrl(s3Key);
		return mapToResponse(attachment, downloadUrl);
	}

	public List<AttachmentResponse> listAttachments(String issueId) {
		return attachmentAccess.findActiveByIssueId(issueId).stream()
			.map(a -> {
				try {
					String url = storageService.generatePresignedUrl(a.getS3Key());
					return mapToResponse(a, url);
				} catch (Exception e) {
					log.warn("Presigned URL generation failed for attachment {}: {}", a.getObjId(), e.getMessage());
					return mapToResponse(a, null);
				}
			})
			.collect(Collectors.toList());
	}

	public String getDownloadUrl(String attachmentId) {
		SnCiraAttachmentModel attachment = attachmentAccess.findById(attachmentId);
		if (attachment.getUseStatCd() == UseStatCd.Delete) {
			throw new CiraException(ErrorCode.ISSUE_NOT_FOUND, "첨부파일을 찾을 수 없습니다: " + attachmentId);
		}
		return storageService.generatePresignedUrl(attachment.getS3Key());
	}

	@Transactional
	public void delete(String attachmentId) {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		GsUserModel user = userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));

		SnCiraAttachmentModel attachment = attachmentAccess.findById(attachmentId);
		if (attachment.getUseStatCd() == UseStatCd.Delete) {
			throw new CiraException(ErrorCode.ISSUE_NOT_FOUND, "첨부파일을 찾을 수 없습니다: " + attachmentId);
		}

		boolean isUploader = user.getObjId().equals(attachment.getUploadedBy());
		boolean isAdmin = projectMemberAccess.findAllByUserId(user.getObjId()).stream()
			.anyMatch(m -> {
				SnCiraIssueModel issue = issueAccess.findByIdOptional(attachment.getIssueId()).orElse(null);
				return issue != null && m.getProjectId().equals(issue.getProjectId()) && "ADMIN".equals(m.getRole());
			});

		if (!isUploader && !isAdmin) {
			throw new CiraException(ErrorCode.PROJECT_NOT_MEMBER, "첨부파일을 삭제할 권한이 없습니다.");
		}

		storageService.delete(attachment.getS3Key());

		attachment.setUseStatCd(UseStatCd.Delete);
		attachmentAccess.save(attachment);

		recordLog(attachment.getIssueId(), "attachment", attachment.getFileNm(), "DELETED", user.getObjId());
	}

	private void validateMembership(String userId, String projectId) {
		projectMemberAccess.findAllByUserId(userId).stream()
			.filter(m -> m.getProjectId().equals(projectId))
			.findFirst()
			.orElseThrow(() -> new CiraException(ErrorCode.PROJECT_NOT_MEMBER));
	}

	private String sanitizeFileName(String originalName) {
		if (originalName == null || originalName.isBlank()) {
			return "attachment_" + System.currentTimeMillis();
		}
		return originalName.replaceAll("[^a-zA-Z0-9._\\-가-힣ㄱ-ㅎㅏ-ㅣ]", "_");
	}

	private void recordLog(String issueId, String field, String oldVal, String newVal, String changerId) {
		SnCiraIssueLogModel log = SnCiraIssueLogModel.builder()
			.issueId(issueId)
			.fieldNm(field)
			.oldVal(oldVal)
			.newVal(newVal)
			.changedBy(changerId)
			.changedAt(LocalDateTime.now())
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("ATTACHMENT-LOG")
			.useStatCd(UseStatCd.Usable)
			.evtNm("RecordLog")
			.prevEvntNm("None")
			.build();
		issueLogAccess.save(log);
	}

	private AttachmentResponse mapToResponse(SnCiraAttachmentModel model, String downloadUrl) {
		return AttachmentResponse.builder()
			.id(model.getObjId())
			.issueId(model.getIssueId())
			.fileName(model.getFileNm())
			.fileSize(model.getFileSize())
			.mimeType(model.getMimeType())
			.uploadedBy(model.getUploadedBy())
			.uploadedAt(model.getCreatedAt())
			.downloadUrl(downloadUrl)
			.build();
	}

}
