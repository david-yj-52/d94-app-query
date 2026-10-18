package tsh.d94.app.server.data.orm.cira.ciraAttachment;

import java.util.List;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.constant.UseStatCd;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraAttachmentRepo extends BaseJpaRepository<SnCiraAttachmentModel, String> {

	List<SnCiraAttachmentModel> findByIssueIdAndUseStatCdNot(String issueId, UseStatCd useStatCd);

	long countByIssueIdAndUseStatCdNot(String issueId, UseStatCd useStatCd);

}
