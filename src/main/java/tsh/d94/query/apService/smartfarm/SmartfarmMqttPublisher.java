package tsh.d94.app.server.apService.smartfarm;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class SmartfarmMqttPublisher {

	@Value("${smartfarm.mqtt.uri:}")
	private String uri;
	@Value("${smartfarm.mqtt.username:}")
	private String username;
	@Value("${smartfarm.mqtt.password:}")
	private String password;

	public void publishPumpCommand(String action) {
		if (uri.isBlank() || username.isBlank() || password.isBlank()) {
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Smartfarm MQTT is not configured");
		}

		try (MqttClient client = new MqttClient(uri, "d94-query-" + UUID.randomUUID(), null)) {
			MqttConnectOptions options = new MqttConnectOptions();
			options.setUserName(username);
			options.setPassword(password.toCharArray());
			options.setConnectionTimeout(5);
			options.setAutomaticReconnect(false);
			client.connect(options);
			try {
				MqttMessage message = new MqttMessage(("PUMP1_" + action).getBytes(StandardCharsets.UTF_8));
				message.setQos(1);
				message.setRetained(false);
				client.publish("smartfarm/esp32/cmd", message);
			} finally {
				if (client.isConnected()) client.disconnect();
			}
		} catch (Exception e) {
			log.warn("Smartfarm MQTT publish failed", e);
			throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Could not publish pump command");
		}
	}
}
