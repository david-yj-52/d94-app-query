package tsh.d94.app.server.data.orm.study.studyTerm;

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
public interface SnStudyTermRepo extends BaseJpaRepository<SnStudyTermModel, String> {

	List<SnStudyTermModel> findByOwnerIdAndDocumentIdIsNull(String ownerId);

	List<SnStudyTermModel> findByOwnerIdAndDocumentId(String ownerId, String documentId);

	Optional<SnStudyTermModel> findByObjIdAndOwnerId(String objId, String ownerId);

	boolean existsByOwnerIdAndDocumentIdAndNormTerm(String ownerId, String documentId, String normTerm);

	boolean existsByOwnerIdAndDocumentIdIsNullAndNormTerm(String ownerId, String normTerm);

	@Modifying
	@Query("delete from SnStudyTermModel m where m.documentId = :documentId")
	int deleteByDocumentId(@Param("documentId") String documentId);

}
