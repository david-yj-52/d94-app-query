package tsh.d94.app.server.data.orm.docs.docsFolder;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnDocsFolderRepo extends BaseJpaRepository<SnDocsFolderModel, String> {

	List<SnDocsFolderModel> findByOwnerIdOrderBySortOrderAscNameAsc(String ownerId);

	Optional<SnDocsFolderModel> findByObjIdAndOwnerId(String objId, String ownerId);

	long countByParentId(String parentId);
}
