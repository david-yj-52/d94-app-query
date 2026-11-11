package tsh.d94.app.server.data.orm.ayaan.aynTask;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

/** 상태 전이·선점·lease 는 시각을 DB now() 로 찍는다(워커와 DB 의 시계 차이 방지). */
@Repository
public interface SnAynTaskRepo extends BaseJpaRepository<SnAynTaskModel, String> {

	List<SnAynTaskModel> findByIssueKeyAndStatusIn(String issueKey, List<String> statuses);

	/** status·tool 은 LIKE 패턴('%' = 전체) */
	@Query("SELECT t FROM SnAynTaskModel t WHERE t.status LIKE :status AND t.toolCd LIKE :tool ORDER BY t.createdAt")
	List<SnAynTaskModel> search(@Param("status") String status, @Param("tool") String tool);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = "UPDATE SN_AYN_TASK SET STATUS = :next, MODIFIED_AT = now() WHERE OBJ_ID = :id AND STATUS = :expected")
	int transition(@Param("id") String id, @Param("expected") String expected, @Param("next") String next);

	/** RETURNING 으로 선점한 OBJ_ID 를 돌려준다 (없으면 빈 리스트) */
	@Query(nativeQuery = true, value = """
		UPDATE SN_AYN_TASK t SET STATUS='RUNNING', LEASE_OWNER=:owner, LEASE_UNTIL=now()+make_interval(secs=>:lease),
		       STARTED_AT=COALESCE(STARTED_AT, now()), MODIFIED_AT=now()
		WHERE t.OBJ_ID = (
		  SELECT c.OBJ_ID FROM SN_AYN_TASK c
		  WHERE c.TOOL_CD=:tool AND (c.STATUS='QUEUED' OR (c.STATUS='PAUSED' AND c.RESUME_AT + make_interval(secs=>:grace) <= now()))
		    AND NOT EXISTS (SELECT 1 FROM SN_AYN_TASK r WHERE r.STATUS='RUNNING' AND r.WORKSPACE_PATH=c.WORKSPACE_PATH)
		  ORDER BY c.CREATED_AT FOR UPDATE SKIP LOCKED LIMIT 1)
		RETURNING t.OBJ_ID
		""")
	List<String> claim(@Param("owner") String owner, @Param("lease") double lease, @Param("tool") String tool, @Param("grace") double grace);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = """
		UPDATE SN_AYN_TASK SET LEASE_UNTIL = now() + make_interval(secs => :lease), MODIFIED_AT = now()
		WHERE OBJ_ID = :id AND STATUS = 'RUNNING' AND LEASE_OWNER = :owner
		""")
	int extendLease(@Param("id") String id, @Param("owner") String owner, @Param("lease") double lease);

	@Query(nativeQuery = true, value = """
		UPDATE SN_AYN_TASK SET STATUS = 'PAUSED', PAUSE_REASON = 'RECOVERED', RESUME_AT = now(),
		       LEASE_OWNER = NULL, LEASE_UNTIL = NULL, MODIFIED_AT = now()
		WHERE STATUS = 'RUNNING' AND LEASE_UNTIL < now()
		RETURNING OBJ_ID
		""")
	List<String> recoverExpired();

}
