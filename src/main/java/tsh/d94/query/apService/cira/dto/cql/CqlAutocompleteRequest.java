package tsh.d94.app.server.apService.cira.dto.cql;

import lombok.Data;

@Data
public class CqlAutocompleteRequest {

	private String cql;
	private int cursor;
}
