package tsh.d94.app.server.data.orm.cira.ciraGitPr;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraGitPrAccess extends AbstractCrudService<SnCiraGitPrModel, String> {

	@Autowired
	SnCiraGitPrRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraGitPrModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraGitPrModel> findByRepoId(String repoId) {
		return repo.findByRepoId(repoId);
	}

	public List<SnCiraGitPrModel> findByIssueId(String issueId) {
		return repo.findByIssueId(issueId);
	}

	public Optional<SnCiraGitPrModel> findByRepoIdAndPrNo(String repoId, Integer prNo) {
		return repo.findByRepoIdAndPrNo(repoId, prNo);
	}

}
