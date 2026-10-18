package tsh.d94.app.server.data.orm.cira.ciraBoard;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraBoardAccess extends AbstractCrudService<SnCiraBoardModel, String> {

	@Autowired
	SnCiraBoardRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraBoardModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraBoardModel> findByProjectId(String projectId) {
		return repo.findByProjectId(projectId);
	}

}
