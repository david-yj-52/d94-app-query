package tsh.d94.app.server.apService.infra;

import static org.assertj.core.api.Assertions.*;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.ObjectMapper;

import tsh.d94.app.server.apService.apiToken.ApiTokenScope;
import tsh.d94.app.server.apService.infra.InfraDtos.IngestRequest;
import tsh.d94.app.server.apService.infra.InfraDtos.Node;
import tsh.d94.app.server.apService.infra.InfraDtos.NodeDetail;
import tsh.d94.app.server.apService.infra.InfraDtos.NodeSummary;
import tsh.d94.app.server.apService.infra.InfraDtos.Proc;
import tsh.d94.app.server.apService.infra.InfraDtos.Specs;
import tsh.d94.app.server.apService.infra.InfraDtos.Svc;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

/** 노드 등록 → 적재(자동 등록·스펙 채움·프로세스) → 목록·상세·차트 → 서비스 CRUD → tenant 격리. */
@IntegrationTest
class InfraFlowIT {

	@Autowired InfraService service;
	@Autowired ObjectMapper om;

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	private static Node node(String key, String status, Integer memTotal) {
		return new Node(null, key, key, "CLOUD_VM", "OCI", status, null, null, null, memTotal, null, null, null, null,
			null, "ap01", "ubuntu", 22, "~/.ssh/ap01", null, null, null, null);
	}

	private IngestRequest ingest(int memUsed, List<Proc> procs) throws Exception {
		return new IngestRequest(null, null, null, new Specs("Ubuntu 22.04", "Ampere", 4, 24000, null, null, 200),
			12.5, memUsed, null, 40.0, 0, 50.0, 25.0, null, null, 0.5, 1000L, om.readTree("{\"k\":1}"), procs);
	}

	@Test
	void registerIngestQuery() throws Exception {
		TestContexts.login(CiraFixtures.uniqueEmail("infra"), "T-" + System.nanoTime());
		String key = "ap01-" + (System.nanoTime() % 100000);

		NodeSummary created = service.create(node(key, "PLANNED", null));
		assertThat(created.online()).isFalse();
		assertThat(created.latest()).isNull();
		assertThatThrownBy(() -> service.create(node(key, null, null))).isInstanceOf(ResponseStatusException.class);

		service.ingest(key, ingest(6000, List.of(
			new Proc(1, "java", "PROCESS", 800.0, 3.0, 5.0, "java -jar app.jar"),
			new Proc(null, "d94-query", "CONTAINER", 1200.0, 5.0, 2.0, null),
			new Proc(2, "", "PROCESS", 1.0, 0.0, 0.0, null)))); // 이름 없으면 버림

		NodeDetail d = service.detail(key);
		NodeSummary s = d.summary();
		assertThat(s.online()).isTrue();
		assertThat(s.node().status()).isEqualTo("ACTIVE"); // PLANNED → 보고 시작하면 ACTIVE
		assertThat(s.node().memoryTotalMb()).isEqualTo(24000); // 빈 칸만 채움
		assertThat(s.node().osInfo()).isEqualTo("Ubuntu 22.04");
		assertThat(s.memFreeMb()).isEqualTo(18000);
		assertThat(s.latest().extraJson()).isEqualTo("{\"k\":1}");
		assertThat(d.processes()).extracting(Proc::procName).containsExactly("d94-query", "java"); // 메모리 큰 순

		assertThat(service.series(key, "1h")).hasSize(1).first()
			.satisfies(m -> assertThat(m.memUsedMb()).isEqualTo(6000));
		assertThatThrownBy(() -> service.series(key, "3d")).isInstanceOf(ResponseStatusException.class);

		// 처음 보는 키는 자동 등록
		service.ingest(key + "-new", ingest(100, null));
		assertThat(service.list()).extracting(n -> n.node().nodeKey()).contains(key, key + "-new");

		Svc svc = service.createService(key, new Svc(null, null, "d94-query", "DOCKER", "8080", null, null, 1024, null));
		assertThat(service.detail(key).services()).extracting(Svc::name).containsExactly("d94-query");
		service.updateService(key, svc.id(), new Svc(null, null, "d94-query2", "DOCKER", null, null, null, null, null));
		assertThat(service.detail(key).services()).extracting(Svc::name).containsExactly("d94-query2");
		assertThatThrownBy(() -> service.createService(key, new Svc(null, null, "x", "BAD", null, null, null, null, null)))
			.isInstanceOf(ResponseStatusException.class);

		// 다른 tenant 에서는 안 보임
		TestContexts.login("other@x", "OTHER-" + System.nanoTime());
		assertThat(service.list()).isEmpty();
		assertThatThrownBy(() -> service.detail(key)).isInstanceOf(ResponseStatusException.class);
	}

	@Test
	void onlineWindowAndScope() {
		OffsetDateTime now = OffsetDateTime.now();
		assertThat(InfraService.isOnline(now.minusMinutes(2), now)).isTrue();
		assertThat(InfraService.isOnline(now.minusMinutes(4), now)).isFalse();
		assertThat(InfraService.isOnline(null, now)).isFalse();
		assertThat(ApiTokenScope.INFRA.allows("/api/v1/infra/ingest/delap")).isTrue();
		assertThat(ApiTokenScope.ownerOf("/api/v1/infra/nodes")).isEqualTo(ApiTokenScope.INFRA);
	}
}
