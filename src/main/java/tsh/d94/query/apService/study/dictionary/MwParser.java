package tsh.d94.app.server.apService.study.dictionary;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import tsh.d94.app.server.apService.study.dto.StudyDtos.DictionaryEntry;
import tsh.d94.app.server.apService.study.dto.StudyDtos.DictionaryResponse;
import tsh.d94.app.server.apService.study.dto.StudyDtos.DictionarySense;

public final class MwParser {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Pattern TAGS = Pattern.compile("\\{bc\\}|\\{(?:sx|a_link|d_link|i_link|et_link|mat|dxt)\\|([^|}]*)(?:\\|[^}]*)?\\}|\\{[^}]*}");
    private MwParser() { }

    public static boolean isInvalidKey(String body) { return body != null && body.startsWith("Invalid API key"); }
    static String clean(String s) { return TAGS.matcher(s == null ? "" : s).replaceAll("$1").replaceAll("\\s+", " ").replaceAll("^[: ]+", "").trim(); }

    public static DictionaryResponse parse(String word, String body) {
        try {
            JsonNode root = JSON.readTree(body);
            List<DictionaryEntry> entries = new ArrayList<>();
            List<String> suggestions = new ArrayList<>();
            if (!root.isArray()) throw new IllegalArgumentException("bad response");
            for (JsonNode item : root) {
                if (item.isTextual()) { if (suggestions.size() < 5) suggestions.add(item.asText()); continue; }
                if (!item.isObject() || entries.size() >= 3) continue;
                String headword = item.path("hwi").path("hw").asText(word).replace("*", "");
                String pos = item.path("fl").asText("");
                List<DictionarySense> senses = new ArrayList<>();
                for (JsonNode def : item.path("def")) walkSenses(def.path("sseq"), senses);
                if (senses.isEmpty()) for (JsonNode d : item.path("shortdef")) {
                    if (senses.size() == 3) break;
                    senses.add(new DictionarySense(clean(d.asText()), null));
                }
                if (!senses.isEmpty()) entries.add(new DictionaryEntry(headword, pos, senses));
            }
            return new DictionaryResponse(word, entries.isEmpty() ? "NOT_FOUND" : "FOUND", entries, suggestions);
        } catch (Exception e) { throw new IllegalArgumentException("Invalid Merriam-Webster response", e); }
    }
    private static void walkSenses(JsonNode node, List<DictionarySense> out) {
        if (out.size() >= 3 || node == null) return;
        if (node.isArray()) {
            if (node.size() > 1 && "sense".equals(node.path(0).asText())) {
                JsonNode dt = node.path(1).path("dt"); String meaning = null, example = null;
                for (JsonNode d : dt) {
                    if ("text".equals(d.path(0).asText()) && meaning == null) meaning = clean(d.path(1).asText());
                    if ("vis".equals(d.path(0).asText()) && example == null) example = clean(d.path(1).path(0).path("t").asText());
                }
                if (meaning != null && !meaning.isBlank()) out.add(new DictionarySense(meaning, example));
            }
            for (JsonNode child : node) walkSenses(child, out);
        }
    }
}
