package tsh.d94.app.server.data.orm.study.studyTerm;

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
public class SnStudyTermAccess extends AbstractCrudService<SnStudyTermModel, String> {

	@Autowired
	SnStudyTermRepo repo;

	@Override
	protected BaseJpaRepository<SnStudyTermModel, String> getRepository() {
		return repo;
	}

	public List<SnStudyTermModel> findByOwnerIdAndDocumentIdIsNull(String ownerId) {
		return repo.findByOwnerIdAndDocumentIdIsNull(ownerId);
	}

	public List<SnStudyTermModel> findByOwnerIdAndDocumentId(String ownerId, String documentId) {
		return repo.findByOwnerIdAndDocumentId(ownerId, documentId);
	}

	public Optional<SnStudyTermModel> findByObjIdAndOwnerId(String objId, String ownerId) {
		return repo.findByObjIdAndOwnerId(objId, ownerId);
	}

	public boolean existsByOwnerIdAndDocumentIdAndNormTerm(String ownerId, String documentId, String normTerm) {
		return repo.existsByOwnerIdAndDocumentIdAndNormTerm(ownerId, documentId, normTerm);
	}

	public boolean existsByOwnerIdAndDocumentIdIsNullAndNormTerm(String ownerId, String normTerm) {
		return repo.existsByOwnerIdAndDocumentIdIsNullAndNormTerm(ownerId, normTerm);
	}

	@Transactional
	public int deleteByDocumentId(String documentId) {
		return repo.deleteByDocumentId(documentId);
	}

	public List<SnStudyTermModel> saveAll(List<SnStudyTermModel> models) {
		return repo.saveAll(models);
	}

	/** 물리 삭제 (상위 delete 는 소프트 삭제라 쓰지 않는다) */
	public void remove(SnStudyTermModel model) {
		repo.delete(model);
	}

}
