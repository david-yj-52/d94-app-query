package tsh.d94.app.server.data.orm.cira.ciraExpense;

import org.springframework.stereotype.Repository;

import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

@Repository
public interface SnCiraExpenseRepo extends BaseJpaRepository<SnCiraExpenseModel, String> {
}
