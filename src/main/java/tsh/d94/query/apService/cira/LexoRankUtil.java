package tsh.d94.app.server.apService.cira;

import java.util.ArrayList;
import java.util.List;

/**
 * 보드 내 이슈 순서를 lexicographic 비교 가능한 문자열로 관리한다.
 * 15자리 zero-padded 정수 문자열을 사용하므로 String.compareTo() 로 정렬하면 된다.
 */
public final class LexoRankUtil {

	private static final long INITIAL = 100_000_000_000_000L;
	private static final long STEP = 100_000_000L;
	private static final long MIN_GAP = 2L;

	private LexoRankUtil() {
	}

	public static String initial() {
		return format(INITIAL);
	}

	public static String after(String rank) {
		return format(parse(rank) + STEP);
	}

	public static String before(String rank) {
		return format(Math.max(1L, parse(rank) - STEP));
	}

	/**
	 * left < 반환값 < right 를 만족하는 rank 를 반환한다.
	 * 간격이 너무 작아 중간 값을 만들 수 없으면 null 을 반환하므로, 호출자가 rebalance 해야 한다.
	 */
	public static String between(String left, String right) {
		long l = parse(left);
		long r = parse(right);
		if (r - l < MIN_GAP)
			return null;
		return format((l + r) / 2);
	}

	/**
	 * count 개의 rank 를 균등 간격으로 재생성한다.
	 */
	public static List<String> rebalance(int count) {
		List<String> ranks = new ArrayList<>(count);
		for (int i = 0; i < count; i++) {
			ranks.add(format(INITIAL + (long)i * STEP));
		}
		return ranks;
	}

	private static String format(long v) {
		return String.format("%015d", v);
	}

	private static long parse(String rank) {
		if (rank == null || rank.isBlank())
			return INITIAL;
		try {
			String s = rank.trim();
			// 레거시 "0|i00000:" 포맷 처리
			if (!s.chars().allMatch(Character::isDigit))
				return INITIAL;
			return Long.parseLong(s);
		} catch (NumberFormatException e) {
			return INITIAL;
		}
	}
}
