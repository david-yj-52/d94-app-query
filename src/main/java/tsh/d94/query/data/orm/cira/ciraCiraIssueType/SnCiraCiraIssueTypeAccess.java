package tsh.d94.app.server.data.orm.cira.ciraCiraIssueType;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraCiraIssueTypeAccess extends AbstractCrudService<SnCiraCiraIssueTypeModel, String> {

	@Autowired
	SnCiraCiraIssueTypeRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraCiraIssueTypeModel, String> getRepository() {
		return repo;
	}

	/** 이슈 유형은 전역 테이블 (Flyway V320 시드: Task·Bug·Story·Epic) */
	public Optional<SnCiraCiraIssueTypeModel> findByTypeNm(String typeNm) {
		return repo.findByTypeNm(typeNm);
	}

	public List<SnCiraCiraIssueTypeModel> findAllUsable() {
		return repo.findByUseStatCdOrderByTypeNm(UseStatCd.Usable);
	}

}
