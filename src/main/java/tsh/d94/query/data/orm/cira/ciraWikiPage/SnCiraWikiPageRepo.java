package tsh.d94.app.server.data.orm.cira.ciraWikiPage;

import java.util.List;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraWikiPageRepo extends BaseJpaRepository<SnCiraWikiPageModel, String> {

	List<SnCiraWikiPageModel> findByProjectIdAndUseStatCdNotOrderBySortOrderAsc(String projectId, UseStatCd useStatCd);

	List<SnCiraWikiPageModel> findByParentIdAndUseStatCdNotOrderBySortOrderAsc(String parentId, UseStatCd useStatCd);
}
