package tsh.d94.app.server.data.orm.cira.ciraIssueLog;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraIssueLogRepo extends BaseJpaRepository<SnCiraIssueLogModel, String> {

	List<SnCiraIssueLogModel> findByIssueIdOrderByChangedAtAsc(String issueId);

	@Query("SELECT l FROM SnCiraIssueLogModel l WHERE l.issueId IN :issueIds AND l.fieldNm = 'status' ORDER BY l.changedAt ASC")
	List<SnCiraIssueLogModel> findStatusLogsByIssueIds(@Param("issueIds") List<String> issueIds);

	@Query("SELECT l FROM SnCiraIssueLogModel l WHERE l.changedBy = :userId AND l.changedAt >= :since ORDER BY l.changedAt DESC")
	List<SnCiraIssueLogModel> findRecentActivityByUser(@Param("userId") String userId,
		@Param("since") LocalDateTime since);

}
