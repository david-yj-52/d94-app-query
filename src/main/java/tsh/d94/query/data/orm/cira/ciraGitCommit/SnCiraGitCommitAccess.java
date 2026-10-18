package tsh.d94.app.server.data.orm.cira.ciraGitCommit;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraGitCommitAccess extends AbstractCrudService<SnCiraGitCommitModel, String> {

	@Autowired
	SnCiraGitCommitRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraGitCommitModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraGitCommitModel> findByRepoId(String repoId) {
		return repo.findByRepoId(repoId);
	}

	public List<SnCiraGitCommitModel> findByIssueId(String issueId) {
		return repo.findByIssueId(issueId);
	}

	public Optional<SnCiraGitCommitModel> findByRepoIdAndCommitHash(String repoId, String commitHash) {
		return repo.findByRepoIdAndCommitHash(repoId, commitHash);
	}

}
