package tsh.d94.app.server.data.orm.ayaan.aynTaskRun;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnAynTaskRunAccess extends AbstractCrudService<SnAynTaskRunModel, String> {

	@Autowired
	SnAynTaskRunRepo repo;

	@Override
	protected BaseJpaRepository<SnAynTaskRunModel, String> getRepository() {
		return repo;
	}

}
