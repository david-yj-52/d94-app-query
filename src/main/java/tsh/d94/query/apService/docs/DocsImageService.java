package tsh.d94.app.server.apService.docs;

import static tsh.d94.app.server.apService.docs.DocsException.Code.*;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.service.StorageService;

/**
 * 노트에 붙여넣은 이미지. MinIO `docs/images/{소유자 해시}/{id}` 에 저장하고, 조회도 로그인 소유자의 경로에서만 찾는다(남의 것 404).
 * 형식은 선언된 Content-Type 대신 앞 바이트로 판별 — PNG·JPEG·GIF·WebP 만 (SVG·HTML 은 XSS 위험이라 거부).
 * ponytail: 메타 테이블 없음. 노트에서 지워져도 이미지는 남는다 — 용량이 문제 되면 본문 참조 기준 정리 작업.
 */
@Service
@RequiredArgsConstructor
public class DocsImageService {

	/** 웹은 1MB(UMD 본문 상한) 아래로 줄여 보낸다. 서버 상한은 여유를 둔다 */
	static final int MAX_BYTES = 5 * 1024 * 1024;
	private static final Pattern ID = Pattern.compile("[0-9a-f-]{36}");

	private final StorageService storage;

	public record Image(byte[] bytes, String contentType) {
	}

	public String upload(byte[] body) {
		if (body == null || body.length == 0 || body.length > MAX_BYTES) {
			throw new DocsException(INVALID_REQUEST, "이미지는 5MB 이하만 올릴 수 있습니다.");
		}
		String type = sniff(body);
		if (type == null) {
			throw new DocsException(INVALID_REQUEST, "PNG·JPEG·GIF·WebP 이미지만 올릴 수 있습니다.");
		}
		String id = UUID.randomUUID().toString();
		storage.putBytes(key(id), body, type);
		return id;
	}

	public Image get(String id) {
		if (!ID.matcher(id).matches() || storage.objectSize(key(id)) < 0) {
			throw new DocsException(NOT_FOUND);
		}
		try (InputStream in = storage.getObjectStream(key(id))) {
			byte[] bytes = in.readAllBytes();
			return new Image(bytes, sniff(bytes));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String key(String id) {
		return "docs/images/" + ownerHash() + "/" + id;
	}

	/** 경로에 이메일을 그대로 쓰지 않는다 */
	private static String ownerHash() {
		try {
			byte[] h = MessageDigest.getInstance("SHA-256").digest(Docs.owner().getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(h, 0, 16);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	static String sniff(byte[] b) {
		if (starts(b, 0x89, 'P', 'N', 'G')) {
			return "image/png";
		}
		if (starts(b, 0xFF, 0xD8, 0xFF)) {
			return "image/jpeg";
		}
		if (starts(b, 'G', 'I', 'F', '8')) {
			return "image/gif";
		}
		if (b.length >= 12 && starts(b, 'R', 'I', 'F', 'F') && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
			return "image/webp";
		}
		return null;
	}

	private static boolean starts(byte[] b, int... sig) {
		if (b.length < sig.length) {
			return false;
		}
		for (int i = 0; i < sig.length; i++) {
			if ((b[i] & 0xFF) != sig[i]) {
				return false;
			}
		}
		return true;
	}
}
