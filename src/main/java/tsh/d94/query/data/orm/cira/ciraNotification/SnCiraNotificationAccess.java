package tsh.d94.app.server.data.orm.cira.ciraNotification;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraNotificationAccess extends AbstractCrudService<SnCiraNotificationModel, String> {

	@Autowired
	SnCiraNotificationRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraNotificationModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraNotificationModel> findRecentByUserId(String userId) {
		return repo.findTop50ByUserId(userId, PageRequest.of(0, 50));
	}

	public List<SnCiraNotificationModel> findAllUnreadByUserId(String userId) {
		return repo.findAllUnreadByUserId(userId);
	}

	public long countUnread(String userId) {
		return repo.countByUserIdAndReadYn(userId, "N");
	}

}
