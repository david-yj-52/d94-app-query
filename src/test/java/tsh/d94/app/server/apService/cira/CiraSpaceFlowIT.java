package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;

import tsh.d94.app.server.apService.cira.CiraDocsService.DocRuleDto;
import tsh.d94.app.server.apService.cira.CiraDocsService.ImportRulesRequest;
import tsh.d94.app.server.apService.cira.CiraDocsService.PromoteRequest;
import tsh.d94.app.server.apService.cira.IssueService.FeatureNode;
import tsh.d94.app.server.apService.cira.dto.AddStatusRequest;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.Field;
import tsh.d94.app.server.apService.cira.dto.CreateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.cira.dto.SpaceDto.SaveSpace;
import tsh.d94.app.server.apService.cira.dto.SpaceDto.Space;
import tsh.d94.app.server.apService.cira.dto.UpdateIssueRequest;
import tsh.d94.app.server.apService.cira.dto.WorkflowStatusResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.VaultResponse;
import tsh.d94.app.server.apService.docs.DocsException;
import tsh.d94.app.server.apService.docs.DocsNoteService;
import tsh.d94.app.server.apService.docs.DocsVaultService;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/** V630: 작업 공간(프리셋 복사·공간 관리자 자율) · 2단계 Feature(공통 우선) · Docs 단일 원본 · 3단계 산출물 규칙 */
@IntegrationTest
class CiraSpaceFlowIT {

	@Autowired CiraFixtures fixtures;
	@Autowired SpaceService spaces;
	@Autowired ProjectService projects;
	@Autowired IssueService issues;
	@Autowired CiraFieldService fields;
	@Autowired CiraDocsService ciraDocs;
	@Autowired DocsNoteService notes;
	@Autowired DocsVaultService vaults;
	@Autowired JdbcTemplate jdbc;

	private String adminEmail;
	private GsUserModel dev;
	private ProjectResponse project;
	private Space general;

	@BeforeEach
	void setUp() {
		adminEmail = CiraFixtures.uniqueEmail("spadmin");
		TestContexts.login(adminEmail);
		fixtures.user(adminEmail);
		dev = fixtures.user(CiraFixtures.uniqueEmail("spdev"));
		project = fixtures.project("KANBAN");
		fixtures.member(project.getId(), dev);
		general = spaces.list(project.getId()).get(0);
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	@Test
	void spaces_presetCopy_spaceAdminAutonomy() {
		assertThat(general.isDefault()).isTrue();
		assertThat(general.name()).isEqualTo(SpaceService.DEFAULT_NAME);

		String scrum = jdbc.queryForObject("select OBJ_ID from SN_CIRA_PROJECT_TYPE where TENANT='TSH' and TYPE_NM='SCRUM'", String.class);
		Space hw = spaces.create(project.getId(), new SaveSpace("Hardware", null, null, scrum, dev.getObjId(), null));
		Space etc = spaces.create(project.getId(), new SaveSpace("별도 공간", "ETC", null, null, null, null));
		assertThat(hw.key()).isEqualTo("HARDWARE");
		assertThat(statusNames(hw)).containsExactly("To Do", "In Progress", "In Review", "Done");
		assertThat(statusNames(etc)).containsExactly("To Do", "In Progress", "Done");
		assertThat(jdbc.queryForObject("select BOARD_TYPE from SN_CIRA_BOARD where SPACE_ID=?", String.class, hw.id())).isEqualTo("SCRUM");
		assertThatThrownBy(() -> spaces.create(project.getId(), new SaveSpace("Hardware", "HW2", null, null, null, null)))
			.isInstanceOf(IllegalStateException.class);

		// 공간 관리자(dev)는 자기 공간만: 상태 추가·필드 세트(프리셋 필드도) 자유롭게, 기본 공간은 못 바꾼다
		TestContexts.login(dev.getEmail());
		projects.addWorkflowStatus(project.getId(), hw.id(), status("Soldering"));
		assertThat(statusNames(hw)).contains("Soldering");
		assertThatThrownBy(() -> projects.addWorkflowStatus(project.getId(), general.id(), status("X")))
			.isInstanceOf(AccessDeniedException.class);
		List<Field> hwFields = fields.replaceProjectFields(project.getId(), hw.id(), List.of("sys:dueDate"));
		assertThat(hwFields).extracting(Field::fieldKey).containsExactly("sys:type", "sys:key", "sys:summary", "sys:status", "sys:dueDate");
		assertThatThrownBy(() -> spaces.delete(etc.id())).isInstanceOf(AccessDeniedException.class); // 삭제는 프로젝트 ADMIN

		// Task 는 그 공간 워크플로의 첫 상태 · 그 공간 보드에 놓인다
		IssueResponse task = issues.createIssue(project.getId(), CreateIssueRequest.builder().title("릴레이 납땜").spaceId(hw.id()).build());
		assertThat(task.getSpaceId()).isEqualTo(hw.id());
		assertThat(task.getStatusNm()).isEqualTo("To Do");
		assertThat(jdbc.queryForObject("""
			select b.SPACE_ID from SN_CIRA_ISSUE_POSITION p join SN_CIRA_BOARD_COLUMN c on c.OBJ_ID=p.COLUMN_ID
			join SN_CIRA_BOARD b on b.OBJ_ID=c.BOARD_ID where p.ISSUE_ID=?""", String.class, task.getId())).isEqualTo(hw.id());
		String generalTodo = jdbc.queryForObject("select OBJ_ID from SN_CIRA_ISSUE_STATUS where SPACE_ID=? and STATUS_NM='To Do'",
			String.class, general.id());
		assertThatThrownBy(() -> issues.createIssue(project.getId(),
			CreateIssueRequest.builder().title("x").spaceId(hw.id()).statusId(generalTodo).build()))
			.isInstanceOf(IllegalArgumentException.class);

		TestContexts.login(adminEmail);
		assertThatThrownBy(() -> spaces.delete(hw.id())).isInstanceOf(IllegalStateException.class); // Task 남음
		assertThatThrownBy(() -> spaces.delete(general.id())).isInstanceOf(IllegalStateException.class); // 기본 공간
		spaces.delete(etc.id());
		assertThat(jdbc.queryForObject("select count(*) from SN_CIRA_ISSUE_STATUS where SPACE_ID=? and USE_STAT_CD='Usable'",
			Integer.class, etc.id())).isZero();
		assertThat(spaces.list(project.getId())).extracting(Space::name).containsExactly(SpaceService.DEFAULT_NAME, "Hardware");
	}

	@Test
	void features_twoLevels_commonAlwaysFirst() {
		Space hw = spaces.create(project.getId(), new SaveSpace("HW", null, null, null, null, null));
		Space sw = spaces.create(project.getId(), new SaveSpace("SW", null, null, null, null, null));
		String feature = fixtures.issueType("Feature");

		// 단위 Feature 를 먼저 만들어도 공통 Feature 가 위로
		IssueResponse hwUnit = issues.createIssue(project.getId(), CreateIssueRequest.builder()
			.title("ESP32 릴레이 회로").issueTypeId(feature).spaceId(hw.id()).build());
		IssueResponse common = issues.createIssue(project.getId(), CreateIssueRequest.builder()
			.title("원격 펌프 제어 자동화").issueTypeId(feature).featureScope("COMMON").spaceId(hw.id()).build());
		IssueResponse swUnit = issues.createIssue(project.getId(), CreateIssueRequest.builder()
			.title("MQTT 제어 API").issueTypeId(feature).spaceId(sw.id()).featureId(common.getId()).build());
		assertThat(hwUnit.getFeatureScope()).isEqualTo("UNIT");
		assertThat(common.getFeatureScope()).isEqualTo("COMMON");
		assertThat(common.getSpaceId()).isNull(); // 공통 레이어

		IssueResponse task = issues.createIssue(project.getId(), CreateIssueRequest.builder()
			.title("릴레이 배선").spaceId(hw.id()).featureId(hwUnit.getId()).build());
		assertThat(task.getFeatureId()).isEqualTo(hwUnit.getId());
		assertThatThrownBy(() -> issues.createIssue(project.getId(), CreateIssueRequest.builder()
			.title("x").spaceId(sw.id()).featureId(hwUnit.getId()).build())).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> issues.createIssue(project.getId(), CreateIssueRequest.builder()
			.title("x").issueTypeId(feature).featureScope("COMMON").featureId(common.getId()).build()))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> issues.updateIssue(task.getId(), UpdateIssueRequest.builder().featureId(task.getId()).build()))
			.isInstanceOf(IllegalArgumentException.class); // Feature 가 아닌 이슈

		List<FeatureNode> tree = issues.getFeatureTree(project.getId(), null);
		assertThat(tree).extracting(n -> n.feature().getTitle()).containsExactly("원격 펌프 제어 자동화", "ESP32 릴레이 회로");
		assertThat(tree.get(0).children()).extracting(n -> n.feature().getTitle()).containsExactly("MQTT 제어 API");
		assertThat(tree.get(1).taskCount()).isEqualTo(1);
		// 공간 필터: 공통은 항상, 단위는 그 공간 것만
		assertThat(issues.getFeatureTree(project.getId(), sw.id())).extracting(n -> n.feature().getTitle())
			.containsExactly("원격 펌프 제어 자동화");
	}

	@Test
	void docs_singleSource_contentLivesInMainNote() {
		IssueResponse task = issues.createIssue(project.getId(), CreateIssueRequest.builder().title("설명 테스트").content("첫 설명").build());
		assertThat(task.getContent()).isEqualTo("첫 설명");
		assertThat(jdbc.queryForObject("select CONTENT from SN_CIRA_ISSUE where OBJ_ID=?", String.class, task.getId())).isNull();
		IssueResponse got = ciraDocs.attachMain(issues.getIssue(task.getId()));
		assertThat(got.getMainNoteId()).isNotNull();
		assertThat(got.getContent()).isEqualTo("첫 설명"); // 진행 경과는 노트에만

		issues.updateIssue(task.getId(), UpdateIssueRequest.builder().content("고친 설명").build());
		String body = notes.get(got.getMainNoteId()).content();
		assertThat(body).contains("고친 설명", "## 진행 경과", "kind: task").doesNotContain("첫 설명");

		// 레거시: 메인 노트 없이 CONTENT 만 있는 Task → 조회 시 노트로 옮기고 CONTENT 를 비운다
		IssueResponse legacy = issues.createIssue(project.getId(), CreateIssueRequest.builder().title("옛 Task").build());
		jdbc.update("delete from SN_CIRA_ISSUE_DOC where ISSUE_ID=?", legacy.getId());
		jdbc.update("update SN_CIRA_ISSUE set CONTENT='예전 설명' where OBJ_ID=?", legacy.getId());
		IssueResponse migrated = ciraDocs.attachMain(issues.getIssue(legacy.getId()));
		assertThat(migrated.getContent()).isEqualTo("예전 설명");
		assertThat(jdbc.queryForObject("select CONTENT from SN_CIRA_ISSUE where OBJ_ID=?", String.class, legacy.getId())).isNull();

		// Team vault 노트 승격 → 그 노트 자체가 MAIN
		String teamVault = vaults.vaults().stream().filter(v -> project.getId().equals(v.projectId()))
			.map(VaultResponse::id).findFirst().orElseThrow();
		String noteId = notes.create(new NoteRequest("펌프 아이디어", "펌프를 원격으로", null, null, teamVault)).id();
		IssueResponse promoted = ciraDocs.promote(project.getId(), noteId, new PromoteRequest(null, null, general.id(), null));
		assertThat(ciraDocs.docs(project.getId(), promoted.getId()).main().noteId()).isEqualTo(noteId);
		assertThat(notes.get(noteId).content()).contains("kind: task", "펌프를 원격으로", "CIRA Task 로 등록");
	}

	@Test
	void docRules_globalImportedLocked_spaceRulesFree() {
		String typeId = fixtures.issueType("Issue");
		String folder = "요구사항-" + java.util.UUID.randomUUID().toString().substring(0, 6);
		List<DocRuleDto> global = ciraDocs.saveGlobalRules(typeId, List.of(new DocRuleDto(null, null, folder, null, "doc", null, null, true)));
		String globalId = global.stream().filter(r -> r.folderName().equals(folder)).findFirst().orElseThrow().id();

		List<DocRuleDto> imported = ciraDocs.importRules(project.getId(), new ImportRulesRequest(List.of(globalId)));
		DocRuleDto locked = imported.stream().filter(r -> r.folderName().equals(folder)).findFirst().orElseThrow();
		assertThat(locked.locked()).isTrue();
		assertThat(locked.sourceRuleId()).isEqualTo(globalId);
		ciraDocs.importRules(project.getId(), new ImportRulesRequest(List.of(globalId))); // 두 번 가져와도 1개
		assertThat(ciraDocs.rules(project.getId())).filteredOn(r -> r.folderName().equals(folder)).hasSize(1);

		// 프로젝트 저장(빈 목록)으로도 잠금 규칙은 안 지워진다
		ciraDocs.saveRules(project.getId(), typeId, List.of());
		assertThat(ciraDocs.rules(project.getId())).extracting(DocRuleDto::id).contains(locked.id());

		// 공간 관리자: 공간 규칙은 자유, 잠금 규칙과 같은 이름은 거절
		Space hw = spaces.create(project.getId(), new SaveSpace("HW", null, null, null, dev.getObjId(), null));
		TestContexts.login(dev.getEmail());
		assertThatThrownBy(() -> ciraDocs.saveSpaceRules(hw.id(), typeId,
			List.of(new DocRuleDto(null, null, folder, null, null, null, null, false)))).isInstanceOf(DocsException.class);
		List<DocRuleDto> hwRules = ciraDocs.saveSpaceRules(hw.id(), typeId,
			List.of(new DocRuleDto(null, null, "회로도", null, null, null, null, true),
				new DocRuleDto(locked.id(), null, "바꿔치기", null, null, null, null, false))); // 잠금 id 는 무시
		assertThat(hwRules).extracting(DocRuleDto::folderName).contains(folder, "회로도").doesNotContain("바꿔치기");
		assertThatThrownBy(() -> ciraDocs.saveSpaceRules(general.id(), typeId, List.of())).isInstanceOf(DocsException.class);

		// HW Task 에는 공통(잠금) + HW 규칙, 기본 공간 Task 에는 공통만
		IssueResponse hwTask = issues.createIssue(project.getId(), CreateIssueRequest.builder().title("t").issueTypeId(typeId).spaceId(hw.id()).build());
		assertThat(ciraDocs.docs(project.getId(), hwTask.getId()).rules()).extracting(DocRuleDto::folderName).containsExactly(folder, "회로도");
		IssueResponse genTask = issues.createIssue(project.getId(), CreateIssueRequest.builder().title("g").issueTypeId(typeId).build());
		assertThat(ciraDocs.docs(project.getId(), genTask.getId()).rules()).extracting(DocRuleDto::folderName).containsExactly(folder);
	}

	private List<String> statusNames(Space s) {
		return projects.getWorkflowStatuses(project.getId(), s.id()).stream().map(WorkflowStatusResponse::getStatusNm).toList();
	}

	private static AddStatusRequest status(String nm) {
		AddStatusRequest r = new AddStatusRequest();
		r.setStatusNm(nm);
		r.setCategory("IN_PROGRESS");
		r.setColorCd("#123456");
		r.setSortOrd((short)50);
		return r;
	}
}
