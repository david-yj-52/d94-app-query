package tsh.d94.app.server.data.orm.cira.ciraIssueVersion;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraIssueVersionAccess extends AbstractCrudService<SnCiraIssueVersionModel, String> {

	@Autowired
	SnCiraIssueVersionRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraIssueVersionModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraIssueVersionModel> findByVersionId(String versionId) {
		return repo.findByVersionId(versionId);
	}

	public List<SnCiraIssueVersionModel> findByVersionIdAndRelType(String versionId, String relType) {
		return repo.findByVersionIdAndRelType(versionId, relType);
	}

	public List<SnCiraIssueVersionModel> findByIssueId(String issueId) {
		return repo.findByIssueId(issueId);
	}

	public Optional<SnCiraIssueVersionModel> findByIssueIdAndVersionIdAndRelType(
		String issueId, String versionId, String relType) {
		return repo.findByIssueIdAndVersionIdAndRelType(issueId, versionId, relType);
	}

}
