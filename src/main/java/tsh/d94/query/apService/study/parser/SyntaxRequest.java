package tsh.d94.app.server.apService.study.parser;

import java.util.List;
public record SyntaxRequest(List<Block> blocks) {
    public record Block(String key, String text) { }
}
