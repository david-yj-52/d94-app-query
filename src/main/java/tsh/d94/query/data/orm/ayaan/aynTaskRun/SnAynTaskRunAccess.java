package tsh.d94.app.server.data.orm.ayaan.aynTaskRun;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnAynTaskRunAccess extends AbstractCrudService<SnAynTaskRunModel, String> {

	@Autowired
	SnAynTaskRunRepo repo;

	/** 내부 API 는 tenant 컨텍스트가 없어 AbstractCrudService.findById(tenant 필터)를 쓰지 않는다 */
	public java.util.Optional<SnAynTaskRunModel> get(String id) {
		return repo.findById(id);
	}

	public java.util.List<SnAynTaskRunModel> findByTaskId(String taskId) {
		return repo.findByTaskIdOrderByRunSeq(taskId);
	}

	public SnAynTaskRunModel saveRun(SnAynTaskRunModel m) {
		return repo.saveAndFlush(m);
	}

	@Override
	protected BaseJpaRepository<SnAynTaskRunModel, String> getRepository() {
		return repo;
	}

}
