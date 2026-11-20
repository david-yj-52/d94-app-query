package tsh.d94.app.server.apService.mdm;

import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.data.orm.mdm.setting.SnSettingModel;
import tsh.d94.app.server.data.orm.mdm.setting.SnSettingRepo;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;

/** 기준정보 설정. 허용된 키만 저장하고, 하나라도 틀리면 아무것도 저장하지 않는다 */
@Service
@RequiredArgsConstructor
public class SettingService {

	static final String LOGO_TEXT = "general.logo.text";
	static final String LOGO_FONT = "general.logo.font";
	static final int LOGO_TEXT_MAX = 30;
	// d94-web src/components/layout/logoFonts.ts 의 LOGO_FONTS id 와 같아야 한다
	static final Set<String> LOGO_FONTS = Set.of("default", "noto-sans-kr", "nanum-gothic", "black-han-sans", "do-hyeon");

	private final SnSettingRepo repo;

	public Map<String, String> getAll() {
		Map<String, String> result = new TreeMap<>();
		repo.findByTenantAndUseStatCd(TenantContext.get(), UseStatCd.Usable)
			.forEach(s -> result.put(s.getSettingKey(), s.getSettingValue()));
		return result;
	}

	@Transactional
	public Map<String, String> update(Map<String, String> changes) {
		Map<String, String> clean = new TreeMap<>();
		changes.forEach((key, value) -> clean.put(key, validate(key, value)));

		String tenant = TenantContext.get();
		clean.forEach((key, value) -> {
			SnSettingModel row = repo.findByTenantAndSettingKey(tenant, key).orElseGet(() -> newRow(tenant, key));
			row.setSettingValue(value);
			repo.save(row);
		});
		return getAll();
	}

	private static String validate(String key, String value) {
		String v = value == null ? "" : value.trim();
		switch (key) {
			case LOGO_TEXT -> {
				if (v.isEmpty() || v.length() > LOGO_TEXT_MAX) {
					throw new IllegalArgumentException("로고 글자는 1~" + LOGO_TEXT_MAX + "자여야 합니다.");
				}
			}
			case LOGO_FONT -> {
				if (!LOGO_FONTS.contains(v)) {
					throw new IllegalArgumentException("지원하지 않는 폰트입니다: " + v);
				}
			}
			default -> throw new IllegalArgumentException("알 수 없는 설정 키입니다: " + key);
		}
		return v;
	}

	private static SnSettingModel newRow(String tenant, String key) {
		return SnSettingModel.builder()
			.settingKey(key)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(tenant)
			.traceId("UpdateSetting")
			.useStatCd(UseStatCd.Usable)
			.evtNm("UpdateSetting")
			.prevEvntNm("None")
			.build();
	}
}
