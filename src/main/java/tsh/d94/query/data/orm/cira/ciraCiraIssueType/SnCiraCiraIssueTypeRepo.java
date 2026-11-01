package tsh.d94.app.server.data.orm.cira.ciraCiraIssueType;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraCiraIssueTypeRepo extends BaseJpaRepository<SnCiraCiraIssueTypeModel, String> {

	Optional<SnCiraCiraIssueTypeModel> findByTypeNm(String typeNm);

	List<SnCiraCiraIssueTypeModel> findByUseStatCdOrderByTypeNm(UseStatCd useStatCd);

}
