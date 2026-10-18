package tsh.d94.app.server.interfaces.controller.util;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solacesystems.jcsmp.JCSMPException;
import tsh.d94.app.server.interfaces.subscriber.SolaceTaskReceiver;
import tsh.d94.lib.core.messaging.solace.outbound.SolaceMessagePublisher;
import tsh.d94.lib.core.messaging.solace.vo.SolaceOutBoundMessage;
import tsh.d94.lib.core.spec.constant.ApMessageList;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/util/msg/outbound")
@Slf4j
@Validated
public class MessageOutboundController {

	@Autowired
	SolaceMessagePublisher solaceMessagePublisher;

	@PostMapping
	public void requestSolaceMessage(@RequestParam String eventName, @RequestParam String destName,
		@RequestBody String payload) {

		ApMessageList msgEventName = ApMessageList.valueOf(eventName);
		Map<String, Object> map = new HashMap<>();
		map.put(SolaceTaskReceiver.PROP_EVENT_NM, msgEventName);

		SolaceOutBoundMessage msg = SolaceOutBoundMessage.builder()
			.eventNm(msgEventName)
			.destination(destName)
			.payload(payload)
			.msgHeader(map)
			.build();

		try {
			this.solaceMessagePublisher.publishToQueue(msg);
		} catch (JCSMPException e) {
			throw new RuntimeException(e);
		}

	}
}
