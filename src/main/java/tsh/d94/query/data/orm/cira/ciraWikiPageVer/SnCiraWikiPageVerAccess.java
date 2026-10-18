package tsh.d94.app.server.data.orm.cira.ciraWikiPageVer;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraWikiPageVerAccess extends AbstractCrudService<SnCiraWikiPageVerModel, String> {

	@Autowired
	SnCiraWikiPageVerRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraWikiPageVerModel, String> getRepository() {
		return repo;
	}

	public List<SnCiraWikiPageVerModel> findByPageId(String pageId) {
		return repo.findByPageIdOrderByVersionDesc(pageId);
	}
}
