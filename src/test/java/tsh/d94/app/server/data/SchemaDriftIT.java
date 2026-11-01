package tsh.d94.app.server.data;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Collectors;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import tsh.d94.app.server.support.IntegrationTest;

/**
 * 엔티티 ↔ Flyway 마이그레이션 차이(drift) 목록.
 * <ul>
 *   <li>A: 전용 컨테이너에 Flyway 만 적용한 스키마 (= 마이그레이션이 보장하는 것)</li>
 *   <li>B: 앱이 실제로 쓰는 스키마 (Flyway + {@code ddl-auto=update}, @IntegrationTest 공용 컨테이너)</li>
 * </ul>
 * B 에만 있는 테이블·컬럼 = Hibernate 가 조용히 만들어 준 것 = 마이그레이션 누락이다. 운영도 update 라 동작은 하지만,
 * 새 DB 를 Flyway 로만 만들거나 ddl-auto 를 끄면 깨진다. 결과는 {@code target/schema-drift.txt} 에 남긴다.
 */
@IntegrationTest
class SchemaDriftIT {

	private static final Path REPORT = Path.of("target", "schema-drift.txt");

	private static PostgreSQLContainer<?> flywayOnly;
	private static JdbcTemplate flywayJdbc;

	@Autowired
	private JdbcTemplate appJdbc;

	@BeforeAll
	static void migrateFlywayOnly() {
		flywayOnly = new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));
		flywayOnly.start();
		DataSource ds = new DriverManagerDataSource(flywayOnly.getJdbcUrl(), flywayOnly.getUsername(),
			flywayOnly.getPassword());
		// application.yml 의 Flyway 설정과 같게
		Flyway.configure().dataSource(ds).locations("classpath:db/migration")
			.baselineOnMigrate(true).baselineVersion("0").load().migrate();
		flywayJdbc = new JdbcTemplate(ds);
	}

	@AfterAll
	static void stop() {
		if (flywayOnly != null) {
			flywayOnly.stop();
		}
	}

	/** public 스키마의 "테이블.컬럼" → "자료형" */
	private static Map<String, String> columns(JdbcTemplate jdbc) {
		List<Map<String, Object>> rows = jdbc.queryForList("""
			select table_name, column_name, data_type
			from information_schema.columns
			where table_schema = 'public' and table_name <> 'flyway_schema_history'
			""");
		Map<String, String> result = new TreeMap<>();
		for (Map<String, Object> r : rows) {
			result.put(r.get("table_name") + "." + r.get("column_name"), String.valueOf(r.get("data_type")));
		}
		return result;
	}

	private static Set<String> tables(Map<String, String> columns) {
		return columns.keySet().stream().map(k -> k.substring(0, k.indexOf('.')))
			.collect(Collectors.toCollection(TreeSet::new));
	}

	@Test
	@DisplayName("ddl-auto=update 가 추가한 테이블·컬럼이 없어야 한다 (마이그레이션 누락 목록)")
	void flywayCoversAllEntityColumns() throws IOException {
		Map<String, String> a = columns(flywayJdbc);
		Map<String, String> b = columns(appJdbc);

		Set<String> missingTables = new TreeSet<>(tables(b));
		missingTables.removeAll(tables(a));

		Set<String> missingColumns = new TreeSet<>();
		Set<String> typeChanges = new TreeSet<>();
		b.forEach((col, type) -> {
			String table = col.substring(0, col.indexOf('.'));
			if (missingTables.contains(table)) {
				return; // 테이블 단위로 이미 보고
			}
			if (!a.containsKey(col)) {
				missingColumns.add(col + " " + type);
			} else if (!a.get(col).equals(type)) {
				typeChanges.add(col + " flyway=" + a.get(col) + " app=" + type);
			}
		});

		StringBuilder sb = new StringBuilder();
		sb.append("# schema drift: Flyway only vs Flyway + ddl-auto=update\n");
		sb.append("## missing tables (").append(missingTables.size()).append(")\n");
		missingTables.forEach(t -> sb.append(t).append('\n'));
		sb.append("## missing columns (").append(missingColumns.size()).append(")\n");
		missingColumns.forEach(c -> sb.append(c).append('\n'));
		sb.append("## type differences (").append(typeChanges.size()).append(")\n");
		typeChanges.forEach(c -> sb.append(c).append('\n'));
		Files.createDirectories(REPORT.getParent());
		Files.writeString(REPORT, sb.toString(), StandardCharsets.UTF_8);

		assertThat(missingTables).as("마이그레이션에 없는 테이블 → " + REPORT).isEmpty();
		assertThat(missingColumns).as("마이그레이션에 없는 컬럼 → " + REPORT).isEmpty();
		assertThat(typeChanges).as("자료형 차이 → " + REPORT).isEmpty();
	}

}
