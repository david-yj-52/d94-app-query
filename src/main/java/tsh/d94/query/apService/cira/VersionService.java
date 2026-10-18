package tsh.d94.app.server.apService.cira;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.app.server.apService.cira.dto.version.IssueSummary;
import tsh.d94.app.server.apService.cira.dto.version.ReleaseNoteGroup;
import tsh.d94.app.server.apService.cira.dto.version.ReleaseNotesResponse;
import tsh.d94.app.server.apService.cira.dto.version.VersionRequest;
import tsh.d94.app.server.apService.cira.dto.version.VersionResponse;
import tsh.d94.app.server.apService.cira.exception.CiraException;
import tsh.d94.app.server.apService.cira.exception.ErrorCode;
import tsh.d94.app.server.data.orm.cira.ciraCiraIssueType.SnCiraCiraIssueTypeAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueVersion.SnCiraIssueVersionAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueVersion.SnCiraIssueVersionModel;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectAccess;
import tsh.d94.app.server.data.orm.cira.ciraVersion.SnCiraVersionAccess;
import tsh.d94.app.server.data.orm.cira.ciraVersion.SnCiraVersionModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class VersionService {

	private static final String STAT_UNRELEASED = "UNRELEASED";
	private static final String STAT_RELEASED = "RELEASED";
	private static final String STAT_ARCHIVED = "ARCHIVED";
	private static final String REL_FIX_VERSION = "FIX_VERSION";

	private final SnCiraVersionAccess versionAccess;
	private final SnCiraIssueVersionAccess issueVersionAccess;
	private final SnCiraProjectAccess projectAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraIssueStatusAccess issueStatusAccess;
	private final SnCiraCiraIssueTypeAccess issueTypeAccess;

	// ---------------------------------------------------------------
	// CRUD
	// ---------------------------------------------------------------

	@Transactional
	public VersionResponse createVersion(String projectId, VersionRequest request) {
		projectAccess.findById(projectId);

		SnCiraVersionModel version = SnCiraVersionModel.builder()
			.projectId(projectId)
			.versionNm(request.getVersionNm())
			.descr(request.getDescr())
			.status(request.getStatus() != null ? request.getStatus() : STAT_UNRELEASED)
			.planRelDt(request.getPlanRelDt())
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("CREATE-VERSION")
			.useStatCd(UseStatCd.Usable)
			.evtNm("CreateVersion")
			.prevEvntNm("None")
			.build();

		versionAccess.save(version);
		return mapToResponse(version);
	}

	public List<VersionResponse> getVersions(String projectId) {
		projectAccess.findById(projectId);
		return versionAccess.findByProjectId(projectId).stream()
			.filter(v -> UseStatCd.Usable.equals(v.getUseStatCd()))
			.map(this::mapToResponse)
			.collect(Collectors.toList());
	}

	public VersionResponse getVersion(String versionId) {
		return mapToResponse(findActiveVersion(versionId));
	}

	@Transactional
	public VersionResponse updateVersion(String versionId, VersionRequest request) {
		SnCiraVersionModel version = findActiveVersion(versionId);

		if (request.getVersionNm() != null)
			version.setVersionNm(request.getVersionNm());
		if (request.getDescr() != null)
			version.setDescr(request.getDescr());
		if (request.getStatus() != null)
			version.setStatus(request.getStatus());
		if (request.getPlanRelDt() != null)
			version.setPlanRelDt(request.getPlanRelDt());

		version.setEvtNm("UpdateVersion");
		version.setPrevEvntNm("None");
		versionAccess.save(version);
		return mapToResponse(version);
	}

	@Transactional
	public void deleteVersion(String versionId) {
		SnCiraVersionModel version = findActiveVersion(versionId);
		version.setUseStatCd(UseStatCd.Delete);
		version.setEvtNm("DeleteVersion");
		version.setPrevEvntNm("None");
		versionAccess.save(version);
	}

	// ---------------------------------------------------------------
	// 상태 전이
	// ---------------------------------------------------------------

	@Transactional
	public VersionResponse releaseVersion(String versionId) {
		SnCiraVersionModel version = findActiveVersion(versionId);
		if (STAT_RELEASED.equals(version.getStatus())) {
			throw new CiraException(ErrorCode.VERSION_INVALID_TRANSITION, "이미 릴리즈된 버전입니다.");
		}
		version.setStatus(STAT_RELEASED);
		version.setReleasedAt(OffsetDateTime.now());
		version.setEvtNm("ReleaseVersion");
		version.setPrevEvntNm("None");
		versionAccess.save(version);
		return mapToResponse(version);
	}

	@Transactional
	public VersionResponse archiveVersion(String versionId) {
		SnCiraVersionModel version = findActiveVersion(versionId);
		if (STAT_ARCHIVED.equals(version.getStatus())) {
			throw new CiraException(ErrorCode.VERSION_INVALID_TRANSITION, "이미 아카이브된 버전입니다.");
		}
		version.setStatus(STAT_ARCHIVED);
		version.setEvtNm("ArchiveVersion");
		version.setPrevEvntNm("None");
		versionAccess.save(version);
		return mapToResponse(version);
	}

	// ---------------------------------------------------------------
	// 릴리즈 노트
	// ---------------------------------------------------------------

	public ReleaseNotesResponse getReleaseNotes(String versionId) {
		SnCiraVersionModel version = findActiveVersion(versionId);

		// FIX_VERSION 연결 이슈 조회
		List<SnCiraIssueVersionModel> links =
			issueVersionAccess.findByVersionIdAndRelType(versionId, REL_FIX_VERSION);

		// Done 상태 ID 집합
		Set<String> doneStatusIds = issueStatusAccess.findByProjectId(version.getProjectId()).stream()
			.filter(s -> "DONE".equals(s.getCategory()))
			.map(s -> s.getObjId())
			.collect(Collectors.toSet());

		// 이슈 타입명 캐시
		Map<String, String> typeNameCache = new HashMap<>();

		// 타입별 그룹화 (이슈 순서 보존)
		Map<String, List<IssueSummary>> grouped = new LinkedHashMap<>();

		for (SnCiraIssueVersionModel link : links) {
			SnCiraIssueModel issue;
			try {
				issue = issueAccess.findById(link.getIssueId());
			} catch (Exception e) {
				continue;
			}
			if (issue.getDeletedAt() != null)
				continue;
			if (!doneStatusIds.contains(issue.getStatusId()))
				continue;

			String typeName = typeNameCache.computeIfAbsent(issue.getIssueTypeId(), typeId -> {
				try {
					return issueTypeAccess.findById(typeId).getTypeNm();
				} catch (Exception e) {
					return "기타";
				}
			});

			grouped.computeIfAbsent(typeName, k -> new ArrayList<>())
				.add(IssueSummary.builder()
					.id(issue.getObjId())
					.issueKey(issue.getIssueKey())
					.title(issue.getTitle())
					.issueTypeNm(typeName)
					.build());
		}

		List<ReleaseNoteGroup> groups = grouped.entrySet().stream()
			.map(e -> ReleaseNoteGroup.builder()
				.category(e.getKey())
				.issues(e.getValue())
				.build())
			.collect(Collectors.toList());

		return ReleaseNotesResponse.builder()
			.versionName(version.getVersionNm())
			.groups(groups)
			.build();
	}

	// ---------------------------------------------------------------
	// 이슈 연결 / 해제
	// ---------------------------------------------------------------

	@Transactional
	public void linkIssueToVersion(String versionId, String issueId, String type) {
		findActiveVersion(versionId);

		boolean exists = issueVersionAccess
			.findByIssueIdAndVersionIdAndRelType(issueId, versionId, type)
			.isPresent();
		if (exists) {
			throw new CiraException(ErrorCode.VERSION_ISSUE_ALREADY_LINKED);
		}

		SnCiraIssueVersionModel link = SnCiraIssueVersionModel.builder()
			.issueId(issueId)
			.versionId(versionId)
			.relType(type)
			.srvId(ApplicationProperties.getApplicationServiceName())
			.tenant(ApplicationProperties.getApplicationTenant())
			.traceId("LINK-ISSUE-VERSION")
			.useStatCd(UseStatCd.Usable)
			.evtNm("LinkIssueToVersion")
			.prevEvntNm("None")
			.build();

		issueVersionAccess.save(link);
	}

	@Transactional
	public void unlinkIssueFromVersion(String versionId, String issueId, String type) {
		SnCiraIssueVersionModel link = issueVersionAccess
			.findByIssueIdAndVersionIdAndRelType(issueId, versionId, type)
			.orElseThrow(() -> new CiraException(ErrorCode.VERSION_ISSUE_NOT_LINKED));

		issueVersionAccess.delete(link.getObjId());
	}

	// ---------------------------------------------------------------
	// private helpers
	// ---------------------------------------------------------------

	private SnCiraVersionModel findActiveVersion(String versionId) {
		SnCiraVersionModel version = versionAccess.findById(versionId);
		if (!UseStatCd.Usable.equals(version.getUseStatCd())) {
			throw new CiraException(ErrorCode.VERSION_NOT_FOUND, "버전을 찾을 수 없습니다: " + versionId);
		}
		return version;
	}

	private VersionResponse mapToResponse(SnCiraVersionModel model) {
		return VersionResponse.builder()
			.id(model.getObjId())
			.projectId(model.getProjectId())
			.versionName(model.getVersionNm())
			.description(model.getDescr())
			.status(model.getStatus())
			.plannedReleaseDate(model.getPlanRelDt())
			.releasedAt(model.getReleasedAt())
			.build();
	}
}
