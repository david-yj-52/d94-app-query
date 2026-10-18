package tsh.d94.app.server.data.orm.cira.ciraIssueStatus;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraIssueStatusAccess extends AbstractCrudService<SnCiraIssueStatusModel, String> {

	@Autowired
	SnCiraIssueStatusRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraIssueStatusModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraIssueStatusModel> findByProjectId(String projectId) {
		return repo.findByProjectIdOrderBySortOrd(projectId);
	}

	public Optional<SnCiraIssueStatusModel> findByProjectIdAndStatusNm(String projectId, String statusNm) {
		return repo.findByProjectIdAndStatusNm(projectId, statusNm);
	}

	public Set<String> findDoneStatusIdsByProject(String projectId) {
		List<SnCiraIssueStatusModel> doneStatuses = repo.findByProjectIdAndCategory(projectId, "DONE");
		if (doneStatuses.isEmpty()) {
			doneStatuses = repo.findByCategory("DONE");
		}
		return doneStatuses.stream().map(SnCiraIssueStatusModel::getObjId).collect(Collectors.toSet());
	}

	public List<SnCiraIssueStatusModel> findAllByProject(String projectId) {
		return repo.findByProjectIdOrderBySortOrd(projectId);
	}

}
