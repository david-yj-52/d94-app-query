package tsh.d94.app.server.data.orm.cira.ciraNotification;

import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraNotificationRepo extends BaseJpaRepository<SnCiraNotificationModel, String> {

	@Query("SELECT n FROM SnCiraNotificationModel n WHERE n.userId = :userId ORDER BY n.createdAt DESC")
	List<SnCiraNotificationModel> findTop50ByUserId(@Param("userId") String userId,
		org.springframework.data.domain.Pageable pageable);

	long countByUserIdAndReadYn(String userId, String readYn);

	@Query("SELECT n FROM SnCiraNotificationModel n WHERE n.userId = :userId AND n.readYn = 'N' AND n.useStatCd <> tsh.d94.lib.core.data.constant.UseStatCd.Delete")
	List<SnCiraNotificationModel> findAllUnreadByUserId(@Param("userId") String userId);

}
