package tsh.d94.app.server.data.orm.cira.ciraNotifPref;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraNotifPrefRepo extends BaseJpaRepository<SnCiraNotifPrefModel, String> {

	List<SnCiraNotifPrefModel> findByUserId(String userId);

	Optional<SnCiraNotifPrefModel> findByUserIdAndChannelAndEventType(
		String userId, String channel, String eventType);

}
