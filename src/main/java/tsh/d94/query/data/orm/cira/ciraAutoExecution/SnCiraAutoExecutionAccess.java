package tsh.d94.app.server.data.orm.cira.ciraAutoExecution;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraAutoExecutionAccess extends AbstractCrudService<SnCiraAutoExecutionModel, String> {

	@Autowired
	SnCiraAutoExecutionRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraAutoExecutionModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraAutoExecutionModel> findRecentByRuleIdAndIssueId(String ruleId, String issueId,
		OffsetDateTime since) {
		return repo.findRecentByRuleIdAndIssueId(ruleId, issueId, since);
	}

	public List<SnCiraAutoExecutionModel> findByRuleId(String ruleId) {
		return repo.findByRuleIdOrderByExecutedAtDesc(ruleId);
	}

	public List<SnCiraAutoExecutionModel> findByRuleIdPaged(String ruleId, int size) {
		return repo.findByRuleIdOrderByExecutedAtDesc(ruleId, PageRequest.of(0, size));
	}

}
