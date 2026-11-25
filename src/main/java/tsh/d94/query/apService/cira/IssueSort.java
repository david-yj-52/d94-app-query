package tsh.d94.app.server.apService.cira;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Sort;

import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Order;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeModel;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusModel;
import tsh.d94.app.server.data.orm.cira.ciraSprint.SnCiraSprintModel;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/**
 * Task 목록 정렬 (설계 5.1). sort=sys:dueDate,asc. ID 로 저장된 필드는 보이는 값 기준
 * (상태 순서·우선순위 심각도·유형/사람 이름·스프린트 시작일). 빈 값은 방향과 상관없이 맨 뒤.
 * sys: 가 아닌 값은 예전처럼 엔티티 속성 이름으로 받는다.
 */
final class IssueSort {

	private IssueSort() {}

	/** 쿼리 전에 막아야 400 이 된다 (스펙 람다 안에서 던지면 Spring 이 감싸 500) */
	static void validate(Sort sort) {
		for (Sort.Order o : sort) {
			if (o.getProperty().startsWith("sys:") && !CiraFieldService.SYSTEM.stream()
				.anyMatch(f -> f.fieldKey().equals(o.getProperty()))) {
				throw new IllegalArgumentException("정렬할 수 없는 필드입니다: " + o.getProperty());
			}
		}
	}

	static List<Order> orders(Root<SnCiraIssueModel> root, CriteriaQuery<?> query, CriteriaBuilder cb, Sort sort) {
		List<Order> out = new ArrayList<>();
		for (Sort.Order o : sort) {
			Expression<?> e = expression(root, query, cb, o.getProperty());
			out.add(cb.asc(cb.selectCase().when(cb.isNull(e), 1).otherwise(0)));
			out.add(o.isAscending() ? cb.asc(e) : cb.desc(e));
		}
		out.add(cb.asc(root.get("objId"))); // 페이지 경계 안정
		return out;
	}

	private static Expression<?> expression(Root<SnCiraIssueModel> root, CriteriaQuery<?> query, CriteriaBuilder cb,
		String key) {
		if (!key.startsWith("sys:")) {
			return root.get(key);
		}
		return switch (key) {
			case "sys:type" -> lookup(root, query, cb, SnCiraCiraIssueTypeModel.class, "typeNm", "issueTypeId");
			// ponytail: 키 문자열은 "T-10" < "T-2" 라 생성 순서(= 키 번호 순서)로 정렬
			case "sys:key" -> root.get("createdAt");
			case "sys:summary" -> root.get("title");
			case "sys:status" -> lookup(root, query, cb, SnCiraIssueStatusModel.class, "sortOrd", "statusId");
			case "sys:priority" -> cb.selectCase(root.get("priority"))
				.when("HIGHEST", 1).when("HIGH", 2).when("MEDIUM", 3).when("LOW", 4).when("LOWEST", 5)
				.otherwise((Integer)null);
			case "sys:assignee" -> lookup(root, query, cb, GsUserModel.class, "userNm", "assigneeId");
			case "sys:reporter" -> lookup(root, query, cb, GsUserModel.class, "userNm", "reporterId");
			case "sys:storyPoints" -> root.get("storyPnt");
			case "sys:sprint" -> lookup(root, query, cb, SnCiraSprintModel.class, "startDt", "sprintId");
			case "sys:dueDate" -> root.get("dueDt");
			case "sys:startedAt" -> root.get("startedAt");
			case "sys:resolvedAt" -> root.get("resolvedAt");
			case "sys:createdAt" -> root.get("createdAt");
			case "sys:modifiedAt" -> root.get("modifiedAt");
			default -> throw new IllegalArgumentException("정렬할 수 없는 필드입니다: " + key);
		};
	}

	/** (select entity.valueAttr from entity where entity.objId = issue.fkAttr) */
	@SuppressWarnings({"unchecked", "rawtypes"})
	private static <T> Expression<?> lookup(Root<SnCiraIssueModel> root, CriteriaQuery<?> query, CriteriaBuilder cb,
		Class<T> entity, String valueAttr, String fkAttr) {
		Subquery sq = query.subquery(Comparable.class);
		Root<T> r = sq.from(entity);
		sq.select(r.get(valueAttr)).where(cb.equal(r.get("objId"), root.get(fkAttr)));
		return sq;
	}
}
