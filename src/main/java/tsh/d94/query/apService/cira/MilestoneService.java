package tsh.d94.app.server.apService.cira;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.dto.milestone.MilestoneProgressResponse;
import tsh.d94.app.server.apService.cira.dto.milestone.MilestoneRequest;
import tsh.d94.app.server.apService.cira.dto.milestone.MilestoneResponse;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraMilestone.SnCiraMilestoneAccess;
import tsh.d94.app.server.data.orm.cira.ciraMilestone.SnCiraMilestoneModel;
import tsh.d94.app.server.data.orm.cira.ciraMilestoneIssue.SnCiraMilestoneIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraMilestoneIssue.SnCiraMilestoneIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectAccess;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class MilestoneService {

	private static final String STAT_OPEN = "OPEN";

	private final SnCiraMilestoneAccess milestoneAccess;
	private final SnCiraMilestoneIssueAccess milestoneIssueAccess;
	private final SnCiraProjectAccess projectAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraIssueStatusAccess issueStatusAccess;

	// ---------------------------------------------------------------
	// CRUD
	// ---------------------------------------------------------------

	@Transactional
	public MilestoneResponse createMilestone(String projectId, MilestoneRequest request) {
		projectAccess.findById(projectId);

		SnCiraMilestoneModel milestone = SnCiraMilestoneModel.builder()
			.projectId(projectId)
			.milestoneNm(request.getName())
			.descr(request.getDescription())
			.dueDt(request.getDueDate())
			.status(request.getStatus() != null ? request.getStatus() : STAT_OPEN)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("CREATE-MILESTONE")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateMilestone")
			.prevEvntNm("None")
			.build();

		milestoneAccess.save(milestone);
		return mapToResponse(milestone);
	}

	public List<MilestoneResponse> getMilestones(String projectId) {
		projectAccess.findById(projectId);
		return milestoneAccess.findByProjectId(projectId).stream()
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.map(this::mapToResponse)
			.collect(Collectors.toList());
	}

	public MilestoneResponse getMilestone(String milestoneId) {
		return mapToResponse(findActiveMilestone(milestoneId));
	}

	@Transactional
	public MilestoneResponse updateMilestone(String milestoneId, MilestoneRequest request) {
		SnCiraMilestoneModel milestone = findActiveMilestone(milestoneId);

		if (request.getName() != null)
			milestone.setMilestoneNm(request.getName());
		if (request.getDescription() != null)
			milestone.setDescr(request.getDescription());
		if (request.getDueDate() != null)
			milestone.setDueDt(request.getDueDate());
		if (request.getStatus() != null)
			milestone.setStatus(request.getStatus());

		milestone.setEvtNm("UpdateMilestone");
		milestone.setPrevEvntNm("None");
		milestoneAccess.save(milestone);
		return mapToResponse(milestone);
	}

	@Transactional
	public void deleteMilestone(String milestoneId) {
		SnCiraMilestoneModel milestone = findActiveMilestone(milestoneId);
		milestone.setUseStatCd(UseStatCd.Delete);
		milestone.setEvtNm("DeleteMilestone");
		milestone.setPrevEvntNm("None");
		milestoneAccess.save(milestone);
	}

	// ---------------------------------------------------------------
	// 진행률
	// ---------------------------------------------------------------

	public MilestoneProgressResponse getMilestoneProgress(String milestoneId) {
		SnCiraMilestoneModel milestone = findActiveMilestone(milestoneId);

		List<SnCiraMilestoneIssueModel> milestoneIssues =
			milestoneIssueAccess.findByMilestoneId(milestoneId);

		int total = milestoneIssues.size();
		if (total == 0) {
			return MilestoneProgressResponse.builder()
				.milestoneId(milestoneId)
				.total(0)
				.completed(0)
				.percentage(0.0)
				.build();
		}

		Set<String> doneStatusIds = issueStatusAccess.findByProjectId(milestone.getProjectId()).stream()
			.filter(s -> "DONE".equals(s.getCategory()))
			.map(s -> s.getObjId())
			.collect(Collectors.toSet());

		int completed = 0;
		for (SnCiraMilestoneIssueModel mi : milestoneIssues) {
			try {
				SnCiraIssueModel issue = issueAccess.findById(mi.getIssueId());
				if (issue.getDeletedAt() == null && doneStatusIds.contains(issue.getStatusId())) {
					completed++;
				}
			} catch (Exception e) {
				// 이슈를 찾을 수 없는 경우 무시
			}
		}

		double percentage = Math.round(completed * 100.0 / total * 10.0) / 10.0;

		return MilestoneProgressResponse.builder()
			.milestoneId(milestoneId)
			.total(total)
			.completed(completed)
			.percentage(percentage)
			.build();
	}

	// ---------------------------------------------------------------
	// 이슈 추가 / 제거
	// ---------------------------------------------------------------

	@Transactional
	public void addIssueToMilestone(String milestoneId, String issueId) {
		findActiveMilestone(milestoneId);

		boolean exists = milestoneIssueAccess
			.findByMilestoneIdAndIssueId(milestoneId, issueId)
			.isPresent();
		if (exists) {
			throw new CiraException(ErrorCode.MILESTONE_ISSUE_ALREADY_LINKED);
		}

		SnCiraMilestoneIssueModel mi = SnCiraMilestoneIssueModel.builder()
			.milestoneId(milestoneId)
			.issueId(issueId)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("ADD-MILESTONE-ISSUE")
			.useStatCd(UseStatCd.Usable)
			.evtNm("AddIssueToMilestone")
			.prevEvntNm("None")
			.build();

		milestoneIssueAccess.save(mi);
	}

	@Transactional
	public void removeIssueFromMilestone(String milestoneId, String issueId) {
		SnCiraMilestoneIssueModel mi = milestoneIssueAccess
			.findByMilestoneIdAndIssueId(milestoneId, issueId)
			.orElseThrow(() -> new CiraException(ErrorCode.MILESTONE_ISSUE_NOT_LINKED));

		milestoneIssueAccess.delete(mi.getObjId());
	}

	// ---------------------------------------------------------------
	// private helpers
	// ---------------------------------------------------------------

	private SnCiraMilestoneModel findActiveMilestone(String milestoneId) {
		SnCiraMilestoneModel milestone = milestoneAccess.findById(milestoneId);
		if (!UseStatCd.Usable.equals(milestone.getUseStatCd())) {
			throw new CiraException(ErrorCode.MILESTONE_NOT_FOUND, "마일스톤을 찾을 수 없습니다: " + milestoneId);
		}
		return milestone;
	}

	private MilestoneResponse mapToResponse(SnCiraMilestoneModel model) {
		return MilestoneResponse.builder()
			.id(model.getObjId())
			.name(model.getMilestoneNm())
			.description(model.getDescr())
			.dueDate(model.getDueDt())
			.status(model.getStatus())
			.projectId(model.getProjectId())
			.build();
	}
}
