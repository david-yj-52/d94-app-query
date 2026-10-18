package tsh.d94.app.server.data.orm.cira.ciraGitCommit;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraGitCommitRepo extends BaseJpaRepository<SnCiraGitCommitModel, String> {

	List<SnCiraGitCommitModel> findByRepoId(String repoId);

	List<SnCiraGitCommitModel> findByIssueId(String issueId);

	Optional<SnCiraGitCommitModel> findByRepoIdAndCommitHash(String repoId, String commitHash);
}
