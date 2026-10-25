package tsh.d94.app.server.data.orm.ifTest.ifTestItem;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnIfTestItemRepo extends BaseJpaRepository<SnIfTestItemModel, String> {

	List<SnIfTestItemModel> findTop100ByTenantAndUseStatCdOrderByCreatedAtDesc(String tenant, UseStatCd useStatCd);

	Optional<SnIfTestItemModel> findByObjIdAndTenantAndUseStatCd(String objId, String tenant, UseStatCd useStatCd);

}
