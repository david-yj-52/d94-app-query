package tsh.d94.app.server.apService.docs;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import tsh.d94.app.server.data.orm.docs.docsFolder.SnDocsFolderModel;
import tsh.d94.app.server.data.orm.docs.docsIndex.DocsIndexAccess.NoteRow;
import tsh.d94.app.server.data.orm.docs.docsVault.SnDocsVaultModel;

/** docs API 요청·응답. 필드명은 웹 `src/types/docs.ts` 와 짝. ponytail: 파일 하나에 모음. */
public final class DocsDtos {

	private DocsDtos() {
	}

	/** type: PRIVATE | TEAM. projectId 는 TEAM 일 때 */
	public record VaultResponse(String id, String type, String name, String projectId) {
		public static VaultResponse of(SnDocsVaultModel v) {
			return new VaultResponse(v.getObjId(), v.getVaultType(), v.getName(), v.getProjectId());
		}
	}

	/** vaultId: 최상위 생성 때 대상 vault (생략 = 내 Private). parentId 가 있으면 그 폴더의 vault */
	public record FolderRequest(String name, String parentId, String vaultId) {
		public FolderRequest(String name, String parentId) {
			this(name, parentId, null);
		}
	}

	public record MoveFolderRequest(String parentId, Integer sortOrder) {
	}

	public record FolderResponse(String id, String vaultId, String parentId, String name, int sortOrder) {
		public static FolderResponse of(SnDocsFolderModel f) {
			return new FolderResponse(f.getObjId(), f.getVaultId(), f.getParentId(), f.getName(), f.getSortOrder());
		}
	}

	/** vaultId: 생성 때 대상 vault (생략 = 내 Private). folderId 가 있으면 그 폴더의 vault */
	public record NoteRequest(String title, String content, String folderId, Integer version, String vaultId) {
		public NoteRequest(String title, String content, String folderId, Integer version) {
			this(title, content, folderId, version, null);
		}
	}

	public record MoveNoteRequest(String folderId) {
	}

	public record NoteSummary(String id, String vaultId, String title, String folderId, String kind, String stream,
		LocalDateTime modifiedAt) {
		public static NoteSummary of(NoteRow r) {
			return new NoteSummary(r.id(), r.vaultId(), r.title(), r.folderId(), r.kind(), r.stream(), r.modifiedAt());
		}
	}

	/** exists=false 면 아직 없는 노트를 가리키는 링크 */
	public record LinkRef(String title, String rel, boolean exists) {
	}

	public record NoteResponse(String id, String vaultId, String title, String content, String folderId, String kind, String stream,
		String origin, int version, List<String> tags, List<LinkRef> links, LocalDateTime createdAt,
		LocalDateTime modifiedAt) {
	}

	public record VersionResponse(int version, String title, String content, String editedBy,
		LocalDateTime editedAt) {
	}

	public record DeriveRequest(String title, String stream, String kind) {
	}

	public record KindRequest(String kind) {
	}

	public record TaskResponse(String noteId, String vaultId, String noteTitle, int lineNo, String text, String status,
		LocalDate dueDt) {
	}

	public record TaskStatusRequest(String status) {
	}

	public record LineageNode(String id, String title, String kind, String stream, String origin) {
	}
}
