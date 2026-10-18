package tsh.d94.app.server.data.orm.cira.ciraIssueTransition;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraIssueTransitionAccess extends AbstractCrudService<SnCiraIssueTransitionModel, String> {

	@Autowired
	SnCiraIssueTransitionRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraIssueTransitionModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraIssueTransitionModel> findByProjectId(String projectId) {
		return repo.findByProjectId(projectId);
	}

	public List<SnCiraIssueTransitionModel> findByProjectIdAndFromStatusId(String projectId, String fromStatusId) {
		return repo.findByProjectIdAndFromStatusId(projectId, fromStatusId);
	}

}
