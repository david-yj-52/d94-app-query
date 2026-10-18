package tsh.d94.app.server.data.orm.cira.ciraSprint;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraSprintAccess extends AbstractCrudService<SnCiraSprintModel, String> {

	private static final String STAT_ACTIVE = "Active";

	@Autowired
	SnCiraSprintRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraSprintModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraSprintModel> findByProjectId(String projectId) {
		return repo.findByProjectId(projectId);
	}

	public List<SnCiraSprintModel> findByProjectIdAndSprintStat(String projectId, String sprintStat) {
		return repo.findByProjectIdAndSprintStat(projectId, sprintStat);
	}

	/**
	 * 전체 테넌트에서 Active + 삭제되지 않은 스프린트 목록 반환.
	 * CQL openSprints() 함수 처리용.
	 */
	public List<SnCiraSprintModel> findAllActive() {
		return repo.findBySprintStatAndUseStatCd(STAT_ACTIVE, UseStatCd.Usable);
	}

}
