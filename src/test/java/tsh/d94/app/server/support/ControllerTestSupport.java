package tsh.d94.app.server.support;

import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import tsh.d94.app.server.interfaces.controller.GlobalExceptionHandler;

/**
 * 컨트롤러 단위 테스트용 MockMvc. Spring 컨텍스트 없이 컨트롤러 하나 + 운영과 같은 예외 처리기 + Pageable·Bean Validation.
 * 서비스는 Mockito mock 으로 주입한다 (@InjectMocks).
 */
public final class ControllerTestSupport {

	private ControllerTestSupport() {
	}

	public static MockMvc mvc(Object controller) {
		LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
		validator.afterPropertiesSet();
		return MockMvcBuilders.standaloneSetup(controller)
			.setControllerAdvice(new GlobalExceptionHandler())
			.setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
			.setValidator(validator)
			.build();
	}

}
