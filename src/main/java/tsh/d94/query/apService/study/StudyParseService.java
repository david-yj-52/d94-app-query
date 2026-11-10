package tsh.d94.app.server.apService.study;

import java.io.InputStream;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tsh.d94.app.server.apService.study.dto.StudyDtos.StudyDocumentUploadedEvent;
import tsh.d94.app.server.apService.study.parser.ParserFailure;
import tsh.d94.app.server.apService.study.parser.ParserResponse;
import tsh.d94.app.server.apService.study.parser.PdfParserClient;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentAccess;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentModel;
import tsh.d94.app.server.service.StorageService;

import java.time.LocalDateTime;

/** 업로드가 커밋된 뒤 비동기로 파서를 호출하고 결과를 저장한다. */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudyParseService {

	static final String SERVER_ERROR = "파싱 서버 오류";
	static final String INTERRUPTED = "파싱이 중단되었습니다";

	private final SnStudyDocumentAccess documentAccess;
	private final StorageService storage;
	private final PdfParserClient client;
	private final StudyParseResultWriter writer;

	@Async("studyParseExecutor")
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	public void onUploaded(StudyDocumentUploadedEvent event) {
		parse(event.documentId());
	}

	public void parse(String documentId) {
		SnStudyDocumentModel doc = documentAccess.findById(documentId);
		try {
			byte[] pdf;
			try (InputStream in = storage.getObjectStream(doc.getStorageKey())) {
				pdf = in.readAllBytes();
			}
			ParserResponse result = client.parse(pdf, doc.getFileNm());
			writer.save(doc, result);
		} catch (ParserFailure f) {
			fail(doc, f.getMessage());
		} catch (Exception e) {
			log.error("study parse failed: document={}", documentId, e);
			fail(doc, SERVER_ERROR);
		}
	}

	/**
	 * 기동 시 PARSING 으로 남은 문서는 모두 중단된 것이다 (파싱 큐는 메모리에만 있다).
	 * ponytail: 인스턴스 1개 가정. 여러 대로 늘리면 다른 인스턴스가 파싱 중인 문서도 실패 처리되므로 소유 인스턴스 기록 필요.
	 */
	@EventListener(ApplicationReadyEvent.class)
	public void failStaleParsing() {
		for (SnStudyDocumentModel doc : documentAccess.findByStatusAndModifiedAtBefore(Study.PARSING, LocalDateTime.now())) {
			fail(doc, INTERRUPTED);
		}
	}

	private void fail(SnStudyDocumentModel doc, String reason) {
		doc.setStatus(Study.FAILED);
		doc.setFailReason(reason);
		documentAccess.save(doc);
	}
}
