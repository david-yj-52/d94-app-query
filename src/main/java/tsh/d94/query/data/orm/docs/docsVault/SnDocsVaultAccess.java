package tsh.d94.app.server.data.orm.docs.docsVault;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnDocsVaultAccess extends AbstractCrudService<SnDocsVaultModel, String> {

	@Autowired
	SnDocsVaultRepo repo;

	@Override
	protected BaseJpaRepository<SnDocsVaultModel, String> getRepository() {
		return repo;
	}

	public Optional<SnDocsVaultModel> findPrivate(String ownerId) {
		return repo.findByVaultTypeAndOwnerId(SnDocsVaultModel.PRIVATE, ownerId);
	}

	public Optional<SnDocsVaultModel> findByProject(String projectId) {
		return repo.findByProjectId(projectId);
	}

	public List<SnDocsVaultModel> findByProjects(Collection<String> projectIds) {
		return projectIds.isEmpty() ? List.of() : repo.findByProjectIdIn(projectIds);
	}

	public SnDocsVaultModel saveAndFlush(SnDocsVaultModel m) {
		return repo.saveAndFlush(m);
	}
}
