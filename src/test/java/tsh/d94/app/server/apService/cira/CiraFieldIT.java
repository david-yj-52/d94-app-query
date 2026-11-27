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
		project = fixtures.project(type.typeNm()); // 이 프리셋으로 기본 공간을 만든다
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private List<String> keys(List<Field> fs) {
		return fs.stream().map(Field::fieldKey).toList();
	}

	private static String uniq(String name) {
		return name + "-" + UUID.randomUUID().toString().substring(0, 6);
	}

	private Field globalSelect(String name, boolean required) {
		return fields.createGlobalField(new SaveCustomField(uniq(name), "SELECT", required, List.of("A", "B"), null));
	}

	private Field typeSelect(String name) {
		return fields.createTypeField(type.id(), new SaveCustomField(uniq(name), "SELECT", false, List.of("A", "B"), null));
	}

	private String id(Field f) {
		return f.fieldKey().substring(3);
	}

	/** 프로젝트에 일반 멤버(DEVELOPER)를 추가하고 그 사람으로 로그인. 소유자 이메일을 돌려준다 */
	private String loginAsNewMember() {
		String owner = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getName();
		var member = fixtures.member(project.getId(), fixtures.user(CiraFixtures.uniqueEmail("member")));
		TestContexts.login(member.getEmail());
		return owner;
	}

	@Test
	@DisplayName("V590 이관: SCRUM 타입은 기본 세트 5개, 옛 이름(projectType)으로 만든 프로젝트도 타입 ID 로 연결되고 보드는 칸반")
	void migration_seedsTypes_andLegacyNameResolves() {
		ProjectType scrum = fields.types().stream().filter(t -> t.typeNm().equals("SCRUM")).findFirst().orElseThrow();
		assertThat(scrum.fields()).extracting(TypeField::fieldKey)
			.containsExactly("sys:priority", "sys:assignee", "sys:modifiedAt", "sys:sprint", "sys:storyPoints");
		ProjectResponse p = fixtures.project("SCRUM");
		assertThat(jdbc.queryForObject("select PRESET_TYPE_ID from SN_CIRA_SPACE where PROJECT_ID=? and IS_DEFAULT_YN='Y'",
			String.class, p.getId())).isEqualTo(scrum.id());
		assertThat(jdbc.queryForObject("select board_type from sn_cira_board where project_id=?", String.class, p.getId()))
			.isEqualTo("SCRUM"); // V650: 타입 = 기본 작업 공간 프리셋, 보드 유형도 프리셋에서
		assertThat(jdbc.queryForList("select type_nm from sn_cira_cira_issue_type", String.class)).contains("Issue")
			.doesNotContain("Task");
	}

	@Test
	@DisplayName("V650: 유효 필드 = 고정 → 공간 필드 세트. 타입(프리셋)은 만들 때 1회 복사 — 이후 타입 변경과 무관하고, 공간이 프리셋 필드를 자유롭게 뺀다")
	void effectiveFields_presetCopiedOnce_spaceOwnsSet() {
		assertThat(keys(fields.projectFields(project.getId()))).containsExactly(
			"sys:type", "sys:key", "sys:summary", "sys:status", "sys:priority", "sys:assignee", "sys:modifiedAt");

		Field customer = typeSelect("고객사");
		fields.replaceTypeFields(type.id(), List.of(new TypeField("sys:dueDate", true), new TypeField(customer.fieldKey(), true)));
		assertThat(keys(fields.projectFields(project.getId()))).doesNotContain("sys:dueDate", customer.fieldKey());

		List<Field> eff = fields.replaceProjectFields(project.getId(), List.of("sys:storyPoints", "sys:dueDate", "sys:summary"));
		assertThat(keys(eff)).containsExactly("sys:type", "sys:key", "sys:summary", "sys:status", "sys:storyPoints", "sys:dueDate");
		assertThat(eff.get(4).source()).isEqualTo("project");

		// 프리셋 사용 수 = 이 프리셋으로 만든 작업 공간 수
		assertThat(fields.types().stream().filter(t -> t.id().equals(type.id())).findFirst().orElseThrow().spaceCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("사람별 컬럼: 저장 후 공간 세트에 새로 생긴 기본 표시 필드는 뒤에 붙고, 죽은 키는 걸러지고, 키·요약은 항상 보인다")
	void columnPref_newDefaultVisibleAppended_deadKeysFiltered() {
		assertThat(fields.columnPref(project.getId()).fieldKeys()).containsExactly(
			"sys:type", "sys:key", "sys:summary", "sys:status", "sys:priority", "sys:assignee", "sys:modifiedAt");

		Field customer = fields.createProjectField(project.getId(), new SaveCustomField("고객사", "TEXT", false, null, null));
		fields.saveColumnPref(project.getId(), List.of("sys:status", customer.fieldKey()));
		assertThat(fields.columnPref(project.getId()).fieldKeys())
			.containsExactly("sys:key", "sys:summary", "sys:status", customer.fieldKey());

		fields.replaceProjectFields(project.getId(), List.of("sys:priority", "sys:reporter"));
		assertThat(fields.columnPref(project.getId()).fieldKeys())
			.containsExactly("sys:key", "sys:summary", "sys:status", "sys:reporter");

		assertThat(fields.resetColumnPref(project.getId()).fieldKeys()).contains("sys:priority", "sys:reporter");
	}

	@Test
	@DisplayName("Task 값: 필수 누락·다른 프로젝트 키·선택지 밖 값은 400, 저장한 값은 목록·상세에 붙고, 필드 삭제 시 값·참조도 지워진다")
	void customValues_validateStoreAndCascade() {
		Field stage = fields.createProjectField(project.getId(),
			new SaveCustomField("계약 단계", "SELECT", true, List.of("리드", "계약"), null));
		Field amount = fields.createProjectField(project.getId(), new SaveCustomField("금액", "NUMBER", false, null, null));
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
	@DisplayName("타입 세트에는 전역 필드를 바로 넣을 수 없다 (먼저 타입으로 가져온다)")
	void typeSet_rejectsGlobalField() {
		Field g = globalSelect("고객사", false);
		assertThatThrownBy(() -> fields.replaceTypeFields(type.id(), List.of(new TypeField(g.fieldKey(), true))))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("전역 필드는 먼저 타입으로 가져와야 세트에 넣을 수 있습니다");
	}

	@Test
	@DisplayName("전역 필드를 만들며 넣을 타입을 고르면 그 타입의 복사본이 생기고, 복사본 수가 보인다")
	void createGlobal_copyToTypes_createsTypeCopies() {
		Field g = fields.createGlobalField(new SaveCustomField(uniq("고객사"), "TEXT", false, null, List.of(type.id())));
		List<Field> typeFields = fields.catalog().typeFields().get(type.id());
		assertThat(typeFields).extracting(Field::name).contains(g.name());
		assertThat(typeFields).filteredOn(f -> f.name().equals(g.name())).first()
			.satisfies(f -> {
				assertThat(f.scope()).isEqualTo("type");
				assertThat(f.sourceFieldId()).isEqualTo(id(g));
			});
		assertThat(fields.catalog().copies()).containsEntry(id(g), 1);
		assertThat(fields.usage(id(g)).copies()).isEqualTo(1);
	}

	@Test
	@DisplayName("멤버가 전역 필드를 가져오면 프로젝트 복사본이 생겨 바로 적용되고, 같은 것을 다시 가져오면 400")
	void importToProject_copiesAndApplies() {
		Field g = globalSelect("고객사", false);
		loginAsNewMember();
		Field copy = fields.importToProject(project.getId(), id(g));
		assertThat(copy.scope()).isEqualTo("project");
		assertThat(copy.sourceName()).isEqualTo(g.name());
		assertThat(copy.canEdit()).isTrue();
		assertThat(keys(fields.projectFields(project.getId()))).contains(copy.fieldKey()).doesNotContain(g.fieldKey());
		assertThatThrownBy(() -> fields.importToProject(project.getId(), id(g)))
			.isInstanceOf(IllegalArgumentException.class).hasMessageContaining("이 프로젝트에 같은 이름의 필드가 있습니다");
		assertThat(fields.library(project.getId())).filteredOn(i -> i.field().fieldKey().equals(g.fieldKey())).first()
			.satisfies(i -> assertThat(i.importedAs()).isEqualTo(copy.fieldKey()));
	}

	@Test
	@DisplayName("다른 프로젝트의 필드는 가져올 수 없다 (전역·프리셋 필드만)")
	void importToProject_otherProjectField_notFound() {
		ProjectResponse other = fixtures.project(type.typeNm());
		Field theirs = fields.createProjectField(other.getId(), new SaveCustomField(uniq("남의"), "TEXT", false, null, null));
		assertThatThrownBy(() -> fields.importToProject(project.getId(), id(theirs)))
			.isInstanceOf(jakarta.persistence.EntityNotFoundException.class);
	}

	@Test
	@DisplayName("복사본과 원본은 서로 독립: 어느 쪽 이름을 바꿔도 다른 쪽은 그대로")
	void copy_isIndependent() {
		Field g = globalSelect("고객사", false);
		Field copy = fields.importToProject(project.getId(), id(g));
		fields.updateProjectField(project.getId(), id(copy), new SaveCustomField("우리 고객사", null, false, List.of("A", "B"), null));
		fields.updateGlobalField(id(g), new SaveCustomField(g.name() + "2", null, false, List.of("A", "B"), null));
		assertThat(fields.catalog().custom()).filteredOn(f -> f.fieldKey().equals(g.fieldKey())).first()
			.extracting(Field::name).isEqualTo(g.name() + "2");
		assertThat(fields.projectFields(project.getId())).filteredOn(f -> f.fieldKey().equals(copy.fieldKey())).first()
			.extracting(Field::name).isEqualTo("우리 고객사");
	}

	@Test
	@DisplayName("전역 원본을 지워도 복사본과 그 값은 남고, 출처 이름은 비어 있다")
	void deleteGlobal_keepsCopiesAndValues() {
		Field g = fields.createGlobalField(new SaveCustomField(uniq("메모"), "TEXT", false, null, null));
		Field copy = fields.importToProject(project.getId(), id(g));
		String issueId = issues.createIssue(project.getId(), CreateIssueRequest.builder().title("t")
			.statusId(fixtures.statusId(project.getId(), "To Do")).customFields(Map.of(copy.fieldKey(), "남아야 함")).build()).getId();
		fields.deleteGlobalField(id(g));
		assertThat(issues.getIssue(issueId).getCustomFields()).containsEntry(copy.fieldKey(), "남아야 함");
		Field after = fields.projectFields(project.getId()).stream().filter(f -> f.fieldKey().equals(copy.fieldKey())).findFirst()
			.orElseThrow();
		assertThat(after.sourceFieldId()).isEqualTo(id(g));
		assertThat(after.sourceName()).isNull();
	}

	@Test
	@DisplayName("멤버 A 가 만든 프로젝트 필드는 멤버 B 가 지울 수 없고, 프로젝트 ADMIN 은 지울 수 있다")
	void projectField_editPermission() {
		String owner = loginAsNewMember();
		Field mine = fields.createProjectField(project.getId(), new SaveCustomField(uniq("내 컬럼"), "TEXT", false, null, null));
		assertThat(mine.createdBy()).isNotNull();
		var other = fixtures.user(CiraFixtures.uniqueEmail("other"));
		TestContexts.login(owner);
		fixtures.member(project.getId(), other);
		TestContexts.login(other.getEmail());
		assertThat(fields.projectFields(project.getId())).filteredOn(f -> f.fieldKey().equals(mine.fieldKey())).first()
			.extracting(Field::canEdit).isEqualTo(false);
		assertThatThrownBy(() -> fields.deleteProjectField(project.getId(), id(mine)))
			.isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
		TestContexts.login(owner);
		fields.deleteProjectField(project.getId(), id(mine));
		assertThat(keys(fields.projectFields(project.getId()))).doesNotContain(mine.fieldKey());
	}

	@Test
	@DisplayName("V660: 프로젝트에는 타입이 없다 — 다른 프리셋의 필드도 가져오고, 프리셋을 지워도 공간 필드는 남는다")
	void presetIsOnlyATemplate() {
		ProjectType other = fields.createType(new SaveType(uniq("기타"), null));
		Field otherField = fields.createTypeField(other.id(), new SaveCustomField(uniq("치수"), "TEXT", false, null, null));
		assertThat(fields.library(project.getId())).extracting(i -> i.field().fieldKey()).contains(otherField.fieldKey());
		Field imported = fields.importToProject(project.getId(), id(otherField));
		assertThat(keys(fields.projectFields(project.getId()))).contains(imported.fieldKey());

		Field preset = typeSelect("고객사");
		fields.replaceTypeFields(type.id(), List.of(new TypeField(preset.fieldKey(), true)));
		ProjectResponse fresh = fixtures.project(type.typeNm());
		List<String> freshKeys = keys(fields.projectFields(fresh.getId()));
		assertThat(freshKeys).doesNotContain(preset.fieldKey()).hasSize(5); // 고정 4 + 복사본 1

		fields.deleteType(type.id()); // 이 프리셋으로 만든 공간이 있어도 지울 수 있다
		assertThat(keys(fields.projectFields(fresh.getId()))).isEqualTo(freshKeys);
		assertThat(jdbc.queryForObject("select count(*) from SN_CIRA_SPACE where PRESET_TYPE_ID=?", Integer.class, type.id())).isZero();
	}

	private List<String> titles(Sort sort) {
		return issues.listIssues(project.getId(), new IssueFilterRequest(), PageRequest.of(0, 20, sort)).getContent().stream()
			.map(IssueResponse::getTitle).toList();
	}
}
