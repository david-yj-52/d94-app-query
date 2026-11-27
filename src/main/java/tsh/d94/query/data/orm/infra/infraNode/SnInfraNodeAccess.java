package tsh.d94.app.server.data.orm.infra.infraNode;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.SimplePropertySqlParameterSource;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.infra.InfraDtos.Node;
import tsh.d94.app.server.apService.infra.InfraDtos.Specs;
import tsh.d94.app.server.apService.infra.InfraDtos.Svc;

/** SN_INFRA_NODE·SN_INFRA_SERVICE (JdbcClient 전용, 엔티티 없음). 노드는 tenant 안에서 NODE_KEY 로 찾는다. */
@Service
@RequiredArgsConstructor
public class SnInfraNodeAccess {

	private static final String NODE_COLS = "ID, NODE_KEY, NAME, NODE_TYPE, LOCATION, STATUS, OS_INFO, CPU_MODEL, "
		+ "CPU_CORES, MEMORY_TOTAL_MB, GPU_MODEL, GPU_MEMORY_MB, DISK_TOTAL_GB, SPECS_EXTRA_JSON, HOST_LABEL, SSH_ALIAS, "
		+ "SSH_USER, SSH_PORT, SSH_KEY_HINT, ACCESS_NOTES, LAST_SEEN_AT, CREATED_AT, UPDATED_AT";

	private final JdbcClient jdbc;

	/**
	 * record 의 필드 + 추가 이름 값. JdbcClient 는 paramSource 와 param(name) 을 섞으면 앞의 것을 버려서 하나로 합친다.
	 */
	public static SqlParameterSource params(Object record, Object... nameValues) {
		SqlParameterSource base = new SimplePropertySqlParameterSource(record);
		MapSqlParameterSource extra = new MapSqlParameterSource();
		for (int i = 0; i < nameValues.length; i += 2) {
			extra.addValue((String)nameValues[i], nameValues[i + 1]);
		}
		return new SqlParameterSource() {
			@Override
			public boolean hasValue(String name) {
				return extra.hasValue(name) || base.hasValue(name);
			}

			@Override
			public Object getValue(String name) {
				return extra.hasValue(name) ? extra.getValue(name) : base.getValue(name);
			}
		};
	}

	public List<Node> list(String tenant) {
		return jdbc.sql("SELECT " + NODE_COLS + " FROM SN_INFRA_NODE WHERE TENANT=? ORDER BY LOCATION, NODE_KEY")
			.param(tenant).query(Node.class).list();
	}

	public Optional<Node> find(String tenant, String nodeKey) {
		return jdbc.sql("SELECT " + NODE_COLS + " FROM SN_INFRA_NODE WHERE TENANT=? AND NODE_KEY=?")
			.params(tenant, nodeKey).query(Node.class).optional();
	}

	public void insert(String tenant, Node n) {
		jdbc.sql("INSERT INTO SN_INFRA_NODE (TENANT, NODE_KEY, NAME, NODE_TYPE, LOCATION, STATUS, OS_INFO, CPU_MODEL, "
				+ "CPU_CORES, MEMORY_TOTAL_MB, GPU_MODEL, GPU_MEMORY_MB, DISK_TOTAL_GB, SPECS_EXTRA_JSON, HOST_LABEL, "
				+ "SSH_ALIAS, SSH_USER, SSH_PORT, SSH_KEY_HINT, ACCESS_NOTES) VALUES (:tenant, :nodeKey, :name, :nodeType, "
				+ ":location, :status, :osInfo, :cpuModel, :cpuCores, :memoryTotalMb, :gpuModel, :gpuMemoryMb, "
				+ ":diskTotalGb, :specsExtraJson, :hostLabel, :sshAlias, :sshUser, :sshPort, :sshKeyHint, :accessNotes)")
			.paramSource(params(n, "tenant", tenant)).update();
	}

	/** 스케줄러용(tenant 없음): 모든 tenant 의 같은 키 노드 ID */
	public List<Long> idsByKey(String nodeKey) {
		return jdbc.sql("SELECT ID FROM SN_INFRA_NODE WHERE NODE_KEY=?").param(nodeKey).query(Long.class).list();
	}

	/** NODE_KEY 는 바꾸지 않는다 */
	public int update(String tenant, String nodeKey, Node n) {
		return jdbc.sql("UPDATE SN_INFRA_NODE SET NAME=:name, NODE_TYPE=:nodeType, LOCATION=:location, STATUS=:status, "
				+ "OS_INFO=:osInfo, CPU_MODEL=:cpuModel, CPU_CORES=:cpuCores, MEMORY_TOTAL_MB=:memoryTotalMb, "
				+ "GPU_MODEL=:gpuModel, GPU_MEMORY_MB=:gpuMemoryMb, DISK_TOTAL_GB=:diskTotalGb, "
				+ "SPECS_EXTRA_JSON=:specsExtraJson, HOST_LABEL=:hostLabel, SSH_ALIAS=:sshAlias, SSH_USER=:sshUser, "
				+ "SSH_PORT=:sshPort, SSH_KEY_HINT=:sshKeyHint, ACCESS_NOTES=:accessNotes, UPDATED_AT=now() "
				+ "WHERE TENANT=:tenant AND NODE_KEY=:key")
			.paramSource(params(n, "tenant", tenant, "key", nodeKey)).update();
	}

	public int delete(String tenant, String nodeKey) {
		return jdbc.sql("DELETE FROM SN_INFRA_NODE WHERE TENANT=? AND NODE_KEY=?").params(tenant, nodeKey).update();
	}

	/** 수집 시: 빈 스펙 칸만 채우고 LAST_SEEN_AT 갱신. PLANNED 장비가 보고하기 시작하면 ACTIVE 로 */
	public void touch(long nodeId, Specs s) {
		jdbc.sql("UPDATE SN_INFRA_NODE SET OS_INFO=COALESCE(OS_INFO, :osInfo), CPU_MODEL=COALESCE(CPU_MODEL, :cpuModel), "
				+ "CPU_CORES=COALESCE(CPU_CORES, :cpuCores), MEMORY_TOTAL_MB=COALESCE(MEMORY_TOTAL_MB, :memoryTotalMb), "
				+ "GPU_MODEL=COALESCE(GPU_MODEL, :gpuModel), GPU_MEMORY_MB=COALESCE(GPU_MEMORY_MB, :gpuMemoryMb), "
				+ "DISK_TOTAL_GB=COALESCE(DISK_TOTAL_GB, :diskTotalGb), "
				+ "STATUS=CASE WHEN STATUS='PLANNED' THEN 'ACTIVE' ELSE STATUS END, LAST_SEEN_AT=now() WHERE ID=:id")
			.paramSource(params(s != null ? s : new Specs(null, null, null, null, null, null, null), "id", nodeId))
			.update();
	}

	public List<Svc> services(long nodeId) {
		return jdbc.sql("SELECT * FROM SN_INFRA_SERVICE WHERE NODE_ID=? ORDER BY NAME").param(nodeId)
			.query(Svc.class).list();
	}

	public long insertService(long nodeId, Svc s) {
		return jdbc.sql("INSERT INTO SN_INFRA_SERVICE (NODE_ID, NAME, KIND, PORTS_TEXT, ENDPOINT_URL, COMPOSE_PATH, "
				+ "ESTIMATED_MEM_MB, DESCRIPTION) VALUES (:node, :name, :kind, :portsText, :endpointUrl, :composePath, "
				+ ":estimatedMemMb, :description) RETURNING ID")
			.paramSource(params(s, "node", nodeId)).query(Long.class).single();
	}

	public int updateService(long nodeId, long id, Svc s) {
		return jdbc.sql("UPDATE SN_INFRA_SERVICE SET NAME=:name, KIND=:kind, PORTS_TEXT=:portsText, "
				+ "ENDPOINT_URL=:endpointUrl, COMPOSE_PATH=:composePath, ESTIMATED_MEM_MB=:estimatedMemMb, "
				+ "DESCRIPTION=:description WHERE NODE_ID=:node AND ID=:sid")
			.paramSource(params(s, "node", nodeId, "sid", id)).update();
	}

	public int deleteService(long nodeId, long id) {
		return jdbc.sql("DELETE FROM SN_INFRA_SERVICE WHERE NODE_ID=? AND ID=?").params(nodeId, id).update();
	}
}
