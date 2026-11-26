package tsh.d94.app.server.data.orm.docs.docsVault;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnDocsVaultRepo extends BaseJpaRepository<SnDocsVaultModel, String> {

	Optional<SnDocsVaultModel> findByVaultTypeAndOwnerId(String vaultType, String ownerId);

	Optional<SnDocsVaultModel> findByProjectId(String projectId);

	List<SnDocsVaultModel> findByProjectIdIn(Collection<String> projectIds);
}
