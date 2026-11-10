package tsh.d94.app.server.data.orm.study.studyBlock;

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
public interface SnStudyBlockRepo extends BaseJpaRepository<SnStudyBlockModel, String> {

	List<SnStudyBlockModel> findByDocumentIdAndSectionIdInOrderByOrd(String documentId, Collection<String> sectionIds);

	Optional<SnStudyBlockModel> findByDocumentIdAndBlockKey(String documentId, String blockKey);

	List<SnStudyBlockModel> findByDocumentIdAndOrdBetweenOrderByOrd(String documentId, Integer from, Integer to);

	@Modifying
	@Query("delete from SnStudyBlockModel m where m.documentId = :documentId")
	int deleteByDocumentId(@Param("documentId") String documentId);

}
