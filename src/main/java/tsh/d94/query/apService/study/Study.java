package tsh.d94.app.server.apService.study;

import org.springframework.security.core.context.SecurityContextHolder;

import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;

/** study 공통: 소유자, BaseModel 공통 컬럼, 상수. */
public final class Study {

	public static final String PARSING = "PARSING";
	public static final String READY = "READY";
	public static final String FAILED = "FAILED";

	private Study() {
	}

	/** 소유자 = 로그인 이메일 */
	public static String owner() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	public static <T extends BaseModel.BaseModelBuilder<?, ?>> T base(T builder, String event, String tenant) {
		builder.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(tenant)
			.traceId(event)
			.useStatCd(UseStatCd.Usable)
			.evtNm(event)
			.prevEvntNm("None");
		return builder;
	}

	public static String truncate(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}
}
