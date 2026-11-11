package tsh.d94.app.server.interfaces.controller.cira;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.BDDMockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;

import tsh.d94.app.server.apService.cira.AttachmentService;
import tsh.d94.app.server.data.orm.cira.ciraAttachment.SnCiraAttachmentModel;
import tsh.d94.app.server.support.ControllerTestSupport;

@ExtendWith(MockitoExtension.class)
class AttachmentControllerTest {

	@Mock
	private AttachmentService attachmentService;
	@InjectMocks
	private AttachmentController controller;

	private MockMvc mvc;

	@BeforeEach
	void setUp() {
		mvc = ControllerTestSupport.mvc(controller);
	}

	@Test
	@DisplayName("GET /attachments/{id}/download → 비동기 없이 본문·inline·UTF-8 파일명")
	void download() throws Exception {
		SnCiraAttachmentModel att = mock(SnCiraAttachmentModel.class);
		given(att.getFileNm()).willReturn("스크린샷.png");
		given(att.getMimeType()).willReturn("image/png");
		given(att.getS3Key()).willReturn("k");
		given(att.getFileSize()).willReturn(3L);
		given(attachmentService.getAttachmentForDownload("a-1")).willReturn(att);
		given(attachmentService.getObjectStream("k")).willReturn(new ByteArrayInputStream(new byte[] {1, 2, 3}));

		mvc.perform(get("/api/v1/attachments/a-1/download"))
			// 비동기(StreamingResponseBody)면 ASYNC 재디스패치에서 보안 필터에 막힌다
			.andExpect(request().asyncNotStarted())
			.andExpect(status().isOk())
			.andExpect(content().contentType("image/png"))
			.andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.allOf(org.hamcrest.Matchers.startsWith("inline"),
				containsString("filename*=UTF-8''" + java.net.URLEncoder.encode("스크린샷", StandardCharsets.UTF_8)))))
			.andExpect(content().bytes(new byte[] {1, 2, 3}));
	}


	@Test
	@DisplayName("Range: bytes=1-2 → 206 + Content-Range, 해당 구간만 MinIO 에서 읽음")
	void download_range() throws Exception {
		SnCiraAttachmentModel att = mock(SnCiraAttachmentModel.class);
		given(att.getFileNm()).willReturn("a.bin");
		given(att.getMimeType()).willReturn("application/octet-stream");
		given(att.getS3Key()).willReturn("k");
		given(att.getFileSize()).willReturn(10L);
		given(attachmentService.getAttachmentForDownload("a-1")).willReturn(att);
		given(attachmentService.getObjectStream("k", 1L, 2L)).willReturn(new ByteArrayInputStream(new byte[] {5, 6}));

		mvc.perform(get("/api/v1/attachments/a-1/download").header("Range", "bytes=1-2"))
			.andExpect(status().isPartialContent())
			.andExpect(header().string("Content-Range", "bytes 1-2/10"))
			.andExpect(content().bytes(new byte[] {5, 6}));
	}

	@Test
	@DisplayName("범위 밖 Range → 416")
	void download_badRange() throws Exception {
		SnCiraAttachmentModel att = mock(SnCiraAttachmentModel.class);
		given(att.getFileNm()).willReturn("a.bin");
		given(att.getMimeType()).willReturn("application/octet-stream");
		given(att.getFileSize()).willReturn(10L);
		given(attachmentService.getAttachmentForDownload("a-1")).willReturn(att);

		mvc.perform(get("/api/v1/attachments/a-1/download").header("Range", "bytes=20-30"))
			.andExpect(status().isRequestedRangeNotSatisfiable());
	}

}
