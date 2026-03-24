package pt.up.fe.comp.jmm.analysis;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.StageResult;
import pt.up.fe.specs.util.SpecsCollections;

import java.util.List;
import java.util.Map;

/**
 * A semantic analysis returns the analysed tree and the generated symbol table.
 */
public class JmmSemanticsResult implements StageResult {

    private final JmmNode rootNode;
    private final SymbolTable symbolTable;
    private final List<Report> reports;
    private final Map<String, String> config;

    public JmmSemanticsResult(JmmNode rootNode, SymbolTable symbolTable, List<Report> reports,
                              Map<String, String> config) {
        this.rootNode = rootNode;
        this.symbolTable = symbolTable;
        this.reports = reports;
        this.config = config;
    }

    public JmmSemanticsResult(JmmParserResult parserResult, SymbolTable symbolTable, List<Report> reports) {
        this(parserResult.rootNode(), symbolTable, SpecsCollections.concat(parserResult.reports(), reports),
                parserResult.config());
    }

    public JmmSemanticsResult(JmmSemanticsResult semanticsResult, List<Report> reports) {
        this(semanticsResult.getRootNode(), semanticsResult.getSymbolTable(), SpecsCollections.concat(semanticsResult.reports(), reports),
                semanticsResult.config());
    }

    public JmmNode getRootNode() {
        return this.rootNode;
    }

    public SymbolTable getSymbolTable() {
        return this.symbolTable;
    }

    public List<Report> reports() {
        return this.reports;
    }

    public Map<String, String> config() {
        return config;
    }
}
