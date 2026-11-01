package tsh.d94.app.server.data;

import static org.assertj.core.api.Assertions.*;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.support.TransactionTemplate;

import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;
import tsh.d94.lib.core.data.orm.common.repo.BaseJpaRepository;

/**
 * CIRA 리포지토리의 **모든 커스텀 메서드**(파생 쿼리·@Query·native)를 실제 PostgreSQL 에서 한 번씩 실행한다.
 * 결과 값이 아니라 "SQL 이 PostgreSQL 에서 오류 없이 실행되는가"(바인딩·자료형·native 문법)를 본다.
 * 각 호출은 롤백되는 트랜잭션 안에서 실행하므로 수정 쿼리도 데이터를 남기지 않는다.
 * 메서드별 결과는 {@code target/repository-queries.txt}. 의미 검증(필터·정렬·tenant)은 도메인별 *AccessIT 에서 한다.
 */
@IntegrationTest
class CiraRepositoryQueryIT {

	private static final Path REPORT = Path.of("target", "repository-queries.txt");
	private static final String CIRA_ORM_PACKAGE = "tsh.d94.app.server.data.orm.cira.";
	/** JpaRepository·JpaSpecificationExecutor 등 Spring Data 기본 메서드는 제외 (프레임워크가 검증) */
	private static final Set<String> BASE_TYPES = Set.of(
		"org.springframework.data.", "tsh.d94.lib.core.data.orm.common.repo.");

	@Autowired
	private ApplicationContext context;
	@Autowired
	private TransactionTemplate tx;

	@BeforeEach
	void setUp() {
		TestContexts.login("query-smoke@test.d94");
	}

	@AfterEach
	void tearDown() {
		TestContexts.clear();
	}

	@Test
	@DisplayName("CIRA 리포지토리 커스텀 메서드가 모두 PostgreSQL 에서 실행된다")
	@SuppressWarnings("rawtypes")
	void allCustomRepositoryMethodsExecute() throws IOException {
		Map<String, String> results = new TreeMap<>();
		List<String> failures = new ArrayList<>();

		Map<String, BaseJpaRepository> repos = context.getBeansOfType(BaseJpaRepository.class);
		for (BaseJpaRepository repo : repos.values()) {
			Class<?> repoInterface = Arrays.stream(AopUtils.getTargetClass(repo).getInterfaces().length > 0
					? repo.getClass().getInterfaces() : new Class<?>[0])
				.filter(i -> i.getName().startsWith(CIRA_ORM_PACKAGE))
				.findFirst().orElse(null);
			if (repoInterface == null) {
				continue;
			}
			Method[] methods = repoInterface.getDeclaredMethods();
			Arrays.sort(methods, Comparator.comparing(Method::toGenericString));
			for (Method m : methods) {
				if (m.isDefault() || m.isSynthetic()
					|| BASE_TYPES.stream().anyMatch(p -> m.getDeclaringClass().getName().startsWith(p))) {
					continue;
				}
				String key = repoInterface.getSimpleName() + "." + m.getName()
					+ Arrays.toString(m.getParameterTypes()).replaceAll("class |java\\.(lang|util|time|math)\\.", "");
				try {
					Object[] args = argsFor(m);
					tx.executeWithoutResult(status -> {
						try {
							Object out = m.invoke(repo, args);
							results.put(key, "OK " + describe(out));
						} catch (IllegalAccessException e) {
							throw new IllegalStateException(e);
						} catch (InvocationTargetException e) {
							throw new QueryFailure(e.getTargetException());
						} finally {
							status.setRollbackOnly();
						}
					});
				} catch (QueryFailure e) {
					String msg = rootMessage(e.getCause());
					results.put(key, "FAIL " + msg);
					failures.add(key + " → " + msg);
				} catch (UnsupportedOperationException e) {
					results.put(key, "SKIP " + e.getMessage());
				}
			}
		}

		StringBuilder sb = new StringBuilder("# CIRA repository custom queries on postgres:16\n");
		results.forEach((k, v) -> sb.append(v.startsWith("OK") ? "  " : "!!").append(' ')
			.append(k).append(" : ").append(v).append('\n'));
		sb.append("\n# total=").append(results.size()).append(" failed=").append(failures.size()).append('\n');
		Files.createDirectories(REPORT.getParent());
		Files.writeString(REPORT, sb.toString(), StandardCharsets.UTF_8);

		assertThat(results).as("CIRA 리포지토리를 하나도 찾지 못함 (스캔 경로 확인)").isNotEmpty();
		assertThat(failures).as("PostgreSQL 에서 실패한 쿼리 → " + REPORT).isEmpty();
	}

	private static Object[] argsFor(Method m) {
		Type[] types = m.getGenericParameterTypes();
		Object[] args = new Object[types.length];
		for (int i = 0; i < types.length; i++) {
			args[i] = sample(types[i]);
		}
		return args;
	}

	@SuppressWarnings({"unchecked", "rawtypes"})
	private static Object sample(Type type) {
		Class<?> raw = type instanceof ParameterizedType p ? (Class<?>)p.getRawType() : (Class<?>)type;
		if (raw == String.class) {
			return "it-sample";
		}
		if (raw == Integer.class || raw == int.class) {
			return 1;
		}
		if (raw == Long.class || raw == long.class) {
			return 1L;
		}
		if (raw == Boolean.class || raw == boolean.class) {
			return true;
		}
		if (raw == BigDecimal.class) {
			return BigDecimal.ONE;
		}
		if (raw == LocalDate.class) {
			return LocalDate.now();
		}
		if (raw == LocalDateTime.class) {
			return LocalDateTime.now();
		}
		if (raw == OffsetDateTime.class) {
			return OffsetDateTime.now();
		}
		if (raw == Pageable.class) {
			return PageRequest.of(0, 10);
		}
		if (raw == Sort.class) {
			return Sort.unsorted();
		}
		if (raw.isEnum()) {
			return raw.getEnumConstants()[0];
		}
		if (Collection.class.isAssignableFrom(raw) && type instanceof ParameterizedType p) {
			Object element = sample(p.getActualTypeArguments()[0]);
			return Set.class.isAssignableFrom(raw) ? Set.of(element) : List.of(element);
		}
		throw new UnsupportedOperationException("sample 인자 없음: " + type.getTypeName());
	}

	private static String describe(Object out) {
		if (out == null) {
			return "null";
		}
		if (out instanceof Collection<?> c) {
			return "size=" + c.size();
		}
		if (out instanceof java.util.Optional<?> o) {
			return o.isPresent() ? "present" : "empty";
		}
		return out.getClass().getSimpleName();
	}

	private static String rootMessage(Throwable t) {
		Throwable root = t;
		while (root.getCause() != null && root.getCause() != root) {
			root = root.getCause();
		}
		String msg = root.getClass().getSimpleName() + ": " + root.getMessage();
		return msg.length() > 300 ? msg.substring(0, 300) : msg;
	}

	private static final class QueryFailure extends RuntimeException {
		QueryFailure(Throwable cause) {
			super(cause);
		}
	}

}
