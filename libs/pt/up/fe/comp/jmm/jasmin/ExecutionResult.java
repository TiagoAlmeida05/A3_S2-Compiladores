package pt.up.fe.comp.jmm.jasmin;

import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp.jmm.report.StageResult;
import pt.up.fe.specs.util.system.ProcessOutputAsString;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ExecutionResult implements StageResult {

    private final Map<String, String> config;
    private final File workingDir;
    private final List<String> classpath;
    private final String className;
    private final List<String> args;
    private final String input;
    private final List<Report> reports;
    private final ProcessOutputAsString output;


    public ExecutionResult(String className, List<String> args, String input, File workingDir, List<String> classpath,
                           ProcessOutputAsString output, Map<String, String> config) {
        this.config = config;
        this.workingDir = workingDir;
        this.output = output;
        this.classpath = classpath;
        this.className = className;
        this.args = args;
        this.input = input;
        this.reports = new ArrayList<Report>();
        buildReports();
    }

    private void buildReports() {
        if (output.isError()) {
            reports.add(Report.newError(Stage.EXECUTION, -1, -1, "Program execution ended with exit value " + output.getReturnValue(), null));
        }
        output.getOutputException().ifPresent(e ->
                reports.add(Report.newError(Stage.EXECUTION, -1, -1, "Program execution ended with exception: " + e.getMessage(), e))
        );
        if (!output.getStdErr().isBlank()) {
            reports.add(Report.newWarn(Stage.EXECUTION, -1, -1, "Program execution produced output in stderr: " + output.getStdErr(), null));
        }
    }

    @Override
    public List<Report> reports() {
        return List.of();
    }

    @Override
    public Map<String, String> config() {
        return Map.of();
    }

    public Map<String, String> getConfig() {
        return config;
    }

    public File getWorkingDir() {
        return workingDir;
    }

    public int getReturnCode() {
        return output.getReturnValue();
    }

    public List<String> getClasspath() {
        return classpath;
    }

    public String getFullOutput() {
        return output.getOutput();
    }

    public String getStdout() {
        return output.getStdOut();
    }

    public String getStderr() {
        return output.getStdErr();
    }

    public String getClassName() {
        return className;
    }

    public List<String> getArgs() {
        return args;
    }

    public String getInput() {
        return input;
    }
}
