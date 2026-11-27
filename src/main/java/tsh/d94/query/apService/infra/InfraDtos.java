package tsh.d94.app.server.apService.infra;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * infra 요청·응답·DB 행. ponytail: 같은 record 를 요청 본문과 DB 행에 함께 쓴다 —
 * 요청에서 id·lastSeenAt 같은 서버 관리 필드는 무시된다.
 */
public final class InfraDtos {

	private InfraDtos() {
	}

	/** SN_INFRA_NODE 한 행. 비밀번호·개인키 필드는 일부러 없다. */
	public record Node(Long id, String nodeKey, String name, String nodeType, String location, String status,
		String osInfo, String cpuModel, Integer cpuCores, Integer memoryTotalMb, String gpuModel, Integer gpuMemoryMb,
		Integer diskTotalGb, String specsExtraJson, String hostLabel, String sshAlias, String sshUser, Integer sshPort,
		String sshKeyHint, String accessNotes, OffsetDateTime lastSeenAt, OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {
	}

	/** SN_INFRA_SERVICE 한 행 */
	public record Svc(Long id, Long nodeId, String name, String kind, String portsText, String endpointUrl,
		String composePath, Integer estimatedMemMb, String description) {
	}

	/** SN_INFRA_METRIC_SNAPSHOT 한 행 (차트 집계 행도 같은 모양) */
	public record Metric(Long id, Long nodeId, OffsetDateTime collectedAt, Double cpuPct, Integer memUsedMb,
		Integer memAvailableMb, Double memPct, Integer swapUsedMb, Double diskUsedGb, Double diskPct, Double gpuUtilPct,
		Integer gpuMemUsedMb, Double loadAvg1m, Long uptimeSec, String extraJson) {
	}

	/** SN_INFRA_PROCESS_SNAPSHOT 한 행 */
	public record Proc(Integer pid, String procName, String procType, Double memRssMb, Double memPct, Double cpuPct,
		String commandLine) {
	}

	/** 목록 카드: 스펙 + 최신 메트릭 + 계산 값 */
	public record NodeSummary(Node node, Metric latest, boolean online, Integer memFreeMb, Integer gpuMemFreeMb) {
	}

	public record NodeDetail(NodeSummary summary, List<Svc> services, List<Proc> processes) {
	}

	/** 에이전트가 함께 보내는 정적 스펙 — 노드의 빈 칸만 채운다(사람이 적은 값은 덮지 않음) */
	public record Specs(String osInfo, String cpuModel, Integer cpuCores, Integer memoryTotalMb, String gpuModel,
		Integer gpuMemoryMb, Integer diskTotalGb) {
	}

	/** POST /ingest/{nodeKey}. extra 는 JSON 그대로(ESP32 free_heap·rssi, Vercel 배포 요약 등) */
	public record IngestRequest(String name, String nodeType, String location, Specs specs, Double cpuPct,
		Integer memUsedMb, Integer memAvailableMb, Double memPct, Integer swapUsedMb, Double diskUsedGb, Double diskPct,
		Double gpuUtilPct, Integer gpuMemUsedMb, Double loadAvg1m, Long uptimeSec,
		com.fasterxml.jackson.databind.JsonNode extra, List<Proc> processes) {
	}
}
