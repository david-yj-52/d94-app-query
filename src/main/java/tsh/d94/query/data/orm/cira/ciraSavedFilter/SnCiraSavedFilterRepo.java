package tsh.d94.app.server.data.orm.cira.ciraSavedFilter;

import java.util.List;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraSavedFilterRepo extends BaseJpaRepository<SnCiraSavedFilterModel, String> {

	List<SnCiraSavedFilterModel> findByUserId(String userId);

}
