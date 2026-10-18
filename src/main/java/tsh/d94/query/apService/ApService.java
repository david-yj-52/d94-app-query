package tsh.d94.app.server.apService;

import tsh.d94.app.server.ApProcessVo;
import tsh.d94.app.server.interfaces.subscriber.MessageInfoVo;
import tsh.d94.lib.core.interfaces.InterfaceType;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;
import tsh.d94.lib.core.spec.ApMessageBody;
import tsh.d94.lib.core.spec.constant.ApMessageList;

public interface ApService<R, T extends ApMessageBody> {

	ApiResponse<R> run(ApProcessVo<T> procVo);

	// CompletableFuture<ApiResponse<R>> runAsync(ApProcessVo<T> procVo);

	ApMessageList getSupportedEvent();

	void handle(String payload, InterfaceType interfaceType, MessageInfoVo infoVo) throws Exception;
}
