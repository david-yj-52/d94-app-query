package tsh.d94.app.server.data.orm.study.studyAnnotation;

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
public interface SnStudyAnnotationRepo extends BaseJpaRepository<SnStudyAnnotationModel, String> {

	List<SnStudyAnnotationModel> findByOwnerIdAndDocumentIdAndStartBlockKeyIn(String ownerId, String documentId, Collection<String> startBlockKeys);

	Optional<SnStudyAnnotationModel> findByObjIdAndOwnerId(String objId, String ownerId);

	List<SnStudyAnnotationModel> findByOwnerIdOrderByCreatedAtDesc(String ownerId);

	List<SnStudyAnnotationModel> findByOwnerIdAndDocumentIdOrderByCreatedAtDesc(String ownerId, String documentId);

	@Modifying
	@Query("delete from SnStudyAnnotationModel m where m.documentId = :documentId")
	int deleteByDocumentId(@Param("documentId") String documentId);

}
