package tsh.d94.app.server.data.orm.ayaan.aynRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnAynRequestAccess extends AbstractCrudService<SnAynRequestModel, String> {

	@Autowired
	SnAynRequestRepo repo;

	/** 내부 API 는 tenant 컨텍스트가 없어 AbstractCrudService.findById(tenant 필터)를 쓰지 않는다 */
	public java.util.Optional<SnAynRequestModel> get(String id) {
		return repo.findById(id);
	}

	/** 제약 위반(작업당 OPEN 1건)을 그대로 던진다 */
	public SnAynRequestModel insert(SnAynRequestModel m) {
		return repo.saveAndFlush(m);
	}

	public java.util.List<SnAynRequestModel> search(String status, String taskId) {
		return repo.search(status == null ? "%" : status, taskId == null ? "%" : taskId);
	}

	public int answer(String id, String answer, String decision, String by) {
		return repo.answer(id, answer, decision, by);
	}

	public int consume(String id) {
		return repo.consume(id);
	}

	public int cancel(String id) {
		return repo.cancel(id);
	}

	@Override
	protected BaseJpaRepository<SnAynRequestModel, String> getRepository() {
		return repo;
	}

}
