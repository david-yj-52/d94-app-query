package tsh.d94.app.server.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Getter;
import lombok.Setter;

/** study.parser.* — PDF 파서 호출 설정 */
@Getter
@Setter
@ConfigurationProperties(prefix = "study.parser")
public class StudyProperties {

	private String gatewayUrl = "http://gateway-cloud-gateway-1:10001";
	private String serviceToken = "";
	private Duration timeout = Duration.ofSeconds(120);
}
