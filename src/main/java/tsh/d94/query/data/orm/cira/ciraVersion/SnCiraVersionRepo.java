package tsh.d94.app.server.data.orm.cira.ciraVersion;

import java.util.List;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraVersionRepo extends BaseJpaRepository<SnCiraVersionModel, String> {

	List<SnCiraVersionModel> findByProjectIdAndUseStatCdNot(String projectId, UseStatCd useStatCd);

	List<SnCiraVersionModel> findByProjectIdAndStatus(String projectId, String status);

}
