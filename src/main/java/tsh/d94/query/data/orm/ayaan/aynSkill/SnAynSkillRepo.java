package tsh.d94.app.server.data.orm.ayaan.aynSkill;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SnAynSkillRepo extends JpaRepository<SnAynSkillModel, String> {

	java.util.List<SnAynSkillModel> findAllByOrderBySortNoAscSkillCdAsc();

}
