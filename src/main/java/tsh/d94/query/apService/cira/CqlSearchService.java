package tsh.d94.app.server.apService.cira;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import tsh.d94.app.server.apService.cira.cql.CqlLexer;
import tsh.d94.app.server.apService.cira.cql.CqlParseException;
import tsh.d94.app.server.apService.cira.cql.CqlParseResult;
import tsh.d94.app.server.apService.cira.cql.CqlParser;
import tsh.d94.app.server.apService.cira.cql.CqlQueryBuilder;
import tsh.d94.app.server.apService.cira.cql.CqlToken;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.cql.CqlAutocompleteResponse;
import tsh.d94.app.server.apService.cira.dto.cql.CqlAutocompleteResponse.SuggestionItem;
import tsh.d94.app.server.apService.cira.dto.cql.CqlValidateResponse;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraSprint.SnCiraSprintAccess;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * CQL 고급 검색 서비스.
 * <p>
 * CqlLexer → CqlParser → CqlQueryBuilder 순서로 처리하여
 * JPA Specification 으로 변환 후 issueAccess 를 통해 조회한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CqlSearchService {

	private static final List<String> SUPPORTED_FIELDS = List.of(
		"project", "status", "assignee", "reporter", "priority",
		"issueType", "text", "created", "updated", "due", "storyPoints", "sprint"
	);

	private static final List<String> OPERATORS = List.of(
		"=", "!=", "~", "!~", "<", "<=", ">", ">="
	);

	private static final List<String> STATUS_VALUES = List.of("TODO", "IN_PROGRESS", "DONE", "REVIEW");
	private static final List<String> PRIORITY_VALUES = List.of("HIGHEST", "HIGH", "MEDIUM", "LOW", "LOWEST");

	private final SnCiraIssueAccess issueAccess;
	private final SnCiraSprintAccess sprintAccess;

	// ────────────────────────────────────────
	// search
	// ────────────────────────────────────────

	/**
	 * CQL 문자열로 이슈를 검색한다.
	 * CqlParseException 발생 시 그대로 throw → GlobalExceptionHandler 에서 400 처리.
	 */
	public Page<IssueResponse> search(String cql, Pageable pageable) {
		CqlParseResult result = parse(cql);
		CqlQueryBuilder builder = new CqlQueryBuilder(sprintAccess);

		Specification<SnCiraIssueModel> spec = result.getCondition() != null
			? builder.buildSpec(result.getCondition())
			: Specification.where(null);

		Sort sort = builder.buildSort(result.getOrderBy());
		Pageable effectivePageable = sort.isSorted()
			? PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort)
			: pageable;

		return issueAccess.findAll(spec, effectivePageable).map(this::mapToResponse);
	}

	// ────────────────────────────────────────
	// validate
	// ────────────────────────────────────────

	public CqlValidateResponse validate(String cql) {
		try {
			parse(cql);
			return CqlValidateResponse.builder().valid(true).build();
		} catch (CqlParseException e) {
			return CqlValidateResponse.builder()
				.valid(false)
				.error(e.getMessage())
				.position(e.getPosition())
				.build();
		}
	}

	// ────────────────────────────────────────
	// autocomplete
	// ────────────────────────────────────────

	/**
	 * 커서 위치까지의 CQL 부분 문자열을 분석하여 다음 입력 후보를 반환한다.
	 */
	public CqlAutocompleteResponse autocomplete(String cql, int cursor) {
		String prefix = cql == null ? "" : cql;
		if (cursor >= 0 && cursor < prefix.length()) {
			prefix = prefix.substring(0, cursor);
		}

		List<CqlToken> meaningful;
		try {
			meaningful = new CqlLexer(prefix).tokenize().stream()
				.filter(t -> t.getType() != CqlToken.TokenType.EOF)
				.collect(Collectors.toList());
		} catch (CqlParseException e) {
			return toResponse(fieldSuggestions());
		}

		return toResponse(determineSuggestions(meaningful));
	}

	// ────────────────────────────────────────
	// autocomplete internals
	// ────────────────────────────────────────

	private List<SuggestionItem> determineSuggestions(List<CqlToken> tokens) {
		if (tokens.isEmpty()) {
			return fieldSuggestions();
		}

		CqlToken last = tokens.get(tokens.size() - 1);
		CqlToken.TokenType lastType = last.getType();

		// AND / OR / NOT 직후 → 필드 후보
		if (isConnector(lastType) || lastType == CqlToken.TokenType.NOT) {
			return fieldSuggestions();
		}

		// 연산자 직후 → 값 후보
		if (isOperator(lastType) || lastType == CqlToken.TokenType.IN) {
			return valueSuggestions(findPrecedingField(tokens));
		}

		// IDENTIFIER: 필드명 입력 중이거나 완성된 상태
		if (lastType == CqlToken.TokenType.IDENTIFIER) {
			// 앞 토큰이 없거나 AND/OR/NOT 이면 → 필드명 자동완성
			if (tokens.size() == 1 || isConnector(tokens.get(tokens.size() - 2).getType())
				|| tokens.get(tokens.size() - 2).getType() == CqlToken.TokenType.NOT) {
				return fieldSuggestions();
			}
			// 앞 토큰이 연산자면 → 값 후보 (필드 이름 외 식별자를 값으로 입력 중)
			if (isOperator(tokens.get(tokens.size() - 2).getType())) {
				return valueSuggestions(findPrecedingField(tokens));
			}
			// 앞 토큰이 없거나 시작 → 연산자 후보
			return operatorSuggestions();
		}

		// 값 완성 후 (STRING, NUMBER, FUNCTION, RPAREN) → AND / OR / ORDER BY
		if (lastType == CqlToken.TokenType.STRING
			|| lastType == CqlToken.TokenType.NUMBER
			|| lastType == CqlToken.TokenType.FUNCTION
			|| lastType == CqlToken.TokenType.RPAREN) {
			return connectorSuggestions();
		}

		return fieldSuggestions();
	}

	private boolean isConnector(CqlToken.TokenType type) {
		return type == CqlToken.TokenType.AND || type == CqlToken.TokenType.OR;
	}

	private boolean isOperator(CqlToken.TokenType type) {
		return switch (type) {
			case EQ, NEQ, LT, LTE, GT, GTE, LIKE, NLIKE -> true;
			default -> false;
		};
	}

	private String findPrecedingField(List<CqlToken> tokens) {
		for (int i = tokens.size() - 1; i >= 0; i--) {
			CqlToken t = tokens.get(i);
			if (t.getType() == CqlToken.TokenType.IDENTIFIER && SUPPORTED_FIELDS.contains(t.getValue())) {
				return t.getValue();
			}
		}
		return null;
	}

	private List<SuggestionItem> fieldSuggestions() {
		return SUPPORTED_FIELDS.stream()
			.map(f -> SuggestionItem.builder()
				.text(f).kind("FIELD").description(fieldDescription(f)).build())
			.collect(Collectors.toList());
	}

	private List<SuggestionItem> operatorSuggestions() {
		return OPERATORS.stream()
			.map(op -> SuggestionItem.builder()
				.text(op).kind("OPERATOR").description(operatorDescription(op)).build())
			.collect(Collectors.toList());
	}

	private List<SuggestionItem> valueSuggestions(String field) {
		if (field == null)
			return List.of();
		List<String> candidates = switch (field) {
			case "status" -> STATUS_VALUES;
			case "priority" -> PRIORITY_VALUES;
			case "assignee", "reporter" -> List.of("currentUser()");
			case "sprint" -> List.of("openSprints()");
			default -> List.of();
		};
		return candidates.stream()
			.map(v -> SuggestionItem.builder().text(v).kind("VALUE").description(null).build())
			.collect(Collectors.toList());
	}

	private List<SuggestionItem> connectorSuggestions() {
		return List.of(
			SuggestionItem.builder().text("AND").kind("KEYWORD").description("AND 조건 추가").build(),
			SuggestionItem.builder().text("OR").kind("KEYWORD").description("OR 조건 추가").build(),
			SuggestionItem.builder().text("ORDER BY").kind("KEYWORD").description("정렬 기준 지정").build()
		);
	}

	private String fieldDescription(String field) {
		return switch (field) {
			case "project" -> "프로젝트 ID";
			case "status" -> "이슈 상태 (TODO/IN_PROGRESS/DONE/REVIEW)";
			case "assignee" -> "담당자";
			case "reporter" -> "보고자";
			case "priority" -> "우선순위 (HIGHEST/HIGH/MEDIUM/LOW/LOWEST)";
			case "issueType" -> "이슈 유형 ID";
			case "text" -> "제목/내용 텍스트 검색";
			case "created" -> "생성일시 (yyyy-MM-ddTHH:mm:ss)";
			case "updated" -> "수정일시 (yyyy-MM-ddTHH:mm:ss)";
			case "due" -> "마감일 (yyyy-MM-dd)";
			case "storyPoints" -> "스토리 포인트 (숫자)";
			case "sprint" -> "스프린트 ID";
			default -> null;
		};
	}

	private String operatorDescription(String op) {
		return switch (op) {
			case "=" -> "같음";
			case "!=" -> "다름";
			case "~" -> "포함 (LIKE)";
			case "!~" -> "미포함";
			case "<" -> "미만";
			case "<=" -> "이하";
			case ">" -> "초과";
			case ">=" -> "이상";
			default -> null;
		};
	}

	private CqlAutocompleteResponse toResponse(List<SuggestionItem> items) {
		return CqlAutocompleteResponse.builder().suggestions(items).build();
	}

	// ────────────────────────────────────────
	// internal parse helper
	// ────────────────────────────────────────

	private CqlParseResult parse(String cql) {
		return new CqlParser(new CqlLexer(cql).tokenize()).parse();
	}

	// ────────────────────────────────────────
	// entity → DTO
	// ────────────────────────────────────────

	private IssueResponse mapToResponse(SnCiraIssueModel m) {
		return IssueResponse.builder()
			.id(m.getObjId())
			.issueKey(m.getIssueKey())
			.title(m.getTitle())
			.content(m.getContent())
			.issueTypeId(m.getIssueTypeId())
			.statusId(m.getStatusId())
			.priority(m.getPriority())
			.storyPnt(m.getStoryPnt())
			.projectId(m.getProjectId())
			.sprintId(m.getSprintId())
			.dueDt(m.getDueDt())
			.createdAt(m.getCreatedAt())
			.modifiedAt(m.getModifiedAt())
			.build();
	}
}
