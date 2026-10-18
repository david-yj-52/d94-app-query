package tsh.d94.app.server.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import lombok.Getter;
import lombok.Setter;

@Component
@ConfigurationProperties(prefix = "cira.storage")
@Getter
@Setter
public class StorageProperties {

	private String endpoint;
	private String accessKey;
	private String secretKey;
	private String bucket;
	private String region;

}
