package tsh.d94.app.server.interfaces.controller.infra;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.infra.InfraDtos.IngestRequest;
import tsh.d94.app.server.apService.infra.InfraDtos.Metric;
import tsh.d94.app.server.apService.infra.InfraDtos.Node;
import tsh.d94.app.server.apService.infra.InfraDtos.NodeDetail;
import tsh.d94.app.server.apService.infra.InfraDtos.NodeSummary;
import tsh.d94.app.server.apService.infra.InfraDtos.Svc;
import tsh.d94.app.server.apService.infra.InfraService;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

/**
 * 테넌트 공용 인프라. 조회는 로그인 사용자 전체, 등록·수정·적재는 ADMIN.
 * ponytail: 에이전트 적재는 ADMIN 이 발급한 d94_infra WRITE 토큰 — 토큰은 발급자 권한으로 동작하므로 같은 규칙 하나로 충분.
 */
@RestController
@RequestMapping("/api/v1/infra")
@RequiredArgsConstructor
public class InfraController {

	private static final String ADMIN = "hasRole('ADMIN')";

	private final InfraService service;

	@GetMapping("/nodes")
	public ApiResponse<List<NodeSummary>> list() {
		return ApiResponse.ok(service.list());
	}

	@GetMapping("/nodes/{nodeKey}")
	public ApiResponse<NodeDetail> detail(@PathVariable String nodeKey) {
		return ApiResponse.ok(service.detail(nodeKey));
	}

	@GetMapping("/nodes/{nodeKey}/metrics")
	public ApiResponse<List<Metric>> metrics(@PathVariable String nodeKey,
		@RequestParam(required = false) String range) {
		return ApiResponse.ok(service.series(nodeKey, range));
	}

	@PreAuthorize(ADMIN)
	@PostMapping("/nodes")
	public ApiResponse<NodeSummary> create(@RequestBody Node req) {
		return ApiResponse.ok(service.create(req));
	}

	@PreAuthorize(ADMIN)
	@PutMapping("/nodes/{nodeKey}")
	public ApiResponse<NodeSummary> update(@PathVariable String nodeKey, @RequestBody Node req) {
		return ApiResponse.ok(service.update(nodeKey, req));
	}

	@PreAuthorize(ADMIN)
	@DeleteMapping("/nodes/{nodeKey}")
	public ApiResponse<Void> delete(@PathVariable String nodeKey) {
		service.delete(nodeKey);
		return ApiResponse.noContent();
	}

	@PreAuthorize(ADMIN)
	@PostMapping("/nodes/{nodeKey}/services")
	public ApiResponse<Svc> createService(@PathVariable String nodeKey, @RequestBody Svc req) {
		return ApiResponse.ok(service.createService(nodeKey, req));
	}

	@PreAuthorize(ADMIN)
	@PutMapping("/nodes/{nodeKey}/services/{id}")
	public ApiResponse<Svc> updateService(@PathVariable String nodeKey, @PathVariable long id, @RequestBody Svc req) {
		return ApiResponse.ok(service.updateService(nodeKey, id, req));
	}

	@PreAuthorize(ADMIN)
	@DeleteMapping("/nodes/{nodeKey}/services/{id}")
	public ApiResponse<Void> deleteService(@PathVariable String nodeKey, @PathVariable long id) {
		service.deleteService(nodeKey, id);
		return ApiResponse.noContent();
	}

	@PreAuthorize(ADMIN)
	@PostMapping("/ingest/{nodeKey}")
	public ApiResponse<Void> ingest(@PathVariable String nodeKey, @RequestBody IngestRequest req) {
		service.ingest(nodeKey, req);
		return ApiResponse.noContent();
	}
}
