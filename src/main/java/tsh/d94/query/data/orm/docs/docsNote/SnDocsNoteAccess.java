package tsh.d94.app.server.data.orm.docs.docsNote;

import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnDocsNoteAccess extends AbstractCrudService<SnDocsNoteModel, String> {

	@Autowired
	SnDocsNoteRepo repo;

	@Override
	protected BaseJpaRepository<SnDocsNoteModel, String> getRepository() {
		return repo;
	}

	public Optional<SnDocsNoteModel> findByTitle(String vaultId, String title) {
		return repo.findByVaultIdAndTitle(vaultId, title);
	}

	public long countByFolder(String folderId) {
		return repo.countByFolderId(folderId);
	}

	/** 제목 유일 위반을 저장 시점에 바로 받기 위해 flush 한다 */
	public SnDocsNoteModel saveAndFlush(SnDocsNoteModel m) {
		return repo.saveAndFlush(m);
	}

	public void remove(SnDocsNoteModel m) {
		repo.delete(m);
	}
}
