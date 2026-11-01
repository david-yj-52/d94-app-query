package tsh.d94.app.server.apService.cira.cql;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class CqlParserTest {

	private static CqlParseResult parse(String cql) {
		return new CqlParser(new CqlLexer(cql).tokenize()).parse();
	}

	@Test
	@DisplayName("빈 쿼리는 조건·정렬 없음")
	void empty() {
		CqlParseResult r = parse("");
		assertThat(r.getCondition()).isNull();
		assertThat(r.getOrderBy()).isEmpty();
	}

	@Test
	@DisplayName("단일 비교")
	void comparison() {
		assertThat(parse("priority = HIGH").getCondition())
			.isEqualTo(new CqlNode.ComparisonNode("priority", "=", "HIGH"));
	}

	@Test
	@DisplayName("AND 가 OR 보다 먼저 묶인다: a OR b AND c → a OR (b AND c)")
	void precedence_andBeforeOr() {
		CqlNode a = new CqlNode.ComparisonNode("a", "=", "1");
		CqlNode b = new CqlNode.ComparisonNode("b", "=", "2");
		CqlNode c = new CqlNode.ComparisonNode("c", "=", "3");

		assertThat(parse("a = 1 OR b = 2 AND c = 3").getCondition())
			.isEqualTo(new CqlNode.LogicalNode("OR", a, new CqlNode.LogicalNode("AND", b, c)));
	}

	@Test
	@DisplayName("괄호가 우선순위를 바꾼다: (a OR b) AND c")
	void parentheses() {
		CqlNode a = new CqlNode.ComparisonNode("a", "=", "1");
		CqlNode b = new CqlNode.ComparisonNode("b", "=", "2");
		CqlNode c = new CqlNode.ComparisonNode("c", "=", "3");

		assertThat(parse("(a = 1 OR b = 2) AND c = 3").getCondition())
			.isEqualTo(new CqlNode.LogicalNode("AND", new CqlNode.LogicalNode("OR", a, b), c));
	}

	@Test
	@DisplayName("같은 연산자는 왼쪽부터 묶인다 (좌결합)")
	void leftAssociative() {
		CqlNode a = new CqlNode.ComparisonNode("a", "=", "1");
		CqlNode b = new CqlNode.ComparisonNode("b", "=", "2");
		CqlNode c = new CqlNode.ComparisonNode("c", "=", "3");

		assertThat(parse("a = 1 AND b = 2 AND c = 3").getCondition())
			.isEqualTo(new CqlNode.LogicalNode("AND", new CqlNode.LogicalNode("AND", a, b), c));
	}

	@Test
	@DisplayName("NOT 은 바로 뒤 항 하나에만 적용된다")
	void notBindsTight() {
		CqlNode a = new CqlNode.ComparisonNode("a", "=", "1");
		CqlNode b = new CqlNode.ComparisonNode("b", "=", "2");

		assertThat(parse("NOT a = 1 AND b = 2").getCondition())
			.isEqualTo(new CqlNode.LogicalNode("AND", new CqlNode.NotNode(a), b));
	}

	@Test
	@DisplayName("IN / NOT IN, 값 목록에 문자열·함수")
	void inAndNotIn() {
		assertThat(parse("status IN (\"To Do\", 'Done')").getCondition())
			.isEqualTo(new CqlNode.InNode("status", List.of("To Do", "Done"), false));
		assertThat(parse("sprint NOT IN (openSprints())").getCondition())
			.isEqualTo(new CqlNode.InNode("sprint", List.of("openSprints"), true));
	}

	@Test
	@DisplayName("ORDER BY 여러 항목, 기본 ASC")
	void orderBy() {
		CqlParseResult r = parse("priority = HIGH ORDER BY created DESC, priority");

		assertThat(r.getOrderBy()).extracting(CqlParseResult.OrderItem::getField).containsExactly("created", "priority");
		assertThat(r.getOrderBy()).extracting(CqlParseResult.OrderItem::isAscending).containsExactly(false, true);
	}

	@Test
	@DisplayName("[D15] 조건 없이 ORDER BY 만 있어도 된다 (지금은 ORDER 를 필드명으로 읽어 오류)")
	void orderByOnly() {
		CqlParseResult r = parse("ORDER BY updated");
		assertThat(r.getCondition()).isNull();
		assertThat(r.getOrderBy()).hasSize(1);
	}

	@ParameterizedTest(name = "\"{0}\"")
	@ValueSource(strings = {
		"priority",                 // 연산자 없음
		"priority =",               // 값 없음
		"= HIGH",                   // 필드 없음
		"(a = 1",                   // 닫는 괄호 없음
		"a = 1 b = 2",              // 연결자 없음
		"a IN ()",                  // 빈 목록
		"a IN (1,)",                // 끝 쉼표
		"ORDER created",            // BY 없음
		"a = 1 AND"                 // 뒤 항 없음
	})
	@DisplayName("문법 오류는 CqlParseException")
	void syntaxErrors(String cql) {
		assertThatThrownBy(() -> parse(cql)).isInstanceOf(CqlParseException.class);
	}

	@Test
	@DisplayName("오류 위치(position)는 문제 토큰의 시작")
	void errorPosition() {
		assertThatThrownBy(() -> parse("a = 1 b = 2"))
			.isInstanceOf(CqlParseException.class)
			.extracting("position").isEqualTo(6);
	}

}
