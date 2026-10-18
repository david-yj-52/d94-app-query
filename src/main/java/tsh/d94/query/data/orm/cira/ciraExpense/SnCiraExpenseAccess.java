package tsh.d94.app.server.data.orm.cira.ciraExpense;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import tsh.d94.lib.core.data.orm.common.access.AbstractCrudService;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SnCiraExpenseAccess extends AbstractCrudService<SnCiraExpenseModel, String> {

	@Autowired
	SnCiraExpenseRepo repo;

	@Override
	protected BaseJpaRepository<SnCiraExpenseModel, String> getRepository() {
		return repo;
	}

}
