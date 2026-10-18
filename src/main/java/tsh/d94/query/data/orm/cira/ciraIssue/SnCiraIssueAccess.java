package tsh.d94.app.server.data.orm.cira.ciraIssue;

import java.time.LocalDate;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraIssueAccess extends AbstractCrudService<SnCiraIssueModel, String> {

	@Autowired
	SnCiraIssueRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraIssueModel, String> getRepository() {
		return repo;
	}

	public Page<SnCiraIssueModel> findAll(Specification<SnCiraIssueModel> spec, Pageable pageable) {
		return repo.findAll(spec, pageable);
	}

	public List<SnCiraIssueModel> findBySprintId(String sprintId) {
		return repo.findBySprintIdAndDeletedAtIsNull(sprintId);
	}

	public List<SnCiraIssueModel> findByProjectId(String projectId) {
		return repo.findByProjectIdAndDeletedAtIsNull(projectId);
	}

	public List<SnCiraIssueModel> findByAssigneeId(String assigneeId) {
		return repo.findByAssigneeIdAndDeletedAtIsNull(assigneeId);
	}

	public List<SnCiraIssueModel> findUpcomingDeadlines(String userId, LocalDate deadline) {
		return repo.findUpcomingDeadlines(userId, deadline);
	}

	public List<SnCiraIssueModel> findBySprintIds(List<String> sprintIds) {
		return repo.findBySprintIds(sprintIds);
	}

	public java.util.Optional<SnCiraIssueModel> findByIssueKey(String issueKey) {
		return repo.findByIssueKeyAndDeletedAtIsNull(issueKey);
	}

}
