package tsh.d94.app.server.config;

import static org.assertj.core.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;

class AsyncConfigTest {

	@AfterEach
	void tearDown() {
		TenantContext.clear();
	}

	@Test
	void decorator_propagates_caller_tenant_to_worker_thread() throws Exception {
		TenantContext.set("USER_TENANT");
		AtomicReference<String> seen = new AtomicReference<>();
		AtomicReference<String> afterRun = new AtomicReference<>("unset");

		Runnable decorated = AsyncConfig.tenantPropagatingDecorator().decorate(() -> seen.set(TenantContext.get()));

		Thread worker = new Thread(() -> {
			decorated.run();
			afterRun.set(TenantContext.getOrNull());
		});
		worker.start();
		worker.join();

		assertThat(seen.get()).isEqualTo("USER_TENANT");
		assertThat(afterRun.get()).isNull();
	}

	@Test
	void decorator_without_caller_tenant_leaves_worker_empty() throws Exception {
		AtomicReference<String> seen = new AtomicReference<>("unset");

		Runnable decorated = AsyncConfig.tenantPropagatingDecorator().decorate(() -> seen.set(TenantContext.getOrNull()));

		Thread worker = new Thread(decorated);
		worker.start();
		worker.join();

		assertThat(seen.get()).isNull();
	}

}
