package tsh.d94.app.server.data.orm.cira.ciraMilestone;

import java.util.List;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraMilestoneRepo extends BaseJpaRepository<SnCiraMilestoneModel, String> {

	List<SnCiraMilestoneModel> findByProjectIdAndUseStatCdNot(String projectId, UseStatCd useStatCd);

}
