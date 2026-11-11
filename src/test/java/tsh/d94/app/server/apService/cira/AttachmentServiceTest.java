package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraAttachment.SnCiraAttachmentAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueLog.SnCiraIssueLogAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberModel;
import tsh.d94.app.server.service.StorageService;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/** 조각 업로드: 빠진 조각·크기 불일치 거절, 정상이면 조각을 이어 한 객체로 저장 */
@ExtendWith(MockitoExtension.class)
class AttachmentServiceTest {

	@Mock
	SnCiraAttachmentAccess attachmentAccess;
	@Mock
	SnCiraIssueAccess issueAccess;
	@Mock
	SnCiraProjectMemberAccess projectMemberAccess;
	@Mock
	SnCiraIssueLogAccess issueLogAccess;
	@Mock
	GsUserAccess userAccess;
	@Mock
	StorageService storage;
	@InjectMocks
	AttachmentService service;

	static final int PART = AttachmentService.PART_SIZE;

	@BeforeEach
	void setUp() {
		SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("u@x", null, List.of()));
		TenantContext.set("T");
		GsUserModel user = mock(GsUserModel.class);
		given(user.getObjId()).willReturn("u-1");
		given(userAccess.findByEmail("u@x")).willReturn(Optional.of(user));
		SnCiraIssueModel issue = mock(SnCiraIssueModel.class);
		lenient().when(issue.getObjId()).thenReturn("i-1");
		given(issue.getProjectId()).willReturn("p-1");
		given(issueAccess.findById("i-1")).willReturn(issue);
		SnCiraProjectMemberModel member = mock(SnCiraProjectMemberModel.class);
		given(member.getProjectId()).willReturn("p-1");
		given(projectMemberAccess.findAllByUserId("u-1")).willReturn(List.of(member));
	}

	@AfterEach
	void tearDown() {
		TenantContext.clear();
		SecurityContextHolder.clearContext();
	}

	@Test
	@DisplayName("start: 50MB 초과 거절, 조각 수 = ceil(size / 768KB)")
	void start() {
		assertThat(service.startUpload("i-1", PART * 2L + 1).partCount()).isEqualTo(3);
		assertThatThrownBy(() -> service.startUpload("i-1", AttachmentService.MAX_FILE_BYTES + 1))
			.isInstanceOf(CiraException.class);
	}

	@Test
	@DisplayName("complete: 빠진 조각이 있으면 missing=번호 로 거절")
	void complete_missing() {
		given(storage.objectSize(endsWith("part-0001"))).willReturn((long)PART);
		given(storage.objectSize(endsWith("part-0002"))).willReturn(-1L);

		assertThatThrownBy(() -> service.completeUpload("i-1", "up", "a.zip", PART + 10L, "application/zip"))
			.isInstanceOf(CiraException.class)
			.hasMessageContaining("missing=2")
			.satisfies(e -> assertThat(((CiraException)e).getErrorCode()).isEqualTo(ErrorCode.ATTACHMENT_UPLOAD_INCOMPLETE));
	}

	@Test
	@DisplayName("complete: 조각 합 ≠ 파일 크기면 거절")
	void complete_sizeMismatch() {
		given(storage.objectSize(anyString())).willReturn(5L);
		assertThatThrownBy(() -> service.completeUpload("i-1", "up", "a.txt", 6L, "text/plain"))
			.isInstanceOf(CiraException.class);
	}

	@Test
	@DisplayName("complete: 조각을 순서대로 이어 한 객체로 저장하고 조각은 지운다")
	void complete_ok() throws Exception {
		given(storage.objectSize(endsWith("part-0001"))).willReturn((long)PART);
		given(storage.objectSize(endsWith("part-0002"))).willReturn(2L);
		given(storage.getObjectStream(endsWith("part-0001"))).willReturn(new ByteArrayInputStream(new byte[PART]));
		given(storage.getObjectStream(endsWith("part-0002"))).willReturn(new ByteArrayInputStream(new byte[] {7, 8}));
		byte[][] stored = new byte[1][];
		willAnswer(inv -> stored[0] = ((InputStream)inv.getArgument(1)).readAllBytes())
			.given(storage).putStream(anyString(), any(), eq(PART + 2L), eq("image/png"));

		// 파일명은 macOS 처럼 자모 분리형(NFD)으로 보낸다
		var res = service.completeUpload("i-1", "up",
			java.text.Normalizer.normalize("그림 1.png", java.text.Normalizer.Form.NFD), PART + 2L, "image/png");

		assertThat(stored[0]).hasSize(PART + 2);
		assertThat(stored[0][PART]).isEqualTo((byte)7);
		assertThat(res.getFileName()).isEqualTo("그림_1.png");
		verify(storage).deletePrefix("attachments/uploads/i-1/up/");
		ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
		verify(storage).putStream(key.capture(), any(), anyLong(), anyString());
		assertThat(key.getValue()).startsWith("attachments/p-1/i-1/");
	}
}
