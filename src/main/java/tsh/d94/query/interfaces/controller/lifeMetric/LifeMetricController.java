package tsh.d94.app.server.interfaces.controller.lifeMetric;

import java.util.List;

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
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.EvaluateResponse;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.InputsRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.LogRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.LogResponse;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.MetricRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.MetricResponse;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.PreviewRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.TemplateResponse;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricService;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

/** life_metric — 로그인 사용자의 지표. 남의 지표·기준값·로그는 404. */
@RestController
@RequestMapping("/api/v1/life-metric")
@RequiredArgsConstructor
public class LifeMetricController {

	private final LifeMetricService service;

	@GetMapping("/templates")
	public ApiResponse<List<TemplateResponse>> templates() {
		return ApiResponse.ok(service.templates());
	}

	@GetMapping("/metrics")
	public ApiResponse<List<MetricResponse>> list() {
		return ApiResponse.ok(service.list());
	}

	@PostMapping("/metrics")
	public ApiResponse<MetricResponse> create(@RequestBody MetricRequest req) {
		return ApiResponse.ok(service.create(req));
	}

	@GetMapping("/metrics/{id}")
	public ApiResponse<MetricResponse> get(@PathVariable String id) {
		return ApiResponse.ok(service.get(id));
	}

	@PutMapping("/metrics/{id}")
	public ApiResponse<MetricResponse> update(@PathVariable String id, @RequestBody MetricRequest req) {
		return ApiResponse.ok(service.update(id, req));
	}

	@DeleteMapping("/metrics/{id}")
	public ApiResponse<Void> delete(@PathVariable String id) {
		service.delete(id);
		return ApiResponse.noContent();
	}

	@PutMapping("/metrics/{id}/inputs")
	public ApiResponse<MetricResponse> putInputs(@PathVariable String id, @RequestBody InputsRequest req) {
		return ApiResponse.ok(service.putInputs(id, req));
	}

	@GetMapping("/metrics/{id}/logs")
	public ApiResponse<List<LogResponse>> logs(@PathVariable String id, @RequestParam(required = false) String key,
		@RequestParam(required = false) Integer limit) {
		return ApiResponse.ok(service.logs(id, key, limit));
	}

	@PostMapping("/metrics/{id}/logs")
	public ApiResponse<LogResponse> addLog(@PathVariable String id, @RequestBody LogRequest req) {
		return ApiResponse.ok(service.addLog(id, req));
	}

	@DeleteMapping("/metrics/{id}/logs/{logId}")
	public ApiResponse<Void> deleteLog(@PathVariable String id, @PathVariable long logId) {
		service.deleteLog(id, logId);
		return ApiResponse.noContent();
	}

	@GetMapping("/metrics/{id}/evaluate")
	public ApiResponse<EvaluateResponse> evaluate(@PathVariable String id) {
		return ApiResponse.ok(service.evaluate(id));
	}

	@PostMapping("/preview")
	public ApiResponse<EvaluateResponse> preview(@RequestBody PreviewRequest req) {
		return ApiResponse.ok(service.preview(req));
	}
}
