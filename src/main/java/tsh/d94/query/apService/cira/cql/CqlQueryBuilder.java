package tsh.d94.app.server.apService.cira.cql;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.core.context.SecurityContextHolder;

import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraSprint.SnCiraSprintAccess;

import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;

/**
 * CQL AST(CqlParseResult) → JPA Specification<SnCiraIssueModel> 변환기.
 *
 * 지원 필드:
 *   project(projectId), status(statusId), assignee(assigneeId),
 *   reporter(reporterId), priority, issueType(issueTypeId),
 *   text(title/content FTS-like), created(createdAt), updated(modifiedAt),
 *   due(dueDt), storyPoints(storyPnt), sprint(sprintId)
 *
 * 지원 함수:
 *   currentUser() → SecurityContext 현재 유저 이름
 *   openSprints() → ACTIVE 스프린트 ID 목록 (IN 절에서 사용)
 */
@RequiredArgsConstructor
public class CqlQueryBuilder {

	private static final Set<String> NUMERIC_FIELDS = Set.of("storyPoints", "storyPnt");
	private static final Set<String> DATE_FIELDS = Set.of("created", "updated", "due");

	private final SnCiraSprintAccess sprintAccess;

	/** 조건 Specification 생성 */
	public Specification<SnCiraIssueModel> buildSpec(CqlNode node) {
		return (root, query, cb) -> buildPredicate(node, root, query, cb);
	}

	/** ORDER BY → Spring Data Sort 변환 */
	public Sort buildSort(List<CqlParseResult.OrderItem> items) {
		if (items == null || items.isEmpty())
			return Sort.unsorted();
		List<Sort.Order> orders = items.stream()
			.map(item -> {
				String col = mapFieldToColumn(item.getField());
				return item.isAscending() ? Sort.Order.asc(col) : Sort.Order.desc(col);
			})
			.collect(Collectors.toList());
		return Sort.by(orders);
	}

	// ────────────────────────────────────────
	// predicate builder
	// ────────────────────────────────────────

	private jakarta.persistence.criteria.Predicate buildPredicate(
		CqlNode node,
		jakarta.persistence.criteria.Root<SnCiraIssueModel> root,
		jakarta.persistence.criteria.CriteriaQuery<?> query,
		jakarta.persistence.criteria.CriteriaBuilder cb
	) {
		return switch (node) {
			case CqlNode.LogicalNode ln -> {
				Predicate left = buildPredicate(ln.left(), root, query, cb);
				Predicate right = buildPredicate(ln.right(), root, query, cb);
				yield "OR".equals(ln.operator()) ? cb.or(left, right) : cb.and(left, right);
			}
			case CqlNode.NotNode nn -> cb.not(buildPredicate(nn.expr(), root, query, cb));

			case CqlNode.InNode in -> {
				String col = mapFieldToColumn(in.field());
				List<String> resolved = resolveValues(in.values());
				Predicate pred = root.get(col).in(resolved);
				yield in.negated() ? pred.not() : pred;
			}
			case CqlNode.ComparisonNode cn -> buildComparison(cn, root, cb);
		};
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private jakarta.persistence.criteria.Predicate buildComparison(
		CqlNode.ComparisonNode cn,
		jakarta.persistence.criteria.Root<SnCiraIssueModel> root,
		jakarta.persistence.criteria.CriteriaBuilder cb
	) {
		String col = mapFieldToColumn(cn.field());
		String value = resolveValue(cn.value());

		// text 필드: LIKE 검색 (title OR content)
		if ("text".equals(cn.field())) {
			String pattern = "%" + value + "%";
			return cb.or(
				cb.like(cb.lower(root.get("title")), pattern.toLowerCase()),
				cb.like(cb.lower(root.get("content")), pattern.toLowerCase())
			);
		}

		// 날짜 필드
		if (DATE_FIELDS.contains(cn.field())) {
			return buildDatePredicate(cn.field(), cn.operator(), value, root, cb);
		}

		// 숫자 필드
		if (NUMERIC_FIELDS.contains(cn.field())) {
			BigDecimal num = new BigDecimal(value);
			jakarta.persistence.criteria.Expression<BigDecimal> expr = root.get(col);
			return switch (cn.operator()) {
				case "=" -> cb.equal(expr, num);
				case "!=" -> cb.notEqual(expr, num);
				case "<" -> cb.lessThan(expr, num);
				case "<=" -> cb.lessThanOrEqualTo(expr, num);
				case ">" -> cb.greaterThan(expr, num);
				case ">=" -> cb.greaterThanOrEqualTo(expr, num);
				default -> throw new CqlParseException("storyPoints 필드는 수치 비교만 지원합니다.");
			};
		}

		// 문자열 필드
		jakarta.persistence.criteria.Expression<String> expr = root.get(col);
		return switch (cn.operator()) {
			case "=" -> cb.equal(expr, value);
			case "!=" -> cb.notEqual(expr, value);
			case "~" -> cb.like(cb.lower(expr), "%" + value.toLowerCase() + "%");
			case "!~" -> cb.notLike(cb.lower(expr), "%" + value.toLowerCase() + "%");
			default -> throw new CqlParseException("문자열 필드에 지원되지 않는 연산자: " + cn.operator());
		};
	}

	@SuppressWarnings("unchecked")
	private jakarta.persistence.criteria.Predicate buildDatePredicate(
		String field, String op, String value,
		jakarta.persistence.criteria.Root<SnCiraIssueModel> root,
		jakarta.persistence.criteria.CriteriaBuilder cb
	) {
		String col = mapFieldToColumn(field);
		if ("due".equals(field)) {
			// dueDt: LocalDate
			LocalDate date = LocalDate.parse(value);
			jakarta.persistence.criteria.Expression<LocalDate> expr = root.get(col);
			return switch (op) {
				case "=" -> cb.equal(expr, date);
				case "!=" -> cb.notEqual(expr, date);
				case "<" -> cb.lessThan(expr, date);
				case "<=" -> cb.lessThanOrEqualTo(expr, date);
				case ">" -> cb.greaterThan(expr, date);
				case ">=" -> cb.greaterThanOrEqualTo(expr, date);
				default -> throw new CqlParseException("날짜 필드에 지원되지 않는 연산자: " + op);
			};
		} else {
			// createdAt / modifiedAt: LocalDateTime
			LocalDateTime dt = LocalDateTime.parse(value);
			jakarta.persistence.criteria.Expression<LocalDateTime> expr = root.get(col);
			return switch (op) {
				case "=" -> cb.equal(expr, dt);
				case "!=" -> cb.notEqual(expr, dt);
				case "<" -> cb.lessThan(expr, dt);
				case "<=" -> cb.lessThanOrEqualTo(expr, dt);
				case ">" -> cb.greaterThan(expr, dt);
				case ">=" -> cb.greaterThanOrEqualTo(expr, dt);
				default -> throw new CqlParseException("날짜 필드에 지원되지 않는 연산자: " + op);
			};
		}
	}

	// ────────────────────────────────────────
	// field name mapping
	// ────────────────────────────────────────

	private String mapFieldToColumn(String field) {
		return switch (field) {
			case "project" -> "projectId";
			case "status" -> "statusId";
			case "assignee" -> "assigneeId";
			case "reporter" -> "reporterId";
			case "priority" -> "priority";
			case "issueType" -> "issueTypeId";
			case "text" -> "title";          // 복합 처리: title + content
			case "created" -> "createdAt";
			case "updated" -> "modifiedAt";
			case "due" -> "dueDt";
			case "storyPoints" -> "storyPnt";
			case "sprint" -> "sprintId";
			default -> field;            // 그대로 전달
		};
	}

	// ────────────────────────────────────────
	// function resolution
	// ────────────────────────────────────────

	private String resolveValue(String value) {
		if ("currentUser".equals(value)) {
			return SecurityContextHolder.getContext().getAuthentication().getName();
		}
		return value;
	}

	private List<String> resolveValues(List<String> values) {
		List<String> result = new ArrayList<>();
		for (String v : values) {
			if ("openSprints".equals(v)) {
				// Active 상태 스프린트 ID 조회 (Access 계층 경유)
				List<String> activeIds = sprintAccess.findAllActive()
					.stream()
					.map(s -> s.getObjId())
					.collect(Collectors.toList());
				result.addAll(activeIds);
			} else {
				result.add(resolveValue(v));
			}
		}
		return result;
	}
}
