package tsh.d94.app.server;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import tsh.d94.app.server.apService.ApService;
import tsh.d94.lib.core.spec.constant.ApMessageList;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class MessageHandlerRegistry {

	private final Map<ApMessageList, ApService<?, ?>> handlerMap;

	// ApService 구현체 전부 자동 주입
	public MessageHandlerRegistry(List<ApService<?, ?>> services) {
		this.handlerMap = services.stream()
			.filter(s -> s.getSupportedEvent() != null)
			.collect(Collectors.toMap(
				ApService::getSupportedEvent,
				service -> service
			));
		log.info("Registered handlers: {}", handlerMap.keySet());
	}

	public ApService<?, ?> getHandler(ApMessageList eventName) {
		ApService<?, ?> handler = handlerMap.get(eventName);
		if (handler == null) {
			throw new IllegalArgumentException("No handler for event: " + eventName);
		}
		return handler;
	}
}
