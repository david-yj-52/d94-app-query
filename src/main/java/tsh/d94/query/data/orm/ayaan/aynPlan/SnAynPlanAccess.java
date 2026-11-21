package tsh.d94.app.server.data.orm.ayaan.aynPlan;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnAynPlanAccess extends AbstractCrudService<SnAynPlanModel, String> {

	@Autowired
	SnAynPlanRepo repo;

	/** 내부 API 는 tenant 컨텍스트가 없어 AbstractCrudService.findById(tenant 필터)를 쓰지 않는다 */
	public Optional<SnAynPlanModel> get(String id) {
		return repo.findById(id);
	}

	/** 제약 위반(DataIntegrityViolationException)을 그대로 던진다 */
	public SnAynPlanModel insert(SnAynPlanModel m) {
		return repo.saveAndFlush(m);
	}

	public SnAynPlanModel savePlan(SnAynPlanModel m) {
		return repo.save(m);
	}

	public List<SnAynPlanModel> search(String projectKey) {
		return repo.search(projectKey == null ? "%" : projectKey);
	}

	public List<SnAynPlanModel> findActive(String projectKey) {
		return repo.findActive(projectKey);
	}

	public int transition(String id, String expected, String next) {
		return repo.transition(id, expected, next);
	}

	@Override
	protected BaseJpaRepository<SnAynPlanModel, String> getRepository() {
		return repo;
	}

}
