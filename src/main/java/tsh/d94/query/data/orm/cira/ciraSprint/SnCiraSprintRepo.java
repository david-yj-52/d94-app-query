package tsh.d94.app.server.data.orm.cira.ciraSprint;

import java.util.List;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraSprintRepo extends BaseJpaRepository<SnCiraSprintModel, String> {

	List<SnCiraSprintModel> findByProjectId(String projectId);

	List<SnCiraSprintModel> findByProjectIdAndSprintStat(String projectId, String sprintStat);

	List<SnCiraSprintModel> findBySprintStatAndUseStatCd(String sprintStat, UseStatCd useStatCd);

}
