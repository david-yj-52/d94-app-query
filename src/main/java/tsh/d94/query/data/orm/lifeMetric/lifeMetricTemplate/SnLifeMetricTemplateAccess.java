package tsh.d94.app.server.data.orm.lifeMetric.lifeMetricTemplate;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

/** 템플릿은 전 tenant 공용이라 tenant 필터를 켜는 상위 메서드 대신 repo 를 직접 쓴다. */
@Service
public class SnLifeMetricTemplateAccess extends AbstractCrudService<SnLifeMetricTemplateModel, String> {

	@Autowired
	SnLifeMetricTemplateRepo repo;

	@Override
	protected BaseJpaRepository<SnLifeMetricTemplateModel, String> getRepository() {
		return repo;
	}

	public List<SnLifeMetricTemplateModel> listAll() {
		return repo.findAllByOrderByCodeAsc();
	}

	public Optional<SnLifeMetricTemplateModel> find(String id) {
		return repo.findById(id);
	}
}
