package tsh.d94.app.server.data.orm.cira.ciraGitPr;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraGitPrRepo extends BaseJpaRepository<SnCiraGitPrModel, String> {

	List<SnCiraGitPrModel> findByRepoId(String repoId);

	List<SnCiraGitPrModel> findByIssueId(String issueId);

	Optional<SnCiraGitPrModel> findByRepoIdAndPrNo(String repoId, Integer prNo);
}
