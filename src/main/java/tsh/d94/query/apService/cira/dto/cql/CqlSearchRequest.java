package tsh.d94.app.server.apService.cira.dto.cql;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CqlSearchRequest {

	@NotBlank(message = "cql은 필수입니다.")
	private String cql;

	private int page = 0;
	private int size = 20;
}
