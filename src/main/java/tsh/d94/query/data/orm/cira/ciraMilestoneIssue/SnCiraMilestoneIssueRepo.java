package tsh.d94.app.server.data.orm.cira.ciraMilestoneIssue;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraMilestoneIssueRepo extends BaseJpaRepository<SnCiraMilestoneIssueModel, String> {

	List<SnCiraMilestoneIssueModel> findByMilestoneId(String milestoneId);

	List<SnCiraMilestoneIssueModel> findByIssueId(String issueId);

	Optional<SnCiraMilestoneIssueModel> findByMilestoneIdAndIssueId(String milestoneId, String issueId);

}
