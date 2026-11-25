package tsh.d94.app.server.data.orm.docs.docsFolder;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnDocsFolderAccess extends AbstractCrudService<SnDocsFolderModel, String> {

	@Autowired
	SnDocsFolderRepo repo;

	@Override
	protected BaseJpaRepository<SnDocsFolderModel, String> getRepository() {
		return repo;
	}

	public List<SnDocsFolderModel> findByOwner(String ownerId) {
		return repo.findByOwnerIdOrderBySortOrderAscNameAsc(ownerId);
	}

	public Optional<SnDocsFolderModel> findOwned(String id, String ownerId) {
		return repo.findByObjIdAndOwnerId(id, ownerId);
	}

	public long countChildFolders(String parentId) {
		return repo.countByParentId(parentId);
	}

	public SnDocsFolderModel saveAndFlush(SnDocsFolderModel m) {
		return repo.saveAndFlush(m);
	}

	/** 물리 삭제 (상위 delete 는 소프트 삭제라 쓰지 않는다) */
	public void remove(SnDocsFolderModel m) {
		repo.delete(m);
	}
}
