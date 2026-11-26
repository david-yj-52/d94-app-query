package tsh.d94.app.server.data.orm.lifeMetric.lifeMetric;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnLifeMetricAccess extends AbstractCrudService<SnLifeMetricModel, String> {

	@Autowired
	SnLifeMetricRepo repo;

	@Override
	protected BaseJpaRepository<SnLifeMetricModel, String> getRepository() {
		return repo;
	}

	public Optional<SnLifeMetricModel> findOwned(String id, String ownerId, String tenant) {
		return repo.findByObjIdAndOwnerIdAndTenant(id, ownerId, tenant);
	}

	public Optional<SnLifeMetricModel> findByCode(String ownerId, String tenant, String code) {
		return repo.findByOwnerIdAndTenantAndCode(ownerId, tenant, code);
	}

	public List<SnLifeMetricModel> listOwned(String ownerId, String tenant) {
		return repo.findByOwnerIdAndTenantOrderByCreatedAtAsc(ownerId, tenant);
	}

	/** CODE 유일 위반을 저장 시점에 바로 받기 위해 flush 한다 */
	public SnLifeMetricModel saveAndFlush(SnLifeMetricModel m) {
		return repo.saveAndFlush(m);
	}

	/** 기준값·로그는 FK CASCADE 로 함께 지워진다 */
	public void remove(SnLifeMetricModel m) {
		repo.delete(m);
	}
}
