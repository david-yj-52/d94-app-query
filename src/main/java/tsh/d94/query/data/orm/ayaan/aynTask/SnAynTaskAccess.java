package tsh.d94.app.server.data.orm.ayaan.aynTask;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnAynTaskAccess extends AbstractCrudService<SnAynTaskModel, String> {

	@Autowired
	SnAynTaskRepo repo;

	/** 내부 API 는 tenant 컨텍스트가 없어 AbstractCrudService.findById(tenant 필터)를 쓰지 않는다 */
	public java.util.Optional<SnAynTaskModel> get(String id) {
		return repo.findById(id);
	}

	/** 제약 위반(DataIntegrityViolationException)을 그대로 던진다 — create() 는 RuntimeException 으로 감싼다 */
	public SnAynTaskModel insert(SnAynTaskModel m) {
		return repo.saveAndFlush(m);
	}

	public SnAynTaskModel saveTask(SnAynTaskModel m) {
		return repo.save(m);
	}

	public java.util.List<SnAynTaskModel> activeByIssueKey(String issueKey) {
		return repo.findByIssueKeyAndStatusIn(issueKey, java.util.List.of("QUEUED", "RUNNING", "PAUSED", "WAITING"));
	}

	public java.util.List<SnAynTaskModel> search(String status, String tool) {
		return repo.search(status == null ? "%" : status, tool == null ? "%" : tool);
	}

	public int transition(String id, String expected, String next) {
		return repo.transition(id, expected, next);
	}

	public java.util.List<String> claim(String owner, long leaseSeconds, String tool, long graceSeconds) {
		return repo.claim(owner, leaseSeconds, tool, graceSeconds);
	}

	public int extendLease(String id, String owner, long leaseSeconds) {
		return repo.extendLease(id, owner, leaseSeconds);
	}

	public java.util.List<String> recoverExpired() {
		return repo.recoverExpired();
	}

	@Override
	protected BaseJpaRepository<SnAynTaskModel, String> getRepository() {
		return repo;
	}

}
