package tsh.d94.app.server.data.orm.cira.ciraAutoExecution;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraAutoExecutionRepo extends BaseJpaRepository<SnCiraAutoExecutionModel, String> {

	List<SnCiraAutoExecutionModel> findByRuleIdOrderByExecutedAtDesc(String ruleId, Pageable pageable);

	@Query("SELECT e FROM SnCiraAutoExecutionModel e WHERE e.ruleId = :ruleId AND e.issueId = :issueId AND e.executedAt >= :since")
	List<SnCiraAutoExecutionModel> findRecentByRuleIdAndIssueId(
		@Param("ruleId") String ruleId,
		@Param("issueId") String issueId,
		@Param("since") OffsetDateTime since
	);

	List<SnCiraAutoExecutionModel> findByRuleIdOrderByExecutedAtDesc(String ruleId);

}
