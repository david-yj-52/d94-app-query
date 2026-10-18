package tsh.d94.app.server.data.orm.cira.ciraSavedFilter;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraSavedFilterAccess extends AbstractCrudService<SnCiraSavedFilterModel, String> {

	@Autowired
	SnCiraSavedFilterRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraSavedFilterModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraSavedFilterModel> findByUserId(String userId) {
		return repo.findByUserId(userId);
	}

}
