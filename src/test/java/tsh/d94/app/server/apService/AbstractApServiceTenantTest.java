package tsh.d94.app.server.apService;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import tsh.d94.app.server.ApProcessVo;
import tsh.d94.lib.core.ApMessage;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;
import tsh.d94.lib.core.interfaces.InterfaceType;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;
import tsh.d94.lib.core.spec.ApMessageBody;
import tsh.d94.lib.core.spec.constant.ApMessageList;

class AbstractApServiceTenantTest {

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	/** mainAction 안에서 본 tenant 를 기록하고, 필요하면 예외를 던지는 테스트용 서비스 */
	static class RecordingService extends AbstractApService<Void, ApMessageBody> {
		final AtomicReference<String> seen = new AtomicReference<>();
		boolean fail;

		@Override
		protected Class<? extends ApMessage> getIvoClass() {
			return ApMessage.class;
		}

		@Override
		protected ApProcessVo<ApMessageBody> buildProcessVo(InterfaceType interfaceType, ApMessage ivo) {
			return null;
		}

		@Override
		protected void mainAction(ApProcessVo<ApMessageBody> procVo) {
			seen.set(TenantContext.get());
			if (fail) {
				throw new IllegalStateException("boom");
			}
		}

		@Override
		protected ApiResponse<Void> resultAction(ApProcessVo<ApMessageBody> procVo) {
			return null;
		}

		@Override
		public ApMessageList getSupportedEvent() {
			return null;
		}
	}

	private ApProcessVo<ApMessageBody> procVo(String tenant) {
		ApProcessVo<ApMessageBody> vo = new ApProcessVo<>();
		vo.setTenant(tenant);
		return vo;
	}

	@Test
	void message_tenant_is_used_and_cleared_when_none_before() {
		RecordingService service = new RecordingService();

		service.run(procVo("MSG_TENANT"));

		assertThat(service.seen.get()).isEqualTo("MSG_TENANT");
		assertThat(TenantContext.getOrNull()).isNull();
	}

	@Test
	void previous_request_tenant_is_restored() {
		TenantContext.set("USER_TENANT");
		RecordingService service = new RecordingService();

		service.run(procVo("MSG_TENANT"));

		assertThat(service.seen.get()).isEqualTo("MSG_TENANT");
		assertThat(TenantContext.get()).isEqualTo("USER_TENANT");
	}

	@Test
	void previous_tenant_is_restored_even_on_failure() {
		TenantContext.set("USER_TENANT");
		RecordingService service = new RecordingService();
		service.fail = true;

		assertThatThrownBy(() -> service.run(procVo("MSG_TENANT"))).isInstanceOf(RuntimeException.class);
		assertThat(TenantContext.get()).isEqualTo("USER_TENANT");
	}

}
