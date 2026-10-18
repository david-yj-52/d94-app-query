package tsh.d94.app.server.apService.cira;

import static org.assertj.core.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LexoRankUtilTest {

	@Test
	@DisplayName("initial() - 기본 초기 rank 반환")
	void initial_returnsFixedValue() {
		String rank = LexoRankUtil.initial();
		assertThat(rank).isEqualTo("100000000000000");
	}

	@Test
	@DisplayName("after() - 현재 rank 보다 큰 rank 반환")
	void after_returnsLargerRank() {
		String rank = LexoRankUtil.initial();
		String next = LexoRankUtil.after(rank);
		assertThat(next).isGreaterThan(rank);
	}

	@Test
	@DisplayName("before() - 현재 rank 보다 작은 rank 반환")
	void before_returnsSmallerRank() {
		String rank = LexoRankUtil.after(LexoRankUtil.initial());
		String prev = LexoRankUtil.before(rank);
		assertThat(prev).isLessThan(rank);
	}

	@Test
	@DisplayName("between() - left < result < right 만족")
	void between_satisfiesOrder() {
		String left = LexoRankUtil.initial();
		String right = LexoRankUtil.after(left);
		String mid = LexoRankUtil.between(left, right);
		assertThat(mid).isNotNull();
		assertThat(mid).isGreaterThan(left);
		assertThat(mid).isLessThan(right);
	}

	@Test
	@DisplayName("between() - 간격이 너무 작으면 null 반환")
	void between_tooSmallGap_returnsNull() {
		String a = "100000000000001";
		String b = "100000000000002";
		String result = LexoRankUtil.between(a, b);
		assertThat(result).isNull();
	}

	@Test
	@DisplayName("rebalance() - count 개 rank, 오름차순 정렬")
	void rebalance_returnsOrderedList() {
		List<String> ranks = LexoRankUtil.rebalance(5);
		assertThat(ranks).hasSize(5);
		for (int i = 0; i < ranks.size() - 1; i++) {
			assertThat(ranks.get(i)).isLessThan(ranks.get(i + 1));
		}
	}

	@Test
	@DisplayName("after(null) - null 입력 시 initial 기반으로 동작")
	void after_nullInput_usesInitial() {
		String result = LexoRankUtil.after(null);
		assertThat(result).isGreaterThan(LexoRankUtil.initial());
	}

	@Test
	@DisplayName("연속 after() - 순서가 단조 증가")
	void consecutiveAfter_monotonicallyIncreasing() {
		String r1 = LexoRankUtil.initial();
		String r2 = LexoRankUtil.after(r1);
		String r3 = LexoRankUtil.after(r2);
		String r4 = LexoRankUtil.after(r3);
		assertThat(r1).isLessThan(r2);
		assertThat(r2).isLessThan(r3);
		assertThat(r3).isLessThan(r4);
	}
}
