package tsh.d94.app.server.data.orm.cira.ciraBoardColumn;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraBoardColumnAccess extends AbstractCrudService<SnCiraBoardColumnModel, String> {

	@Autowired
	SnCiraBoardColumnRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraBoardColumnModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraBoardColumnModel> findByStatusId(String statusId) {
		return repo.findByStatusId(statusId);
	}

	public Optional<SnCiraBoardColumnModel> findByBoardIdAndStatusId(String boardId, String statusId) {
		return repo.findByBoardIdAndStatusId(boardId, statusId);
	}

	public List<SnCiraBoardColumnModel> findByBoardIdOrderBySortOrd(String boardId) {
		return repo.findByBoardIdOrderBySortOrd(boardId);
	}

}
