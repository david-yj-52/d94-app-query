package tsh.d94.app.server.data.orm.cira.ciraProject;

import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraProjectRepo extends BaseJpaRepository<SnCiraProjectModel, String> {

	Optional<SnCiraProjectModel> findByProjectKey(String projectKey);

}
