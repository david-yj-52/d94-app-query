package tsh.d94.app.server.data.orm.infra.infraMetric;

import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import static tsh.d94.app.server.data.orm.infra.infraNode.SnInfraNodeAccess.params;
import tsh.d94.app.server.apService.infra.InfraDtos.IngestRequest;
import tsh.d94.app.server.apService.infra.InfraDtos.Metric;
import tsh.d94.app.server.apService.infra.InfraDtos.Proc;

/** SN_INFRA_METRIC_SNAPSHOT·SN_INFRA_PROCESS_SNAPSHOT (JdbcClient 전용). */
@Service
@RequiredArgsConstructor
public class SnInfraMetricAccess {

	private static final String COLS = "ID, NODE_ID, COLLECTED_AT, CPU_PCT, MEM_USED_MB, MEM_AVAILABLE_MB, MEM_PCT, "
		+ "SWAP_USED_MB, DISK_USED_GB, DISK_PCT, GPU_UTIL_PCT, GPU_MEM_USED_MB, LOAD_AVG_1M AS LOAD_AVG1M, UPTIME_SEC, EXTRA_JSON";

	private final JdbcClient jdbc;

	/** 수집 시각은 서버 now() — 장비 시계 오차를 믿지 않는다 */
	public long insert(long nodeId, IngestRequest r, String extraJson) {
		return jdbc.sql("INSERT INTO SN_INFRA_METRIC_SNAPSHOT (NODE_ID, CPU_PCT, MEM_USED_MB, MEM_AVAILABLE_MB, MEM_PCT, "
				+ "SWAP_USED_MB, DISK_USED_GB, DISK_PCT, GPU_UTIL_PCT, GPU_MEM_USED_MB, LOAD_AVG_1M, UPTIME_SEC, EXTRA_JSON) "
				+ "VALUES (:node, :cpuPct, :memUsedMb, :memAvailableMb, :memPct, :swapUsedMb, :diskUsedGb, :diskPct, "
				+ ":gpuUtilPct, :gpuMemUsedMb, :loadAvg1m, :uptimeSec, :extraJson) RETURNING ID")
			.paramSource(params(r, "node", nodeId, "extraJson", extraJson)).query(Long.class).single();
	}

	public void insertProcesses(long snapshotId, long nodeId, List<Proc> procs) {
		for (Proc p : procs) {
			jdbc.sql("INSERT INTO SN_INFRA_PROCESS_SNAPSHOT (SNAPSHOT_ID, NODE_ID, COLLECTED_AT, PID, PROC_NAME, "
					+ "PROC_TYPE, MEM_RSS_MB, MEM_PCT, CPU_PCT, COMMAND_LINE) SELECT :sid, :node, COLLECTED_AT, :pid, "
					+ ":procName, :procType, :memRssMb, :memPct, :cpuPct, :commandLine "
					+ "FROM SN_INFRA_METRIC_SNAPSHOT WHERE ID=:sid")
				.paramSource(params(p, "sid", snapshotId, "node", nodeId)).update();
		}
	}

	public Optional<Metric> latest(long nodeId) {
		return jdbc.sql("SELECT " + COLS + " FROM SN_INFRA_METRIC_SNAPSHOT WHERE NODE_ID=? ORDER BY COLLECTED_AT DESC "
			+ "LIMIT 1").param(nodeId).query(Metric.class).optional();
	}

	public List<Proc> processes(long snapshotId) {
		return jdbc.sql("SELECT PID, PROC_NAME, PROC_TYPE, MEM_RSS_MB, MEM_PCT, CPU_PCT, COMMAND_LINE "
				+ "FROM SN_INFRA_PROCESS_SNAPSHOT WHERE SNAPSHOT_ID=? ORDER BY MEM_RSS_MB DESC NULLS LAST")
			.param(snapshotId).query(Proc.class).list();
	}

	/** 최근 sinceSec 초를 bucketSec 초 단위 평균으로. 시간순. */
	public List<Metric> series(long nodeId, long sinceSec, long bucketSec) {
		return jdbc.sql("SELECT NULL::BIGINT AS ID, NODE_ID, "
				+ "to_timestamp(floor(extract(epoch FROM COLLECTED_AT) / :b) * :b) AS COLLECTED_AT, "
				+ "avg(CPU_PCT) AS CPU_PCT, avg(MEM_USED_MB)::INT AS MEM_USED_MB, "
				+ "avg(MEM_AVAILABLE_MB)::INT AS MEM_AVAILABLE_MB, avg(MEM_PCT) AS MEM_PCT, "
				+ "avg(SWAP_USED_MB)::INT AS SWAP_USED_MB, avg(DISK_USED_GB) AS DISK_USED_GB, avg(DISK_PCT) AS DISK_PCT, "
				+ "avg(GPU_UTIL_PCT) AS GPU_UTIL_PCT, avg(GPU_MEM_USED_MB)::INT AS GPU_MEM_USED_MB, "
				+ "avg(LOAD_AVG_1M) AS LOAD_AVG1M, max(UPTIME_SEC) AS UPTIME_SEC, NULL AS EXTRA_JSON "
				+ "FROM SN_INFRA_METRIC_SNAPSHOT WHERE NODE_ID=:node "
				+ "AND COLLECTED_AT >= now() - make_interval(secs => :since) GROUP BY NODE_ID, 3 ORDER BY 3")
			.param("node", nodeId).param("b", bucketSec).param("since", sinceSec).query(Metric.class).list();
	}

	/** 보존 기간 지난 스냅샷 삭제(프로세스는 CASCADE) */
	public int purgeOlderThanDays(int days) {
		return jdbc.sql("DELETE FROM SN_INFRA_METRIC_SNAPSHOT WHERE COLLECTED_AT < now() - make_interval(days => ?)")
			.param(days).update();
	}
}
