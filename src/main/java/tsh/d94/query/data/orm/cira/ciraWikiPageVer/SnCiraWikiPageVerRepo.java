package tsh.d94.app.server.data.orm.cira.ciraWikiPageVer;

import java.util.List;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraWikiPageVerRepo extends BaseJpaRepository<SnCiraWikiPageVerModel, String> {

	List<SnCiraWikiPageVerModel> findByPageIdOrderByVersionDesc(String pageId);
}
