package tsh.d94.app.server.apService.cira;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.cira.dto.SpaceDto.SaveSpace;
import tsh.d94.app.server.apService.cira.dto.SpaceDto.Space;
import tsh.d94.app.server.apService.cira.dto.CiraFieldDto.PresetStatus;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoard.SnCiraBoardModel;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnAccess;
import tsh.d94.app.server.data.orm.cira.ciraBoardColumn.SnCiraBoardColumnModel;
import tsh.d94.app.server.data.orm.cira.ciraField.CiraFieldAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssue.SnCiraIssueAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueStatus.SnCiraIssueStatusModel;
import tsh.d94.app.server.data.orm.cira.ciraIssueTransition.SnCiraIssueTransitionAccess;
import tsh.d94.app.server.data.orm.cira.ciraIssueTransition.SnCiraIssueTransitionModel;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.cira.ciraSpace.SnCiraSpaceAccess;
import tsh.d94.app.server.data.orm.cira.ciraSpace.SnCiraSpaceModel;
import tsh.d94.lib.core.config.ApplicationProperties;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserModel;

/**
 * 작업 공간(Space) — 프로젝트 하위에서 워크플로·필드 세트·보드를 독립 운영하는 단위 (V650).
 * 생성·삭제·관리자 임명 = 프로젝트 ADMIN. 이름·설명 수정 = 프로젝트 ADMIN 또는 공간 관리자.
 * 프리셋(구 ProjectType)은 만들 때 1회 복사하고 끝 — 이후 프리셋 변경과 무관.
 */
@Service
@RequiredArgsConstructor
public class SpaceService {

	public static final String DEFAULT_NAME = "General";

	private final SnCiraSpaceAccess spaceAccess;
	private final SnCiraIssueAccess issueAccess;
	private final SnCiraIssueStatusAccess statusAccess;
	private final SnCiraIssueTransitionAccess transitionAccess;
	private final SnCiraBoardAccess boardAccess;
	private final SnCiraBoardColumnAccess columnAccess;
	private final SnCiraProjectMemberAccess memberAccess;
	private final GsUserAccess userAccess;
	private final CiraFieldAccess fieldAccess;
	private final tsh.d94.app.server.data.orm.cira.ciraDocs.CiraDocsAccess docsAccess;
	private final CiraFieldService fieldService;
	private final SpaceGuard guard;

	public List<Space> list(String projectId) {
		guard.requireMember(projectId);
		return spaceAccess.findByProjectId(projectId).stream().map(this::toDto).toList();
	}

	public Space get(String spaceId) {
		SnCiraSpaceModel s = guard.get(spaceId);
		guard.requireMember(s.getProjectId());
		return toDto(s);
	}

	@Transactional
	public Space create(String projectId, SaveSpace req) {
		requireProjectAdmin(projectId);
		return toDto(createInternal(projectId, req, false));
	}

	/** 프로젝트 생성 직후 (ProjectService) — 권한 검사 없이 기본 공간 1개 */
	@Transactional
	public SnCiraSpaceModel createDefault(String projectId, String name, String presetTypeId, String ownerId) {
		return createInternal(projectId,
			new SaveSpace(blank(name) ? DEFAULT_NAME : name, null, null, presetTypeId, ownerId, (short)0), true);
	}

	/** 프로젝트 생성 직후 기본 공간 외 처음 공간들 (ProjectService) — 권한 검사 없음, 관리자 = 만든 사람 */
	@Transactional
	public SnCiraSpaceModel createInitial(String projectId, String name, String presetTypeId, String ownerId, short sortOrd) {
		return createInternal(projectId, new SaveSpace(name, null, null, presetTypeId, ownerId, sortOrd), false);
	}

	@Transactional
	public Space update(String spaceId, SaveSpace req) {
		SnCiraSpaceModel s = guard.requireManager(guard.get(spaceId));
		if (!blank(req.name())) {
			String name = name(req.name());
			if (!name.equals(s.getSpaceNm()) && taken(s.getProjectId(), name, null)) {
				throw conflict("같은 이름의 작업 공간이 있습니다: " + name);
			}
			s.setSpaceNm(name);
		}
		if (!blank(req.key())) {
			String key = key(req.key());
			if (!key.equals(s.getSpaceKey()) && taken(s.getProjectId(), null, key)) {
				throw conflict("같은 키의 작업 공간이 있습니다: " + key);
			}
			s.setSpaceKey(key);
		}
		if (req.description() != null) {
			s.setDescr(req.description());
		}
		if (req.sortOrd() != null) {
			s.setSortOrd(req.sortOrd());
		}
		if (req.adminUserId() != null && !req.adminUserId().equals(s.getAdminUserId())) {
			requireProjectAdmin(s.getProjectId()); // 관리자 임명은 프로젝트 ADMIN 만
			requireProjectMember(s.getProjectId(), req.adminUserId());
			s.setAdminUserId(req.adminUserId());
		}
		s.setEvtNm("UpdateSpace");
		spaceAccess.save(s);
		return toDto(s);
	}

	/** 기본 공간·Task 가 남은 공간은 지울 수 없다. 공간 소유 상태·전이·보드·필드 세트·컬럼 설정은 함께 지운다 */
	@Transactional
	public void delete(String spaceId) {
		SnCiraSpaceModel s = guard.get(spaceId);
		requireProjectAdmin(s.getProjectId());
		if (s.isDefault()) {
			throw conflict("기본 작업 공간은 삭제할 수 없습니다");
		}
		if (issueAccess.countLiveBySpaceId(spaceId) > 0) {
			throw conflict("Task 가 남아 있는 작업 공간은 삭제할 수 없습니다");
		}
		for (SnCiraBoardModel b : boardAccess.findBySpaceId(spaceId)) {
			columnAccess.findByBoardIdOrderBySortOrd(b.getObjId()).forEach(c -> columnAccess.delete(c.getObjId()));
			boardAccess.delete(b.getObjId());
		}
		transitionAccess.findBySpaceId(spaceId).forEach(t -> transitionAccess.delete(t.getObjId()));
		statusAccess.findBySpaceId(spaceId).forEach(st -> statusAccess.delete(st.getObjId()));
		fieldAccess.deleteSpaceRows(spaceId);
		docsAccess.deleteSpaceRules(spaceId);
		spaceAccess.hardDelete(spaceId);
	}

	// ── 내부 ──

	private SnCiraSpaceModel createInternal(String projectId, SaveSpace req, boolean isDefault) {
		String name = name(req.name());
		boolean autoKey = blank(req.key());
		String key = key(autoKey ? (isDefault ? "DEFAULT" : name) : req.key());
		if (taken(projectId, name, null)) {
			throw conflict("같은 이름의 작업 공간이 있습니다: " + name);
		}
		// 이름에서 만든 키는 겹치면 번호를 붙인다 (한글 이름은 모두 SPACE 가 되므로). 직접 준 키만 거절
		String base = key;
		for (int n = 2; autoKey && taken(projectId, null, key); n++) {
			String suffix = "_" + n;
			key = (base.length() + suffix.length() > 20 ? base.substring(0, 20 - suffix.length()) : base) + suffix;
		}
		if (taken(projectId, null, key)) {
			throw conflict("같은 키의 작업 공간이 있습니다: " + key);
		}
		String adminId = blank(req.adminUserId()) ? guard.currentUserId() : req.adminUserId();
		requireProjectMember(projectId, adminId);
		String presetId = blank(req.presetTypeId()) ? null : req.presetTypeId();
		if (presetId != null && fieldAccess.type(TenantContext.get(), presetId) == null) {
			throw new IllegalArgumentException("작업 공간 프리셋이 없습니다: " + presetId);
		}
		SnCiraSpaceModel s = SnCiraSpaceModel.builder()
			.projectId(projectId).spaceNm(name).spaceKey(key).descr(req.description())
			.presetTypeId(presetId).adminUserId(adminId).isDefaultYn(isDefault ? "Y" : "N")
			.sortOrd(req.sortOrd() != null ? req.sortOrd() : (short)(spaceAccess.findByProjectId(projectId).size()))
			.srvId(ApplicationProperties.getApplicationServiceName()).tenant(TenantContext.get())
			.traceId("CREATE-SPACE").useStatCd(UseStatCd.Usable).evtNm("CreateSpace").prevEvntNm("None")
			.build();
		spaceAccess.save(s);
		initWorkflow(s, presetId);
		fieldService.copyPresetFields(projectId, s.getObjId(), presetId);
		return s;
	}

	/** 빈 공간: 3단계 + 칸반 */
	private static final List<PresetStatus> EMPTY_SPACE_STATUSES = List.of(new PresetStatus("To Do", "TODO"),
		new PresetStatus("In Progress", "IN_PROGRESS"), new PresetStatus("Done", "DONE"));
	private static final Map<String, String> CATEGORY_COLOR = Map.of("TODO", "#DFE1E6", "IN_PROGRESS", "#0052CC", "DONE", "#00875A");

	/**
	 * 프리셋의 상태·보드 유형을 새 공간에 복사한다 (V670: 프리셋마다 상태 정의, 없으면 기본 4단계). 빈 공간은 3단계 + 칸반.
	 * 전이: 이웃끼리 앞뒤 + 처음 → 마지막.
	 */
	private void initWorkflow(SnCiraSpaceModel space, String presetId) {
		String p = space.getProjectId();
		String sid = space.getObjId();
		CiraFieldAccess.ProjectType preset = presetId == null ? null : fieldAccess.type(TenantContext.get(), presetId);
		List<PresetStatus> defs = preset == null ? EMPTY_SPACE_STATUSES : fieldService.presetStatuses(preset);
		List<SnCiraIssueStatusModel> sts = new java.util.ArrayList<>();
		for (int i = 0; i < defs.size(); i++) {
			PresetStatus d = defs.get(i);
			sts.add(status(p, sid, d.name(), d.category(), CATEGORY_COLOR.getOrDefault(d.category(), "#DFE1E6"), i + 1));
		}
		for (int i = 0; i + 1 < sts.size(); i++) {
			transition(p, sid, sts.get(i), sts.get(i + 1));
			transition(p, sid, sts.get(i + 1), sts.get(i));
		}
		if (sts.size() > 2) {
			transition(p, sid, sts.get(0), sts.get(sts.size() - 1));
		}
		String boardType = preset == null ? "KANBAN" : preset.boardType();
		SnCiraBoardModel board = SnCiraBoardModel.builder()
			.projectId(p).spaceId(sid).boardNm(space.getSpaceNm() + " Board").boardType(boardType)
			.srvId(ApplicationProperties.getApplicationServiceName()).tenant(TenantContext.get())
			.traceId("INIT-SPACE").useStatCd(UseStatCd.Usable).evtNm("CreateDefaultBoard").prevEvntNm("None")
			.build();
		boardAccess.save(board);
		short ord = 1;
		for (SnCiraIssueStatusModel st : sts) {
			columnAccess.save(SnCiraBoardColumnModel.builder()
				.boardId(board.getObjId()).statusId(st.getObjId()).columnNm(st.getStatusNm()).sortOrd(ord++)
				.srvId(ApplicationProperties.getApplicationServiceName()).tenant(TenantContext.get())
				.traceId("INIT-SPACE").useStatCd(UseStatCd.Usable).evtNm("CreateBoardColumn").prevEvntNm("None")
				.build());
		}
	}

	private SnCiraIssueStatusModel status(String projectId, String spaceId, String nm, String category, String color, int ord) {
		SnCiraIssueStatusModel s = SnCiraIssueStatusModel.builder()
			.projectId(projectId).spaceId(spaceId).statusNm(nm).category(category).colorCd(color).sortOrd((short)ord)
			.srvId(ApplicationProperties.getApplicationServiceName()).tenant(TenantContext.get())
			.traceId("INIT-SPACE").useStatCd(UseStatCd.Usable).evtNm("CreateStatus").prevEvntNm("None")
			.build();
		statusAccess.save(s);
		return s;
	}

	private void transition(String projectId, String spaceId, SnCiraIssueStatusModel from, SnCiraIssueStatusModel to) {
		transitionAccess.save(SnCiraIssueTransitionModel.builder()
			.projectId(projectId).spaceId(spaceId).fromStatusId(from.getObjId()).toStatusId(to.getObjId()).allowYn("Y")
			.srvId(ApplicationProperties.getApplicationServiceName()).tenant(TenantContext.get())
			.traceId("INIT-SPACE").useStatCd(UseStatCd.Usable).evtNm("CreateTransition").prevEvntNm("None")
			.build());
	}

	private boolean taken(String projectId, String name, String key) {
		return spaceAccess.findByProjectId(projectId).stream()
			.anyMatch(s -> (name != null && s.getSpaceNm().equals(name)) || (key != null && s.getSpaceKey().equals(key)));
	}

	private void requireProjectAdmin(String projectId) {
		if (!"ADMIN".equals(guard.requireMember(projectId).getRole())) {
			throw new org.springframework.security.access.AccessDeniedException("프로젝트 관리자만 변경할 수 있습니다");
		}
	}

	private void requireProjectMember(String projectId, String userId) {
		memberAccess.findByProjectIdAndUserId(projectId, userId)
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.orElseThrow(() -> new IllegalArgumentException("공간 관리자는 프로젝트 멤버여야 합니다: " + userId));
	}

	private Space toDto(SnCiraSpaceModel s) {
		String adminNm = userAccess.findByIdOptional(s.getAdminUserId()).map(GsUserModel::getUserNm).orElse(null);
		return new Space(s.getObjId(), s.getProjectId(), s.getSpaceNm(), s.getSpaceKey(), s.getDescr(), s.getPresetTypeId(),
			s.getAdminUserId(), adminNm, s.isDefault(), s.getSortOrd() == null ? 0 : s.getSortOrd(), guard.canManage(s));
	}

	static String name(String raw) {
		String n = raw == null ? "" : raw.strip();
		if (n.isEmpty() || n.length() > 60) {
			throw new IllegalArgumentException("작업 공간 이름은 1~60자입니다");
		}
		return n;
	}

	/** 영문 대문자·숫자·_ 만, 최대 20자. 이름에서 만들 때 남는 게 없으면 SPACE */
	static String key(String raw) {
		String k = raw == null ? "" : raw.strip().toUpperCase().replaceAll("[^A-Z0-9_]+", "_").replaceAll("^_+|_+$", "");
		if (k.isEmpty()) {
			k = "SPACE";
		}
		return k.length() > 20 ? k.substring(0, 20) : k;
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	/** 사용자에게 사유를 보여줄 충돌 (409). IllegalStateException 은 전역에서 500 이다 */
	private static ResponseStatusException conflict(String reason) {
		return new ResponseStatusException(HttpStatus.CONFLICT, reason);
	}
}
