package tsh.d94.app.server.interfaces.subscriber;

import com.rabbitmq.client.AMQP;
import com.rabbitmq.client.Envelope;

import lombok.Data;
import lombok.experimental.SuperBuilder;

@SuperBuilder
@Data
public class RabbitMqMessageInfoVo implements MessageInfoVo {

	byte[] msgBody;
	Envelope envelope;
	AMQP.BasicProperties properties;

	/**
	 * RabbitMQ RPC 패턴의 replyTo 큐명을 Solace의 responseTopic 개념에 매핑합니다.
	 */
	@Override
	public String getResponseTopic() {
		return properties != null ? properties.getReplyTo() : null;
	}

	/**
	 * RabbitMQ의 correlationId를 Solace의 selectorKey 개념에 매핑합니다.
	 */
	@Override
	public String getSelectorKey() {
		return properties != null ? properties.getCorrelationId() : null;
	}
}
