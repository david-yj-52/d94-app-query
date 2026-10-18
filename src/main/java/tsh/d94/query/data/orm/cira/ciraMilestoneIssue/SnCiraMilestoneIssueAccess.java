package tsh.d94.app.server.data.orm.cira.ciraMilestoneIssue;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraMilestoneIssueAccess extends AbstractCrudService<SnCiraMilestoneIssueModel, String> {

	@Autowired
	SnCiraMilestoneIssueRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraMilestoneIssueModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraMilestoneIssueModel> findByMilestoneId(String milestoneId) {
		return repo.findByMilestoneId(milestoneId);
	}

	public List<SnCiraMilestoneIssueModel> findByIssueId(String issueId) {
		return repo.findByIssueId(issueId);
	}

	public Optional<SnCiraMilestoneIssueModel> findByMilestoneIdAndIssueId(String milestoneId, String issueId) {
		return repo.findByMilestoneIdAndIssueId(milestoneId, issueId);
	}

}
