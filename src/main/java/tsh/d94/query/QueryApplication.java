package tsh.d94.app.server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {
	"tsh.d94.lib.core", "tsh.d94.app.server"
})
@EntityScan(basePackages = {"tsh.d94.lib.core", "tsh.d94.app.server"})
@EnableJpaRepositories(basePackages = {"tsh.d94.lib.core", "tsh.d94.app.server"})
@EnableCaching
class D94QueryApplication {

	public static void main(String[] args) {
		SpringApplication.run(D94QueryApplication.class, args);
	}

}
