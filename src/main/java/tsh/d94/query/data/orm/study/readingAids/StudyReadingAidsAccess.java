package tsh.d94.app.server.data.orm.study.readingAids;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;

/** Reading-aids persistence boundary; SQL remains out of apService classes. */
@Service @RequiredArgsConstructor
public class StudyReadingAidsAccess {
    private final JdbcTemplate jdbc;
    public List<String> keyReferences(String owner) {
        return jdbc.query("select REFERENCE from SN_STUDY_DICTIONARY_KEY where OWNER_ID=?", (r,n)->r.getString(1), owner);
    }
    public void upsertKey(String owner, String encryptedKey, String reference) {
        jdbc.update("insert into SN_STUDY_DICTIONARY_KEY(OWNER_ID,API_KEY,REFERENCE) values(?,?,?) on conflict(OWNER_ID) do update set API_KEY=excluded.API_KEY,REFERENCE=excluded.REFERENCE,UPDATED_AT=current_timestamp", owner, encryptedKey, reference);
    }
    public void deleteOwnerCache(String owner) { jdbc.update("delete from SN_STUDY_DICTIONARY where OWNER_ID=?", owner); }
    public void deleteOwnerKey(String owner) { jdbc.update("delete from SN_STUDY_DICTIONARY_KEY where OWNER_ID=?", owner); }
    public List<String[]> key(String owner) {
        return jdbc.query("select API_KEY,REFERENCE from SN_STUDY_DICTIONARY_KEY where OWNER_ID=?", (r,n)->new String[]{r.getString(1),r.getString(2)}, owner);
    }
    public List<String> dictionaryCache(String owner, String word, String reference) {
        return jdbc.query("select PAYLOAD::text from SN_STUDY_DICTIONARY where OWNER_ID=? and WORD=? and REFERENCE=?", (r,n)->r.getString(1), owner, word, reference);
    }
    public void saveDictionaryCache(String owner, String word, String reference, String status, String payload) {
        jdbc.update("insert into SN_STUDY_DICTIONARY(OWNER_ID,WORD,REFERENCE,STATUS,PAYLOAD) values(?,?,?,?,?::jsonb)", owner, word, reference, status, payload);
    }
    public List<String[]> syntaxCache(String document, String section) {
        return jdbc.query("select ANALYZER_VERSION,PAYLOAD::text from SN_STUDY_SYNTAX where DOCUMENT_ID=? and SECTION_ID=? order by CREATED_AT desc limit 1", (r,n)->new String[]{r.getString(1),r.getString(2)}, document, section);
    }
    public void saveSyntaxCache(String document, String section, String version, String payload) {
        jdbc.update("insert into SN_STUDY_SYNTAX(DOCUMENT_ID,SECTION_ID,ANALYZER_VERSION,PAYLOAD) values(?,?,?,?::jsonb) on conflict(DOCUMENT_ID,SECTION_ID,ANALYZER_VERSION) do update set PAYLOAD=excluded.PAYLOAD,CREATED_AT=current_timestamp", document, section, version, payload);
    }
    public void deleteSyntaxByDocumentId(String document) { jdbc.update("delete from SN_STUDY_SYNTAX where DOCUMENT_ID=?", document); }
}
