package tsh.d94.app.server.data.orm.lifeMetric.lifeMetricInput;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;

/** SN_LIFE_METRIC_INPUT — 지표별 기준값(key → 숫자 또는 텍스트). ponytail: 키-값 한 줄이라 엔티티 없이 JDBC. */
@Service
@RequiredArgsConstructor
public class SnLifeMetricInputAccess {

	public record InputRow(Double num, String text) {
	}

	private final JdbcTemplate jdbc;

	public Map<String, InputRow> find(String metricId) {
		Map<String, InputRow> out = new LinkedHashMap<>();
		jdbc.query("SELECT INPUT_KEY, NUM_VALUE, TEXT_VALUE FROM SN_LIFE_METRIC_INPUT WHERE METRIC_ID=? ORDER BY INPUT_KEY",
			rs -> {
				Double n = rs.getObject(2, Double.class);
				out.put(rs.getString(1), new InputRow(n, rs.getString(3)));
			}, metricId);
		return out;
	}

	public void upsert(String metricId, String key, Double num, String text) {
		jdbc.update("""
			INSERT INTO SN_LIFE_METRIC_INPUT (METRIC_ID, INPUT_KEY, NUM_VALUE, TEXT_VALUE, MODIFIED_AT)
			VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)
			ON CONFLICT (METRIC_ID, INPUT_KEY)
			DO UPDATE SET NUM_VALUE = EXCLUDED.NUM_VALUE, TEXT_VALUE = EXCLUDED.TEXT_VALUE, MODIFIED_AT = CURRENT_TIMESTAMP
			""", metricId, key, num, text);
	}

	public void delete(String metricId, String key) {
		jdbc.update("DELETE FROM SN_LIFE_METRIC_INPUT WHERE METRIC_ID=? AND INPUT_KEY=?", metricId, key);
	}
}
