package tsh.d94.app.server.data.orm.cira.ciraProject;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraProjectAccess extends AbstractCrudService<SnCiraProjectModel, String> {

	@Autowired
	SnCiraProjectRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraProjectModel, String> getRepository() {
		return repo;
	}

	public Optional<SnCiraProjectModel> findByProjectKey(String projectKey) {
		return repo.findByProjectKey(projectKey);
	}

	public List<SnCiraProjectModel> findAllById(List<String> ids) {
		return repo.findAllById(ids);
	}

}
