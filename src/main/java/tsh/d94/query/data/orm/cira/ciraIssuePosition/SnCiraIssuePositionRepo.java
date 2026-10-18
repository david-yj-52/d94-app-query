package tsh.d94.app.server.data.orm.cira.ciraIssuePosition;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraIssuePositionRepo extends BaseJpaRepository<SnCiraIssuePositionModel, String> {

	List<SnCiraIssuePositionModel> findByIssueId(String issueId);

	List<SnCiraIssuePositionModel> findByColumnIdOrderByRankStr(String columnId);

	List<SnCiraIssuePositionModel> findByColumnId(String columnId);

	Optional<SnCiraIssuePositionModel> findByIssueIdAndColumnId(String issueId, String columnId);
}
