package tsh.d94.app.server.apService.study.dictionary;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

import com.fasterxml.jackson.databind.ObjectMapper;

import tsh.d94.app.server.apService.cira.GitRepositoryService;
import tsh.d94.app.server.apService.study.dto.StudyDtos.*;
import tsh.d94.app.server.apService.study.exception.StudyErrorCode;
import tsh.d94.app.server.apService.study.exception.StudyException;
import tsh.d94.app.server.data.orm.study.readingAids.StudyReadingAidsAccess;
import tsh.d94.app.server.support.TestContexts;

@ExtendWith(MockitoExtension.class)
class StudyDictionaryServiceTest {
	static final String KEY = "01234567-89ab-cdef-0123-456789abcdef";
	static final DictionaryResponse FOUND = new DictionaryResponse("implemented", "FOUND",
		List.of(new DictionaryEntry("implement", "verb", List.of(new DictionarySense("to carry out", null)))), List.of());

	@Mock StudyReadingAidsAccess data;
	@Mock MwDictionaryClient client;
	@Mock GitRepositoryService crypto;
	ObjectMapper mapper = new ObjectMapper();
	StudyDictionaryService service;

	@BeforeEach void setUp() {
		TestContexts.login("u@t.com");
		service = new StudyDictionaryService(data, client, crypto, mapper);
	}
	@AfterEach void tearDown() { TestContexts.clear(); }

	private void hasKey(String ref) {
		when(data.key("u@t.com")).thenReturn(List.<String[]>of(new String[] {"enc", ref}));
	}
	private String json(DictionaryResponse r) throws Exception { return mapper.writeValueAsString(r); }

	@Test void normalizeRules() {
		assertThat(StudyDictionaryService.normalize("“Auditor's,")).isEqualTo("auditor");
		assertThat(StudyDictionaryService.normalize("Well-known")).isEqualTo("well-known");
		assertThatThrownBy(() -> StudyDictionaryService.normalize("123")).isInstanceOf(StudyException.class);
		assertThatThrownBy(() -> StudyDictionaryService.normalize("a".repeat(51))).isInstanceOf(StudyException.class);
	}

	@Test void lookupWithoutKeyRequiresKey() {
		when(data.key("u@t.com")).thenReturn(List.of());
		assertThatThrownBy(() -> service.lookup("implemented")).isInstanceOfSatisfying(StudyException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(StudyErrorCode.DICTIONARY_KEY_REQUIRED));
		verifyNoInteractions(client);
	}

	@Test void lookupCacheHitSkipsApi() throws Exception {
		hasKey("collegiate");
		when(data.dictionaryCache("u@t.com", "implemented", "collegiate")).thenReturn(List.of(json(FOUND)));
		assertThat(service.lookup("Implemented")).isEqualTo(FOUND);
		verify(client, never()).lookup(any(), any(), any());
	}

	@Test void lookupCacheMissCallsApiAndSaves() {
		hasKey("collegiate");
		when(data.dictionaryCache(any(), any(), any())).thenReturn(List.of());
		when(crypto.decryptToken("enc")).thenReturn(KEY);
		when(client.lookup("collegiate", KEY, "implemented")).thenReturn(FOUND);
		assertThat(service.lookup("implemented")).isEqualTo(FOUND);
		verify(client, times(1)).lookup("collegiate", KEY, "implemented");
		verify(data).saveDictionaryCache(eq("u@t.com"), eq("implemented"), eq("collegiate"), eq("FOUND"), anyString());
	}

	@Test void lookupNotFoundIsSaved() {
		hasKey("collegiate");
		when(data.dictionaryCache(any(), any(), any())).thenReturn(List.of());
		when(crypto.decryptToken("enc")).thenReturn(KEY);
		var nf = new DictionaryResponse("asdf", "NOT_FOUND", List.of(), List.of("sower"));
		when(client.lookup("collegiate", KEY, "asdf")).thenReturn(nf);
		assertThat(service.lookup("asdf").status()).isEqualTo("NOT_FOUND");
		verify(data).saveDictionaryCache(eq("u@t.com"), eq("asdf"), eq("collegiate"), eq("NOT_FOUND"), anyString());
	}

	@Test void lookupApiErrorNotCached() {
		hasKey("collegiate");
		when(data.dictionaryCache(any(), any(), any())).thenReturn(List.of());
		when(crypto.decryptToken("enc")).thenReturn(KEY);
		when(client.lookup(any(), any(), any())).thenThrow(new StudyException(StudyErrorCode.DICTIONARY_UNAVAILABLE));
		assertThatThrownBy(() -> service.lookup("implemented")).isInstanceOf(StudyException.class);
		verify(data, never()).saveDictionaryCache(any(), any(), any(), any(), any());
	}

	@Test void lookupUsesCurrentReference() {
		hasKey("learners");
		when(data.dictionaryCache("u@t.com", "implemented", "learners")).thenReturn(List.of());
		when(crypto.decryptToken("enc")).thenReturn(KEY);
		when(client.lookup("learners", KEY, "implemented")).thenReturn(FOUND);
		service.lookup("implemented");
		verify(data).dictionaryCache("u@t.com", "implemented", "learners");
	}

	@Test void lookupConcurrentInsertReturnsStored() throws Exception {
		hasKey("collegiate");
		var stored = new DictionaryResponse("implemented", "FOUND", List.of(), List.of("stored"));
		when(data.dictionaryCache("u@t.com", "implemented", "collegiate")).thenReturn(List.of()).thenReturn(List.of(json(stored)));
		when(crypto.decryptToken("enc")).thenReturn(KEY);
		when(client.lookup(any(), any(), any())).thenReturn(FOUND);
		doThrow(new DuplicateKeyException("dup")).when(data).saveDictionaryCache(any(), any(), any(), any(), any());
		assertThat(service.lookup("implemented")).isEqualTo(stored);
	}

	@Test void registerKeyFallsBackToCollegiate() {
		when(client.lookup("learners", KEY, "test")).thenThrow(new StudyException(StudyErrorCode.DICTIONARY_KEY_INVALID));
		when(client.lookup("collegiate", KEY, "test")).thenReturn(FOUND);
		when(crypto.encryptToken(KEY)).thenReturn("enc");
		assertThat(service.registerKey(KEY)).isEqualTo(new DictionaryKeyStatus(true, "collegiate"));
		verify(data).upsertKey("u@t.com", "enc", "collegiate");
	}

	@Test void registerKeyRejectsWhenBothInvalid() {
		when(client.lookup(any(), eq(KEY), any())).thenThrow(new StudyException(StudyErrorCode.DICTIONARY_KEY_INVALID));
		assertThatThrownBy(() -> service.registerKey(KEY)).isInstanceOfSatisfying(StudyException.class,
			e -> assertThat(e.getErrorCode()).isEqualTo(StudyErrorCode.DICTIONARY_KEY_REJECTED));
		verify(data, never()).upsertKey(any(), any(), any());
	}

	@Test void registerKeyRejectsBadFormatWithoutApiCall() {
		assertThatThrownBy(() -> service.registerKey("nope")).isInstanceOf(StudyException.class);
		verifyNoInteractions(client);
	}

	@Test void keyStatusNeverExposesKey() {
		when(data.keyReferences("u@t.com")).thenReturn(List.of("learners"));
		var s = service.keyStatus();
		assertThat(s.registered()).isTrue();
		assertThat(s.reference()).isEqualTo("learners");
		assertThat(DictionaryKeyStatus.class.getRecordComponents()).extracting("name").containsExactly("registered", "reference");
	}

	@Test void deleteKeyRemovesKeyAndCache() {
		service.deleteKey();
		verify(data).deleteOwnerCache("u@t.com");
		verify(data).deleteOwnerKey("u@t.com");
	}
}
