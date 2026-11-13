package tsh.d94.app.server.data.orm.ayaan.aynSkill;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** 자연키 테이블이라 AbstractCrudService(BaseModel 전용)를 쓰지 않는다. */
@Service
public class SnAynSkillAccess {

	@Autowired
	SnAynSkillRepo repo;

	public java.util.List<SnAynSkillModel> findAll() {
		return repo.findAllByOrderBySortNoAscSkillCdAsc();
	}

	public java.util.Optional<SnAynSkillModel> find(String id) {
		return repo.findById(id);
	}

	public SnAynSkillModel save(SnAynSkillModel m) {
		return repo.save(m);
	}

	public void delete(String skillCd) {
		repo.deleteById(skillCd);
	}

}
