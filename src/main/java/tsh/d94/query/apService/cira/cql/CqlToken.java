package tsh.d94.app.server.apService.cira.cql;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * CQL 토큰: 타입 + 값
 */
@Getter
@RequiredArgsConstructor
public class CqlToken {

	private final TokenType type;
	private final String value;
	private final int position;

	@Override
	public String toString() {
		return "[" + type + ":" + value + "@" + position + "]";
	}

	public enum TokenType {
		// 키워드
		AND, OR, NOT, IN, ORDER, BY, ASC, DESC,
		// 연산자
		EQ,        // =
		NEQ,       // !=
		LT,        // <
		LTE,       // <=
		GT,        // >
		GTE,       // >=
		LIKE,      // ~
		NLIKE,     // !~
		// 구조
		LPAREN,    // (
		RPAREN,    // )
		COMMA,     // ,
		// 값
		IDENTIFIER,
		STRING,
		NUMBER,
		// 함수
		FUNCTION,  // currentUser(), openSprints()
		// 끝
		EOF
	}
}
