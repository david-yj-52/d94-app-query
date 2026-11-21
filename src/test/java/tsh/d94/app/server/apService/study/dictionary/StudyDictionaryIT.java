package tsh.d94.app.server.apService.study.dictionary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.jdbc.core.JdbcTemplate;

import tsh.d94.app.server.apService.study.dto.StudyDtos.*;
import tsh.d94.app.server.apService.study.exception.StudyErrorCode;
import tsh.d94.app.server.apService.study.exception.StudyException;
import tsh.d94.app.server.support.CiraFixtures;
import tsh.d94.app.server.support.IntegrationTest;
import tsh.d94.app.server.support.TestContexts;

@IntegrationTest
class StudyDictionaryIT {
	static final String KEY = "01234567-89ab-cdef-0123-456789abcdef";
	@Autowired StudyDictionaryService service;
	@Autowired JdbcTemplate jdbc;
	@MockBean MwDictionaryClient client;

	@AfterEach void tearDown() { TestContexts.clear(); }

	@Test void cachesPerUserAndRequiresOwnKey() {
		String u = CiraFixtures.uniqueEmail("dict-u"), v = CiraFixtures.uniqueEmail("dict-v");
		var found = new DictionaryResponse("implemented", "FOUND",
			List.of(new DictionaryEntry("implement", "verb", List.of(new DictionarySense("to carry out", "ex")))), List.of());
		when(client.lookup(anyString(), anyString(), anyString())).thenReturn(found);

		TestContexts.login(u);
		assertThat(service.registerKey(KEY).reference()).isEqualTo("learners");
		clearInvocations(client);
		var first = service.lookup("Implemented");
		var second = service.lookup("implemented");
		assertThat(second).isEqualTo(first).isEqualTo(found);
		verify(client, times(1)).lookup(eq("learners"), eq(KEY), eq("implemented"));
		assertThat(jdbc.queryForObject("select count(*) from SN_STUDY_DICTIONARY where OWNER_ID=? and WORD='implemented' and REFERENCE='learners'",
			Integer.class, u)).isEqualTo(1);

		TestContexts.login(v);
		assertThatThrownBy(() -> service.lookup("implemented")).isInstanceOfSatisfying(StudyException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(StudyErrorCode.DICTIONARY_KEY_REQUIRED));

		TestContexts.login(u);
		service.deleteKey();
		assertThat(jdbc.queryForObject("select count(*) from SN_STUDY_DICTIONARY where OWNER_ID=?", Integer.class, u)).isZero();
	}
}
