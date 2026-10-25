package tsh.d94.app.server.apService.ifTest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * POST·PUT 은 name 필수(전체 값), PATCH 는 null 이 아닌 필드만 반영한다.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterfaceTestItemRequest {

	private String name;
	private String content;
	private String status;
}
