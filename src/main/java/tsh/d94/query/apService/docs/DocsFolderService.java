package tsh.d94.app.server.apService.docs;

import static tsh.d94.app.server.apService.docs.DocsException.Code.*;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.docs.DocsDtos.FolderRequest;
import tsh.d94.app.server.apService.docs.DocsDtos.FolderResponse;
import tsh.d94.app.server.apService.docs.DocsDtos.MoveFolderRequest;
import tsh.d94.app.server.data.orm.docs.docsFolder.SnDocsFolderAccess;
import tsh.d94.app.server.data.orm.docs.docsFolder.SnDocsFolderModel;
import tsh.d94.app.server.data.orm.docs.docsNote.SnDocsNoteAccess;

/** 폴더 트리. 노트는 ID 로 폴더를 가리키므로 폴더 이름 변경·이동은 폴더 행 하나만 바뀐다. */
@Service
@RequiredArgsConstructor
public class DocsFolderService {

	private static final String FORBIDDEN = "/\\";

	private final SnDocsFolderAccess folderAccess;
	private final SnDocsNoteAccess noteAccess;
	private final DocsVaultService vaults;

	/** 없거나 접근 못 하는 vault 의 폴더면 404 */
	public SnDocsFolderModel requireOwned(String id) {
		return folderAccess.findByIdOptional(id).filter(f -> vaults.canAccess(f.getVaultId()))
			.orElseThrow(() -> new DocsException(NOT_FOUND));
	}

	/** vaultId 생략·ALL = 통합 뷰 */
	public List<FolderResponse> list(String vaultId) {
		return folderAccess.findByVaults(vaults.scope(vaultId)).stream().map(FolderResponse::of).toList();
	}

	/** vault 안의 폴더 경로를 이름으로 찾거나 만든다(멱등). 권한 검사는 호출 쪽. 마지막 폴더 id */
	@Transactional
	public String ensurePath(String vaultId, String... names) {
		String parentId = null;
		for (String raw : names) {
			String name = Docs.name(raw, 200, FORBIDDEN, "폴더 이름");
			String pid = parentId;
			parentId = folderAccess.findChild(vaultId, pid, name).orElseGet(() -> save(
				Docs.base(SnDocsFolderModel.builder(), "CreateDocsFolder")
					.ownerId(Docs.owner()).vaultId(vaultId).parentId(pid).name(name).sortOrder(0).build()))
				.getObjId();
		}
		return parentId;
	}

	@Transactional
	public FolderResponse create(FolderRequest req) {
		String parentId = blankToNull(req.parentId());
		String vaultId = parentId != null ? requireOwned(parentId).getVaultId() : vaults.target(req.vaultId());
		SnDocsFolderModel f = Docs.base(SnDocsFolderModel.builder(), "CreateDocsFolder")
			.ownerId(Docs.owner())
			.vaultId(vaultId)
			.parentId(parentId)
			.name(Docs.name(req.name(), 200, FORBIDDEN, "폴더 이름"))
			.sortOrder(0)
			.build();
		return FolderResponse.of(save(f));
	}

	@Transactional
	public FolderResponse rename(String id, FolderRequest req) {
		SnDocsFolderModel f = requireOwned(id);
		f.setName(Docs.name(req.name(), 200, FORBIDDEN, "폴더 이름"));
		return FolderResponse.of(save(f));
	}

	@Transactional
	public FolderResponse move(String id, MoveFolderRequest req) {
		SnDocsFolderModel f = requireOwned(id);
		String parentId = blankToNull(req.parentId());
		if (parentId != null) {
			Map<String, SnDocsFolderModel> all = folderAccess.findByVaults(List.of(f.getVaultId())).stream()
				.collect(Collectors.toMap(SnDocsFolderModel::getObjId, Function.identity()));
			if (!all.containsKey(parentId)) {
				throw new DocsException(NOT_FOUND);
			}
			// 새 상위에서 루트까지 올라가며 자기 자신을 만나면 순환
			for (String p = parentId; p != null; p = all.get(p) == null ? null : all.get(p).getParentId()) {
				if (p.equals(id)) {
					throw new DocsException(INVALID_MOVE);
				}
			}
		}
		f.setParentId(parentId);
		if (req.sortOrder() != null) {
			f.setSortOrder(req.sortOrder());
		}
		return FolderResponse.of(save(f));
	}

	@Transactional
	public void delete(String id) {
		SnDocsFolderModel f = requireOwned(id);
		if (folderAccess.countChildFolders(id) > 0 || noteAccess.countByFolder(id) > 0) {
			throw new DocsException(FOLDER_NOT_EMPTY);
		}
		folderAccess.remove(f);
	}

	private SnDocsFolderModel save(SnDocsFolderModel f) {
		try {
			return folderAccess.saveAndFlush(f);
		} catch (DataIntegrityViolationException e) {
			throw new DocsException(DUPLICATE_FOLDER);
		}
	}

	static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s;
	}
}
