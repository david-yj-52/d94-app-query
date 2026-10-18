package tsh.d94.app.server;

import java.util.concurrent.ConcurrentHashMap;

import tsh.d94.app.server.interfaces.subscriber.MessageInfoVo;
import tsh.d94.lib.core.ApMessage;
import tsh.d94.lib.core.apService.util.ServerNameUtil;
import tsh.d94.lib.core.interfaces.InterfaceType;
import tsh.d94.lib.core.spec.ApMessageBody;
import tsh.d94.lib.core.spec.process.ApCommonProcessVo;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class ApProcessVo<T extends ApMessageBody> extends ApCommonProcessVo<T> {

	String serverInstanceId;
	boolean preComp;
	boolean mainComp;
	boolean postComp;
	boolean responseComp;

	boolean logicCompYn;

	MessageInfoVo msgInfoVo;

	ConcurrentHashMap<String, Object> objMap;

	@Override
	public ApCommonProcessVo<T> init(ApMessage ivo) {
		// 공통 초기화는 부모에게
		// 구체 body 타입은 각 IVO 변환 메서드에서 처리
		throw new UnsupportedOperationException("use init(ivo, body) instead");
	}

	public ApProcessVo<T> init(InterfaceType interfaceType, ApMessage ivo, T body) {
		super.initCommon(interfaceType, ivo, body);

		this.objMap = new ConcurrentHashMap<>();
		this.serverInstanceId = ServerNameUtil.getHostName();
		return this;
	}

	@Override
	protected void validate() {
		super.validate();
		log.info("AP 수준 Vo 검증");

	}

}
