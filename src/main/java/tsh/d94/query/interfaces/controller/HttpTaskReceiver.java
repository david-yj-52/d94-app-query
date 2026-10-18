package tsh.d94.app.server.interfaces.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tsh.d94.app.server.MessageHandlerRegistry;
import tsh.d94.lib.core.interfaces.InterfaceType;
import tsh.d94.lib.core.spec.constant.ApMessageList;

import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/api/msg/execute")
@Slf4j
@Validated
public class HttpTaskReceiver {

	@Autowired
	MessageHandlerRegistry registry;

	@PostMapping
	public void run(@RequestParam ApMessageList eventName, @RequestBody String payload) throws Exception {

		log.info("eventName:{} , payload:{}", eventName.name(), payload);
		registry.getHandler(eventName).handle(payload, InterfaceType.REST, null);

	}
}
