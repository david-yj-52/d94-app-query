package tsh.d94.app.server.interfaces.subscriber;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.solacesystems.jcsmp.BytesXMLMessage;
import com.solacesystems.jcsmp.EndpointProperties;
import com.solacesystems.jcsmp.JCSMPException;
import tsh.d94.app.server.MessageHandlerRegistry;
import tsh.d94.lib.core.interfaces.InterfaceType;
import tsh.d94.lib.core.messaging.solace.inbound.SolaceMessageReceiver;
import tsh.d94.lib.core.messaging.solace.util.SolaceQueueDiscovery;
import tsh.d94.lib.core.spec.constant.ApMessageList;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
@RequiredArgsConstructor
public class SolaceTaskReceiver implements SolaceMessageReceiver {

	public static final String PROP_EVENT_NM = "eventName";
	public static final String PROP_RESP_TOPIC = "responseTopic";
	public static final String PROP_SELECT_KEY = "selectorKey";

	@Autowired
	SolaceQueueDiscovery solaceQueueDiscovery;

	@Autowired
	MessageHandlerRegistry registry;

	@Override
	public List<String> getQueueNames() {
		return solaceQueueDiscovery.findQueuesByPattern("TET.REQ.*");
	}

	@Override
	public EndpointProperties getEndpointProperties() {
		EndpointProperties props = new EndpointProperties();
		props.setPermission(EndpointProperties.PERMISSION_CONSUME);
		props.setAccessType(EndpointProperties.ACCESSTYPE_EXCLUSIVE); // 커스텀
		return props;
	}

	@Override
	public void onMessage(BytesXMLMessage message) throws Exception {
		// Tomcat Controller와 동일하게 그냥 동기 호출
		String payload = extractPayload(message);
		String eventString = message.getProperties().getString(PROP_EVENT_NM);
		String responseTopic = message.getProperties().getString(PROP_RESP_TOPIC);
		String selectorKey = message.getProperties().getString(PROP_SELECT_KEY);
		log.info("payload:{} evetName:{}", payload, eventString);

		SolaceMessageInfoVo infoVo = SolaceMessageInfoVo.builder()
			.msgObject(message).responseTopic(responseTopic).selectorKey(selectorKey).build();
		ApMessageList eventName = ApMessageList.valueOf(eventString);
		registry.getHandler(eventName).handle(payload, InterfaceType.SOLACE, infoVo);

	}

	@Override
	public void onException(JCSMPException ex) {
		log.error("[AgentTask] FlowReceiver exception", ex);
	}
}