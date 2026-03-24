package pt.up.fe.comp.test.env.utils;

import org.junit.runner.Description;
import pt.up.fe.comp.jmm.jasmin.ExecutionResult;
import pt.up.fe.comp.test.env.providers.ContentProvider;
import pt.up.fe.comp.test.env.providers.ObjectProvider;
import pt.up.fe.comp.test.env.providers.StreamProvider;
import pt.up.fe.comp.test.env.providers.TextProvider;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;

/**
 * Utility that stores intermediate compilation artifacts and debugging information produced during the execution of
 * the tests. Provides methods to reset state, store artifacts to temporary or resource files,build debug messages
 * (including file:// references), and captured execution results and user logs.
 *
 * <p>This is a class to be used withing the {@link pt.up.fe.comp.test.env.CompilerAssertion} class. It populates an
 * instance of this class with relevant information during the compilation and execution process. The assertion methods
 * will then use methods such as {@link #buildDebugInfoMessage(String, String, String)} or {@link #debugInfoTempFiles()}
 * to obtain file references and debugging information suitable for inclusion in test failure messages.</p>
 */
public class CompilerHistory {
    //if we are not running in a jar, then the resource file is accessible directly from the file system,
    //for which we should specify the resource folder specified in the Gradle file, relative path from the project root

    private ObjectProvider<Map<String, String>> config;

    // Parser
    private ContentProvider jmm;
    private String ast;
    private String astOpt;
    private String symbolTable;

    // OLLIR
    private ContentProvider ollir;
    private ContentProvider comparingOllir;
    private String ollirComparingCodeDescription;
    private String ollirThisCodeDescription;

    //Jasmin
    private ContentProvider jasmin;
    private ExecutionResult executionResult;
    private ContentProvider executionReport;

    //Logging
    private StreamProvider userLog;
    private StreamProvider reportsLog;
    private String ollirDescription;

    //Contains info regarding the test (Name, class,...)
    private TestInfo testInfo;
    //A textual description of the test, to be included in the report (may be null or empty)
    private String testDescription;

    private InfoHtml html;

    /**
     * Create a new {@code CompilerHistory} instance.
     *
     * @param config configuration map (may be {@code null}) used by tests or reporters
     */
    public CompilerHistory(Map<String, String> config) {
        Objects.requireNonNull(config, "Config map cannot be null. Use an Collections.emptyMap() instead.");
        reset();
        this.setConfig(config);
        File failDir = new File("build/reports/tests/test/fails");
        if (!failDir.exists()) {
            boolean created = failDir.mkdirs();
            if (!created) {
                throw new RuntimeException("Unable to create directory for test reports: " + failDir.getAbsolutePath());
            }
        }
        this.html = new InfoHtml(failDir);
    }


    /**
     * Reset stored artifacts and logs to an initial empty state.
     * Any previously captured data will be discarded.
     */
    public void reset() {
        this.jmm = null;
        this.ast = null;
        this.symbolTable = null;
        this.ollir = null;
        this.ollirDescription = null;
        this.jasmin = null;
        this.astOpt = null;
        this.comparingOllir = null;
        this.ollirComparingCodeDescription = null;
        this.ollirThisCodeDescription = "";
        this.executionResult = null;
        this.executionReport = null;
        this.userLog = new StreamProvider("User Log", new ByteArrayOutputStream(), "log");
        this.reportsLog = new StreamProvider("Reports Log", new ByteArrayOutputStream(), "log");
    }


    /**
     * Build a debug information message that contains a link to a report and to resources/temporary file references
     * for stored artifacts.
     *
     * @param message  failure or context message
     * @param expected expected value (for the report)
     * @param actual   actual value (for the report)
     * @return formatted debug message suitable to append to test assertions or logs
     */
    public String buildDebugInfoMessage(String message, String expected, String actual) {
        String debugMessage = "";
        if (testDescription != null && !testDescription.isBlank()) {
            debugMessage += "\n - Description: " + testDescription;
        }
        try {
            debugMessage += "\n - See report at: " + html.show(testInfo, this, message, expected, actual);
        } catch (IOException e) {
            //won't be able to show the report as a web page.
        }
        debugMessage += "\n - Debugging info:\n" + debugToFiles();
        return debugMessage;
    }

    /**
     * Alias for {@link #debugInfoTempFiles()} retained for backward compatibility.
     *
     * @return debug information referencing temporary or resource files
     */
    public String debugToFiles() {
        return debugInfoTempFiles();
    }

    /**
     * Produce a string containing intermediate step outputs (AST, symbol table, optimized AST)
     * if they are present.
     *
     * @return concatenated intermediate information or an empty string
     */
    public ContentProvider debugMidSteps() {
        var midSteps =
                (this.getCompilerConfig().isEmpty() ? "" : ("# " + this.config.getDescription() + ":\n"
                        + this.config.getContent() + "\n\n"))
                        +
                        (this.ast != null ? "# AST:\n" + this.ast + "\n\n" : "")
                        +
                        (this.symbolTable != null ? "# Symbol Table:\n" + this.symbolTable + "\n\n" : "")
                        +
                        (this.astOpt != null && !Objects.equals(this.ast, this.astOpt) ? "# Optimized AST:\n" + this.astOpt + "\n\n" : "");
        return new TextProvider("Intermediate Information", midSteps, "log");
    }


    /*
     * Write code to a file and return a reference string containing a file:// URL.
     * If {@code codePath} is non-null, it is resolved either against resources base path or used
     * as-is. If {@code codePath} is null, a temporary file is created and the provided code is written.
     *
     * @param codePath      path to use (may be {@code null} to create a temp file)
     * @param code          contents to write (ignored if {@code codePath} and file already exist)
     * @param type          human-readable type shown in the returned reference
     * @param extension     file extension to use for temp files
     * @param resourcesPath if {@code true} prepend the configured resources base path to {@code codePath}
     * @return a single-line reference describing the file and file:// URL
     */
//    public String codeToFile(String codePath, String code, String type, String extension, boolean resourcesPath) {
//        if (codePath == null && code == null) {
//            return "";
//        }
//        String f;
//        boolean temp = false;
//        if (codePath != null) {
//            f = new File((resourcesPath ? resourcesBasePath : "") + codePath).getAbsolutePath();
//        } else {
//            try {
//                var midName = type.toLowerCase().replaceAll("[^a-z0-9]", "_");
//                var tempFile = File.createTempFile("temp_" + midName + "_file_", "." + extension);
//                SpecsIo.write(tempFile, code);
//                f = tempFile.getAbsolutePath();
//                temp = true;
//            } catch (IOException e) {
//                f = "Could not create temp file: " + e.getMessage();
//            }
//        }
//        return referenceToFile(type, temp, f);
//    }

    /**
     * Build a human-readable reference to a file. If the provider is null, an empty string is returned.
     *
     * @param provider The content provider that will provide the file link and description
     * @return formatted single-line reference containing a file:// URL
     */
    public String referenceToFile(ContentProvider provider) {
        //if provider is null, don't do anything
        if (provider == null) {
            return "";
        }
        return provider.getDescription() + " " + (provider.isTempFile() ? "(temp)" : "") + ": " + provider.getFileLink() + System.lineSeparator();
    }

    /**
     * Build a concatenated string containing references (and possibly temporary files) for all
     * stored artifacts: JMM, OLLIR, Jasmin, execution reports and user logs.
     *
     * @return concatenated references and debug text suitable for inclusion in failure messages
     */
    public String debugInfoTempFiles() {

        return referenceToFile(jmm)
                +
                referenceToFile(comparingOllir)
                +
                referenceToFile(ollir)
                +
                referenceToFile(jasmin)
                +
                buildExecFiles()
                +
                buildMidStepFile()
                +
                buildReportsLogFile()
                +
                buildUserLogFile();
    }

    /**
     * Write the report log buffer to a temporary file if it is not blank.
     *
     * @return reference to the written log file or an empty string if log is blank
     */
    private String buildReportsLogFile() {
        var log = reportsLog.toString();
        return log.isBlank() ? "" :
                referenceToFile(reportsLog);
    }


    /**
     * Write the user log buffer to a temporary file if it is not blank.
     *
     * @return reference to the written log file or an empty string if log is blank
     */
    private String buildUserLogFile() {
        var log = userLog.toString();
        return log.isBlank() ? "" :
                referenceToFile(userLog);
    }

    /**
     * Build execution-related files and a summary report for the stored {@link ExecutionResult}.
     *
     * @return concatenated references and files related to execution result or empty string if none
     */
    private String buildExecFiles() {
        var exec = "";
        if (executionResult != null) {
            var workingDir = new TextProvider("Jasmin Working Directory", executionResult.getWorkingDir().getAbsolutePath(), "log");
            var stdoutLog = new TextProvider("Jasmin Output", executionResult.getFullOutput(), "log");
            exec = referenceToFile(workingDir)
                    + referenceToFile(executionReport)
                    + referenceToFile(stdoutLog);
        }
        return exec;
    }

    /**
     * Write intermediate step information to a temporary file if present.
     *
     * @return reference to the file containing intermediate information or an empty string
     */
    private String buildMidStepFile() {
        var mid = debugMidSteps();
        return mid.getContent().isBlank() ? "" : referenceToFile(mid);
    }

    /**
     * @return stored AST representation (may be null)
     */
    public String getAst() {
        return ast;
    }

    /**
     * @return stored symbol table (may be null)
     */
    public String getSymbolTable() {
        return symbolTable;
    }


//    /**
//     * @return current test name used when generating HTML reports (may be {@code null})
//     */
//    public String getCurrentTestName() {
//        return testInfo.getMethodName();
//    }

//    /**
//     * Set the current test name used to label generated reports.
//     *
//     * @param currentTestName human-readable test name
//     */
//    public void setCurrentTestName(String currentTestName) {
//        this.currentTestName = currentTestName;
//        this.setTestDescription(null); //reset description (for tests that doesn't set the description)
//    }

    public void setTestDescription(String testDescription) {
        this.testDescription = testDescription;
    }

    public String getTestDescription() {
        return testDescription;
    }

    /**
     * Replace the configuration map used by the history object.
     *
     * @param config new configuration map (may be {@code null})
     */
    public void setConfig(Map<String, String> config) {
        this.config = new ObjectProvider<>("Compiler Configuration", config, this::ArgsToString);
    }

    private String ArgsToString(Map<String, String> args) {
        if (args == null || args.isEmpty()) {
            return "No configuration parameters.";
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : args.entrySet()) {
            sb.append(entry.getKey()).append(" = ").append(entry.getValue()).append("\n");
        }
        return sb.toString();
    }

    /**
     * @return {@code true} if a comparing OLLIR snippet has been set
     */
    public boolean hasComparingCode() {
        return this.comparingOllir != null;
    }

    /**
     * @return stored optimized AST (may be null)
     */
    public String getAstOpt() {
        return astOpt;
    }

    /**
     * @return description for the comparing OLLIR code (may be null)
     */
    public String getOllirComparingCodeDescription() {
        return ollirComparingCodeDescription;
    }

    /**
     * @return description for the current OLLIR snippet being tested (may be empty)
     */
    public String getOllirThisCodeDescription() {
        return ollirThisCodeDescription;
    }

    /**
     * Set comparing OLLIR snippets and descriptions used to produce side-by-side reports.
     *
     * @param comparingOllir            text of the comparing ollir snippet
     * @param comparingOllirDescription human-friendly description for comparing snippet
     * @param thisOllirDescription      description for the primary OLLIR snippet
     */
    public void setComparingOllir(String comparingOllir, String comparingOllirDescription, String thisOllirDescription) {
        this.comparingOllir = new TextProvider(comparingOllirDescription, comparingOllir, "ollir");
        this.ollirComparingCodeDescription = comparingOllirDescription;
        this.ollirDescription = thisOllirDescription;

    }

    /**
     * @return stored execution result (may be {@code null})
     */
    public ExecutionResult getExecutionResult() {
        return executionResult;
    }

    public ContentProvider getExecutionReport() {
        if (executionReport == null) {
            if (executionResult != null) {
                var classPathList = "";
                var classpath = executionResult.getClasspath();
                if (classpath.isEmpty()) {
                    classPathList = "(unspecified)";
                } else {
                    if (classpath.size() == 1) {
                        classPathList = classpath.getFirst();
                    } else {
                        classPathList = "\n - " + String.join("\n - ", classpath);
                    }
                }
                var log = """
                        Working Directory: %s
                        Classpath: %s
                        Class Name: %s
                        Arguments: %s
                        Input: %s
                        Return Code: %d
                        """.formatted(
                        executionResult.getWorkingDir().getAbsolutePath(),
                        classPathList,
                        executionResult.getClassName(),
                        executionResult.getArgs().isEmpty() ? "(none)" : String.join(" ", executionResult.getArgs()),
                        executionResult.getInput() != null ? executionResult.getInput() : "(none)",
                        executionResult.getReturnCode());
                var execLog = new TextProvider("Jasmin Execution Report", log, "log");

            }
        }
        return executionReport;
    }

    /**
     * @return configuration map associated with this history object (may be {@code null})
     */
    public Map<String, String> getCompilerConfig() {
        return config.get();
    }

    public ContentProvider getConfigProvider() {
        return config;
    }

    /**
     * Append a message to the in-memory user log (no newline).
     *
     * @param message message to append
     */
    public void log(String message) {
        userLog.write(message);
    }

    /**
     * Append a message to the in-memory user log followed by a newline.
     *
     * @param message message to append
     */
    public void logln(String message) {
        userLog.writeln(message);
    }

    public void reportln(String message) {
        reportsLog.writeln(message);
    }

    public void report(String message) {
        reportsLog.write(message);
    }

    /**
     * @return current contents of the in-memory user log
     */
    public StreamProvider getUserLog() {
        return userLog;
    }


    /**
     * Set the stored AST textual representation.
     *
     * @param ast AST text to set
     */
    public void setAst(String ast) {
        this.ast = ast;
    }

    /**
     * Set the optimized AST textual representation.
     *
     * @param astOpt optimized AST text to set
     */
    public void setAstOpt(String astOpt) {
        this.astOpt = astOpt;
    }

    /**
     * Set the symbol table textual representation.
     *
     * @param symbolTable symbol table text to set
     */
    public void setSymbolTable(String symbolTable) {
        this.symbolTable = symbolTable;
    }


    /**
     * Store an execution result captured after running Jasmin-generated code.
     *
     * @param executionResult execution result to store
     */
    public void setExecutionResult(ExecutionResult executionResult) {
        this.executionResult = executionResult;
    }

    public ContentProvider getJmm() {
        return jmm;
    }

    public ContentProvider getComparingOllir() {
        return comparingOllir;
    }

    public ContentProvider getOllir() {
        return ollir;
    }

    public ContentProvider getJasmin() {
        return jasmin;
    }

    public ContentProvider getLogger() {
        return userLog;
    }

    public void setJmm(ContentProvider jmm) {
        this.jmm = jmm;
    }

    public void setOllir(ContentProvider ollir) {
        this.ollir = ollir;
        if (this.ollirDescription != null) {
            this.ollir.setDescription(this.ollirDescription);
        }
    }

    public void setJasmin(ContentProvider jasmin) {
        this.jasmin = jasmin;
    }

    public void setTestInfo(TestInfo testInfo) {
        this.testInfo = testInfo;
    }

    public ContentProvider getReportLog() {
        return this.reportsLog;
    }
}
