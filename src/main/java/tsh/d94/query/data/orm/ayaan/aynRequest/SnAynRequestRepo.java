package tsh.d94.app.server.data.orm.ayaan.aynRequest;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

/** 상태 변경은 조건부 UPDATE 한 문장(CAS) — 같은 버튼 두 번, 승인 재사용을 DB 가 막는다. */
@Repository
public interface SnAynRequestRepo extends BaseJpaRepository<SnAynRequestModel, String> {

	/** status·taskId 는 LIKE 패턴('%' = 전체) */
	@Query("SELECT r FROM SnAynRequestModel r WHERE r.status LIKE :status AND r.taskId LIKE :taskId ORDER BY r.createdAt")
	List<SnAynRequestModel> search(@Param("status") String status, @Param("taskId") String taskId);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = """
		UPDATE SN_AYN_REQUEST SET STATUS = 'ANSWERED', ANSWER = :answer, DECISION = :decision, ANSWERED_BY = :by,
		       ANSWERED_AT = now(), MODIFIED_AT = now()
		WHERE OBJ_ID = :id AND STATUS = 'OPEN'
		""")
	int answer(@Param("id") String id, @Param("answer") String answer, @Param("decision") String decision, @Param("by") String by);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = "UPDATE SN_AYN_REQUEST SET STATUS = 'CONSUMED', CONSUMED_AT = now(), MODIFIED_AT = now() WHERE OBJ_ID = :id AND STATUS = 'ANSWERED'")
	int consume(@Param("id") String id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = "UPDATE SN_AYN_REQUEST SET STATUS = 'CANCELLED', MODIFIED_AT = now() WHERE OBJ_ID = :id AND STATUS = 'OPEN'")
	int cancel(@Param("id") String id);

}
