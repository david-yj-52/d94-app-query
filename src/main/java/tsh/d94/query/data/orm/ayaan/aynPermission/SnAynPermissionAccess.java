package tsh.d94.app.server.data.orm.ayaan.aynPermission;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnAynPermissionAccess extends AbstractCrudService<SnAynPermissionModel, String> {

	@Autowired
	SnAynPermissionRepo repo;

	public java.util.List<SnAynPermissionModel> listAll() {
		return repo.findAllByOrderByToolCdAscCategoryAscPriorityDesc();
	}

	/** 전체 교체 (호출자 트랜잭션 안에서) */
	public java.util.List<SnAynPermissionModel> replaceAll(java.util.List<SnAynPermissionModel> rows) {
		repo.deleteAllInBatch();
		return repo.saveAll(rows);
	}

	@Override
	protected BaseJpaRepository<SnAynPermissionModel, String> getRepository() {
		return repo;
	}

}
