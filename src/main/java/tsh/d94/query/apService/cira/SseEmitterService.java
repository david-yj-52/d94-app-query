package tsh.d94.app.server.apService.cira;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SseEmitterService {

	private static final long SSE_TIMEOUT_MS = 600_000L;

	private final ConcurrentHashMap<String, SseEmitter> emitters = new ConcurrentHashMap<>();

	public SseEmitter connect(String userId) {
		SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

		emitter.onCompletion(() -> {
			emitters.remove(userId, emitter);
			log.debug("SSE completed for user={}", userId);
		});
		emitter.onTimeout(() -> {
			emitters.remove(userId, emitter);
			log.debug("SSE timeout for user={}", userId);
		});
		emitter.onError(e -> {
			emitters.remove(userId, emitter);
			log.debug("SSE error for user={}: {}", userId, e.getMessage());
		});

		SseEmitter previous = emitters.put(userId, emitter);
		if (previous != null) {
			previous.complete();
		}

		try {
			emitter.send(SseEmitter.event().name("connected").data("SSE connected"));
		} catch (IOException e) {
			emitters.remove(userId, emitter);
		}

		return emitter;
	}

	public void send(String userId, String eventName, Object data) {
		SseEmitter emitter = emitters.get(userId);
		if (emitter == null) {
			return;
		}
		try {
			emitter.send(SseEmitter.event().name(eventName).data(data));
		} catch (IOException e) {
			emitters.remove(userId, emitter);
			log.debug("SSE send failed for user={}, removing emitter", userId);
		}
	}

	public void broadcast(List<String> userIds, String eventName, Object data) {
		for (String userId : userIds) {
			send(userId, eventName, data);
		}
	}

}
