package tsh.d94.app.server.data.orm.apiToken.snApiToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnApiTokenAccess extends AbstractCrudService<SnApiTokenModel, String> {

	@Autowired
	SnApiTokenRepo repo;

	@Override
	protected BaseJpaRepository<SnApiTokenModel, String> getRepository() {
		return repo;
	}

	public Optional<SnApiTokenModel> findByTokenHash(String tokenHash) {
		return repo.findByTokenHash(tokenHash);
	}

	public Optional<SnApiTokenModel> findOwned(String objId, String tenant, String ownerId) {
		return repo.findByObjIdAndTenantAndOwnerId(objId, tenant, ownerId);
	}

	public List<SnApiTokenModel> findActiveOfOwner(String tenant, String ownerId) {
		return repo.findByTenantAndOwnerIdAndRevokedAtIsNullOrderByCreatedAtDesc(tenant, ownerId);
	}

	public long countUsableOfOwner(String tenant, String ownerId, LocalDateTime now) {
		return repo.countByTenantAndOwnerIdAndRevokedAtIsNullAndExpiresAtAfter(tenant, ownerId, now);
	}

	@Transactional
	public int touchLastUsed(String id, LocalDateTime now) {
		return repo.touchLastUsed(id, now, now.minusMinutes(1));
	}
}
