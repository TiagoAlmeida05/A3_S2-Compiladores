package pt.up.fe.comp.jmm.ollir;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.specs.comp.ollir.ClassUnit;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp.jmm.report.StageResult;
import pt.up.fe.specs.util.SpecsCollections;

/**
 * An OLLIR result returns the parsed OLLIR code and the corresponding symbol table.
 */
public class OllirResult implements StageResult {

    private final String ollirCode;
    private final ClassUnit ollirClass;
    private final List<Report> reports;
    private final Map<String, String> config;

    private OllirResult(String ollirCode, List<Report> reports,
                        Map<String, String> config) {

        this.ollirCode = ollirCode;
        this.reports = new ArrayList<>(reports);
        this.config = config;
        var parseResult = OllirUtils.parse(ollirCode);
        if (parseResult.hasErrors()) {
            parseResult.errors().stream().map(e ->
                    Report.newError(Stage.LLIR_OPTIMIZATION, e.line(), e.column(), e.message(), null)
            ).forEach(this.reports::add);
//            this.reports.add(Report.newError(Stage.OPTIMIZATION, 0, 0, e.getMessage(), e));
        }
        this.ollirClass = parseResult.classUnit();
    }

    public OllirResult(String ollirCode, Map<String, String> config) {
        this(ollirCode, Collections.emptyList(), config);
    }

    /**
     * Creates a new instance from the analysis stage results and a String containing OLLIR code.
     *
     * @param semanticsResult
     * @param ollirCode
     * @param reports
     */
    public OllirResult(JmmSemanticsResult semanticsResult, String ollirCode, List<Report> reports) {
        this(ollirCode, SpecsCollections.concat(semanticsResult.reports(), reports), semanticsResult.config());
    }

    public String getOllirCode() {
        return ollirCode;
    }

    public ClassUnit getOllirClass() {
        return this.ollirClass;
    }

    @Override
    public List<Report> reports() {
        return this.reports;
    }

    public Map<String, String> config() {
        return config;
    }
}
