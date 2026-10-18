package tsh.d94.app.server.interfaces.controller.mdm.dto;

import java.time.LocalDateTime;

import tsh.d94.lib.core.data.constant.MessagingSolutionType;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.msgServiceConn.gsMsgSrvConn.GsMsgSrvConnModel;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GnMsgSrvConnRes {

	private String objId;
	private String srvId;
	private String tenant;
	private UseStatCd useStatCd;
	private MessagingSolutionType solNm;
	private String env;
	private String host;
	private int port;
	private String user;      // pwd 제외
	private String domain;
	private LocalDateTime createdAt;
	private LocalDateTime modifiedAt;

	public static GnMsgSrvConnRes from(GsMsgSrvConnModel model) {
		return GnMsgSrvConnRes.builder()
			.objId(model.getObjId())
			.srvId(model.getSrvId())
			.tenant(model.getTenant())
			.useStatCd(model.getUseStatCd())
			.solNm(model.getSolNm())
			.env(model.getEnv())
			.host(model.getHost())
			.port(model.getPort())
			.user(model.getConnUser())
			.domain(model.getDomain())
			.createdAt(model.getCreatedAt())
			.modifiedAt(model.getModifiedAt())
			.build();
	}
}
