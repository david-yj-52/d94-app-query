package tsh.d94.app.server.interfaces.controller.cira;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.DashboardService;
import tsh.d94.app.server.apService.cira.dto.BurndownResponse;
import tsh.d94.app.server.apService.cira.dto.CfdResponse;
import tsh.d94.app.server.apService.cira.dto.ProjectStatsResponse;
import tsh.d94.app.server.apService.cira.dto.UserDashboardResponse;
import tsh.d94.app.server.apService.cira.dto.VelocityItemResponse;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DashboardController {

	private final DashboardService dashboardService;

	@GetMapping("/sprints/{sprintId}/burndown")
	public ApiResponse<BurndownResponse> getBurndown(@PathVariable String sprintId) {
		return ApiResponse.ok(dashboardService.getBurndown(sprintId));
	}

	@GetMapping("/projects/{projectId}/velocity")
	public ApiResponse<List<VelocityItemResponse>> getVelocity(
		@PathVariable String projectId,
		@RequestParam(defaultValue = "6") int lastN
	) {
		return ApiResponse.ok(dashboardService.getVelocity(projectId, lastN));
	}

	@GetMapping("/projects/{projectId}/cfd")
	public ApiResponse<CfdResponse> getCfd(
		@PathVariable String projectId,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
		@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
	) {
		return ApiResponse.ok(dashboardService.getCfd(projectId, startDate, endDate));
	}

	@GetMapping("/users/me/dashboard")
	public ApiResponse<UserDashboardResponse> getUserDashboard() {
		return ApiResponse.ok(dashboardService.getUserDashboard());
	}

	@GetMapping("/projects/{projectId}/stats")
	public ApiResponse<ProjectStatsResponse> getProjectStats(@PathVariable String projectId) {
		return ApiResponse.ok(dashboardService.getProjectStats(projectId));
	}

}
