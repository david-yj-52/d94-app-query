package tsh.d94.app.server.apService.cira;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberModel;
import tsh.d94.app.server.data.orm.cira.ciraSpace.SnCiraSpaceAccess;
import tsh.d94.app.server.data.orm.cira.ciraSpace.SnCiraSpaceModel;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;

/**
 * 작업 공간 찾기·권한. 필드·워크플로·보드·산출물 서비스가 같이 쓴다(SpaceService ↔ 그 서비스들 순환 방지).
 * 공간 설정 수정 = 프로젝트 ADMIN 또는 그 공간의 ADMIN_USER_ID. 조회·Task 작업 = 프로젝트 멤버 누구나.
 */
@Component
@RequiredArgsConstructor
public class SpaceGuard {

	private final SnCiraSpaceAccess spaceAccess;
	private final SnCiraProjectMemberAccess memberAccess;
	private final GsUserAccess userAccess;

	/** spaceId 가 null 이면 기본 공간 (기존 /projects/{id}/... 경로 하위 호환). 다른 프로젝트의 공간이면 404 */
	public SnCiraSpaceModel resolve(String projectId, String spaceId) {
		if (spaceId == null || spaceId.isBlank()) {
			return spaceAccess.findDefault(projectId)
				.orElseThrow(() -> new EntityNotFoundException("기본 작업 공간이 없습니다: " + projectId));
		}
		SnCiraSpaceModel s = get(spaceId);
		if (!s.getProjectId().equals(projectId)) {
			throw new EntityNotFoundException("Space not found: " + spaceId);
		}
		return s;
	}

	public SnCiraSpaceModel get(String spaceId) {
		return spaceAccess.findByIdOptional(spaceId)
			.orElseThrow(() -> new EntityNotFoundException("Space not found: " + spaceId));
	}

	public SnCiraProjectMemberModel requireMember(String projectId) {
		return memberAccess.findByProjectIdAndUserId(projectId, currentUserId())
			.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
			.orElseThrow(() -> new AccessDeniedException("프로젝트 멤버가 아닙니다"));
	}

	public boolean canManage(SnCiraSpaceModel space) {
		SnCiraProjectMemberModel m = memberAccess.findByProjectIdAndUserId(space.getProjectId(), currentUserId())
			.filter(x -> UseStatCd.Usable.equals(x.getUseStatCd())).orElse(null);
		return m != null && ("ADMIN".equals(m.getRole()) || m.getUserId().equals(space.getAdminUserId()));
	}

	public SnCiraSpaceModel requireManager(SnCiraSpaceModel space) {
		if (!canManage(space)) {
			throw new AccessDeniedException("프로젝트 관리자나 공간 관리자만 변경할 수 있습니다");
		}
		return space;
	}

	public String currentUserId() {
		String email = SecurityContextHolder.getContext().getAuthentication().getName();
		return userAccess.findByEmail(email)
			.orElseThrow(() -> new AccessDeniedException("User not found: " + email)).getObjId();
	}
}
