package tsh.d94.app.server.interfaces.controller.mdm.dto;

import tsh.d94.lib.core.data.constant.MessagingSolutionType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class GnMsgSrvConnReq {

	// BaseModel 필수값
	@NotBlank
	private String srvId;
	@NotBlank
	private String tenant;
	@NotBlank
	private String traceId;
	@NotBlank
	private String evetNm;
	@NotBlank
	private String prevEventNm;
	private String actCm;
	private String actCd;

	// GnMsgSrvConnModel 필드
	@NotNull
	private MessagingSolutionType solNm;
	@NotBlank
	private String env;
	@NotBlank
	private String host;
	@NotNull
	private Integer port;
	@NotBlank
	private String user;
	@NotBlank
	private String pwd;
	private String domain;
}
