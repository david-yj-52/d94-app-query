package tsh.d94.app.server.data.orm.cira.ciraNotifPref;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraNotifPrefAccess extends AbstractCrudService<SnCiraNotifPrefModel, String> {

	@Autowired
	SnCiraNotifPrefRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraNotifPrefModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraNotifPrefModel> findByUserId(String userId) {
		return repo.findByUserId(userId);
	}

	public Optional<SnCiraNotifPrefModel> findByUserIdAndChannelAndEventType(
		String userId, String channel, String eventType) {
		return repo.findByUserIdAndChannelAndEventType(userId, channel, eventType);
	}

}
