package tsh.d94.app.server.data.orm.lifeMetric.lifeMetricLog;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** SN_LIFE_METRIC_LOG — 시계열 기록과 기간별 SUM/AVG/COUNT 집계 (JdbcTemplate 전용). */
@Service
@RequiredArgsConstructor
public class SnLifeMetricLogAccess {

	public enum Agg { SUM, AVG, COUNT }

	public record LogRow(long id, String inputKey, double value, String note, LocalDateTime recordedAt) {
	}

	private final JdbcTemplate jdbc;

	public long add(String metricId, String key, double value, String note, LocalDateTime recordedAt) {
		GeneratedKeyHolder kh = new GeneratedKeyHolder();
		jdbc.update(con -> {
			var ps = con.prepareStatement(
				"INSERT INTO SN_LIFE_METRIC_LOG (METRIC_ID, INPUT_KEY, NUM_VALUE, NOTE, RECORDED_AT) VALUES (?,?,?,?,?)",
				new String[] {"log_id"});
			ps.setString(1, metricId);
			ps.setString(2, key);
			ps.setDouble(3, value);
			ps.setString(4, note);
			ps.setTimestamp(5, Timestamp.valueOf(recordedAt));
			return ps;
		}, kh);
		return kh.getKey().longValue();
	}

	/** 최신순. key 가 null 이면 전체 */
	public List<LogRow> list(String metricId, String key, int limit) {
		List<Object> args = new ArrayList<>(List.of(metricId));
		String where = "";
		if (key != null) {
			where = " AND INPUT_KEY=?";
			args.add(key);
		}
		args.add(limit);
		return jdbc.query("SELECT LOG_ID, INPUT_KEY, NUM_VALUE, NOTE, RECORDED_AT FROM SN_LIFE_METRIC_LOG WHERE METRIC_ID=?"
				+ where + " ORDER BY RECORDED_AT DESC, LOG_ID DESC LIMIT ?",
			(rs, i) -> new LogRow(rs.getLong(1), rs.getString(2), rs.getDouble(3), rs.getString(4),
				rs.getTimestamp(5).toLocalDateTime()), args.toArray());
	}

	public int delete(String metricId, long logId) {
		return jdbc.update("DELETE FROM SN_LIFE_METRIC_LOG WHERE METRIC_ID=? AND LOG_ID=?", metricId, logId);
	}

	/** since 이후(null 이면 전체) 기록 집계. 기록이 없으면 SUM·COUNT 는 0, AVG 는 null */
	public Double aggregate(String metricId, String key, Agg agg, LocalDateTime since) {
		String fn = switch (agg) {
			case SUM -> "COALESCE(SUM(NUM_VALUE), 0)";
			case AVG -> "AVG(NUM_VALUE)";
			case COUNT -> "COUNT(*)";
		};
		String sql = "SELECT " + fn + " FROM SN_LIFE_METRIC_LOG WHERE METRIC_ID=? AND INPUT_KEY=?";
		return since == null
			? jdbc.queryForObject(sql, Double.class, metricId, key)
			: jdbc.queryForObject(sql + " AND RECORDED_AT >= ?", Double.class, metricId, key, Timestamp.valueOf(since));
	}
}
