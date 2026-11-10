package tsh.d94.app.server.data.orm.study.studyDocument;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnStudyDocumentRepo extends BaseJpaRepository<SnStudyDocumentModel, String> {

	Page<SnStudyDocumentModel> findByOwnerIdOrderByCreatedAtDesc(String ownerId, Pageable pageable);

	Optional<SnStudyDocumentModel> findByObjIdAndOwnerId(String objId, String ownerId);

	List<SnStudyDocumentModel> findByStatusAndModifiedAtBefore(String status, LocalDateTime cutoff);

}
