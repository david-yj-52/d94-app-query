package tsh.d94.app.server.data.orm.study.studyBlock;

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
public class SnStudyBlockAccess extends AbstractCrudService<SnStudyBlockModel, String> {

	@Autowired
	SnStudyBlockRepo repo;

	@Override
	protected BaseJpaRepository<SnStudyBlockModel, String> getRepository() {
		return repo;
	}

	public List<SnStudyBlockModel> findByDocumentIdAndSectionIdInOrderByOrd(String documentId, Collection<String> sectionIds) {
		return repo.findByDocumentIdAndSectionIdInOrderByOrd(documentId, sectionIds);
	}

	public Optional<SnStudyBlockModel> findByDocumentIdAndBlockKey(String documentId, String blockKey) {
		return repo.findByDocumentIdAndBlockKey(documentId, blockKey);
	}

	public List<SnStudyBlockModel> findByDocumentIdAndOrdBetweenOrderByOrd(String documentId, Integer from, Integer to) {
		return repo.findByDocumentIdAndOrdBetweenOrderByOrd(documentId, from, to);
	}

	@Transactional
	public int deleteByDocumentId(String documentId) {
		return repo.deleteByDocumentId(documentId);
	}

	public List<SnStudyBlockModel> saveAll(List<SnStudyBlockModel> models) {
		return repo.saveAll(models);
	}

	/** 물리 삭제 (상위 delete 는 소프트 삭제라 쓰지 않는다) */
	public void remove(SnStudyBlockModel model) {
		repo.delete(model);
	}

}
