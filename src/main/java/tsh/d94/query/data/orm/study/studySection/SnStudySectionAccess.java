package tsh.d94.app.server.data.orm.study.studySection;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Service
public class SnStudySectionAccess extends AbstractCrudService<SnStudySectionModel, String> {

	@Autowired
	SnStudySectionRepo repo;

	@Override
	protected BaseJpaRepository<SnStudySectionModel, String> getRepository() {
		return repo;
	}

	public List<SnStudySectionModel> findByDocumentIdOrderByOrd(String documentId) {
		return repo.findByDocumentIdOrderByOrd(documentId);
	}

	@Transactional
	public int deleteByDocumentId(String documentId) {
		return repo.deleteByDocumentId(documentId);
	}

	public List<SnStudySectionModel> saveAll(List<SnStudySectionModel> models) {
		return repo.saveAll(models);
	}

	/** 물리 삭제 (상위 delete 는 소프트 삭제라 쓰지 않는다) */
	public void remove(SnStudySectionModel model) {
		repo.delete(model);
	}

}
