package tsh.d94.app.server.apService.cira.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class UserSearchResponse {
	private String id;
	private String name;
	private String email;
	private String avatarUrl;
}
