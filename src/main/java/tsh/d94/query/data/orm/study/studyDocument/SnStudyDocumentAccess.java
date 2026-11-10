package tsh.d94.app.server.data.orm.study.studyDocument;

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
public class SnStudyDocumentAccess extends AbstractCrudService<SnStudyDocumentModel, String> {

	@Autowired
	SnStudyDocumentRepo repo;

	@Override
	protected BaseJpaRepository<SnStudyDocumentModel, String> getRepository() {
		return repo;
	}

	public Page<SnStudyDocumentModel> findByOwnerIdOrderByCreatedAtDesc(String ownerId, Pageable pageable) {
		return repo.findByOwnerIdOrderByCreatedAtDesc(ownerId, pageable);
	}

	public Optional<SnStudyDocumentModel> findByObjIdAndOwnerId(String objId, String ownerId) {
		return repo.findByObjIdAndOwnerId(objId, ownerId);
	}

	public List<SnStudyDocumentModel> findByStatusAndModifiedAtBefore(String status, LocalDateTime cutoff) {
		return repo.findByStatusAndModifiedAtBefore(status, cutoff);
	}

	public List<SnStudyDocumentModel> saveAll(List<SnStudyDocumentModel> models) {
		return repo.saveAll(models);
	}

	/** 물리 삭제 (상위 delete 는 소프트 삭제라 쓰지 않는다) */
	public void remove(SnStudyDocumentModel model) {
		repo.delete(model);
	}

}
