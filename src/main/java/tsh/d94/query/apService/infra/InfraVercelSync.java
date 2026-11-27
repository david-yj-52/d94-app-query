package tsh.d94.app.server.apService.infra;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import tsh.d94.app.server.apService.infra.InfraDtos.IngestRequest;
import tsh.d94.app.server.data.orm.infra.infraMetric.SnInfraMetricAccess;
import tsh.d94.app.server.data.orm.infra.infraNode.SnInfraNodeAccess;

/**
 * Vercel(d94-web) 최근 배포 상태를 5분마다 SERVERLESS 노드 스냅샷 EXTRA_JSON 에 남긴다.
 * INFRA_VERCEL_TOKEN 이 없거나 그 키의 노드가 없으면 아무것도 안 한다.
 * ponytail: @EnableScheduling 을 여기 둔다(앱에 다른 스케줄 작업 없음). 스케줄이 늘면 config 로 옮길 것.
 */
@Slf4j
@Configuration
@EnableScheduling
public class InfraVercelSync {

	private final SnInfraNodeAccess nodes;
	private final SnInfraMetricAccess metrics;
	private final ObjectMapper objectMapper;
	private final String token;
	private final String project;
	private final String teamId;
	private final String nodeKey;
	private final RestClient http = RestClient.create("https://api.vercel.com");

	public InfraVercelSync(SnInfraNodeAccess nodes, SnInfraMetricAccess metrics, ObjectMapper objectMapper,
		@Value("${infra.vercel.token:}") String token, @Value("${infra.vercel.project:d94-web}") String project,
		@Value("${infra.vercel.team-id:}") String teamId, @Value("${infra.vercel.node-key:vercel-web}") String nodeKey) {
		this.nodes = nodes;
		this.metrics = metrics;
		this.objectMapper = objectMapper;
		this.token = token;
		this.project = project;
		this.teamId = teamId;
		this.nodeKey = nodeKey;
	}

	@Scheduled(fixedDelay = 300_000, initialDelay = 60_000)
	public void sync() {
		if (token.isBlank()) {
			return;
		}
		List<Long> ids = nodes.idsByKey(nodeKey);
		if (ids.isEmpty()) {
			return;
		}
		try {
			JsonNode body = http.get()
				.uri(u -> {
					u.path("/v6/deployments").queryParam("projectId", project).queryParam("limit", 5);
					if (!teamId.isBlank()) {
						u.queryParam("teamId", teamId);
					}
					return u.build();
				})
				.header("Authorization", "Bearer " + token)
				.retrieve().body(JsonNode.class);
			String extra = objectMapper.writeValueAsString(summarize(body));
			IngestRequest empty = new IngestRequest(null, null, null, null, null, null, null, null, null, null, null,
				null, null, null, null, null, null);
			for (Long id : ids) {
				metrics.insert(id, empty, extra);
				nodes.touch(id, null);
			}
		} catch (Exception e) {
			log.warn("[infra] vercel sync failed: {}", e.getMessage());
		}
	}

	/** 최근 배포 5건의 상태만 — 토큰·환경변수 같은 값은 담지 않는다 */
	static Map<String, Object> summarize(JsonNode body) {
		List<Map<String, Object>> recent = new ArrayList<>();
		for (JsonNode d : body.path("deployments")) {
			recent.add(Map.of(
				"state", d.path("state").asText(d.path("readyState").asText("")),
				"target", d.path("target").asText(""),
				"url", d.path("url").asText(""),
				"createdAt", d.path("created").asLong(0)));
		}
		return Map.of("source", "vercel", "deployments", recent,
			"latestState", recent.isEmpty() ? "" : recent.get(0).get("state"));
	}
}
