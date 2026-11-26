package tsh.d94.app.server.apService.lifeMetric;

import static tsh.d94.app.server.apService.lifeMetric.LifeMetricException.Code.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.docs.Docs;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.EvaluateResponse;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.InputsRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.LogRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.LogResponse;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.MetricRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.MetricResponse;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.PreviewRequest;
import tsh.d94.app.server.apService.lifeMetric.LifeMetricDtos.TemplateResponse;
import tsh.d94.app.server.data.orm.lifeMetric.lifeMetric.SnLifeMetricAccess;
import tsh.d94.app.server.data.orm.lifeMetric.lifeMetric.SnLifeMetricModel;
import tsh.d94.app.server.data.orm.lifeMetric.lifeMetricInput.SnLifeMetricInputAccess;
import tsh.d94.app.server.data.orm.lifeMetric.lifeMetricInput.SnLifeMetricInputAccess.InputRow;
import tsh.d94.app.server.data.orm.lifeMetric.lifeMetricLog.SnLifeMetricLogAccess;
import tsh.d94.app.server.data.orm.lifeMetric.lifeMetricLog.SnLifeMetricLogAccess.Agg;
import tsh.d94.app.server.data.orm.lifeMetric.lifeMetricTemplate.SnLifeMetricTemplateAccess;
import tsh.d94.app.server.data.orm.lifeMetric.lifeMetricTemplate.SnLifeMetricTemplateModel;
import tsh.d94.lib.core.data.orm.common.tenant.TenantContext;

/**
 * 템플릿 조회, 지표 CRUD, 기준값, 시계열 로그, 평가. 소유자 = 로그인 이메일 + tenant, 남의 지표는 404.
 * ponytail: 계획서의 Template/Metric/Log 서비스 3개를 하나로 — 각자 몇 줄뿐. 커지면 나눈다.
 */
@Service
@RequiredArgsConstructor
public class LifeMetricService {

	private static final Pattern CODE = Pattern.compile("[a-z][a-z0-9_]{0,49}");
	private static final Pattern KEY = Pattern.compile("[A-Za-z_][A-Za-z0-9_]{0,49}");
	private static final int LOG_LIMIT_MAX = 1000;

	private final SnLifeMetricTemplateAccess templateAccess;
	private final SnLifeMetricAccess metricAccess;
	private final SnLifeMetricInputAccess inputAccess;
	private final SnLifeMetricLogAccess logAccess;
	private final ObjectMapper json;

	// ── 템플릿 ──

	public List<TemplateResponse> templates() {
		return templateAccess.listAll().stream()
			.map(t -> new TemplateResponse(t.getObjId(), t.getCode(), t.getName(), readJson(t.getInputSchema()),
				t.getDefaultFormula(), t.getOutputUnit()))
			.toList();
	}

	// ── 지표 ──

	public SnLifeMetricModel requireOwned(String id) {
		return metricAccess.findOwned(id, Docs.owner(), TenantContext.get())
			.orElseThrow(() -> new LifeMetricException(NOT_FOUND));
	}

	public List<MetricResponse> list() {
		return metricAccess.listOwned(Docs.owner(), TenantContext.get()).stream().map(this::toResponse).toList();
	}

	public MetricResponse get(String id) {
		return toResponse(requireOwned(id));
	}

	@Transactional
	public MetricResponse create(MetricRequest req) {
		String code = req.code() == null ? "" : req.code().trim();
		if (!CODE.matcher(code).matches()) {
			throw new LifeMetricException(INVALID_REQUEST, "코드는 영문 소문자로 시작하는 영문 소문자·숫자·_ 1~50자입니다.");
		}
		SnLifeMetricTemplateModel t = null;
		if (req.templateId() != null && !req.templateId().isBlank()) {
			t = templateAccess.find(req.templateId()).orElseThrow(() -> new LifeMetricException(NOT_FOUND));
		}
		boolean useTemplate = t != null && (req.formula() == null || req.formula().isBlank());
		SnLifeMetricModel m = Docs.base(SnLifeMetricModel.builder(), "CreateLifeMetric")
			.ownerId(Docs.owner())
			.templateId(t == null ? null : t.getObjId())
			.code(code)
			.name(name(req.name() != null ? req.name() : t == null ? null : t.getName()))
			.inputSchema(useTemplate ? t.getInputSchema() : schema(req.inputSchema()))
			.formula(useTemplate ? t.getDefaultFormula() : req.formula())
			.outputUnit(useTemplate && req.outputUnit() == null ? t.getOutputUnit() : unit(req.outputUnit()))
			.build();
		checkFormula(m);
		return toResponse(save(m));
	}

	/** code·templateId 는 바꾸지 않는다 (다른 지표의 @code 가 깨지지 않게) */
	@Transactional
	public MetricResponse update(String id, MetricRequest req) {
		SnLifeMetricModel m = requireOwned(id);
		if (req.name() != null) {
			m.setName(name(req.name()));
		}
		if (req.inputSchema() != null) {
			m.setInputSchema(schema(req.inputSchema()));
		}
		if (req.formula() != null) {
			m.setFormula(req.formula());
		}
		if (req.outputUnit() != null) {
			m.setOutputUnit(unit(req.outputUnit()));
		}
		checkFormula(m);
		return toResponse(save(m));
	}

	@Transactional
	public void delete(String id) {
		metricAccess.remove(requireOwned(id));
	}

	// ── 기준값 ──

	@Transactional
	public MetricResponse putInputs(String id, InputsRequest req) {
		SnLifeMetricModel m = requireOwned(id);
		if (req.values() == null) {
			throw new LifeMetricException(INVALID_REQUEST, "values 가 필요합니다.");
		}
		req.values().forEach((key, v) -> {
			key(key);
			if (v == null || (v instanceof String str && str.isBlank())) {
				inputAccess.delete(m.getObjId(), key);
			} else if (v instanceof Number n) {
				inputAccess.upsert(m.getObjId(), key, n.doubleValue(), null);
			} else if (v instanceof String str && str.trim().length() <= 200) {
				inputAccess.upsert(m.getObjId(), key, null, str.trim());
			} else {
				throw new LifeMetricException(INVALID_REQUEST, key + ": 숫자 또는 200자 이하 문자열이어야 합니다.");
			}
		});
		return toResponse(m);
	}

	// ── 로그 ──

	public List<LogResponse> logs(String id, String key, Integer limit) {
		SnLifeMetricModel m = requireOwned(id);
		int n = limit == null ? 200 : Math.max(1, Math.min(limit, LOG_LIMIT_MAX));
		return logAccess.list(m.getObjId(), key == null || key.isBlank() ? null : key, n).stream()
			.map(r -> new LogResponse(r.id(), r.inputKey(), r.value(), r.note(), r.recordedAt())).toList();
	}

	@Transactional
	public LogResponse addLog(String id, LogRequest req) {
		SnLifeMetricModel m = requireOwned(id);
		String key = key(req.inputKey());
		if (req.value() == null || !Double.isFinite(req.value())) {
			throw new LifeMetricException(INVALID_REQUEST, "value 는 숫자여야 합니다.");
		}
		if (req.note() != null && req.note().length() > 500) {
			throw new LifeMetricException(INVALID_REQUEST, "메모는 500자 이하입니다.");
		}
		LocalDateTime at = req.recordedAt() == null ? LocalDateTime.now() : req.recordedAt();
		long logId = logAccess.add(m.getObjId(), key, req.value(), req.note(), at);
		return new LogResponse(logId, key, req.value(), req.note(), at);
	}

	@Transactional
	public void deleteLog(String id, long logId) {
		if (logAccess.delete(requireOwned(id).getObjId(), logId) == 0) {
			throw new LifeMetricException(NOT_FOUND);
		}
	}

	// ── 평가 ──

	public EvaluateResponse evaluate(String id) {
		SnLifeMetricModel m = requireOwned(id);
		try {
			return new EvaluateResponse(eval(m, new ArrayDeque<>()), m.getOutputUnit(), null);
		} catch (LifeMetricException e) {
			return new EvaluateResponse(null, m.getOutputUnit(), e.getMessage());
		}
	}

	public EvaluateResponse preview(PreviewRequest req) {
		Map<String, InputRow> inputs = new HashMap<>();
		if (req.inputs() != null) {
			req.inputs().forEach((k, v) -> {
				if (v instanceof Number n) {
					inputs.put(k, new InputRow(n.doubleValue(), null));
				} else if (v instanceof String s && !s.isBlank()) {
					inputs.put(k, new InputRow(null, s.trim()));
				}
			});
		}
		try {
			LifeMetricFormulaEngine.validate(req.formula()); // 값 오류보다 문법 오류를 먼저 알려 준다
			double v = LifeMetricFormulaEngine.evaluate(req.formula(), scope(null, inputs, new ArrayDeque<>()));
			return new EvaluateResponse(v, null, null);
		} catch (LifeMetricException e) {
			return new EvaluateResponse(null, null, e.getMessage());
		}
	}

	/** path = 지금 평가 중인 code 들 (실행 중 순환 방어 — 저장 때 이미 막지만 동시 수정 대비) */
	private double eval(SnLifeMetricModel m, Deque<String> path) {
		if (path.contains(m.getCode())) {
			throw new LifeMetricException(CIRCULAR_REFERENCE, "지표 참조가 순환합니다: " + String.join(" → ", path) + " → " + m.getCode());
		}
		path.addLast(m.getCode());
		try {
			return LifeMetricFormulaEngine.evaluate(m.getFormula(), scope(m.getObjId(), inputAccess.find(m.getObjId()), path));
		} finally {
			path.removeLast();
		}
	}

	/** metricId 가 null 이면 (미리보기) 로그 집계는 0 */
	private LifeMetricFormulaEngine.Scope scope(String metricId, Map<String, InputRow> inputs, Deque<String> path) {
		return new LifeMetricFormulaEngine.Scope() {
			@Override
			public double var(String key) {
				InputRow r = inputs.get(key);
				if (r == null) {
					throw new LifeMetricException(EVALUATION_FAILED, "기준값이 없습니다: " + key);
				}
				return r.num() != null ? r.num() : parseText(key, r.text());
			}

			@Override
			public double ref(String code) {
				SnLifeMetricModel other = metricAccess.findByCode(Docs.owner(), TenantContext.get(), code)
					.orElseThrow(() -> new LifeMetricException(EVALUATION_FAILED, "지표가 없습니다: @" + code));
				return eval(other, path);
			}

			@Override
			public double agg(String fn, String key, Integer days) {
				if (metricId == null) {
					return 0;
				}
				LocalDateTime since = days == null ? null : LocalDate.now().minusDays(days).atStartOfDay();
				Double v = logAccess.aggregate(metricId, key, Agg.valueOf(fn.toUpperCase()), since);
				if (v == null) {
					throw new LifeMetricException(EVALUATION_FAILED, "기록이 없습니다: " + key);
				}
				return v;
			}

			@Override
			public long todayEpochDay() {
				return LocalDate.now().toEpochDay();
			}
		};
	}

	/** 텍스트 기준값: 날짜(yyyy-MM-dd) → epoch day, 숫자 문자열 → 숫자 */
	private static double parseText(String key, String text) {
		try {
			return LocalDate.parse(text).toEpochDay();
		} catch (DateTimeParseException e) {
			try {
				return Double.parseDouble(text);
			} catch (NumberFormatException e2) {
				throw new LifeMetricException(EVALUATION_FAILED, key + " 은(는) 숫자나 날짜(yyyy-MM-dd)가 아닙니다.");
			}
		}
	}

	/** 문법 검사 + 이 지표를 포함한 내 지표들 사이 @참조 순환 검사 */
	private void checkFormula(SnLifeMetricModel m) {
		LifeMetricFormulaEngine.validate(m.getFormula());
		Map<String, String> formulas = new HashMap<>();
		metricAccess.listOwned(m.getOwnerId(), m.getTenant()).forEach(o -> formulas.put(o.getCode(), o.getFormula()));
		formulas.put(m.getCode(), m.getFormula());
		List<String> cycle = LifeMetricFormulaEngine.findCycle(m.getCode(), formulas);
		if (!cycle.isEmpty()) {
			throw new LifeMetricException(CIRCULAR_REFERENCE, "지표 참조가 순환합니다: @" + String.join(" → @", cycle));
		}
	}

	private SnLifeMetricModel save(SnLifeMetricModel m) {
		if (m.getObjId() == null) {
			metricAccess.findByCode(m.getOwnerId(), m.getTenant(), m.getCode()).ifPresent(o -> {
				throw new LifeMetricException(DUPLICATE_CODE);
			});
		}
		try {
			return metricAccess.saveAndFlush(m);
		} catch (DataIntegrityViolationException e) { // 동시에 같은 코드를 만든 경우
			throw new LifeMetricException(DUPLICATE_CODE);
		}
	}

	private MetricResponse toResponse(SnLifeMetricModel m) {
		Map<String, Object> inputs = new LinkedHashMap<>();
		inputAccess.find(m.getObjId()).forEach((k, r) -> inputs.put(k, r.num() != null ? r.num() : r.text()));
		EvaluateResponse ev;
		try {
			ev = new EvaluateResponse(eval(m, new ArrayDeque<>()), m.getOutputUnit(), null);
		} catch (LifeMetricException e) {
			ev = new EvaluateResponse(null, m.getOutputUnit(), e.getMessage());
		}
		return new MetricResponse(m.getObjId(), m.getTemplateId(), m.getCode(), m.getName(), readJson(m.getInputSchema()),
			m.getFormula(), m.getOutputUnit(), inputs, ev.value(), ev.error(), m.getCreatedAt(), m.getModifiedAt());
	}

	// ── 검증 ──

	private static String name(String raw) {
		String s = raw == null ? "" : raw.trim();
		if (s.isEmpty() || s.length() > 200) {
			throw new LifeMetricException(INVALID_REQUEST, "이름은 1~200자입니다.");
		}
		return s;
	}

	private static String unit(String raw) {
		if (raw != null && raw.trim().length() > 50) {
			throw new LifeMetricException(INVALID_REQUEST, "단위는 50자 이하입니다.");
		}
		return raw == null || raw.isBlank() ? null : raw.trim();
	}

	private static String key(String raw) {
		if (raw == null || !KEY.matcher(raw).matches()) {
			throw new LifeMetricException(INVALID_REQUEST, "입력 키는 영문·숫자·_ 1~50자이고 숫자로 시작할 수 없습니다: " + raw);
		}
		return raw;
	}

	/** 배열이어야 한다. ponytail: 항목 구조는 화면이 쓰는 그대로 저장 — 서버는 키를 수식·입력값에서만 본다. */
	private String schema(JsonNode node) {
		if (node == null || node.isNull()) {
			return "[]";
		}
		if (!node.isArray()) {
			throw new LifeMetricException(INVALID_REQUEST, "inputSchema 는 배열이어야 합니다.");
		}
		return node.toString();
	}

	private JsonNode readJson(String s) {
		try {
			return json.readTree(s == null ? "[]" : s);
		} catch (JsonProcessingException e) {
			return json.createArrayNode();
		}
	}
}
