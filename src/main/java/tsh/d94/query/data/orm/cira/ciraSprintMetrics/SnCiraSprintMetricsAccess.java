package tsh.d94.app.server.data.orm.cira.ciraSprintMetrics;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraSprintMetricsAccess extends AbstractCrudService<SnCiraSprintMetricsModel, String> {

	@Autowired
	SnCiraSprintMetricsRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraSprintMetricsModel, String> getRepository() {
		return repo;
	}

	public Optional<SnCiraSprintMetricsModel> findBySprintId(String sprintId) {
		return repo.findBySprintId(sprintId);
	}

	public List<SnCiraSprintMetricsModel> findBySprintIdIn(List<String> sprintIds) {
		return repo.findBySprintIdIn(sprintIds);
	}

}
