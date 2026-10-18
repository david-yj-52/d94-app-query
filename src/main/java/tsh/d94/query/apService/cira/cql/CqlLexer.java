package tsh.d94.app.server.apService.cira.cql;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * CQL 렉서(토크나이저)
 * 입력 문자열을 CqlToken 목록으로 변환한다.
 */
public class CqlLexer {

	private static final Set<String> KEYWORDS = Set.of(
		"AND", "OR", "NOT", "IN", "ORDER", "BY", "ASC", "DESC"
	);

	private static final Set<String> FUNCTIONS = Set.of(
		"currentUser", "openSprints"
	);

	private final String input;
	private final List<CqlToken> tokens = new ArrayList<>();
	private int pos;

	public CqlLexer(String input) {
		this.input = input == null ? "" : input;
		this.pos = 0;
	}

	public List<CqlToken> tokenize() {
		while (pos < input.length()) {
			skipWhitespace();
			if (pos >= input.length())
				break;

			char c = input.charAt(pos);

			if (c == '(') {
				addToken(CqlToken.TokenType.LPAREN, "(");
				pos++;
				continue;
			}
			if (c == ')') {
				addToken(CqlToken.TokenType.RPAREN, ")");
				pos++;
				continue;
			}
			if (c == ',') {
				addToken(CqlToken.TokenType.COMMA, ",");
				pos++;
				continue;
			}

			if (c == '=') {
				addToken(CqlToken.TokenType.EQ, "=");
				pos++;
				continue;
			}
			if (c == '<') {
				if (peek(1) == '=') {
					addToken(CqlToken.TokenType.LTE, "<=");
					pos += 2;
				} else {
					addToken(CqlToken.TokenType.LT, "<");
					pos++;
				}
				continue;
			}
			if (c == '>') {
				if (peek(1) == '=') {
					addToken(CqlToken.TokenType.GTE, ">=");
					pos += 2;
				} else {
					addToken(CqlToken.TokenType.GT, ">");
					pos++;
				}
				continue;
			}
			if (c == '!') {
				char next = peek(1);
				if (next == '=') {
					addToken(CqlToken.TokenType.NEQ, "!=");
					pos += 2;
				} else if (next == '~') {
					addToken(CqlToken.TokenType.NLIKE, "!~");
					pos += 2;
				} else
					throw new CqlParseException("예상치 못한 문자 '!' at position " + pos, pos);
				continue;
			}
			if (c == '~') {
				addToken(CqlToken.TokenType.LIKE, "~");
				pos++;
				continue;
			}

			if (c == '"' || c == '\'') {
				readString(c);
				continue;
			}
			if (Character.isDigit(c) || (c == '-' && isDigitStart())) {
				readNumber();
				continue;
			}
			if (Character.isLetter(c) || c == '_') {
				readIdentifierOrKeyword();
				continue;
			}

			throw new CqlParseException("알 수 없는 문자 '" + c + "' at position " + pos, pos);
		}
		addToken(CqlToken.TokenType.EOF, "");
		return tokens;
	}

	// ────────────────────────────────────────
	// helpers
	// ────────────────────────────────────────

	private void skipWhitespace() {
		while (pos < input.length() && Character.isWhitespace(input.charAt(pos)))
			pos++;
	}

	private char peek(int offset) {
		int i = pos + offset;
		return i < input.length() ? input.charAt(i) : '\0';
	}

	private boolean isDigitStart() {
		return pos + 1 < input.length() && Character.isDigit(input.charAt(pos + 1));
	}

	private void addToken(CqlToken.TokenType type, String value) {
		tokens.add(new CqlToken(type, value, pos));
	}

	private void readString(char quote) {
		int start = pos;
		pos++; // skip opening quote
		StringBuilder sb = new StringBuilder();
		while (pos < input.length()) {
			char c = input.charAt(pos);
			if (c == quote) {
				pos++;
				break;
			}
			if (c == '\\' && pos + 1 < input.length()) {
				pos++;
				sb.append(input.charAt(pos));
			} else {
				sb.append(c);
			}
			pos++;
		}
		tokens.add(new CqlToken(CqlToken.TokenType.STRING, sb.toString(), start));
	}

	private void readNumber() {
		int start = pos;
		if (input.charAt(pos) == '-')
			pos++;
		while (pos < input.length() && Character.isDigit(input.charAt(pos)))
			pos++;
		if (pos < input.length() && input.charAt(pos) == '.') {
			pos++;
			while (pos < input.length() && Character.isDigit(input.charAt(pos)))
				pos++;
		}
		tokens.add(new CqlToken(CqlToken.TokenType.NUMBER, input.substring(start, pos), start));
	}

	private void readIdentifierOrKeyword() {
		int start = pos;
		while (pos < input.length() && (Character.isLetterOrDigit(input.charAt(pos)) || input.charAt(pos) == '_')) {
			pos++;
		}
		String word = input.substring(start, pos);
		String upper = word.toUpperCase();

		// 함수 호출 체크: identifier immediately followed by '('
		if (FUNCTIONS.contains(word) && pos < input.length() && input.charAt(pos) == '(') {
			// consume "()"
			pos++; // '('
			skipWhitespace();
			if (pos < input.length() && input.charAt(pos) == ')')
				pos++;
			tokens.add(new CqlToken(CqlToken.TokenType.FUNCTION, word, start));
			return;
		}

		if (KEYWORDS.contains(upper)) {
			CqlToken.TokenType type = switch (upper) {
				case "AND" -> CqlToken.TokenType.AND;
				case "OR" -> CqlToken.TokenType.OR;
				case "NOT" -> CqlToken.TokenType.NOT;
				case "IN" -> CqlToken.TokenType.IN;
				case "ORDER" -> CqlToken.TokenType.ORDER;
				case "BY" -> CqlToken.TokenType.BY;
				case "ASC" -> CqlToken.TokenType.ASC;
				case "DESC" -> CqlToken.TokenType.DESC;
				default -> CqlToken.TokenType.IDENTIFIER;
			};
			tokens.add(new CqlToken(type, word, start));
		} else {
			tokens.add(new CqlToken(CqlToken.TokenType.IDENTIFIER, word, start));
		}
	}
}
