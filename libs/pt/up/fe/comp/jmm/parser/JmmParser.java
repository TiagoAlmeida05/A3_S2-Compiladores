package pt.up.fe.comp.jmm.parser;

import pt.up.fe.comp.jmm.lexer.JmmLexerResult;

import java.util.Map;

/**
 * Parses J-- code.
 *
 * @author COMP2021
 *
 */
public interface JmmParser {


    /**
     * Parses the given Java-- code, starting from the given rule, and using the given configuration.
     *
     * @param lexerResult  The result of the lexical analysis, containing the tokens to be parsed
     * @param startingRule
     * @param config
     * @return a {@link JmmParserResult} that contains the root node of the AST, as a {@link pt.up.fe.comp.jmm.ast.JmmNode}, a list of reports and the configuration
     */
    JmmParserResult parse(JmmLexerResult lexerResult, String startingRule, Map<String, String> config);

    /**
     * The name of the default rule that should be used when parse() is called without a rule name.
     * <p>
     * This should correspond to the top rule of your grammar.
     *
     * @return the name of the default grammar rule to be used when parsing
     */
    String getDefaultRule();

    /**
     * Parses the given Java-- code using the default rule.
     *
     * @param lexerResult The result of the lexical analysis, containing the tokens to be parsed
     * @param config
     * @return
     */
    default JmmParserResult parse(JmmLexerResult lexerResult, Map<String, String> config) {
        return parse(lexerResult, getDefaultRule(), config);
    }

    /**
     * Inherits the lexer configuration when parsing.
     *
     * @param lexerResult
     * @return
     */
    default JmmParserResult parse(JmmLexerResult lexerResult) {
        return parse(lexerResult, getDefaultRule(), lexerResult.config());
    }


}