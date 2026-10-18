package tsh.d94.app.server.data.orm.cira.ciraCommentReaction;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraCommentReactionAccess extends AbstractCrudService<SnCiraCommentReactionModel, String> {

	@Autowired
	SnCiraCommentReactionRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraCommentReactionModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraCommentReactionModel> findByCommentId(String commentId) {
		return repo.findByCommentId(commentId);
	}

	public Optional<SnCiraCommentReactionModel> findByCommentIdAndUserIdAndReactionType(
		String commentId, String userId, String reactionType) {
		return repo.findByCommentIdAndUserIdAndReactionType(commentId, userId, reactionType);
	}

}
