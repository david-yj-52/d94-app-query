package tsh.d94.app.server.apService.study.dictionary;

import static org.assertj.core.api.Assertions.assertThat;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class MwParserTest {
    @Test void parsesMeaningAndHeadword() {
        var result = MwParser.parse("implemented", """
            [{"hwi":{"hw":"implement*"},"fl":"verb","def":[{"sseq":[[["sense",{"dt":[["text","{bc}to {it}carry out{/it} {sx|accomplish||}"]]}]]]}],"shortdef":["to execute"]}]
            """);
        assertThat(result.status()).isEqualTo("FOUND");
        assertThat(result.entries().get(0).headword()).isEqualTo("implement");
        assertThat(result.entries().get(0).senses().get(0).definition()).isEqualTo("to carry out accomplish");
    }

    @Test void suggestionsAndEmptyArrayAreNotFound() {
        var result = MwParser.parse("asdfqwer", "[\"sower\",\"answer\"]");
        assertThat(result.status()).isEqualTo("NOT_FOUND");
        assertThat(result.suggestions()).containsExactly("sower", "answer");
        assertThat(MwParser.parse("x", "[]").status()).isEqualTo("NOT_FOUND");
    }

    @Test void detectsInvalidKeyAndCleansFormatting() {
        assertThat(MwParser.isInvalidKey("Invalid API key. Not subscribed for this reference.")).isTrue();
        assertThat(MwParser.clean("{bc}to {it}carry out{/it} {sx|accomplish||}")).isEqualTo("to carry out accomplish");
    }

    private static String fixture(String name) throws Exception { return Files.readString(Path.of("src/test/resources/mw", name)); }

    @Test void parsesRealInflectedFixture() throws Exception {
        var r = MwParser.parse("implemented", fixture("collegiate-implemented.json"));
        assertThat(r.status()).isEqualTo("FOUND");
        assertThat(r.entries().get(0).headword()).isEqualTo("implement");
        assertThat(r.entries().get(0).senses()).isNotEmpty().hasSizeLessThanOrEqualTo(3);
        assertThat(r.entries().get(0).senses().get(0).definition()).doesNotContain("{").doesNotStartWith(":");
    }

    @Test void capsEntriesAndSenses() throws Exception {
        var r = MwParser.parse("assurance", fixture("collegiate-assurance.json"));
        assertThat(r.entries()).hasSizeLessThanOrEqualTo(3);
        r.entries().forEach(e -> assertThat(e.senses()).hasSizeLessThanOrEqualTo(3));
    }

    @Test void realSuggestionsMeanNotFound() throws Exception {
        var r = MwParser.parse("asdfqwer", fixture("collegiate-asdfqwer.json"));
        assertThat(r.status()).isEqualTo("NOT_FOUND");
        assertThat(r.entries()).isEmpty();
        assertThat(r.suggestions()).hasSizeLessThanOrEqualTo(5).contains("sower");
    }
}
