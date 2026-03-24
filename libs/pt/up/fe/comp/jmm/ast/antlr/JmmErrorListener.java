package pt.up.fe.comp.jmm.ast.antlr;

import org.antlr.v4.runtime.BaseErrorListener;
import org.antlr.v4.runtime.RecognitionException;
import org.antlr.v4.runtime.Recognizer;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;

import java.util.ArrayList;
import java.util.List;

public class JmmErrorListener extends BaseErrorListener {

    private final List<Report> reports;
    private final Stage stage;

    public JmmErrorListener(Stage stage) {
        this.reports = new ArrayList<>();
        this.stage = stage;
    }

    @Override
    public void syntaxError(Recognizer<?, ?> recognizer, Object offendingSymbol, int line, int charPositionInLine, String msg, RecognitionException e) {

        reports.add(Report.newError(this.stage, line, charPositionInLine, msg, e));
    }

    public boolean hasErrors() {
        return reports.stream().anyMatch(report -> report.getType() == ReportType.ERROR);
    }

    public List<Report> getErrors() {
        return reports.stream()
                .filter(report -> report.getType() == ReportType.ERROR)
                .toList();
    }

    public List<Report> getReports() {
        return reports;
    }
}
