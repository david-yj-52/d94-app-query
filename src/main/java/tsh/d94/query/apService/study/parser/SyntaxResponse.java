package tsh.d94.app.server.apService.study.parser;

import java.util.List;
public record SyntaxResponse(String analyzerVersion, List<Span> spans) {
    public record Span(String blockKey, int start, int end, String role) { }
}
