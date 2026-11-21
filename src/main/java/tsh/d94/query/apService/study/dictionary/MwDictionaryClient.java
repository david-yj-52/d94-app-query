package tsh.d94.app.server.apService.study.dictionary;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import lombok.extern.slf4j.Slf4j;
import tsh.d94.app.server.apService.study.dto.StudyDtos.DictionaryResponse;
import tsh.d94.app.server.apService.study.exception.StudyErrorCode;
import tsh.d94.app.server.apService.study.exception.StudyException;

@Slf4j
@Component
public class MwDictionaryClient {
    private final RestClient rest = RestClient.builder().baseUrl("https://www.dictionaryapi.com/api/v3/references")
        .requestFactory(factory()).build();
    private static JdkClientHttpRequestFactory factory() {
        var f = new JdkClientHttpRequestFactory(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        f.setReadTimeout(Duration.ofSeconds(5)); return f;
    }
    public DictionaryResponse lookup(String reference, String apiKey, String word) {
        try {
            String body = rest.get().uri("/{reference}/json/{word}?key={key}", reference, word, apiKey)
                .retrieve().body(String.class);
            if (MwParser.isInvalidKey(body)) throw new StudyException(StudyErrorCode.DICTIONARY_KEY_INVALID);
            return MwParser.parse(word, body);
        } catch (StudyException e) { throw e; }
        catch (Exception e) {
            log.warn("Merriam-Webster lookup failed ({})", e.getClass().getSimpleName());
            throw new StudyException(StudyErrorCode.DICTIONARY_UNAVAILABLE);
        }
    }
}
