package tsh.d94.app.server.apService.infra;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tsh.d94.app.server.apService.infra.InfraDtos.IngestRequest;
import tsh.d94.app.server.apService.infra.InfraDtos.Metric;
import tsh.d94.app.server.apService.infra.InfraDtos.Node;
import tsh.d94.app.server.apService.infra.InfraDtos.NodeDetail;
import tsh.d94.app.server.apService.infra.InfraDtos.NodeSummary;
import tsh.d94.app.server.apService.infra.InfraDtos.Proc;
import tsh.d94.app.server.apService.infra.InfraDtos.Svc;
import tsh.d94.app.server.data.orm.infra.infraMetric.SnInfraMetricAccess;
import tsh.d94.app.server.data.orm.infra.infraNode.SnInfraNodeAccess;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;

/**
 * 테넌트 공용 인프라 노드·서비스·메트릭. 권한(ADMIN)은 컨트롤러가 본다.
 * 메트릭이 없는 노드(PLANNED 등)도 목록에 나온다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InfraService {

	static final Duration ONLINE_WINDOW = Duration.ofMinutes(3);
	static final int RETENTION_DAYS = 7;
	private static final int MAX_PROCESSES = 60; // ponytail: 에이전트는 상위 15 + 컨테이너 전부, 폭주 방어용 상한
	private static final int MAX_EXTRA_CHARS = 8000;

	private static final Set<String> NODE_TYPES = Set.of("HOME_PC", "CLOUD_VM", "EDGE_GPU", "MCU", "SERVERLESS");
	private static final Set<String> LOCATIONS = Set.of("HOME_LAN", "OCI", "VERCEL");
	private static final Set<String> STATUSES = Set.of("ACTIVE", "PLANNED", "MAINTENANCE");
	private static final Set<String> KINDS = Set.of("DOCKER", "SYSTEMD", "FIRMWARE", "HOSTED");
	private static final Set<String> PROC_TYPES = Set.of("PROCESS", "CONTAINER");

	/** range → (조회 초, 버킷 초) */
	private static final Map<String, long[]> RANGES = Map.of(
		"1h", new long[] {3600, 60},
		"6h", new long[] {6 * 3600, 300},
		"24h", new long[] {24 * 3600, 900},
		"7d", new long[] {7 * 24 * 3600, 3600});

	private final SnInfraNodeAccess nodes;
	private final SnInfraMetricAccess metrics;

	public List<NodeSummary> list() {
		return nodes.list(TenantContext.get()).stream().map(this::summary).toList();
	}

	public NodeDetail detail(String nodeKey) {
		Node n = node(nodeKey);
		NodeSummary s = summary(n);
		List<Proc> procs = s.latest() == null ? List.of() : metrics.processes(s.latest().id());
		return new NodeDetail(s, nodes.services(n.id()), procs);
	}

	public List<Metric> series(String nodeKey, String range) {
		long[] r = RANGES.get(range == null ? "1h" : range);
		if (r == null) {
			throw bad("range 는 1h·6h·24h·7d 중 하나입니다.");
		}
		return metrics.series(node(nodeKey).id(), r[0], r[1]);
	}

	@Transactional
	public NodeSummary create(Node req) {
		String tenant = TenantContext.get();
		validate(req, true);
		if (nodes.find(tenant, req.nodeKey()).isPresent()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "같은 키의 노드가 이미 있습니다: " + req.nodeKey());
		}
		nodes.insert(tenant, withDefaults(req));
		return summary(node(req.nodeKey()));
	}

	@Transactional
	public NodeSummary update(String nodeKey, Node req) {
		validate(req, false);
		if (nodes.update(TenantContext.get(), nodeKey, withDefaults(req)) == 0) {
			throw notFound();
		}
		return summary(node(nodeKey));
	}

	@Transactional
	public void delete(String nodeKey) {
		if (nodes.delete(TenantContext.get(), nodeKey) == 0) {
			throw notFound();
		}
	}

	@Transactional
	public Svc createService(String nodeKey, Svc req) {
		long nodeId = node(nodeKey).id();
		validate(req);
		long id = nodes.insertService(nodeId, req);
		return service(nodeId, id);
	}

	@Transactional
	public Svc updateService(String nodeKey, long id, Svc req) {
		long nodeId = node(nodeKey).id();
		validate(req);
		if (nodes.updateService(nodeId, id, req) == 0) {
			throw notFound();
		}
		return service(nodeId, id);
	}

	@Transactional
	public void deleteService(String nodeKey, long id) {
		if (nodes.deleteService(node(nodeKey).id(), id) == 0) {
			throw notFound();
		}
	}

	/**
	 * 에이전트·MQTT 브리지·Vercel 동기화의 적재. 처음 보는 nodeKey 는 노드를 만든다
	 * (이름·유형·위치는 본문 값, 없으면 키·HOME_PC·HOME_LAN) — 장비마다 웹에서 먼저 등록하지 않아도 되게.
	 */
	@Transactional
	public void ingest(String nodeKey, IngestRequest req) {
		String tenant = TenantContext.get();
		Node n = nodes.find(tenant, nodeKey).orElseGet(() -> {
			Node created = withDefaults(new Node(null, nodeKey, req.name(), req.nodeType(), req.location(), null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null));
			validate(created, true);
			nodes.insert(tenant, created);
			log.info("[infra] new node registered by ingest: {}", nodeKey);
			return node(nodeKey);
		});
		String extra = req.extra() == null || req.extra().isNull() ? null : req.extra().toString();
		if (extra != null && extra.length() > MAX_EXTRA_CHARS) {
			throw bad("extra 가 너무 큽니다.");
		}
		long snapshotId = metrics.insert(n.id(), req, extra);
		List<Proc> procs = req.processes() == null ? List.of() : req.processes().stream()
			.filter(p -> p.procName() != null && !p.procName().isBlank())
			.limit(MAX_PROCESSES)
			.map(p -> new Proc(p.pid(), cut(p.procName(), 200),
				PROC_TYPES.contains(p.procType()) ? p.procType() : "PROCESS",
				p.memRssMb(), p.memPct(), p.cpuPct(), cut(p.commandLine(), 500)))
			.toList();
		metrics.insertProcesses(snapshotId, n.id(), procs);
		nodes.touch(n.id(), req.specs());
	}

	/** 매일 04:10 서버 시각, 7일 지난 원시 스냅샷 삭제 */
	@Scheduled(cron = "0 10 4 * * *")
	public void purge() {
		int n = metrics.purgeOlderThanDays(RETENTION_DAYS);
		log.info("[infra] purged {} metric snapshots older than {} days", n, RETENTION_DAYS);
	}

	// ---------------------------------------------------------------- 내부

	NodeSummary summary(Node n) {
		Metric m = metrics.latest(n.id()).orElse(null);
		return new NodeSummary(n, m, isOnline(n.lastSeenAt(), OffsetDateTime.now()), memFree(n, m), gpuFree(n, m));
	}

	static boolean isOnline(OffsetDateTime lastSeen, OffsetDateTime now) {
		return lastSeen != null && Duration.between(lastSeen, now).compareTo(ONLINE_WINDOW) <= 0;
	}

	/** 유휴 메모리: 보고된 available 이 있으면 그것, 없으면 총량 - 사용량 */
	static Integer memFree(Node n, Metric m) {
		if (m == null) {
			return null;
		}
		if (m.memAvailableMb() != null) {
			return m.memAvailableMb();
		}
		return n.memoryTotalMb() != null && m.memUsedMb() != null ? Math.max(0, n.memoryTotalMb() - m.memUsedMb()) : null;
	}

	static Integer gpuFree(Node n, Metric m) {
		return m != null && n.gpuMemoryMb() != null && m.gpuMemUsedMb() != null
			? Math.max(0, n.gpuMemoryMb() - m.gpuMemUsedMb()) : null;
	}

	private Node node(String nodeKey) {
		return nodes.find(TenantContext.get(), nodeKey).orElseThrow(InfraService::notFound);
	}

	private Svc service(long nodeId, long id) {
		return nodes.services(nodeId).stream().filter(s -> s.id() == id).findFirst().orElseThrow(InfraService::notFound);
	}

	private static Node withDefaults(Node r) {
		return new Node(r.id(), r.nodeKey(), blank(r.name()) ? r.nodeKey() : r.name(),
			blank(r.nodeType()) ? "HOME_PC" : r.nodeType(), blank(r.location()) ? "HOME_LAN" : r.location(),
			blank(r.status()) ? "ACTIVE" : r.status(), r.osInfo(), r.cpuModel(), r.cpuCores(), r.memoryTotalMb(),
			r.gpuModel(), r.gpuMemoryMb(), r.diskTotalGb(), r.specsExtraJson(), r.hostLabel(), r.sshAlias(), r.sshUser(),
			r.sshPort(), r.sshKeyHint(), r.accessNotes(), null, null, null);
	}

	private static void validate(Node r, boolean checkKey) {
		if (checkKey && (r.nodeKey() == null || !r.nodeKey().matches("[a-z0-9][a-z0-9-]{0,49}"))) {
			throw bad("nodeKey 는 소문자·숫자·- 로 50자 이내입니다.");
		}
		check(r.nodeType(), NODE_TYPES, "nodeType");
		check(r.location(), LOCATIONS, "location");
		check(r.status(), STATUSES, "status");
		if (r.sshKeyHint() != null && r.sshKeyHint().contains("PRIVATE KEY")) {
			throw bad("개인키 원문은 저장하지 않습니다. 키 파일 경로만 적어 주세요.");
		}
	}

	private static void validate(Svc s) {
		if (blank(s.name())) {
			throw bad("서비스 이름이 필요합니다.");
		}
		if (s.kind() == null || !KINDS.contains(s.kind())) {
			throw bad("kind 는 " + KINDS + " 중 하나입니다.");
		}
	}

	private static void check(String v, Set<String> allowed, String field) {
		if (!blank(v) && !allowed.contains(v)) {
			throw bad(field + " 는 " + allowed + " 중 하나입니다.");
		}
	}

	private static boolean blank(String s) {
		return s == null || s.isBlank();
	}

	private static String cut(String s, int max) {
		return s == null || s.length() <= max ? s : s.substring(0, max);
	}

	private static ResponseStatusException bad(String msg) {
		return new ResponseStatusException(HttpStatus.BAD_REQUEST, msg);
	}

	private static ResponseStatusException notFound() {
		return new ResponseStatusException(HttpStatus.NOT_FOUND, "존재하지 않습니다.");
	}
}
