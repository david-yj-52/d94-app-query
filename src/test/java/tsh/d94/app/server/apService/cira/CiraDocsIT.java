package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import tsh.d94.app.server.apService.cira.CiraDocsService.DeriveFromIssueRequest;
import tsh.d94.app.server.apService.cira.CiraDocsService.DocRuleDto;
import tsh.d94.app.server.apService.cira.CiraDocsService.IssueDocsResponse;
import tsh.d94.app.server.apService.cira.CiraDocsService.PromoteRequest;
import tsh.d94.app.server.apService.cira.dto.ChangeStatusRequest;
import tsh.d94.app.server.apService.cira.dto.IssueResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.FolderResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.NoteSummary;
import tsh.d94.app.server.apService.docs.DocsDtos.VaultResponse;
import tsh.d94.app.server.apService.docs.DocsException;
import tsh.d94.app.server.apService.docs.DocsFolderService;
import tsh.d94.app.server.apService.docs.DocsNoteService;
import tsh.d94.app.server.apService.docs.DocsVaultService;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/** Team vault · Task 메인 노트 · 진행 경과 · 기준정보 파생 · 승격 · Private 격리. */
@IntegrationTest
class CiraDocsIT {

	@Autowired CiraFixtures fixtures;
	@Autowired CiraDocsService ciraDocs;
	@Autowired IssueService issues;
	@Autowired DocsVaultService vaults;
	@Autowired DocsNoteService notes;
	@Autowired DocsFolderService folders;

	private String adminEmail;
	private GsUserModel dev;
	private ProjectResponse project;

	@BeforeEach
	void setUp() {
		adminEmail = CiraFixtures.uniqueEmail("cdadmin");
		TestContexts.login(adminEmail);
		fixtures.user(adminEmail);
		dev = fixtures.user(CiraFixtures.uniqueEmail("cddev"));
		project = fixtures.project("SCRUM");
		fixtures.member(project.getId(), dev);
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	@Test
	void task_mainNote_progress_derive_isolation() {
		String typeId = fixtures.issueType("Issue");
		// 기준정보: Issue 타입에 draft(자동 생성)·plan(템플릿) 슬롯
		List<DocRuleDto> rules = ciraDocs.saveRules(project.getId(), typeId, List.of(
			new DocRuleDto(null, null, "draft", null, "idea", null, null, true),
			new DocRuleDto(null, null, "plan", null, "doc", "plan", "## 목표\n", false)));
		assertThat(rules).extracting(DocRuleDto::folderName).containsExactly("draft", "plan");

		IssueResponse issue = fixtures.issue(project.getId(), "로그인 [개선]", null);
		String key = issue.getIssueKey();

		// 메인 노트: Team vault task/{projectKey}/{issueKey}, 금지 문자 치환, 진행 경과 섹션
		IssueDocsResponse d = ciraDocs.docs(project.getId(), issue.getId());
		assertThat(d.main()).isNotNull();
		assertThat(d.main().title()).isEqualTo(key + " 로그인 개선");
		NoteResponse main = notes.get(d.main().noteId());
		assertThat(main.kind()).isEqualTo("task");
		assertThat(main.content()).contains("issueKey: \"" + key + "\"").contains("로그인 [개선] 설명")
			.contains(CiraDocsService.PROGRESS);
		String vaultId = main.vaultId();
		assertThat(vaults.vaults()).extracting(VaultResponse::id).contains(vaultId);
		List<FolderResponse> fs = folders.list(vaultId);
		assertThat(fs).extracting(FolderResponse::name).contains("task", key, "draft").doesNotContain("plan");

		// 상태 변경 → 진행 경과 한 줄, 사용자 본문은 그대로
		ChangeStatusRequest cs = new ChangeStatusRequest();
		org.springframework.test.util.ReflectionTestUtils.setField(cs, "statusId", fixtures.statusId(project.getId(), "In Progress"));
		issues.changeStatus(issue.getId(), cs);
		NoteResponse after = notes.get(main.id());
		assertThat(after.version()).isEqualTo(main.version() + 1);
		assertThat(after.content()).contains("상태 변경: To Do → In Progress");

		// 슬롯 파생: plan 폴더(lazy 생성) + origin + 템플릿, DERIVED 연결 → 계보
		String planRule = rules.get(1).id();
		NoteResponse plan = ciraDocs.derive(project.getId(), issue.getId(),
			new DeriveFromIssueRequest("로그인 기획", planRule, null, null, null));
		assertThat(plan.origin()).isEqualTo(after.title());
		assertThat(plan.stream()).isEqualTo("plan");
		assertThat(plan.content()).endsWith("## 목표\n");
		// 자유 파생: 기준정보에 없는 폴더
		ciraDocs.derive(project.getId(), issue.getId(), new DeriveFromIssueRequest("회고", null, "retro", null, null));
		IssueDocsResponse d2 = ciraDocs.docs(project.getId(), issue.getId());
		assertThat(d2.derived()).extracting(CiraDocsService.LinkedDoc::title).containsExactly("로그인 기획", "회고");
		assertThat(d2.lineage()).hasSize(3);
		assertThat(folders.list(vaultId)).extracting(FolderResponse::name).contains("plan", "retro");

		// 개발자(멤버)는 Team vault 를 보고, 관리자의 Private 노트는 못 본다
		NoteResponse privateNote = notes.create(new NoteRequest("관리자 메모", "비밀", null, null));
		TestContexts.login(dev.getEmail());
		assertThat(notes.list(null, null, null, null, null, null, null)).extracting(NoteSummary::id)
			.contains(main.id()).doesNotContain(privateNote.id());
		assertThat(catchThrowable(() -> notes.get(privateNote.id()))).isInstanceOf(DocsException.class);
		// 기준정보 수정은 ADMIN 만
		assertThat(((DocsException)catchThrowable(() -> ciraDocs.saveRules(project.getId(), typeId, List.of())))
			.getCode()).isEqualTo(DocsException.Code.FORBIDDEN);

		// 비멤버는 Team vault 노트·Task 문서 모두 404
		TestContexts.login(fixtures.user(CiraFixtures.uniqueEmail("cdout")).getEmail());
		assertThat(catchThrowable(() -> notes.get(main.id()))).isInstanceOf(DocsException.class);
		assertThat(catchThrowable(() -> ciraDocs.docs(project.getId(), issue.getId()))).isInstanceOf(DocsException.class);
	}

	@Test
	void promote_privateNote_toTask() {
		NoteResponse idea = notes.create(new NoteRequest("아이디어 A", "---\nkind: idea\n---\n본문", null, null));
		IssueResponse issue = ciraDocs.promote(project.getId(), idea.id(), new PromoteRequest(null, null));
		assertThat(issue.getTitle()).isEqualTo("아이디어 A");
		NoteResponse main = notes.get(ciraDocs.docs(project.getId(), issue.getId()).main().noteId());
		assertThat(main.origin()).isEqualTo("아이디어 A");
		assertThat(main.vaultId()).isNotEqualTo(idea.vaultId());
	}

	@Test
	void progressAppend_keepsOtherSections() {
		String c = "---\nkind: task\n---\n본문\n\n## 진행 경과\n- a\n\n## 메모\n내용";
		assertThat(CiraDocsService.withProgress(c, "- b"))
			.isEqualTo("---\nkind: task\n---\n본문\n\n## 진행 경과\n- a\n- b\n\n## 메모\n내용");
		assertThat(CiraDocsService.withProgress("본문\n", "- b")).isEqualTo("본문\n\n## 진행 경과\n- b\n");
		assertThat(CiraDocsService.mainTitle("D94-1", "a|b#c\n[d]")).isEqualTo("D94-1 a b c d");
	}
}
