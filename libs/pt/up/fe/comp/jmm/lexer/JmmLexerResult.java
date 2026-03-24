package pt.up.fe.comp.jmm.lexer;

import org.antlr.v4.runtime.Token;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.StageResult;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public record JmmLexerResult(List<Token> tokens, List<Report> reports,
                             Map<String, String> config) implements StageResult {

    public JmmLexerResult(List<Token> tokens, List<Report> reports, Map<String, String> config) {
        this.tokens = tokens;
        this.reports = new ArrayList<>(reports);
        this.config = new HashMap<>(config);
    }

    public static JmmLexerResult newError(Report errorReport) {
        return newError(errorReport, Map.of());
    }

    public static JmmLexerResult newError(Report errorReport, Map<String, String> config) {
        return new JmmLexerResult(null, List.of(errorReport), config);
    }
}
