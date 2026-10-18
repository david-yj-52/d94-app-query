package tsh.d94.app.server.apService.cira.cql;

import java.util.List;

import lombok.Builder;
import lombok.Getter;

/**
 * 파서 결과: 조건 트리 + ORDER BY 절
 */
@Getter
@Builder
public class CqlParseResult {

	/** WHERE 조건 AST (null = 조건 없음) */
	private final CqlNode condition;

	/** ORDER BY 목록 */
	private final List<OrderItem> orderBy;

	@Getter
	@Builder
	public static class OrderItem {
		private final String field;
		private final boolean ascending;
	}
}
