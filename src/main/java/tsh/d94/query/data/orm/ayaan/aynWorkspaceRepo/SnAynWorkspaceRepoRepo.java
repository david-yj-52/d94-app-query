package tsh.d94.app.server.data.orm.ayaan.aynWorkspaceRepo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SnAynWorkspaceRepoRepo extends JpaRepository<SnAynWorkspaceRepoModel, SnAynWorkspaceRepoModel.Pk> {

	List<SnAynWorkspaceRepoModel> findByProjectKeyOrderBySortOrdAscRepoNmAsc(String projectKey);

	/** 즉시 실행(flush 순서와 무관) — 같은 PK 를 곧바로 다시 insert 하는 전체 교체용 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("DELETE FROM SnAynWorkspaceRepoModel r WHERE r.projectKey = :projectKey")
	void deleteAllByProjectKey(@Param("projectKey") String projectKey);

}
