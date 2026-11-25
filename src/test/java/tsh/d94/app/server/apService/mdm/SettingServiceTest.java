package tsh.d94.app.server.apService.mdm;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import tsh.d94.app.server.data.orm.mdm.setting.SnSettingModel;
import tsh.d94.app.server.data.orm.mdm.setting.SnSettingRepo;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;

@ExtendWith(MockitoExtension.class)
class SettingServiceTest {

	@Mock
	private SnSettingRepo repo;

	@InjectMocks
	private SettingService service;

	@BeforeEach
	void tenant() {
		TenantContext.set("T");
	}

	@AfterEach
	void clear() {
		TenantContext.clear();
	}

	@Test
	void getAll_returns_empty_map_when_nothing_saved() {
		when(repo.findByTenantAndUseStatCd("T", UseStatCd.Usable)).thenReturn(List.of());

		assertThat(service.getAll()).isEmpty();
	}

	@Test
	void update_saves_trimmed_logo_text() {
		when(repo.findByTenantAndSettingKey("T", "general.logo.text")).thenReturn(Optional.empty());

		service.update(Map.of("general.logo.text", "  ACME  "));

		ArgumentCaptor<SnSettingModel> saved = ArgumentCaptor.forClass(SnSettingModel.class);
		verify(repo).save(saved.capture());
		assertThat(saved.getValue().getSettingKey()).isEqualTo("general.logo.text");
		assertThat(saved.getValue().getSettingValue()).isEqualTo("ACME");
		assertThat(saved.getValue().getTenant()).isEqualTo("T");
	}

	@Test
	void update_overwrites_existing_value() {
		SnSettingModel existing = SnSettingModel.builder().settingKey("general.logo.font").settingValue("default").build();
		when(repo.findByTenantAndSettingKey("T", "general.logo.font")).thenReturn(Optional.of(existing));

		service.update(Map.of("general.logo.font", "do-hyeon"));

		verify(repo).save(existing);
		assertThat(existing.getSettingValue()).isEqualTo("do-hyeon");
	}

	@Test
	void update_rejects_unknown_key() {
		assertThatThrownBy(() -> service.update(Map.of("foo", "x"))).isInstanceOf(IllegalArgumentException.class);
		verify(repo, never()).save(any());
	}

	@Test
	void update_rejects_text_over_30_or_blank() {
		assertThatThrownBy(() -> service.update(Map.of("general.logo.text", "a".repeat(31))))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> service.update(Map.of("general.logo.text", "   ")))
			.isInstanceOf(IllegalArgumentException.class);
		verify(repo, never()).save(any());
	}

	@Test
	void update_rejects_unknown_font() {
		assertThatThrownBy(() -> service.update(Map.of("general.logo.font", "comic-sans")))
			.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void update_is_all_or_nothing() {
		assertThatThrownBy(() -> service.update(Map.of("general.logo.text", "OK", "general.logo.font", "bad")))
			.isInstanceOf(IllegalArgumentException.class);
		verify(repo, never()).save(any());
	}

	@Test
	void update_rejects_removed_cira_project_types_key() {
		// V590: 프로젝트 타입은 SN_CIRA_PROJECT_TYPE 으로 옮겨졌다
		assertThatThrownBy(() -> service.update(Map.of("cira.project.types", "[\"웹개발\"]")))
			.isInstanceOf(IllegalArgumentException.class);
		verify(repo, never()).save(any());
	}
}
