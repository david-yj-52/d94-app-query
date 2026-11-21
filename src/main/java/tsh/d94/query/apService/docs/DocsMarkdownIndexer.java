package tsh.d94.app.server.apService.docs;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

import tsh.d94.app.server.data.orm.docs.docsIndex.DocsIndexAccess.Link;
import tsh.d94.app.server.data.orm.docs.docsIndex.DocsIndexAccess.Task;

/**
 * Markdown 본문에서 frontmatter(kind·stream·origin·tags)·[[링크]]·#태그·체크박스 Task 를 뽑는다. 상태 없음.
 * ponytail: 코드블록 안의 # · [[ · - [ ] 도 색인된다. 오탐이 거슬리면 펜스 블록을 빼고 파싱.
 */
public final class DocsMarkdownIndexer {

	public static final Set<String> KINDS = Set.of("idea", "doc", "task", "knowledge");

	public record Index(String kind, String stream, String origin, List<Link> links, Set<String> tags,
		List<Task> tasks) {
	}

	private static final Pattern FRONTMATTER = Pattern.compile("\\A---\\r?\\n(.*?)\\r?\\n---[ \\t]*(\\r?\\n|\\z)",
		Pattern.DOTALL);
	private static final Pattern WIKILINK = Pattern.compile("\\[\\[([^\\[\\]|#\\n]+)(?:#[^\\[\\]|\\n]*)?(?:\\|[^\\[\\]\\n]*)?]]");
	private static final Pattern TAG = Pattern.compile("(?<![\\p{L}\\p{N}_&/#])#([\\p{L}\\p{N}_][\\p{L}\\p{N}_/-]*)");
	static final Pattern TASK = Pattern.compile("^(\\s*[-*] \\[)([ xX/])(] )(.+)$");
	private static final Pattern DUE = Pattern.compile("📅\\s*(\\d{4}-\\d{2}-\\d{2})");

	private DocsMarkdownIndexer() {
	}

	public static Index index(String content) {
		String text = content == null ? "" : content;
		Map<String, Object> fm = frontmatter(text);
		String body = text.substring(frontmatterEnd(text));

		String kind = str(fm.get("kind"));
		kind = kind != null && KINDS.contains(kind.toLowerCase(Locale.ROOT)) ? kind.toLowerCase(Locale.ROOT) : "idea";
		String stream = str(fm.get("stream"));
		String origin = linkTitle(fm.get("origin"));

		Set<Link> links = new LinkedHashSet<>();
		if (origin != null) {
			links.add(new Link(origin, "origin"));
		}
		Matcher m = WIKILINK.matcher(body);
		while (m.find()) {
			String t = m.group(1).trim();
			if (!t.isEmpty()) {
				links.add(new Link(t, "link"));
			}
		}

		Set<String> tags = new LinkedHashSet<>();
		Object fmTags = fm.get("tags");
		if (fmTags instanceof Collection<?> c) {
			c.forEach(t -> addTag(tags, String.valueOf(t)));
		} else if (fmTags != null) {
			for (String t : String.valueOf(fmTags).split("[,\\s]+")) {
				addTag(tags, t);
			}
		}
		Matcher tm = TAG.matcher(body);
		while (tm.find()) {
			addTag(tags, tm.group(1));
		}

		List<Task> tasks = new ArrayList<>();
		String[] lines = text.split("\\r?\\n", -1);
		int bodyStartLine = lineOf(text, frontmatterEnd(text));
		for (int i = bodyStartLine; i < lines.length; i++) {
			Matcher x = TASK.matcher(lines[i]);
			if (x.matches()) {
				Matcher d = DUE.matcher(x.group(4));
				tasks.add(new Task(i, x.group(4).trim(), status(x.group(2)), d.find() ? parseDate(d.group(1)) : null));
			}
		}
		return new Index(kind, stream, origin, List.copyOf(links), tags, tasks);
	}

	/** 체크박스 문자 → 상태 */
	public static String status(String mark) {
		return switch (mark) {
			case "x", "X" -> "done";
			case "/" -> "doing";
			default -> "todo";
		};
	}

	/** 상태 → 체크박스 문자 */
	public static String mark(String status) {
		return switch (status) {
			case "done" -> "x";
			case "doing" -> "/";
			case "todo" -> " ";
			default -> throw new IllegalArgumentException("status: " + status);
		};
	}

	/** [[old]]·[[old|별칭]]·[[old#헤딩]] 의 제목 부분만 new 로 바꾼다 (frontmatter origin 포함) */
	public static String renameLinks(String content, String oldTitle, String newTitle) {
		if (content == null) {
			return null;
		}
		Pattern p = Pattern.compile("\\[\\[\\s*" + Pattern.quote(oldTitle) + "\\s*(?=[#|\\]])");
		return p.matcher(content).replaceAll(Matcher.quoteReplacement("[[" + newTitle));
	}

	/** frontmatter 의 key 를 value 로 바꾸거나 넣는다. frontmatter 가 없으면 만든다. */
	public static String setFrontmatter(String content, String key, String value) {
		String text = content == null ? "" : content;
		String line = key + ": " + value;
		Matcher m = FRONTMATTER.matcher(text);
		if (!m.find()) {
			return "---\n" + line + "\n---\n" + text;
		}
		String yaml = m.group(1);
		Pattern kp = Pattern.compile("(?m)^" + Pattern.quote(key) + "\\s*:.*$");
		String updated = kp.matcher(yaml).find()
			? kp.matcher(yaml).replaceFirst(Matcher.quoteReplacement(line))
			: yaml + "\n" + line;
		return "---\n" + updated + "\n---" + m.group(2) + text.substring(m.end());
	}

	static Map<String, Object> frontmatter(String text) {
		Matcher m = FRONTMATTER.matcher(text);
		if (!m.find()) {
			return Map.of();
		}
		try {
			Object y = new Yaml(new SafeConstructor(new LoaderOptions())).load(m.group(1));
			if (y instanceof Map<?, ?> map) {
				@SuppressWarnings("unchecked")
				Map<String, Object> r = (Map<String, Object>)map;
				return r;
			}
		} catch (RuntimeException ignored) {
			// 잘못된 YAML 은 저장을 막지 않는다 — 속성 없이 색인
		}
		return Map.of();
	}

	private static int frontmatterEnd(String text) {
		Matcher m = FRONTMATTER.matcher(text);
		return m.find() ? m.end() : 0;
	}

	private static int lineOf(String text, int offset) {
		int n = 0;
		for (int i = 0; i < offset; i++) {
			if (text.charAt(i) == '\n') {
				n++;
			}
		}
		return n;
	}

	/** origin: "[[A]]" · [[A]](YAML 이 중첩 리스트로 읽음) · A 모두 A */
	private static String linkTitle(Object v) {
		while (v instanceof List<?> l && !l.isEmpty()) {
			v = l.get(0);
		}
		String s = str(v);
		if (s == null) {
			return null;
		}
		Matcher m = WIKILINK.matcher(s);
		String t = (m.find() ? m.group(1) : s).trim();
		return t.isEmpty() ? null : t;
	}

	private static String str(Object v) {
		if (v == null || v instanceof Map || v instanceof Collection) {
			return null;
		}
		String s = String.valueOf(v).trim();
		return s.isEmpty() ? null : s;
	}

	private static void addTag(Set<String> tags, String raw) {
		String t = raw.trim().replaceFirst("^#", "");
		if (!t.isEmpty() && !t.chars().allMatch(Character::isDigit) && t.length() <= 200) {
			tags.add(t);
		}
	}

	private static LocalDate parseDate(String s) {
		try {
			return LocalDate.parse(s);
		} catch (RuntimeException e) {
			return null;
		}
	}
}
