package tsh.d94.app.server.config;

import java.util.concurrent.Executor;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
@EnableConfigurationProperties(StudyProperties.class)
public class StudyConfig {

	/** PDF 파싱 전용. 파싱은 파서 쪽에서 무거우므로 동시 2건까지. */
	@Bean(name = "studyParseExecutor")
	public Executor studyParseExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(2);
		executor.setMaxPoolSize(2);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("study-parse-");
		executor.setTaskDecorator(AsyncConfig.tenantPropagatingDecorator());
		executor.initialize();
		return executor;
	}
}
