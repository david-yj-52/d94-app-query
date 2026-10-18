package tsh.d94.app.server.data.orm.cira.ciraComment;

import java.util.List;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraCommentRepo extends BaseJpaRepository<SnCiraCommentModel, String> {

	List<SnCiraCommentModel> findByIssueIdOrderByCreatedAtAsc(String issueId);

	List<SnCiraCommentModel> findByParentIdOrderByCreatedAtAsc(String parentId);
}
