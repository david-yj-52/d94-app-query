package tsh.d94.app.server.data.orm.cira.ciraIssueLog;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraIssueLogAccess extends AbstractCrudService<SnCiraIssueLogModel, String> {

	@Autowired
	SnCiraIssueLogRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraIssueLogModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraIssueLogModel> findStatusLogsByIssueIds(List<String> issueIds) {
		return repo.findStatusLogsByIssueIds(issueIds);
	}

	public List<SnCiraIssueLogModel> findRecentActivityByUser(String userId, LocalDateTime since) {
		return repo.findRecentActivityByUser(userId, since);
	}

}
