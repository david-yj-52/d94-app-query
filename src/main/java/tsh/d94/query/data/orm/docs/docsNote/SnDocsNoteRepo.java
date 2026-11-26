package tsh.d94.app.server.data.orm.docs.docsNote;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnDocsNoteRepo extends BaseJpaRepository<SnDocsNoteModel, String> {

	Optional<SnDocsNoteModel> findByVaultIdAndTitle(String vaultId, String title);

	long countByFolderId(String folderId);
}
