package tsh.d94.app.server.data.orm.cira.ciraIssuePosition;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraIssuePositionAccess extends AbstractCrudService<SnCiraIssuePositionModel, String> {

	@Autowired
	SnCiraIssuePositionRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraIssuePositionModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraIssuePositionModel> findByIssueId(String issueId) {
		return repo.findByIssueId(issueId);
	}

	public List<SnCiraIssuePositionModel> findByColumnIdOrderByRankStr(String columnId) {
		return repo.findByColumnIdOrderByRankStr(columnId);
	}

	public List<SnCiraIssuePositionModel> findByColumnId(String columnId) {
		return repo.findByColumnId(columnId);
	}

	public Optional<SnCiraIssuePositionModel> findByIssueIdAndColumnId(String issueId, String columnId) {
		return repo.findByIssueIdAndColumnId(issueId, columnId);
	}

}
