package tsh.d94.app.server.apService.cira.dto.cql;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CqlValidateRequest {

	@NotNull(message = "cql은 필수입니다.")
	private String cql;
}
