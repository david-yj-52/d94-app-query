package tsh.d94.app.server.config;

import java.util.concurrent.Executor;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestTemplate;

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
		executor.initialize();
		return executor;
	}

	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}

}
