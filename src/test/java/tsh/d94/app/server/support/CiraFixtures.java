package tsh.d94.app.server.support;

import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.cira.ProjectService;
import tsh.d94.app.server.apService.cira.dto.CreateProjectRequest;
import tsh.d94.app.server.apService.cira.dto.ProjectResponse;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeAccess;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/**
 * 통합 테스트 데이터 준비. 사용자는 Access 로 직접 저장하고, 프로젝트는 실제 서비스로 만든다
 * (소유자 멤버 등록·기본 워크플로우까지 운영과 같은 경로). 이름에 난수를 붙여 테스트끼리 겹치지 않게 한다.
 * 호출 전에 {@link TestContexts#login} 으로 tenant 가 설정돼 있어야 한다.
 */
@Component
@RequiredArgsConstructor
public class CiraFixtures {

	private final GsUserAccess userAccess;
	private final ProjectService projectService;
	private final tsh.d94.app.server.apService.cira.IssueService issueService;
	private final SnCiraCiraIssueTypeAccess issueTypeAccess;
	private final JdbcTemplate jdbc;

	public static String uniqueEmail(String prefix) {
		return prefix + "-" + UUID.randomUUID().toString().substring(0, 8) + "@test.d94";
	}

	public GsUserModel user(String email) {
		return userAccess.save(GsUserModel.builder()
			.email(email)
			.userNm(email.substring(0, email.indexOf('@')))
			.pwdHash("not-used-in-tests")
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(TestContexts.TENANT)
			.traceId("IT-FIXTURE")
			.useStatCd(UseStatCd.Usable)
			.evtNm("ItFixture")
			.prevEvntNm("None")
			.build());
	}

	/** 현재 로그인 사용자(TestContexts.login)를 소유자로 프로젝트 생성 */
	public ProjectResponse project(String projectType) {
		String key = "T" + UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
		CreateProjectRequest request = new CreateProjectRequest();
		request.setKey(key);
		request.setName("IT " + key);
		request.setDescription("integration test");
		request.setProjectType(projectType);
		return projectService.createProject(request);
	}

	/** 사용자를 프로젝트 멤버(DEVELOPER)로 추가. 현재 로그인 사용자가 그 프로젝트 ADMIN 이어야 한다 */
	public GsUserModel member(String projectId, GsUserModel user) {
		projectService.addMember(projectId,
			new tsh.d94.app.server.apService.cira.dto.AddMemberRequest(user.getEmail(), "DEVELOPER"));
		return user;
	}

	/** 이슈 유형 (전역 테이블·이름 유니크, 기본 시드 없음 → 없으면 만들고 있으면 재사용) */
	public String issueType(String typeNm) {
		java.util.List<String> existing = jdbc.queryForList(
			"select obj_id from sn_cira_cira_issue_type where type_nm = ?", String.class, typeNm);
		if (!existing.isEmpty()) {
			return existing.get(0);
		}
		return issueTypeAccess.save(SnCiraCiraIssueTypeModel.builder()
			.typeNm(typeNm)
			.colorCd("#0052CC")
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(TestContexts.TENANT)
			.traceId("IT-FIXTURE")
			.useStatCd(UseStatCd.Usable)
			.evtNm("ItFixture")
			.prevEvntNm("None")
			.build()).getObjId();
	}

	/** 상태 "To Do"·유형 "Task" 로 이슈 생성 (D3 를 피하는 정상 경로). 현재 로그인 사용자가 프로젝트 멤버여야 한다 */
	public tsh.d94.app.server.apService.cira.dto.IssueResponse issue(String projectId, String title, String assigneeId) {
		tsh.d94.app.server.apService.cira.dto.CreateIssueRequest req =
			new tsh.d94.app.server.apService.cira.dto.CreateIssueRequest();
		req.setTitle(title);
		req.setContent(title + " 설명");
		req.setPriority("MEDIUM");
		req.setStatusId(statusId(projectId, "To Do"));
		req.setIssueTypeId(issueType("Task"));
		req.setAssigneeId(assigneeId);
		return issueService.createIssue(projectId, req);
	}

	/** createProject 가 만든 기본 상태 ("To Do" / "In Progress" / "In Review" / "Done") */
	public String statusId(String projectId, String statusNm) {
		return jdbc.queryForObject(
			"select obj_id from sn_cira_issue_status where project_id = ? and status_nm = ?",
			String.class, projectId, statusNm);
	}

}
