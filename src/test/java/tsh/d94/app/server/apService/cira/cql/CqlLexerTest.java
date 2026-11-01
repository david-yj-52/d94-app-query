package tsh.d94.app.server.apService.cira.cql;

import static org.assertj.core.api.Assertions.*;
import static tsh.d94.app.server.apService.cira.cql.CqlToken.TokenType.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CqlLexerTest {

	private static List<CqlToken.TokenType> types(String input) {
		return new CqlLexer(input).tokenize().stream().map(CqlToken::getType).toList();
	}

	private static List<String> values(String input) {
		return new CqlLexer(input).tokenize().stream().map(CqlToken::getValue).toList();
	}

	@Test
	@DisplayName("빈 입력·null 은 EOF 하나")
	void emptyInput_onlyEof() {
		assertThat(types("")).containsExactly(EOF);
		assertThat(types(null)).containsExactly(EOF);
		assertThat(types("   ")).containsExactly(EOF);
	}

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({"=,EQ", "!=,NEQ", "<,LT", "<=,LTE", ">,GT", ">=,GTE", "~,LIKE", "!~,NLIKE"})
	@DisplayName("비교 연산자")
	void operators(String op, CqlToken.TokenType expected) {
		assertThat(types("a " + op + " 1")).containsExactly(IDENTIFIER, expected, NUMBER, EOF);
	}

	@Test
	@DisplayName("키워드는 대소문자 무관, 원래 표기는 값으로 남는다")
	void keywords_caseInsensitive() {
		assertThat(types("a = 1 and b = 2 Or NOT c in (1) order BY a desc"))
			.containsExactly(IDENTIFIER, EQ, NUMBER, AND, IDENTIFIER, EQ, NUMBER, OR, NOT, IDENTIFIER, IN, LPAREN,
				NUMBER, RPAREN, ORDER, BY, IDENTIFIER, DESC, EOF);
		assertThat(values("x = 1 and y = 2").get(3)).isEqualTo("and");
	}

	@Test
	@DisplayName("문자열: 큰·작은따옴표, 역슬래시 이스케이프, 공백 포함")
	void strings() {
		assertThat(values("t = \"In Progress\"")).containsExactly("t", "=", "In Progress", "");
		assertThat(values("t = 'it\\'s'")).containsExactly("t", "=", "it's", "");
	}

	@Test
	@DisplayName("숫자: 음수·소수")
	void numbers() {
		assertThat(values("p >= -1.5")).containsExactly("p", ">=", "-1.5", "");
		assertThat(types("p >= -1.5")).containsExactly(IDENTIFIER, GTE, NUMBER, EOF);
	}

	@Test
	@DisplayName("함수 currentUser()·openSprints() 는 괄호까지 한 토큰")
	void functions() {
		assertThat(types("assignee = currentUser()")).containsExactly(IDENTIFIER, EQ, FUNCTION, EOF);
		assertThat(types("sprint in (openSprints())"))
			.containsExactly(IDENTIFIER, IN, LPAREN, FUNCTION, RPAREN, EOF);
		// 괄호가 없으면 일반 식별자
		assertThat(types("x = currentUser")).containsExactly(IDENTIFIER, EQ, IDENTIFIER, EOF);
	}

	@Test
	@DisplayName("토큰 위치(position)는 입력 상의 시작 인덱스")
	void positions() {
		List<CqlToken> tokens = new CqlLexer("ab != 'x'").tokenize();
		assertThat(tokens).extracting(CqlToken::getPosition).containsExactly(0, 3, 6, 9);
	}

	@Test
	@DisplayName("알 수 없는 문자·단독 '!' 는 위치와 함께 CqlParseException")
	void invalidCharacters() {
		assertThatThrownBy(() -> new CqlLexer("a = 1 #").tokenize())
			.isInstanceOf(CqlParseException.class)
			.extracting("position").isEqualTo(6);
		assertThatThrownBy(() -> new CqlLexer("a ! 1").tokenize()).isInstanceOf(CqlParseException.class);
	}

	@Test
	@DisplayName("[관찰] 이슈 키처럼 하이픈이 든 값은 따옴표가 필요하다 (CIRA-1 → IDENTIFIER, NUMBER)")
	void unquotedIssueKey_splits() {
		assertThat(types("key = CIRA-1")).containsExactly(IDENTIFIER, EQ, IDENTIFIER, NUMBER, EOF);
	}

	@Test
	@DisplayName("[D14] 닫히지 않은 문자열은 오류여야 한다 (지금은 끝까지를 값으로 받아들임)")
	void unterminatedString_isError() {
		assertThatThrownBy(() -> new CqlLexer("t = \"abc").tokenize()).isInstanceOf(CqlParseException.class);
	}

}
