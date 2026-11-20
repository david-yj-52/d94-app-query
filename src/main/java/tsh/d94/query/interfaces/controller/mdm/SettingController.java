package tsh.d94.app.server.interfaces.controller.mdm;

import java.util.Map;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.mdm.SettingService;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;

/** 기준정보 설정. 조회는 로그인 사용자 모두(로고가 모든 페이지에 보임), 변경은 ADMIN 만 */
@RestController
@RequestMapping("/api/v1/settings")
@RequiredArgsConstructor
public class SettingController {

	private final SettingService settingService;

	@GetMapping
	public ApiResponse<Map<String, String>> getAll() {
		return ApiResponse.ok(settingService.getAll());
	}

	@PutMapping
	@PreAuthorize("hasRole('ADMIN')")
	public ApiResponse<Map<String, String>> update(@RequestBody Map<String, String> changes) {
		return ApiResponse.ok(settingService.update(changes));
	}
}
