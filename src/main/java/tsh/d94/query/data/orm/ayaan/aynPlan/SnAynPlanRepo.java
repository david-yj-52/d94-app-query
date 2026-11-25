package tsh.d94.app.server.data.orm.ayaan.aynPlan;

import java.util.List;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnAynPlanRepo extends BaseJpaRepository<SnAynPlanModel, String> {

	/** projectKey null = 전체 */
	@Query("SELECT p FROM SnAynPlanModel p WHERE (:projectKey IS NULL OR p.projectKey = :projectKey) ORDER BY p.createdAt DESC")
	List<SnAynPlanModel> search(@Param("projectKey") String projectKey);

	@Query("SELECT p FROM SnAynPlanModel p WHERE p.projectKey = :projectKey AND p.stage NOT IN ('DONE', 'CANCELLED')")
	List<SnAynPlanModel> findActive(@Param("projectKey") String projectKey);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(nativeQuery = true, value = "UPDATE SN_AYN_PLAN SET STAGE = :next, MODIFIED_AT = now() WHERE OBJ_ID = :id AND STAGE = :expected")
	int transition(@Param("id") String id, @Param("expected") String expected, @Param("next") String next);

}
