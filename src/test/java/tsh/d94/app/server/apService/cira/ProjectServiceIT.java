package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tsh.d94.app.server.apService.cira.dto.AddMemberRequest;
import tsh.d94.app.server.apService.cira.dto.ProjectMemberResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/** 프로젝트 생성(기본 워크플로우)·멤버 관리·권한이 실제 DB 에서 맞는지 확인 */
@IntegrationTest
class ProjectServiceIT {

	@Autowired
	private ProjectService projectService;
	@Autowired
	private CiraFixtures fixtures;
	@Autowired
	private JdbcTemplate jdbc;

	private String ownerEmail;
	private GsUserModel member;
	private ProjectResponse project;

	@BeforeEach
	void setUp() {
		ownerEmail = CiraFixtures.uniqueEmail("owner");
		TestContexts.login(ownerEmail);
		fixtures.user(ownerEmail);
		member = fixtures.user(CiraFixtures.uniqueEmail("member"));
		project = fixtures.project("SCRUM");
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private ProjectMemberResponse add(String email, String role) {
		return projectService.addMember(project.getId(), new AddMemberRequest(email, role));
	}

	private int count(String sql, Object... args) {
		return jdbc.queryForObject(sql, Integer.class, args);
	}

	@Test
	@DisplayName("생성 시 소유자 ADMIN 멤버, 상태 4개, 전이 6개, 보드 1개·컬럼 4개가 만들어진다")
	void createProject_seedsDefaultWorkflow() {
		String pid = project.getId();
		assertThat(jdbc.queryForObject("select role from sn_cira_project_member where project_id = ?", String.class,
			pid)).isEqualTo("ADMIN");
		assertThat(count("select count(*) from sn_cira_issue_status where project_id = ?", pid)).isEqualTo(4);
		assertThat(count("select count(*) from sn_cira_issue_transition where project_id = ?", pid)).isEqualTo(6);
		assertThat(count("select count(*) from sn_cira_board where project_id = ?", pid)).isEqualTo(1);
		assertThat(count("""
			select count(*) from sn_cira_board_column c join sn_cira_board b on b.obj_id = c.board_id
			where b.project_id = ?""", pid)).isEqualTo(4);
	}

	@Test
	@DisplayName("같은 프로젝트 키는 두 번 만들 수 없다")
	void createProject_duplicateKey_rejected() {
		assertThatThrownBy(() -> {
			tsh.d94.app.server.apService.cira.dto.CreateProjectRequest req =
				new tsh.d94.app.server.apService.cira.dto.CreateProjectRequest();
			req.setKey(project.getKey());
			req.setName("dup");
			projectService.createProject(req);
		}).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("멤버 추가(기본 역할 DEVELOPER) → 목록·내 프로젝트에 보인다")
	void addMember_visibleInListsAndMyProjects() {
		ProjectMemberResponse added = add(member.getEmail(), null);

		assertThat(added.getRole()).isEqualTo("DEVELOPER");
		assertThat(projectService.getMembers(project.getId())).extracting(ProjectMemberResponse::getEmail)
			.contains(ownerEmail, member.getEmail());
		TestContexts.login(member.getEmail());
		assertThat(projectService.getMyProjects()).extracting(ProjectResponse::getId).contains(project.getId());
	}

	@Test
	@DisplayName("[D20] 제거된 멤버는 내 프로젝트 목록에서 사라지고 이슈를 만들 수 없다")
	void removedMember_losesAccess() {
		add(member.getEmail(), "DEVELOPER");
		projectService.removeMember(project.getId(), member.getObjId());

		TestContexts.login(member.getEmail());
		assertThat(projectService.getMyProjects()).extracting(ProjectResponse::getId)
			.as("findAllByUserId 가 soft delete 된 멤버 행을 걸러내지 않음")
			.doesNotContain(project.getId());
		assertThatThrownBy(() -> fixtures.issue(project.getId(), "제거된 멤버의 이슈", null))
			.isInstanceOfAny(IllegalArgumentException.class, CiraException.class);
	}

	@Test
	@DisplayName("[D21] 제거했던 멤버를 다시 추가할 수 있다 (UNIQUE(project_id,user_id) + soft delete)")
	void reAddRemovedMember_succeeds() {
		add(member.getEmail(), "DEVELOPER");
		projectService.removeMember(project.getId(), member.getObjId());

		ProjectMemberResponse again = add(member.getEmail(), "DEVELOPER");

		assertThat(again.getUserId()).isEqualTo(member.getObjId());
	}

	@Test
	@DisplayName("마지막 ADMIN 은 제거할 수 없다")
	void removeLastAdmin_rejected() {
		String ownerId = jdbc.queryForObject("select obj_id from gs_user where email = ?", String.class, ownerEmail);

		assertThatThrownBy(() -> projectService.removeMember(project.getId(), ownerId)).isInstanceOf(RuntimeException.class);
		assertThat(count("select count(*) from sn_cira_project_member where project_id = ? and use_stat_cd = 'Usable'",
			project.getId())).isEqualTo(1);
	}

	@Test
	@DisplayName("[D19] 권한·중복 오류는 400/403 으로 매핑되는 예외여야 한다 (SecurityException·IllegalStateException 은 500)")
	void errorTypes_mapToClientErrors() {
		add(member.getEmail(), "DEVELOPER");

		Throwable duplicate = catchThrowable(() -> add(member.getEmail(), "DEVELOPER"));
		TestContexts.login(member.getEmail());
		Throwable notAdmin = catchThrowable(() -> add(ownerEmail, "DEVELOPER"));

		assertThat(List.of(duplicate, notAdmin))
			.allSatisfy(t -> assertThat(t).isInstanceOfAny(IllegalArgumentException.class, CiraException.class,
				org.springframework.security.access.AccessDeniedException.class));
	}

	@Test
	@DisplayName("[D18] 멤버가 아닌 사용자는 프로젝트·멤버 목록을 볼 수 없다")
	void nonMember_cannotReadProject() {
		String strangerEmail = CiraFixtures.uniqueEmail("stranger");
		fixtures.user(strangerEmail);
		TestContexts.login(strangerEmail);

		assertThatThrownBy(() -> projectService.getProject(project.getId())).isInstanceOf(RuntimeException.class);
		assertThatThrownBy(() -> projectService.getMembers(project.getId())).isInstanceOf(RuntimeException.class);
	}

}
