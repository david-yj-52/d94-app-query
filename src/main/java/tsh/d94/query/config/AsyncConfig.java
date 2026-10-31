package tsh.d94.app.server.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;

@Configuration
@EnableAsync
public class AsyncConfig {

	@Bean(name = "automationTaskExecutor")
	public Executor automationTaskExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(4);
		executor.setMaxPoolSize(10);
		executor.setQueueCapacity(50);
		executor.setThreadNamePrefix("automation-");
		executor.setKeepAliveSeconds(60);
		executor.setTaskDecorator(tenantPropagatingDecorator());
		executor.initialize();
		return executor;
	}

	/** 호출 스레드의 tenant(ThreadLocal)를 작업 스레드로 넘기고, 작업이 끝나면 비운다. */
	static TaskDecorator tenantPropagatingDecorator() {
		return runnable -> {
			String tenant = TenantContext.getOrNull();
			return () -> {
				if (tenant != null) {
					TenantContext.set(tenant);
				}
				try {
					runnable.run();
				} finally {
					TenantContext.clear();
				}
			};
		};
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

}
