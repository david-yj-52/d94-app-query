package tsh.d94.app.server.data.orm.ayaan.aynWorkspaceRepo;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 자연키 테이블이라 AbstractCrudService 를 쓰지 않는다. */
@Service
public class SnAynWorkspaceRepoAccess {

	@Autowired
	SnAynWorkspaceRepoRepo repo;

	public List<SnAynWorkspaceRepoModel> findByProjectKey(String projectKey) {
		return repo.findByProjectKeyOrderBySortOrdAscRepoNmAsc(projectKey);
	}

	/** 프로젝트의 저장소 매핑을 통째로 교체 */
	public List<SnAynWorkspaceRepoModel> replaceAll(String projectKey, List<SnAynWorkspaceRepoModel> repos) {
		repo.deleteAllByProjectKey(projectKey);
		repo.saveAll(repos);
		return findByProjectKey(projectKey);
	}

	public void deleteByProjectKey(String projectKey) {
		repo.deleteAllByProjectKey(projectKey);
	}

}
