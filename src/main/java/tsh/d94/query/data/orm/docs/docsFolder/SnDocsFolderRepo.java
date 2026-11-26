package tsh.d94.app.server.data.orm.docs.docsFolder;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnDocsFolderRepo extends BaseJpaRepository<SnDocsFolderModel, String> {

	List<SnDocsFolderModel> findByVaultIdInOrderBySortOrderAscNameAsc(Collection<String> vaultIds);

	Optional<SnDocsFolderModel> findByVaultIdAndParentIdAndName(String vaultId, String parentId, String name);

	Optional<SnDocsFolderModel> findByVaultIdAndParentIdIsNullAndName(String vaultId, String name);

	long countByParentId(String parentId);
}
