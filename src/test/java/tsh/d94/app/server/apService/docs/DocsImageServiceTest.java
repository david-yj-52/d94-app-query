package tsh.d94.app.server.apService.docs;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.io.ByteArrayInputStream;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import tsh.d94.app.server.service.StorageService;
import tsh.d94.app.server.support.TestContexts;

class DocsImageServiceTest {

	private static final byte[] PNG = {(byte)0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 1, 2};
	private static final byte[] WEBP = {'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P', 1};

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	@Test
	void sniff_onlyRasterImages() {
		assertThat(DocsImageService.sniff(PNG)).isEqualTo("image/png");
		assertThat(DocsImageService.sniff(WEBP)).isEqualTo("image/webp");
		assertThat(DocsImageService.sniff(new byte[] {(byte)0xFF, (byte)0xD8, (byte)0xFF, 0})).isEqualTo("image/jpeg");
		assertThat(DocsImageService.sniff("GIF89a".getBytes())).isEqualTo("image/gif");
		assertThat(DocsImageService.sniff("<svg onload=alert(1)>".getBytes())).isNull();
		assertThat(DocsImageService.sniff("<html>".getBytes())).isNull();
	}

	@Test
	void upload_thenGet_ownerScoped() {
		StorageService storage = mock(StorageService.class);
		Map<String, byte[]> objects = new HashMap<>();
		doAnswer(i -> objects.put(i.getArgument(0), i.getArgument(1))).when(storage).putBytes(anyString(), any(), any());
		when(storage.objectSize(anyString())).thenAnswer(i -> objects.containsKey((String)i.getArgument(0)) ? 1L : -1L);
		when(storage.getObjectStream(anyString())).thenAnswer(i -> new ByteArrayInputStream(objects.get((String)i.getArgument(0))));
		DocsImageService svc = new DocsImageService(storage);

		TestContexts.login("a@example.test");
		String id = svc.upload(PNG);
		assertThat(objects.keySet()).singleElement().asString().startsWith("docs/images/").endsWith("/" + id).doesNotContain("a@example");
		assertThat(svc.get(id).contentType()).isEqualTo("image/png");

		assertThatThrownBy(() -> svc.upload("<svg/>".getBytes())).isInstanceOf(DocsException.class);
		assertThatThrownBy(() -> svc.get("../../etc")).isInstanceOf(DocsException.class);

		TestContexts.login("b@example.test"); // 다른 사람은 같은 id 라도 못 본다
		assertThatThrownBy(() -> svc.get(id)).isInstanceOf(DocsException.class)
			.extracting(e -> ((DocsException)e).getCode()).isEqualTo(DocsException.Code.NOT_FOUND);
	}
}
