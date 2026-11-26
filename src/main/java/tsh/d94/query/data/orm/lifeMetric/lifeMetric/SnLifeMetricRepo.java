package tsh.d94.app.server.data.orm.lifeMetric.lifeMetric;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnLifeMetricRepo extends BaseJpaRepository<SnLifeMetricModel, String> {

	Optional<SnLifeMetricModel> findByObjIdAndOwnerIdAndTenant(String objId, String ownerId, String tenant);

	Optional<SnLifeMetricModel> findByOwnerIdAndTenantAndCode(String ownerId, String tenant, String code);

	List<SnLifeMetricModel> findByOwnerIdAndTenantOrderByCreatedAtAsc(String ownerId, String tenant);
}
