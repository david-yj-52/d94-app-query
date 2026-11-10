package tsh.d94.app.server.data.orm.study.studyAnnotation;

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
public class SnStudyAnnotationAccess extends AbstractCrudService<SnStudyAnnotationModel, String> {

	@Autowired
	SnStudyAnnotationRepo repo;

	@Override
	protected BaseJpaRepository<SnStudyAnnotationModel, String> getRepository() {
		return repo;
	}

	public List<SnStudyAnnotationModel> findByOwnerIdAndDocumentIdAndStartBlockKeyIn(String ownerId, String documentId, Collection<String> startBlockKeys) {
		return repo.findByOwnerIdAndDocumentIdAndStartBlockKeyIn(ownerId, documentId, startBlockKeys);
	}

	public Optional<SnStudyAnnotationModel> findByObjIdAndOwnerId(String objId, String ownerId) {
		return repo.findByObjIdAndOwnerId(objId, ownerId);
	}

	public List<SnStudyAnnotationModel> findByOwnerIdOrderByCreatedAtDesc(String ownerId) {
		return repo.findByOwnerIdOrderByCreatedAtDesc(ownerId);
	}

	public List<SnStudyAnnotationModel> findByOwnerIdAndDocumentIdOrderByCreatedAtDesc(String ownerId, String documentId) {
		return repo.findByOwnerIdAndDocumentIdOrderByCreatedAtDesc(ownerId, documentId);
	}

	@Transactional
	public int deleteByDocumentId(String documentId) {
		return repo.deleteByDocumentId(documentId);
	}

	public List<SnStudyAnnotationModel> saveAll(List<SnStudyAnnotationModel> models) {
		return repo.saveAll(models);
	}

	/** 물리 삭제 (상위 delete 는 소프트 삭제라 쓰지 않는다) */
	public void remove(SnStudyAnnotationModel model) {
		repo.delete(model);
	}

}
