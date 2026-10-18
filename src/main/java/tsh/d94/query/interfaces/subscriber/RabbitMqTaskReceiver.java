package tsh.d94.app.server.interfaces.subscriber;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;
import tsh.d94.app.server.MessageHandlerRegistry;
import tsh.d94.lib.core.interfaces.InterfaceType;
import tsh.d94.lib.core.messaging.rabbitmq.inbound.RabbitMqMessageReceiver;
import tsh.d94.lib.core.spec.constant.ApMessageList;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class RabbitMqTaskReceiver implements RabbitMqMessageReceiver {

	// Solace의 SDTMap 커스텀 property(eventName)와 동일한 역할 — AMQP header table에 담아 전달
	public static final String PROP_EVENT_NM = "eventName";

	// TODO Solace의 SolaceQueueDiscovery(SEMP 기반 패턴 탐색)와 동일한 RabbitMQ Management API 연동 유틸 추후 개발.
	// 현재는 고정 Queue명 사용 — 운영 반영 전 브로커에 사전 선언 필요.
	public static final String QUEUE_NAME = "TET.REQ.RABBITMQ";

	@Autowired
	MessageHandlerRegistry registry;

	@Override
	public List<String> getQueueNames() {
		return List.of(QUEUE_NAME);
	}

	@Override
	public void onMessage(byte[] body, Envelope envelope, AMQP.BasicProperties properties) throws Exception {
		// Tomcat Controller와 동일하게 그냥 동기 호출
		String payload = extractPayload(body);
		String eventString = resolveEventName(properties);
		log.info("payload:{} eventName:{}", payload, eventString);

		RabbitMqMessageInfoVo infoVo = RabbitMqMessageInfoVo.builder()
			.msgBody(body).envelope(envelope).properties(properties).build();

		ApMessageList eventName = ApMessageList.valueOf(eventString);
		registry.getHandler(eventName).handle(payload, InterfaceType.RABBITMQ, infoVo);

	}

	@Override
	public void onException(Exception ex) {
		log.error("[AgentTask] Consumer exception", ex);
	}

	private String resolveEventName(AMQP.BasicProperties properties) {
		Map<String, Object> headers = properties != null ? properties.getHeaders() : null;
		Object eventNm = headers != null ? headers.get(PROP_EVENT_NM) : null;
		return eventNm != null ? eventNm.toString() : null;
	}
}
