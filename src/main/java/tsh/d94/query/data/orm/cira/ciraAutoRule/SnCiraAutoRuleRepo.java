package tsh.d94.app.server.data.orm.cira.ciraAutoRule;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraAutoRuleRepo extends BaseJpaRepository<SnCiraAutoRuleModel, String> {

	List<SnCiraAutoRuleModel> findByProjectIdAndIsActiveTrueOrderBySortOrdAsc(String projectId);

	List<SnCiraAutoRuleModel> findByProjectIdOrderBySortOrdAsc(String projectId);

	@Query("SELECT r FROM SnCiraAutoRuleModel r WHERE r.projectId = :projectId AND r.isActive = true AND r.triggerType = :triggerType ORDER BY r.sortOrd ASC")
	List<SnCiraAutoRuleModel> findActiveByProjectIdAndTriggerType(
		@Param("projectId") String projectId,
		@Param("triggerType") String triggerType
	);

}
