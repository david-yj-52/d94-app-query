package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Field;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.ProjectType;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.SaveCustomField;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.SaveType;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.TypeField;
import tsh.d94.app.server.apService.cira.dto.CreateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.IssueFilterRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.cira.dto.UpdateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.UpdateProjectRequest;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/** 프로젝트 타입별 필드 세트 · 사용자 정의 필드 · 사람별 컬럼 · 값 · 정렬 (설계 2026-11-21 cira-task-columns) */
@IntegrationTest
class CiraFieldIT {

	@Autowired
	private CiraFieldService fields;
	@Autowired
	private IssueService issues;
	@Autowired
	private ProjectService projects;
	@Autowired
	private CiraFixtures fixtures;
	@Autowired
	private JdbcTemplate jdbc;

	private ProjectType type;
	private ProjectResponse project;

	@BeforeEach
	void setUp() {
		String email = CiraFixtures.uniqueEmail("owner");
		TestContexts.login(email);
		fixtures.user(email);
		type = fields.createType(new SaveType("영업-" + UUID.randomUUID().toString().substring(0, 6), null));
		UpdateProjectRequest req = new UpdateProjectRequest();
		project = fixtures.project("KANBAN");
		req.setProjectTypeId(type.id());
		project = projects.updateProject(project.getId(), req);
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private List<String> keys(List<Field> fs) {
		return fs.stream().map(Field::fieldKey).toList();
	}

	private Field globalSelect(String name, boolean required) {
		return fields.createGlobalField(new SaveCustomField(name + "-" + UUID.randomUUID().toString().substring(0, 6), "SELECT",
			required, List.of("A", "B")));
	}

	@Test
	@DisplayName("V590 이관: SCRUM 타입은 기본 세트 5개, 옛 이름(projectType)으로 만든 프로젝트도 타입 ID 로 연결되고 보드는 칸반")
	void migration_seedsTypes_andLegacyNameResolves() {
		ProjectType scrum = fields.types().stream().filter(t -> t.typeNm().equals("SCRUM")).findFirst().orElseThrow();
		assertThat(scrum.fields()).extracting(TypeField::fieldKey)
			.containsExactly("sys:priority", "sys:assignee", "sys:modifiedAt", "sys:sprint", "sys:storyPoints");
		ProjectResponse p = fixtures.project("SCRUM");
		assertThat(p.getProjectTypeId()).isEqualTo(scrum.id());
		assertThat(jdbc.queryForObject("select board_type from sn_cira_board where project_id=?", String.class, p.getId()))
			.isEqualTo("KANBAN");
		assertThat(jdbc.queryForList("select type_nm from sn_cira_cira_issue_type", String.class)).contains("Issue")
			.doesNotContain("Task");
	}

	@Test
	@DisplayName("유효 필드 = 고정 → 타입 세트 → 추가 필드. 타입 세트 변경은 기존 프로젝트에 즉시 반영되고, 프로젝트가 물려받은 필드를 뺄 수 없다")
	void effectiveFields_followTypeAndCannotDropInherited() {
		assertThat(keys(fields.projectFields(project.getId()))).containsExactly(
			"sys:type", "sys:key", "sys:summary", "sys:status", "sys:priority", "sys:assignee", "sys:modifiedAt");

		Field customer = globalSelect("고객사", false);
		fields.replaceTypeFields(type.id(), List.of(new TypeField("sys:dueDate", true), new TypeField(customer.fieldKey(), true),
			new TypeField("sys:summary", true)));
		fields.replaceProjectFields(project.getId(), List.of("sys:storyPoints", "sys:dueDate"));

		List<Field> eff = fields.projectFields(project.getId());
		assertThat(keys(eff)).containsExactly("sys:type", "sys:key", "sys:summary", "sys:status", "sys:dueDate",
			customer.fieldKey(), "sys:storyPoints");
		assertThat(eff.get(4).source()).isEqualTo("type"); // 추가 목록에도 있지만 타입 쪽이 이긴다

		// 이름을 바꿔도 연결 유지
		fields.updateType(type.id(), new SaveType(type.typeNm() + "2", null));
		assertThat(projects.getProject(project.getId()).getProjectTypeNm()).isEqualTo(type.typeNm() + "2");
	}

	@Test
	@DisplayName("사람별 컬럼: 저장 후 관리자가 타입 세트에 넣은 기본 표시 필드는 뒤에 붙고, 죽은 키는 걸러지고, 키·요약은 항상 보인다")
	void columnPref_newDefaultVisibleAppended_deadKeysFiltered() {
		assertThat(fields.columnPref(project.getId()).fieldKeys()).containsExactly(
			"sys:type", "sys:key", "sys:summary", "sys:status", "sys:priority", "sys:assignee", "sys:modifiedAt");

		Field customer = globalSelect("고객사", false);
		fields.replaceTypeFields(type.id(), List.of(new TypeField("sys:priority", true), new TypeField(customer.fieldKey(), false)));
		fields.saveColumnPref(project.getId(), List.of("sys:status", customer.fieldKey()));
		assertThat(fields.columnPref(project.getId()).fieldKeys())
			.containsExactly("sys:key", "sys:summary", "sys:status", customer.fieldKey());

		fields.replaceTypeFields(type.id(), List.of(new TypeField("sys:priority", true), new TypeField(customer.fieldKey(), false),
			new TypeField("sys:reporter", true), new TypeField("sys:sprint", false)));
		fields.deleteGlobalField(customer.fieldKey().substring(3));
		assertThat(fields.columnPref(project.getId()).fieldKeys())
			.containsExactly("sys:key", "sys:summary", "sys:status", "sys:reporter");

		assertThat(fields.resetColumnPref(project.getId()).fieldKeys()).contains("sys:priority", "sys:reporter")
			.doesNotContain("sys:sprint");
	}

	@Test
	@DisplayName("Task 값: 필수 누락·다른 프로젝트 키·선택지 밖 값은 400, 저장한 값은 목록·상세에 붙고, 필드 삭제 시 값·참조도 지워진다")
	void customValues_validateStoreAndCascade() {
		Field stage = fields.createProjectField(project.getId(),
			new SaveCustomField("계약 단계", "SELECT", true, List.of("리드", "계약")));
		Field amount = fields.createProjectField(project.getId(), new SaveCustomField("금액", "NUMBER", false, null));
		String statusId = fixtures.statusId(project.getId(), "To Do");

		CreateIssueRequest req = CreateIssueRequest.builder().title("t").statusId(statusId).build();
		assertThatThrownBy(() -> issues.createIssue(project.getId(), req)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("계약 단계");
		req.setCustomFields(Map.of(stage.fieldKey(), "없는값"));
		assertThatThrownBy(() -> issues.createIssue(project.getId(), req)).isInstanceOf(IllegalArgumentException.class);
		req.setCustomFields(Map.of(stage.fieldKey(), "리드", "cf:" + UUID.randomUUID(), "x"));
		assertThatThrownBy(() -> issues.createIssue(project.getId(), req)).isInstanceOf(IllegalArgumentException.class);

		req.setCustomFields(Map.of(stage.fieldKey(), "리드", amount.fieldKey(), 1200.5));
		IssueResponse created = issues.createIssue(project.getId(), req);
		assertThat(created.getCustomFields()).containsEntry(stage.fieldKey(), "리드");

		Map<String, Object> clear = new HashMap<>();
		clear.put(amount.fieldKey(), null);
		issues.updateIssue(created.getId(), UpdateIssueRequest.builder().customFields(clear).build());
		IssueResponse listed = issues.listIssues(project.getId(), new IssueFilterRequest(), PageRequest.of(0, 20)).getContent().get(0);
		assertThat(listed.getCustomFields()).containsOnlyKeys(stage.fieldKey());

		fields.deleteProjectField(project.getId(), stage.fieldKey().substring(3));
		assertThat(jdbc.queryForObject("select count(*) from sn_cira_issue_cf_value where issue_id=?", Integer.class,
			created.getId())).isZero();
		assertThat(keys(fields.projectFields(project.getId()))).doesNotContain(stage.fieldKey());
	}

	@Test
	@DisplayName("정렬: 우선순위는 심각도 순, 상태는 워크플로 순서, 빈 마감일은 방향과 상관없이 맨 뒤")
	void sort_byDisplayOrder_nullsLast() {
		String todo = fixtures.statusId(project.getId(), "To Do");
		String done = fixtures.statusId(project.getId(), "Done");
		issues.createIssue(project.getId(), CreateIssueRequest.builder().title("low").statusId(done).priority("LOW")
			.dueDt(java.time.LocalDate.of(2026, 1, 2)).build());
		issues.createIssue(project.getId(), CreateIssueRequest.builder().title("highest").statusId(todo).priority("HIGHEST").build());
		issues.createIssue(project.getId(), CreateIssueRequest.builder().title("medium").statusId(todo).priority("MEDIUM")
			.dueDt(java.time.LocalDate.of(2026, 1, 1)).build());

		assertThat(titles(Sort.by("sys:priority"))).containsExactly("highest", "medium", "low");
		assertThat(titles(Sort.by(Sort.Direction.DESC, "sys:status")).get(0)).isEqualTo("low");
		assertThat(titles(Sort.by("sys:dueDate"))).containsExactly("medium", "low", "highest");
		assertThat(titles(Sort.by(Sort.Direction.DESC, "sys:dueDate"))).containsExactly("low", "medium", "highest");
		assertThatThrownBy(() -> titles(Sort.by("sys:nope"))).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("쓰는 프로젝트가 있는 타입은 삭제할 수 없다 (409)")
	void deleteType_inUse_conflict() {
		assertThatThrownBy(() -> fields.deleteType(type.id())).isInstanceOf(ResponseStatusException.class)
			.hasMessageContaining("409");
	}

	private List<String> titles(Sort sort) {
		return issues.listIssues(project.getId(), new IssueFilterRequest(), PageRequest.of(0, 20, sort)).getContent().stream()
			.map(IssueResponse::getTitle).toList();
	}
}
