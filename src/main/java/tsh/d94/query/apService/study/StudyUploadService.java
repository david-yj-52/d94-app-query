package tsh.d94.app.server.apService.study;

import static tsh.d94.app.server.apService.study.exception.StudyErrorCode.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.study.dto.StudyDtos.DocumentResponse;
import tsh.d94.app.server.apService.study.dto.StudyDtos.StartUploadRequest;
import tsh.d94.app.server.apService.study.dto.StudyDtos.StartUploadResponse;
import tsh.d94.app.server.apService.study.dto.StudyDtos.StudyDocumentUploadedEvent;
import tsh.d94.app.server.apService.study.exception.StudyException;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentAccess;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentModel;
import tsh.d94.app.server.data.orm.study.studyUpload.SnStudyUploadAccess;
import tsh.d94.app.server.data.orm.study.studyUpload.SnStudyUploadModel;
import tsh.d94.app.server.service.StorageService;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;

/**
 * UMD 요청 본문 상한(1MB) 때문에 PDF를 768KB 조각으로 받아 MinIO에서 이어 붙인다.
 * ponytail: 완료되지 않은 업로드 조각 정리 스케줄러 없음 — 쌓이면 추가.
 */
@Service
@RequiredArgsConstructor
public class StudyUploadService {

	public static final long MAX_FILE_BYTES = 52_428_800L;
	public static final int PART_SIZE = 786_432;
	private static final byte[] PDF_SIGNATURE = "%PDF-".getBytes(StandardCharsets.US_ASCII);

	private final SnStudyUploadAccess uploadAccess;
	private final SnStudyDocumentAccess documentAccess;
	private final StorageService storage;
	private final ApplicationEventPublisher events;

	public StartUploadResponse start(StartUploadRequest req) {
		if (req.fileName() == null || !req.fileName().toLowerCase(Locale.ROOT).endsWith(".pdf")) {
			throw new StudyException(INVALID_FILE);
		}
		if (req.fileSize() <= 0 || req.fileSize() > MAX_FILE_BYTES) {
			throw new StudyException(FILE_TOO_LARGE);
		}
		int parts = (int)((req.fileSize() + PART_SIZE - 1) / PART_SIZE);
		SnStudyUploadModel upload = uploadAccess.save(Study.base(SnStudyUploadModel.builder(), "StartStudyUpload",
				TenantContext.get())
			.ownerId(Study.owner())
			.fileNm(Study.truncate(req.fileName(), 500))
			.fileSize(req.fileSize())
			.partCount(parts)
			.build());
		return new StartUploadResponse(upload.getObjId(), PART_SIZE, parts);
	}

	public void putPart(String uploadId, int n, byte[] body) {
		SnStudyUploadModel upload = ownedUpload(uploadId);
		if (n < 1 || n > upload.getPartCount() || body == null || body.length == 0 || body.length > PART_SIZE) {
			throw new StudyException(PART_INVALID);
		}
		storage.putBytes(partKey(uploadId, n), body, "application/octet-stream"); // 재전송은 덮어쓰기
	}

	@Transactional
	public DocumentResponse complete(String uploadId) {
		SnStudyUploadModel upload = ownedUpload(uploadId);
		List<Integer> missing = new ArrayList<>();
		long total = 0;
		for (int n = 1; n <= upload.getPartCount(); n++) {
			long size = storage.objectSize(partKey(uploadId, n));
			if (size < 0) {
				missing.add(n);
			} else {
				total += size;
			}
		}
		if (!missing.isEmpty()) {
			throw new StudyException(UPLOAD_INCOMPLETE,
				"missing=" + missing.stream().map(String::valueOf).collect(Collectors.joining(",")));
		}
		if (total != upload.getFileSize()) {
			throw new StudyException(PART_INVALID, "조각 크기의 합이 파일 크기와 다릅니다.");
		}
		if (!startsWithPdfSignature(partKey(uploadId, 1))) {
			storage.deletePrefix(uploadPrefix(uploadId));
			uploadAccess.remove(upload);
			throw new StudyException(INVALID_FILE);
		}

		SnStudyDocumentModel doc = documentAccess.save(Study.base(SnStudyDocumentModel.builder(),
				"UploadStudyDocument", upload.getTenant())
			.ownerId(upload.getOwnerId())
			.title(Study.truncate(upload.getFileNm().replaceFirst("(?i)\\.pdf$", ""), 500))
			.fileNm(upload.getFileNm())
			.fileSize(upload.getFileSize())
			.status(Study.PARSING)
			.build());
		String key = "study/docs/" + doc.getObjId() + "/source.pdf";
		try (InputStream in = new SequenceInputStream(parts(uploadId, upload.getPartCount()))) {
			storage.putStream(key, in, upload.getFileSize(), "application/pdf");
		} catch (IOException e) {
			throw new IllegalStateException("업로드 조각을 합치지 못했습니다.", e);
		}
		doc.setStorageKey(key);
		documentAccess.save(doc);
		storage.deletePrefix(uploadPrefix(uploadId));
		uploadAccess.remove(upload);
		events.publishEvent(new StudyDocumentUploadedEvent(doc.getObjId()));
		return DocumentResponse.of(doc);
	}

	private SnStudyUploadModel ownedUpload(String uploadId) {
		return uploadAccess.findByObjIdAndOwnerId(uploadId, Study.owner())
			.orElseThrow(() -> new StudyException(NOT_FOUND));
	}

	private boolean startsWithPdfSignature(String key) {
		try (InputStream in = storage.getObjectStream(key)) {
			return Arrays.equals(in.readNBytes(PDF_SIGNATURE.length), PDF_SIGNATURE);
		} catch (IOException e) {
			return false;
		}
	}

	/** 조각 스트림을 하나씩 열어 넘긴다 (전부 메모리에 올리지 않는다) */
	private Enumeration<InputStream> parts(String uploadId, int count) {
		return new Enumeration<>() {
			private int next = 1;

			public boolean hasMoreElements() {
				return next <= count;
			}

			public InputStream nextElement() {
				return storage.getObjectStream(partKey(uploadId, next++));
			}
		};
	}

	static String uploadPrefix(String uploadId) {
		return "study/uploads/" + uploadId + "/";
	}

	static String partKey(String uploadId, int n) {
		return uploadPrefix(uploadId) + String.format("part-%04d", n);
	}
}
