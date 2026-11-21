package tsh.d94.app.server.apService.study.dictionary;

import java.util.List;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import tsh.d94.app.server.apService.cira.GitRepositoryService;
import tsh.d94.app.server.apService.study.Study;
import tsh.d94.app.server.apService.study.dto.StudyDtos.*;
import tsh.d94.app.server.apService.study.exception.*;
import tsh.d94.app.server.data.orm.study.readingAids.StudyReadingAidsAccess;

@Service @RequiredArgsConstructor
public class StudyDictionaryService {
    private static final Pattern KEY = Pattern.compile("^[0-9a-fA-F-]{36}$");
    private static final Pattern WORD = Pattern.compile("^[a-z][a-z'-]{0,49}$");
    private final StudyReadingAidsAccess data;
    private final MwDictionaryClient client;
    private final GitRepositoryService crypto;
    private final ObjectMapper mapper;

    public DictionaryKeyStatus keyStatus() {
        var rows = data.keyReferences(Study.owner());
        return rows.isEmpty() ? new DictionaryKeyStatus(false, null) : new DictionaryKeyStatus(true, rows.get(0));
    }
    @Transactional
    public DictionaryKeyStatus registerKey(String apiKey) {
        if (apiKey == null || !KEY.matcher(apiKey).matches()) throw new StudyException(StudyErrorCode.DICTIONARY_KEY_REJECTED);
        String reference = null;
        for (String ref : List.of("learners", "collegiate")) {
            try { client.lookup(ref, apiKey, "test"); reference = ref; break; }
            catch (StudyException e) { if (e.getErrorCode() != StudyErrorCode.DICTIONARY_KEY_INVALID) throw e; }
        }
        if (reference == null) throw new StudyException(StudyErrorCode.DICTIONARY_KEY_REJECTED);
        data.upsertKey(Study.owner(), crypto.encryptToken(apiKey), reference);
        return new DictionaryKeyStatus(true, reference);
    }
    @Transactional
    public void deleteKey() {
        data.deleteOwnerCache(Study.owner());
        data.deleteOwnerKey(Study.owner());
    }
    public DictionaryResponse lookup(String raw) {
        String word = normalize(raw);
        var keys = data.key(Study.owner());
        if (keys.isEmpty()) throw new StudyException(StudyErrorCode.DICTIONARY_KEY_REQUIRED);
        String owner = Study.owner(), ref = keys.get(0)[1];
        var cached = data.dictionaryCache(owner, word, ref);
        if (!cached.isEmpty()) return read(cached.get(0));
        DictionaryResponse response = client.lookup(ref, crypto.decryptToken(keys.get(0)[0]), word);
        String payload = write(response);
        try { data.saveDictionaryCache(owner, word, ref, response.status(), payload); }
        catch (org.springframework.dao.DuplicateKeyException e) {
            cached = data.dictionaryCache(owner, word, ref);
            if (!cached.isEmpty()) return read(cached.get(0)); throw e;
        }
        return response;
    }
    static String normalize(String raw) {
        String value = raw == null ? "" : raw.toLowerCase().replaceAll("^[\\p{P}]+|[\\p{P}]+$", "").replaceAll("'s$", "");
        if (!WORD.matcher(value).matches()) throw new StudyException(StudyErrorCode.INVALID_REQUEST);
        return value;
    }
    private String write(Object x) { try { return mapper.writeValueAsString(x); } catch (Exception e) { throw new IllegalStateException(e); } }
    private DictionaryResponse read(String x) { try { return mapper.readValue(x, DictionaryResponse.class); } catch (Exception e) { throw new IllegalStateException(e); } }
}
