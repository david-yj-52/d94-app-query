package tsh.d94.app.server.data.orm.cira.ciraSprintMetrics;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraSprintMetricsRepo extends BaseJpaRepository<SnCiraSprintMetricsModel, String> {

	Optional<SnCiraSprintMetricsModel> findBySprintId(String sprintId);

	List<SnCiraSprintMetricsModel> findBySprintIdIn(List<String> sprintIds);

}
