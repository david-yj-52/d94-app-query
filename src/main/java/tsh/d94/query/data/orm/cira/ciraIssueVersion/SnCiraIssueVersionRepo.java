package tsh.d94.app.server.data.orm.cira.ciraIssueVersion;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraIssueVersionRepo extends BaseJpaRepository<SnCiraIssueVersionModel, String> {

	List<SnCiraIssueVersionModel> findByVersionId(String versionId);

	List<SnCiraIssueVersionModel> findByVersionIdAndRelType(String versionId, String relType);

	List<SnCiraIssueVersionModel> findByIssueId(String issueId);

	Optional<SnCiraIssueVersionModel> findByIssueIdAndVersionIdAndRelType(
		String issueId, String versionId, String relType);

}
