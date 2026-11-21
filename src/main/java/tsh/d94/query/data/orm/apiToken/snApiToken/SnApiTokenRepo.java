package tsh.d94.app.server.data.orm.apiToken.snApiToken;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnApiTokenRepo extends BaseJpaRepository<SnApiTokenModel, String> {

	Optional<SnApiTokenModel> findByTokenHash(String tokenHash);

	Optional<SnApiTokenModel> findByObjIdAndTenantAndOwnerId(String objId, String tenant, String ownerId);

	List<SnApiTokenModel> findByTenantAndOwnerIdAndRevokedAtIsNullOrderByCreatedAtDesc(String tenant, String ownerId);

	long countByTenantAndOwnerIdAndRevokedAtIsNullAndExpiresAtAfter(String tenant, String ownerId, LocalDateTime now);

	/** 마지막 사용 시각이 since 보다 오래됐을 때만 갱신 — 요청마다 UPDATE 하지 않으려고 */
	@Modifying
	@Query("UPDATE SnApiTokenModel t SET t.lastUsedAt = :now WHERE t.objId = :id AND (t.lastUsedAt IS NULL OR t.lastUsedAt < :since)")
	int touchLastUsed(@Param("id") String id, @Param("now") LocalDateTime now, @Param("since") LocalDateTime since);
}
