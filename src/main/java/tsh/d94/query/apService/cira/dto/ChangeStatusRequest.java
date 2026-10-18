package tsh.d94.app.server.apService.cira.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChangeStatusRequest {

	@NotBlank(message = "statusId는 필수입니다.")
	private String statusId;
}
