package tsh.d94.app.server.data.orm.cira.ciraWikiPage;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraWikiPageAccess extends AbstractCrudService<SnCiraWikiPageModel, String> {

	@Autowired
	SnCiraWikiPageRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraWikiPageModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraWikiPageModel> findByProjectId(String projectId) {
		return repo.findByProjectIdAndUseStatCdNotOrderBySortOrderAsc(projectId, UseStatCd.Delete);
	}

	public List<SnCiraWikiPageModel> findByParentId(String parentId) {
		return repo.findByParentIdAndUseStatCdNotOrderBySortOrderAsc(parentId, UseStatCd.Delete);
	}
}
