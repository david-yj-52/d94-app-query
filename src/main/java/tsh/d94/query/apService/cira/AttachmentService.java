package tsh.d94.app.server.apService.cira;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.text.Normalizer;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import tsh.d94.app.server.apService.cira.dto.AttachmentResponse;
import tsh.d94.app.server.apService.cira.dto.StartAttachmentUploadResponse;
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
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;
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

	public static final long MAX_FILE_BYTES = 52_428_800L; // 50MB
	public static final int PART_SIZE = 786_432; // 768KB — UMD 요청 본문 1MB 미만

	private final SnCiraAttachmentAccess attachmentAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraProjectMemberAccess projectMemberAccess;
	private final SnCiraIssueLogAccess issueLogAccess;
	private final GsUserAccess userAccess;
	private final StorageService storageService;

	@Transactional
	public AttachmentResponse upload(String issueId, MultipartFile file) {
		GsUserModel uploader = currentUser();
		SnCiraIssueModel issue = memberIssue(issueId);
		String sanitizedName = sanitizeFileName(file.getOriginalFilename());
		String s3Key = newObjectKey(issue, sanitizedName);
		storageService.upload(file, s3Key);
		return saveAttachment(issue, uploader, sanitizedName, s3Key, file.getSize(), file.getContentType());
	}

	// ── 조각 업로드: UMD 요청 본문 상한(1MB) 때문에 PART_SIZE 로 나눠 받아 MinIO 에서 이어 붙인다 (Study 와 같은 방식)
	// ponytail: 업로드 상태 테이블 없음 — uploadId(UUID)·MinIO 경로만 쓰고 메타는 complete 에서 다시 받는다.
	//           완료되지 않은 조각 정리 스케줄러 없음, 쌓이면 추가.

	public StartAttachmentUploadResponse startUpload(String issueId, long fileSize) {
		memberIssue(issueId);
		if (fileSize <= 0 || fileSize > MAX_FILE_BYTES) {
			throw new CiraException(ErrorCode.ATTACHMENT_INVALID, "파일 크기는 50MB 이하여야 합니다.");
		}
		return new StartAttachmentUploadResponse(UUID.randomUUID().toString(), PART_SIZE, partCount(fileSize));
	}

	public void putPart(String issueId, String uploadId, int n, byte[] body) {
		memberIssue(issueId);
		if (n < 1 || n > partCount(MAX_FILE_BYTES) || body == null || body.length == 0 || body.length > PART_SIZE) {
			throw new CiraException(ErrorCode.ATTACHMENT_INVALID, "잘못된 조각입니다.");
		}
		storageService.putBytes(partKey(issueId, uploadId, n), body, "application/octet-stream"); // 재전송은 덮어쓰기
	}

	@Transactional
	public AttachmentResponse completeUpload(String issueId, String uploadId, String fileName, long fileSize, String mimeType) {
		GsUserModel uploader = currentUser();
		SnCiraIssueModel issue = memberIssue(issueId);
		if (fileSize <= 0 || fileSize > MAX_FILE_BYTES) {
			throw new CiraException(ErrorCode.ATTACHMENT_INVALID, "파일 크기는 50MB 이하여야 합니다.");
		}
		int count = partCount(fileSize);
		List<Integer> missing = new ArrayList<>();
		long total = 0;
		for (int n = 1; n <= count; n++) {
			long size = storageService.objectSize(partKey(issueId, uploadId, n));
			if (size < 0) {
				missing.add(n);
			} else {
				total += size;
			}
		}
		if (!missing.isEmpty()) {
			throw new CiraException(ErrorCode.ATTACHMENT_UPLOAD_INCOMPLETE,
				"missing=" + missing.stream().map(String::valueOf).collect(Collectors.joining(",")));
		}
		if (total != fileSize) {
			throw new CiraException(ErrorCode.ATTACHMENT_INVALID, "조각 크기의 합이 파일 크기와 다릅니다.");
		}

		String sanitizedName = sanitizeFileName(fileName);
		String s3Key = newObjectKey(issue, sanitizedName);
		try (InputStream in = new SequenceInputStream(parts(issueId, uploadId, count))) {
			storageService.putStream(s3Key, in, fileSize, mimeType == null ? "application/octet-stream" : mimeType);
		} catch (IOException e) {
			throw new IllegalStateException("업로드 조각을 합치지 못했습니다.", e);
		}
		storageService.deletePrefix(uploadPrefix(issueId, uploadId));
		return saveAttachment(issue, uploader, sanitizedName, s3Key, fileSize, mimeType);
	}

	static int partCount(long fileSize) {
		return (int)((fileSize + PART_SIZE - 1) / PART_SIZE);
	}

	static String uploadPrefix(String issueId, String uploadId) {
		return "attachments/uploads/" + issueId + "/" + uploadId + "/";
	}

	static String partKey(String issueId, String uploadId, int n) {
		return uploadPrefix(issueId, uploadId) + String.format("part-%04d", n);
	}

	/** 조각 스트림을 하나씩 열어 넘긴다 (전부 메모리에 올리지 않는다) */
	private Enumeration<InputStream> parts(String issueId, String uploadId, int count) {
		return new Enumeration<>() {
			private int next = 1;

			public boolean hasMoreElements() {
				return next <= count;
			}

			public InputStream nextElement() {
				return storageService.getObjectStream(partKey(issueId, uploadId, next++));
			}
		};
	}

	private GsUserModel currentUser() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		return userAccess.findByEmail(email)
			.orElseThrow(() -> new EntityNotFoundException("User not found: " + email));
	}

	/** 살아 있는 이슈 + 현재 사용자가 그 프로젝트 멤버인지 확인 */
	private SnCiraIssueModel memberIssue(String issueId) {
		GsUserModel user = currentUser();
		SnCiraIssueModel issue = issueAccess.findById(issueId);
		if (issue.getDeletedAt() != null) {
			throw new CiraException(ErrorCode.ISSUE_NOT_FOUND, "이슈를 찾을 수 없습니다: " + issueId);
		}
		validateMembership(user.getObjId(), issue.getProjectId());
		return issue;
	}

	private String newObjectKey(SnCiraIssueModel issue, String sanitizedName) {
		return String.format("attachments/%s/%s/%s_%s",
			issue.getProjectId(), issue.getObjId(), UUID.randomUUID(), sanitizedName);
	}

	private AttachmentResponse saveAttachment(SnCiraIssueModel issue, GsUserModel uploader, String sanitizedName,
		String s3Key, long fileSize, String mimeType) {
		SnCiraAttachmentModel attachment = SnCiraAttachmentModel.builder()
			.issueId(issue.getObjId())
			.fileNm(sanitizedName)
			.s3Key(s3Key)
			.fileSize(fileSize)
			.mimeType(mimeType)
			.uploadedBy(uploader.getObjId())
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(TenantContext.get())
			.traceId("UPLOAD-ATTACHMENT")
			.useStatCd(UseStatCd.Usable)
			.evtNm("UploadAttachment")
			.prevEvntNm("None")
			.build();

		attachmentAccess.save(attachment);

		recordLog(issue.getObjId(), "attachment", null, sanitizedName, uploader.getObjId());

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

	public SnCiraAttachmentModel getAttachmentForDownload(String attachmentId) {
		SnCiraAttachmentModel attachment = attachmentAccess.findById(attachmentId);
		if (attachment.getUseStatCd() == UseStatCd.Delete) {
			throw new CiraException(ErrorCode.ISSUE_NOT_FOUND, "첨부파일을 찾을 수 없습니다: " + attachmentId);
		}
		return attachment;
	}

	public InputStream getObjectStream(String s3Key) {
		return storageService.getObjectStream(s3Key);
	}

	public InputStream getObjectStream(String s3Key, long offset, long length) {
		return storageService.getObjectStream(s3Key, offset, length);
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
		// macOS 는 한글 파일명을 자모 분리형(NFD)으로 보낸다 → 완성형(NFC)으로 합친 뒤 걸러야 '_' 로 바뀌지 않는다
		return Normalizer.normalize(originalName, Normalizer.Form.NFC)
			.replaceAll("[^a-zA-Z0-9._\\-가-힣ㄱ-ㅎㅏ-ㅣ]", "_");
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
			.tenant(TenantContext.get())
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
