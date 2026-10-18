package tsh.d94.app.server.data.orm.cira.ciraAutoRule;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraAutoRuleAccess extends AbstractCrudService<SnCiraAutoRuleModel, String> {

	@Autowired
	SnCiraAutoRuleRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraAutoRuleModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraAutoRuleModel> findActiveByProjectId(String projectId) {
		return repo.findByProjectIdAndIsActiveTrueOrderBySortOrdAsc(projectId);
	}

	public List<SnCiraAutoRuleModel> findByProjectId(String projectId) {
		return repo.findByProjectIdOrderBySortOrdAsc(projectId);
	}

	public List<SnCiraAutoRuleModel> findActiveByProjectIdAndTriggerType(String projectId, String triggerType) {
		return repo.findActiveByProjectIdAndTriggerType(projectId, triggerType);
	}

}
