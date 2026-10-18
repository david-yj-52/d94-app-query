package tsh.d94.app.server.data.orm.cira.ciraVersion;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraVersionAccess extends AbstractCrudService<SnCiraVersionModel, String> {

	@Autowired
	SnCiraVersionRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraVersionModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraVersionModel> findByProjectId(String projectId) {
		return repo.findByProjectIdAndUseStatCdNot(projectId, UseStatCd.Delete);
	}

	public List<SnCiraVersionModel> findByProjectIdAndStatus(String projectId, String status) {
		return repo.findByProjectIdAndStatus(projectId, status);
	}

}
