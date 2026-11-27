package tsh.d94.app.server.interfaces.controller.smartfarm;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.apService.smartfarm.SmartfarmMqttPublisher;
import tsh.d94.lib.core.interfaces.rest.ApiResponse;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/smartfarm/pumps")
@RequiredArgsConstructor
public class SmartfarmPumpController {

	private final SmartfarmMqttPublisher publisher;

	@PostMapping("/{pumpId}/command")
	@PreAuthorize("hasRole('ADMIN')")
	public ApiResponse<CommandAccepted> command(@PathVariable String pumpId, @RequestBody PumpCommand command) {
		if (!"PUMP1".equals(pumpId) || command.action() == null
			|| !("ON".equals(command.action()) || "OFF".equals(command.action()))) {
			throw new org.springframework.web.server.ResponseStatusException(
				org.springframework.http.HttpStatus.BAD_REQUEST, "Only PUMP1 ON/OFF is supported");
		}
		publisher.publishPumpCommand(command.action());
		return ApiResponse.ok(new CommandAccepted("PUMP1", command.action(), "PUBLISHED"));
	}

	public record PumpCommand(String action) {}
	public record CommandAccepted(String pumpId, String action, String status) {}
}
