package tsh.d94.app.server.data.orm.cira.ciraSpace;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnCiraSpaceAccess extends AbstractCrudService<SnCiraSpaceModel, String> {

	@Autowired
	SnCiraSpaceRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraSpaceModel, String> getRepository() {
		return repo;
	}

	/** 기본 공간 먼저가 아니라 SORT_ORD 순 (기본 공간은 0) */
	public List<SnCiraSpaceModel> findByProjectId(String projectId) {
		return repo.findByProjectIdOrderBySortOrdAscCreatedAtAsc(projectId);
	}

	public Optional<SnCiraSpaceModel> findDefault(String projectId) {
		return findByProjectId(projectId).stream().filter(SnCiraSpaceModel::isDefault).findFirst();
	}

	public void hardDelete(String id) {
		repo.deleteById(id);
	}
}
