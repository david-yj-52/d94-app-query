package tsh.d94.app.server.data.orm.ifTest.ifTestItem;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

/**
 * 조회는 tenant 를 인자로 받는다. AbstractCrudService.findById 는 TenantContext 가 비어 있으면 실패하는데,
 * HTTP 요청 경로에서 TenantContext 를 채우는 곳이 아직 없다.
 */
@Slf4j
@Service
public class SnIfTestItemAccess extends AbstractCrudService<SnIfTestItemModel, String> {

	@Autowired
	SnIfTestItemRepo repo;

	@Override
	protected BaseJpaRepository<SnIfTestItemModel, String> getRepository() {
		return repo;
	}

	/** 최근 생성 순 최대 100건 */
	public List<SnIfTestItemModel> findRecentUsable(String tenant) {
		return repo.findTop100ByTenantAndUseStatCdOrderByCreatedAtDesc(tenant, UseStatCd.Usable);
	}

	public Optional<SnIfTestItemModel> findUsable(String objId, String tenant) {
		return repo.findByObjIdAndTenantAndUseStatCd(objId, tenant, UseStatCd.Usable);
	}

	/** 수정 직후 응답에 modifiedAt 이 반영되도록 flush 까지 한다 */
	public SnIfTestItemModel saveAndFlush(SnIfTestItemModel model) {
		return repo.saveAndFlush(model);
	}

}
