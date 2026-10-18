package tsh.d94.app.server.data.orm.cira.ciraIssueStatus;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraIssueStatusRepo extends BaseJpaRepository<SnCiraIssueStatusModel, String> {

	List<SnCiraIssueStatusModel> findByProjectIdOrderBySortOrd(String projectId);

	Optional<SnCiraIssueStatusModel> findByProjectIdAndStatusNm(String projectId, String statusNm);

	List<SnCiraIssueStatusModel> findByProjectIdAndCategory(String projectId, String category);

	List<SnCiraIssueStatusModel> findByCategory(String category);

}
