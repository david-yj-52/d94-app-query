package tsh.d94.app.server.data.orm.cira.ciraGitRepo;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraGitRepoAccess extends AbstractCrudService<SnCiraGitRepoModel, String> {

	@Autowired
	SnCiraGitRepoRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraGitRepoModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraGitRepoModel> findByProjectId(String projectId) {
		return repo.findByProjectId(projectId);
	}

	public Optional<SnCiraGitRepoModel> findByRepoUrl(String repoUrl) {
		return repo.findByRepoUrl(repoUrl);
	}

}
