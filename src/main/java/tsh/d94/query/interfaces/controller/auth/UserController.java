package tsh.d94.app.server.interfaces.controller.auth;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.cira.dto.UserSearchResponse;
import tsh.d94.lib.core.apService.auth.UserService;
import tsh.d94.lib.core.data.orm.usersAndPermissions.gsUser.GsUserAccess;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;
import tsh.d94.lib.core.interfaces.rest.auth.BaseUserController;

@RestController
@RequestMapping("/api/v1/users")
public class UserController extends BaseUserController {

	private final GsUserAccess userAccess;

	public UserController(UserService userService, GsUserAccess userAccess) {
		super(userService);
		this.userAccess = userAccess;
	}

	@GetMapping("/search")
	public ApiResponse<List<UserSearchResponse>> searchUsers(@RequestParam String keyword) {
		List<UserSearchResponse> results = userAccess.searchByKeyword(keyword).stream()
			.map(u -> UserSearchResponse.builder()
				.id(u.getObjId())
				.name(u.getUserNm())
				.email(u.getEmail())
				.avatarUrl(u.getAvatarUrl())
				.build())
			.collect(Collectors.toList());
		return ApiResponse.ok(results);
	}
}
