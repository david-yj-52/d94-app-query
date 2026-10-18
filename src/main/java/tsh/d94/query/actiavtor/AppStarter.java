package tsh.d94.app.server.actiavtor;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.solacesystems.jcsmp.JCSMPException;
import com.solacesystems.jcsmp.JCSMPSession;
import tsh.d94.lib.core.config.MessagingProperties;
import tsh.d94.lib.core.messaging.MessagingConfManager;
import tsh.d94.lib.core.messaging.rabbitmq.inbound.RabbitMqInboundGateway;
import tsh.d94.lib.core.messaging.rabbitmq.inbound.RabbitMqInboundManager;
import tsh.d94.lib.core.messaging.rabbitmq.outbound.RabbitMqMessagePublisher;
import tsh.d94.lib.core.messaging.solace.inbound.SolaceInboundGateway;
import tsh.d94.lib.core.messaging.solace.inbound.SolaceInboundManager;
import tsh.d94.lib.core.messaging.solace.outbound.SolaceMessagePublisher;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Component
public class AppStarter implements ApplicationRunner {

	@Autowired
	MessagingConfManager messagingConfManager;

	@Autowired
	MessagingProperties messagingProperties;

	@Autowired
	SolaceInboundGateway solaceInboundGateway;

	@Autowired
	SolaceInboundManager solaceInboundManager;

	@Autowired
	SolaceMessagePublisher solaceMessagePublisher;

	@Autowired
	RabbitMqInboundGateway rabbitMqInboundGateway;

	@Autowired
	RabbitMqInboundManager rabbitMqInboundManager;

	@Autowired
	RabbitMqMessagePublisher rabbitMqMessagePublisher;

	@Override
	public void run(ApplicationArguments args) throws Exception {

		log.info("application started.");
		this.startConnectMessagingServer();

	}

	private void startConnectMessagingServer() {

		if (Boolean.parseBoolean(messagingProperties.getSolaceEnable())) {
			JCSMPSession solaceSession = this.messagingConfManager.getSolaceDefaultSession();

			if (Boolean.parseBoolean(messagingProperties.getSolaceSubEnable())) {
				this.solaceInboundGateway.setSession(solaceSession);
				this.solaceInboundManager.registerAll();
			}

			if (Boolean.parseBoolean(messagingProperties.getSolacePubEnable())) {
				try {
					solaceMessagePublisher.setSession(solaceSession);
				} catch (JCSMPException e) {
					throw new RuntimeException(e);
				}
			}
		}

		if (Boolean.parseBoolean(messagingProperties.getRabbitMqEnable())) {
			Connection rabbitMqConnection = this.messagingConfManager.getRabbitMqDefaultConnection();

			if (Boolean.parseBoolean(messagingProperties.getRabbitMqSubEnable())) {
				this.rabbitMqInboundGateway.setConnection(rabbitMqConnection);
				this.rabbitMqInboundManager.registerAll();
			}

			if (Boolean.parseBoolean(messagingProperties.getRabbitMqPubEnable())) {
				try {
					Channel publishChannel = rabbitMqConnection.createChannel();
					rabbitMqMessagePublisher.setChannel(publishChannel);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			}
		}
	}

}
