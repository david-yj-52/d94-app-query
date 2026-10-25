package tsh.d94.app.server.interfaces.controller.ifTest;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.ifTest.InterfaceTestService;
import tsh.d94.app.server.apService.ifTest.dto.InterfaceTestItemRequest;
import tsh.d94.app.server.apService.ifTest.dto.InterfaceTestItemResponse;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import lombok.RequiredArgsConstructor;

/**
 * 인터페이스 테스트용 REST. 상태코드를 REST 의미대로 돌려준다 (생성 201 + Location, 삭제 204).
 * GET 매핑은 HEAD 도 자동으로 처리된다.
 */
@RestController
@RequiredArgsConstructor
public class InterfaceTestController {

	private static final String BASE = "/api/v1/interface-tests";

	private final InterfaceTestService interfaceTestService;

	@GetMapping(BASE)
	public ApiResponse<List<InterfaceTestItemResponse>> getItems() {
		return ApiResponse.ok(interfaceTestService.getItems());
	}

	@GetMapping(BASE + "/{itemId}")
	public ApiResponse<InterfaceTestItemResponse> getItem(@PathVariable String itemId) {
		return ApiResponse.ok(interfaceTestService.getItem(itemId));
	}

	@PostMapping(BASE)
	public ResponseEntity<ApiResponse<InterfaceTestItemResponse>> createItem(
		@RequestBody InterfaceTestItemRequest request
	) {
		InterfaceTestItemResponse created = interfaceTestService.createItem(request);
		// 상대 경로: UI 가 부른 경로(/v1/...) 기준 — 백엔드 context-path·UMD 유무와 무관
		return ResponseEntity.created(URI.create("/v1/interface-tests/" + created.getId()))
			.body(ApiResponse.created(created));
	}

	@PutMapping(BASE + "/{itemId}")
	public ApiResponse<InterfaceTestItemResponse> replaceItem(
		@PathVariable String itemId,
		@RequestBody InterfaceTestItemRequest request
	) {
		return ApiResponse.ok(interfaceTestService.replaceItem(itemId, request));
	}

	@PatchMapping(BASE + "/{itemId}")
	public ApiResponse<InterfaceTestItemResponse> patchItem(
		@PathVariable String itemId,
		@RequestBody InterfaceTestItemRequest request
	) {
		return ApiResponse.ok(interfaceTestService.patchItem(itemId, request));
	}

	@DeleteMapping(BASE + "/{itemId}")
	public ResponseEntity<Void> deleteItem(@PathVariable String itemId) {
		interfaceTestService.deleteItem(itemId);
		return ResponseEntity.noContent().build();
	}
}
