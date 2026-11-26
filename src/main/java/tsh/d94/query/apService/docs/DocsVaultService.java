package tsh.d94.app.server.apService.docs;

import static tsh.d94.app.server.apService.docs.DocsException.Code.*;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.docs.DocsDtos.VaultResponse;
import tsh.d94.app.server.data.orm.cira.ciraProject.SnCiraProjectModel;
import tsh.d94.app.server.data.orm.cira.ciraProjectMember.SnCiraProjectMemberAccess;
import tsh.d94.app.server.data.orm.docs.docsVault.SnDocsVaultAccess;
import tsh.d94.app.server.data.orm.docs.docsVault.SnDocsVaultModel;
import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;

/**
 * vault 접근: PRIVATE 는 소유자(이메일)만, TEAM 은 그 CIRA 프로젝트의 Usable 멤버만. 접근 못 하면 404(존재 숨김).
 * 조회 범위 vaultId: 생략·"ALL" = 통합 뷰(내 Private + 소속 Team), 아니면 그 vault 하나.
 */
@Service
@RequiredArgsConstructor
public class DocsVaultService {

	public static final String ALL = "ALL";

	private final SnDocsVaultAccess vaultAccess;
	private final GsUserAccess userAccess;
	private final SnCiraProjectMemberAccess memberAccess;

	/** 내 Private + 소속 Team vault. ponytail: Team vault 는 그 프로젝트에 첫 Task 가 생길 때 만들어진다. */
	public List<VaultResponse> vaults() {
		List<SnDocsVaultModel> all = new ArrayList<>();
		all.add(privateVault());
		all.addAll(vaultAccess.findByProjects(memberProjectIds()));
		return all.stream().map(VaultResponse::of).toList();
	}

	public List<String> scope(String vaultId) {
		if (vaultId == null || vaultId.isBlank() || ALL.equals(vaultId)) {
			List<String> ids = new ArrayList<>();
			vaultAccess.findPrivate(Docs.owner()).ifPresent(v -> ids.add(v.getObjId()));
			vaultAccess.findByProjects(memberProjectIds()).forEach(v -> ids.add(v.getObjId()));
			return ids;
		}
		requireAccess(vaultId);
		return List.of(vaultId);
	}

	/** 새 폴더·노트를 만들 vault: 생략·ALL 이면 내 Private */
	public String target(String vaultId) {
		if (vaultId == null || vaultId.isBlank() || ALL.equals(vaultId)) {
			return privateVault().getObjId();
		}
		requireAccess(vaultId);
		return vaultId;
	}

	public void requireAccess(String vaultId) {
		if (!canAccess(vaultId)) {
			throw new DocsException(NOT_FOUND);
		}
	}

	public boolean canAccess(String vaultId) {
		SnDocsVaultModel v = vaultAccess.findByIdOptional(vaultId).orElse(null);
		if (v == null) {
			return false;
		}
		if (SnDocsVaultModel.PRIVATE.equals(v.getVaultType())) {
			return Docs.owner().equals(v.getOwnerId());
		}
		return memberProjectIds().contains(v.getProjectId());
	}

	public SnDocsVaultModel privateVault() {
		return vaultAccess.findPrivate(Docs.owner()).orElseGet(() -> vaultAccess.saveAndFlush(
			Docs.base(SnDocsVaultModel.builder(), "CreateDocsVault")
				.vaultType(SnDocsVaultModel.PRIVATE).name("Private").ownerId(Docs.owner()).build()));
	}

	/** 프로젝트 Team vault (없으면 만든다). 권한 검사는 호출 쪽(CIRA 멤버 확인)이 한다. */
	public SnDocsVaultModel teamVault(SnCiraProjectModel p) {
		return vaultAccess.findByProject(p.getObjId()).orElseGet(() -> vaultAccess.saveAndFlush(
			Docs.base(SnDocsVaultModel.builder(), "CreateDocsVault")
				.vaultType(SnDocsVaultModel.TEAM)
				.name("[" + p.getProjectKey() + "] " + p.getProjectNm())
				.projectId(p.getObjId()).build()));
	}

	private List<String> memberProjectIds() {
		return userAccess.findByEmail(Docs.owner())
			.map(u -> memberAccess.findAllByUserId(u.getObjId()).stream()
				.filter(m -> UseStatCd.Usable.equals(m.getUseStatCd()))
				.map(m -> m.getProjectId()).toList())
			.orElse(List.of());
	}
}
