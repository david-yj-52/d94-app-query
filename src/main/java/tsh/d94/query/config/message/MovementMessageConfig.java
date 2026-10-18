package tsh.d94.app.server.config.message;

import java.util.function.BiConsumer;

import com.tsh.mes.lib.spec.ofs.mtc.CarrMoveCompIvo;
import com.tsh.mes.mos.lib.ofs.core.lifecycle.OfsLifeCycleVo;
import com.tsh.mes.mos.lib.ofs.logic.exec.movement.impl.CarrMoveComp;
import tsh.d94.app.server.util.context.MovementLogicContext;
import tsh.d94.lib.core.ApMessage;

public enum MovementMessageConfig {

	CARR_MOVE_COMP((ivo, ctx) -> {
		CarrMoveCompIvo converted = (CarrMoveCompIvo)ivo;
		new CarrMoveComp(new OfsLifeCycleVo(), converted, ctx.movementDataProvider()).execute();
	});

	private final BiConsumer<ApMessage, MovementLogicContext> executor;

	MovementMessageConfig(BiConsumer<ApMessage, MovementLogicContext> executor) {
		this.executor = executor;
	}

	public void execute(ApMessage ivo, MovementLogicContext ctx) {
		executor.accept(ivo, ctx);
	}
}
