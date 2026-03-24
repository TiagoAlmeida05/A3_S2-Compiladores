package pt.up.fe.comp.jmm.analysis;

import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;

import java.util.List;

/**
 * This stage deals with analysis performed at the AST level, essentially semantic analysis and symbol table generation.
 */
public interface JmmAnalysis {

    /**
     * Builds the symbol table.
     *
     * @param parserResult
     * @return
     */
    JmmSemanticsResult buildSymbolTable(JmmParserResult parserResult);

    /**
     * Applies the semantic analysis after the symbol table is built.
     *
     * @param semanticResult
     * @return
     */
    JmmSemanticsResult semanticAnalysis(JmmSemanticsResult semanticResult);

    /**
     * Helper method which calls the two steps of semantic analysis, building the symbol table and applying the analysis passes.
     *
     * @param parserResult
     * @return
     */
    default JmmSemanticsResult semanticAnalysis(JmmParserResult parserResult) {

        // Build symbol table
        var symbolTableResult = buildSymbolTable(parserResult);

        // If there are errors while building the symbol table, it is not possible to proceed
        if (symbolTableResult.hasErrors()) {
            return symbolTableResult;
        }

        // Apply analysis passes
        return semanticAnalysis(symbolTableResult);
    }
}