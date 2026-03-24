package pt.up.fe.comp.jmm.lexer;

import java.util.Map;

public interface JmmLexer {

    JmmLexerResult lex(String jmmCode, Map<String, String> config);
}
