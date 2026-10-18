package tsh.d94.app.server.interfaces.subscriber;

/**
 * 메시징 솔루션(Solace, RabbitMQ 등)에 관계없이 ApService 계층에서 공통으로 사용하는
 * 요청-응답 라우팅 정보 인터페이스입니다.
 *
 * - Solace: Topic 기반 응답(responseTopic) + Selector 기반 매칭(selectorKey)
 * - RabbitMQ: AMQP RPC 패턴의 replyTo 큐(responseTopic) + correlationId(selectorKey)
 *
 * 새로운 메시징 솔루션을 추가할 때는 이 인터페이스를 구현하는 전용 XxxMessageInfoVo를 만들고,
 * ApService/AbstractApService/ApProcessVo는 수정할 필요가 없습니다.
 */
public interface MessageInfoVo {

	String getResponseTopic();

	String getSelectorKey();

}
