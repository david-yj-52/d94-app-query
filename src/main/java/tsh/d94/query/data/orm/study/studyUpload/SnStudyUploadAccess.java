package tsh.d94.app.server.data.orm.study.studyUpload;

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
public class SnStudyUploadAccess extends AbstractCrudService<SnStudyUploadModel, String> {

	@Autowired
	SnStudyUploadRepo repo;

	@Override
	protected BaseJpaRepository<SnStudyUploadModel, String> getRepository() {
		return repo;
	}

	public Optional<SnStudyUploadModel> findByObjIdAndOwnerId(String objId, String ownerId) {
		return repo.findByObjIdAndOwnerId(objId, ownerId);
	}

	public List<SnStudyUploadModel> saveAll(List<SnStudyUploadModel> models) {
		return repo.saveAll(models);
	}

	/** 물리 삭제 (상위 delete 는 소프트 삭제라 쓰지 않는다) */
	public void remove(SnStudyUploadModel model) {
		repo.delete(model);
	}

}
