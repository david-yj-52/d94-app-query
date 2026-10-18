package tsh.d94.app.server.data.orm.cira.ciraGitRepo;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraGitRepoRepo extends BaseJpaRepository<SnCiraGitRepoModel, String> {

	List<SnCiraGitRepoModel> findByProjectId(String projectId);

	Optional<SnCiraGitRepoModel> findByRepoUrl(String repoUrl);
}
