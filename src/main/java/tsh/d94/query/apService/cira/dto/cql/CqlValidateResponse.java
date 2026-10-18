package tsh.d94.app.server.apService.cira.dto.cql;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class CqlValidateResponse {

	private boolean valid;
	private String error;
	private int position;
}
