package tsh.d94.app.server.apService.cira.cql;

import static tsh.d94.app.server.apService.cira.cql.CqlToken.TokenType.*;

import java.util.ArrayList;
import java.util.List;

/**
 * CQL 재귀 하강 파서.
 *
 * 문법:
 *   query      → expression orderByClause? EOF
 *   expression → orExpr
 *   orExpr     → andExpr (OR andExpr)*
 *   andExpr    → notExpr (AND notExpr)*
 *   notExpr    → NOT? primary
 *   primary    → field op value
 *              | field IN (valueList)
 *              | field NOT IN (valueList)
 *              | ( expression )
 *   orderByClause → ORDER BY orderItem (, orderItem)*
 *   orderItem     → field (ASC|DESC)?
 */
public class CqlParser {

	private final List<CqlToken> tokens;
	private int cursor;

	public CqlParser(List<CqlToken> tokens) {
		this.tokens = tokens;
		this.cursor = 0;
	}

	public CqlParseResult parse() {
		CqlNode condition = null;
		List<CqlParseResult.OrderItem> orderBy = List.of();

		if (!check(EOF)) {
			condition = parseOrExpr();
		}
		if (check(ORDER)) {
			orderBy = parseOrderByClause();
		}
		expect(EOF);
		return CqlParseResult.builder()
			.condition(condition)
			.orderBy(orderBy)
			.build();
	}

	// ────────────────────────────────────────
	// expression parsing
	// ────────────────────────────────────────

	private CqlNode parseOrExpr() {
		CqlNode left = parseAndExpr();
		while (check(OR)) {
			advance();
			CqlNode right = parseAndExpr();
			left = new CqlNode.LogicalNode("OR", left, right);
		}
		return left;
	}

	private CqlNode parseAndExpr() {
		CqlNode left = parseNotExpr();
		while (check(AND)) {
			advance();
			CqlNode right = parseNotExpr();
			left = new CqlNode.LogicalNode("AND", left, right);
		}
		return left;
	}

	private CqlNode parseNotExpr() {
		if (check(NOT)) {
			advance();
			CqlNode expr = parsePrimary();
			return new CqlNode.NotNode(expr);
		}
		return parsePrimary();
	}

	private CqlNode parsePrimary() {
		// ( expression )
		if (check(LPAREN)) {
			advance();
			CqlNode inner = parseOrExpr();
			expect(RPAREN);
			return inner;
		}

		// field ...
		String field = parseFieldName();

		// field NOT IN (...)
		if (check(NOT)) {
			int saved = cursor;
			advance();
			if (check(IN)) {
				advance();
				List<String> values = parseValueList();
				return new CqlNode.InNode(field, values, true);
			}
			cursor = saved; // rollback
		}

		// field IN (...)
		if (check(IN)) {
			advance();
			List<String> values = parseValueList();
			return new CqlNode.InNode(field, values, false);
		}

		// field op value
		String op = parseOperator();
		String value = parseValue();
		return new CqlNode.ComparisonNode(field, op, value);
	}

	// ────────────────────────────────────────
	// ORDER BY
	// ────────────────────────────────────────

	private List<CqlParseResult.OrderItem> parseOrderByClause() {
		expect(ORDER);
		expect(BY);
		List<CqlParseResult.OrderItem> items = new ArrayList<>();
		items.add(parseOrderItem());
		while (check(COMMA)) {
			advance();
			items.add(parseOrderItem());
		}
		return items;
	}

	private CqlParseResult.OrderItem parseOrderItem() {
		String field = parseFieldName();
		boolean asc = true;
		if (check(DESC)) {
			advance();
			asc = false;
		} else if (check(ASC)) {
			advance();
		}
		return CqlParseResult.OrderItem.builder().field(field).ascending(asc).build();
	}

	// ────────────────────────────────────────
	// value / operator / field helpers
	// ────────────────────────────────────────

	private String parseFieldName() {
		CqlToken t = current();
		if (t.getType() == IDENTIFIER) {
			advance();
			return t.getValue();
		}
		throw new CqlParseException("필드명이 예상되는 위치에 '" + t.getValue() + "' 발견", t.getPosition());
	}

	private String parseOperator() {
		CqlToken t = current();
		String op = switch (t.getType()) {
			case EQ -> "=";
			case NEQ -> "!=";
			case LT -> "<";
			case LTE -> "<=";
			case GT -> ">";
			case GTE -> ">=";
			case LIKE -> "~";
			case NLIKE -> "!~";
			default -> null;
		};
		if (op == null) {
			throw new CqlParseException("연산자가 예상되는 위치에 '" + t.getValue() + "' 발견", t.getPosition());
		}
		advance();
		return op;
	}

	private String parseValue() {
		CqlToken t = current();
		if (t.getType() == STRING || t.getType() == NUMBER || t.getType() == IDENTIFIER || t.getType() == FUNCTION) {
			advance();
			return t.getValue();
		}
		throw new CqlParseException("값이 예상되는 위치에 '" + t.getValue() + "' 발견", t.getPosition());
	}

	private List<String> parseValueList() {
		expect(LPAREN);
		List<String> values = new ArrayList<>();
		values.add(parseValue());
		while (check(COMMA)) {
			advance();
			values.add(parseValue());
		}
		expect(RPAREN);
		return values;
	}

	// ────────────────────────────────────────
	// token navigation helpers
	// ────────────────────────────────────────

	private CqlToken current() {
		return tokens.get(cursor);
	}

	private boolean check(CqlToken.TokenType type) {
		return current().getType() == type;
	}

	private void advance() {
		if (cursor < tokens.size() - 1)
			cursor++;
	}

	private void expect(CqlToken.TokenType type) {
		CqlToken t = current();
		if (t.getType() != type) {
			throw new CqlParseException(
				"'" + type + "' 가 예상되는 위치에 '" + t.getValue() + "' 발견", t.getPosition());
		}
		advance();
	}
}
