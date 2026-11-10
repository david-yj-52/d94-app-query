package tsh.d94.app.server.apService.study;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import tsh.d94.app.server.apService.study.dto.StudyDtos.StartUploadRequest;
import tsh.d94.app.server.apService.study.dto.StudyDtos.StudyDocumentUploadedEvent;
import tsh.d94.app.server.apService.study.exception.StudyErrorCode;
import tsh.d94.app.server.apService.study.exception.StudyException;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentAccess;
import tsh.d94.app.server.data.orm.study.studyDocument.SnStudyDocumentModel;
import tsh.d94.app.server.data.orm.study.studyUpload.SnStudyUploadAccess;
import tsh.d94.app.server.data.orm.study.studyUpload.SnStudyUploadModel;
import tsh.d94.app.server.service.StorageService;
import tsh.d94.app.server.support.TestContexts;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudyUploadServiceTest {

	@Mock SnStudyUploadAccess uploadAccess;
	@Mock SnStudyDocumentAccess documentAccess;
	@Mock StorageService storage;
	@Mock ApplicationEventPublisher events;
	@InjectMocks StudyUploadService service;

	private SnStudyUploadModel upload;

	@BeforeEach
	void setUp() {
		TestContexts.login("u@t.com");
		upload = SnStudyUploadModel.builder().ownerId("u@t.com").fileNm("guide.pdf").fileSize(1_000_000L)
			.partCount(2).tenant(TestContexts.TENANT).build();
		ReflectionTestUtils.setField(upload, "objId", "up1");
		when(uploadAccess.findByObjIdAndOwnerId("up1", "u@t.com")).thenReturn(Optional.of(upload));
		when(uploadAccess.save(any())).thenAnswer(i -> {
			SnStudyUploadModel m = i.getArgument(0);
			ReflectionTestUtils.setField(m, "objId", "up1");
			return m;
		});
		when(documentAccess.save(any())).thenAnswer(i -> {
			SnStudyDocumentModel m = i.getArgument(0);
			ReflectionTestUtils.setField(m, "objId", "doc1");
			return m;
		});
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private static StudyErrorCode code(Runnable r) {
		return catchThrowableOfType(r::run, StudyException.class).getErrorCode();
	}

	@Test
	void startValidatesAndComputesPartCount() {
		assertThat(code(() -> service.start(new StartUploadRequest("a.txt", 10)))).isEqualTo(StudyErrorCode.INVALID_FILE);
		assertThat(code(() -> service.start(new StartUploadRequest("a.pdf", 52_428_801)))).isEqualTo(StudyErrorCode.FILE_TOO_LARGE);
		var res = service.start(new StartUploadRequest("CISA.pdf", 11_094_606));
		assertThat(res.partSize()).isEqualTo(786_432);
		assertThat(res.partCount()).isEqualTo(15);
	}

	@Test
	void putPartValidatesAndOverwritesOnRetry() {
		assertThat(code(() -> service.putPart("up1", 1, new byte[786_433]))).isEqualTo(StudyErrorCode.PART_INVALID);
		assertThat(code(() -> service.putPart("up1", 3, new byte[10]))).isEqualTo(StudyErrorCode.PART_INVALID);
		assertThat(code(() -> service.putPart("other", 1, new byte[10]))).isEqualTo(StudyErrorCode.NOT_FOUND);
		service.putPart("up1", 1, new byte[10]);
		service.putPart("up1", 1, new byte[10]);
		verify(storage, times(2)).putBytes(eq("study/uploads/up1/part-0001"), any(), any());
	}

	@Test
	void completeReportsMissingPartsAndSizeMismatch() {
		when(storage.objectSize("study/uploads/up1/part-0001")).thenReturn(786_432L);
		when(storage.objectSize("study/uploads/up1/part-0002")).thenReturn(-1L);
		StudyException e = catchThrowableOfType(() -> service.complete("up1"), StudyException.class);
		assertThat(e.getErrorCode()).isEqualTo(StudyErrorCode.UPLOAD_INCOMPLETE);
		assertThat(e.getMessage()).isEqualTo("missing=2");
		when(storage.objectSize("study/uploads/up1/part-0002")).thenReturn(1L);
		assertThat(code(() -> service.complete("up1"))).isEqualTo(StudyErrorCode.PART_INVALID);
	}

	@Test
	void completeRejectsNonPdfAndCleansUp() {
		when(storage.objectSize(anyString())).thenReturn(500_000L);
		when(storage.getObjectStream("study/uploads/up1/part-0001")).thenReturn(new ByteArrayInputStream("hello".getBytes()));
		assertThat(code(() -> service.complete("up1"))).isEqualTo(StudyErrorCode.INVALID_FILE);
		verify(storage).deletePrefix("study/uploads/up1/");
		verify(uploadAccess).remove(upload);
	}

	@Test
	void completeStoresSourceCreatesParsingDocumentAndPublishesEvent() {
		when(storage.objectSize(anyString())).thenReturn(500_000L);
		when(storage.getObjectStream(anyString())).thenAnswer(i -> new ByteArrayInputStream("%PDF-1.7".getBytes()));
		var doc = service.complete("up1");
		assertThat(doc.status()).isEqualTo("PARSING");
		assertThat(doc.title()).isEqualTo("guide");
		verify(storage).putStream(eq("study/docs/doc1/source.pdf"), any(), eq(1_000_000L), eq("application/pdf"));
		verify(storage).deletePrefix("study/uploads/up1/");
		verify(uploadAccess).remove(upload);
		verify(events).publishEvent(new StudyDocumentUploadedEvent("doc1"));
	}
}
