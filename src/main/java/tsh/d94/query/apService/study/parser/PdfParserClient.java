package tsh.d94.app.server.apService.study.parser;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import tsh.d94.app.server.config.StudyProperties;

/** gateway(`/pdf/**`)를 거쳐 d94-app-pdf 를 호출한다. */
@Slf4j
@Component
public class PdfParserClient {

	static final String SERVER_ERROR = "파싱 서버 오류";
	static final String AUTH_ERROR = "파싱 서버 인증 실패";
	private static final ObjectMapper JSON = new ObjectMapper();

	private final RestClient rest;

	public PdfParserClient(StudyProperties props) {
		JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(
			HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
		factory.setReadTimeout(props.getTimeout());
		this.rest = RestClient.builder()
			.baseUrl(props.getGatewayUrl())
			.requestFactory(factory)
			.defaultHeader("X-Service-Token", props.getServiceToken())
			.build();
	}

	public ParserResponse parse(byte[] pdf, String fileName) {
		MultipartBodyBuilder body = new MultipartBodyBuilder();
		body.part("file", new ByteArrayResource(pdf) {
			@Override
			public String getFilename() {
				return fileName;
			}
		}).contentType(MediaType.APPLICATION_PDF);
		try {
			return rest.post().uri("/pdf/api/v1/parse")
				.contentType(MediaType.MULTIPART_FORM_DATA)
				.body(body.build())
				.retrieve()
				.onStatus(s -> s.value() == 422, (req, res) -> {
					throw new ParserFailure(JSON.readTree(res.getBody()).path("message").asText("PDF를 처리할 수 없습니다."));
				})
				.onStatus(s -> s.value() == 401, (req, res) -> {
					throw new ParserFailure(AUTH_ERROR);
				})
				.body(ParserResponse.class);
		} catch (ParserFailure f) {
			throw f;
		} catch (Exception e) {
			log.error("PDF parser call failed", e);
			throw new ParserFailure(SERVER_ERROR);
		}
	}
}
