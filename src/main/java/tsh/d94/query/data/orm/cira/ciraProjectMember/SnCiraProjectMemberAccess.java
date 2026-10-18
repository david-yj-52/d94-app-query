package tsh.d94.app.server.data.orm.cira.ciraProjectMember;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraProjectMemberAccess extends AbstractCrudService<SnCiraProjectMemberModel, String> {

	@Autowired
	SnCiraProjectMemberRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraProjectMemberModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraProjectMemberModel> findAllByUserId(String userId) {
		return repo.findAllByUserId(userId);
	}

	public List<SnCiraProjectMemberModel> findAllByProjectId(String projectId) {
		return repo.findAllByProjectId(projectId);
	}

	public Optional<SnCiraProjectMemberModel> findByProjectIdAndUserId(String projectId, String userId) {
		return repo.findByProjectIdAndUserId(projectId, userId);
	}
}
