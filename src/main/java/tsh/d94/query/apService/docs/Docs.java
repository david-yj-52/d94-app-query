package tsh.d94.app.server.apService.docs;

import org.springframework.security.core.context.SecurityContextHolder;

import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.model.BaseModel;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;

/** docs 공통: vault 소유자, BaseModel 공통 컬럼. */
public final class Docs {

	private Docs() {
	}

	/** vault 소유자 = 로그인 이메일 */
	public static String owner() {
		return SecurityContextHolder.getContext().getAuthentication().getName();
	}

	public static <T extends BaseModel.BaseModelBuilder<?, ?>> T base(T builder, String event) {
		builder.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(TenantContext.get())
			.traceId(event)
			.useStatCd(UseStatCd.Usable)
			.evtNm(event)
			.prevEvntNm("None");
		return builder;
	}

	/** 앞뒤 공백 제거 후 비었거나 max 초과, 금지 문자가 있으면 400 */
	public static String name(String raw, int max, String forbidden, String label) {
		String s = raw == null ? "" : raw.trim();
		if (s.isEmpty() || s.length() > max || s.chars().anyMatch(c -> forbidden.indexOf(c) >= 0 || c == '\n')) {
			throw new DocsException(DocsException.Code.INVALID_REQUEST,
				label + "은(는) 1~" + max + "자이고 " + forbidden + " 문자를 쓸 수 없습니다.");
		}
		return s;
	}
}
