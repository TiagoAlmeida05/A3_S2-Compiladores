// language: java
package pt.up.fe.comp.test.env;

import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.StringDescription;
import org.junit.Assert;
import org.junit.Rule;
import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.jasmin.ExecutionResult;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.lexer.JmmLexer;
import pt.up.fe.comp.jmm.lexer.JmmLexerResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp.jmm.report.StageResult;
import pt.up.fe.comp.test.env.providers.FileProvider;
import pt.up.fe.comp.test.env.providers.ResourceProvider;
import pt.up.fe.comp.test.env.providers.TextProvider;
import pt.up.fe.comp.test.env.utils.CompilerHistory;
import pt.up.fe.comp.test.env.providers.ContentProvider;
import pt.up.fe.comp.test.env.utils.TestInfo;

import java.io.File;
import java.util.*;
import java.util.stream.Collectors;

import static pt.up.fe.comp.TestUtils.getJmmOptimization;

/**
 * Helper that asserts compiler pipeline stages inside tests and records intermediate
 * artifacts and diagnostics in a {@link CompilerHistory} instance.
 *
 * <p>Provides convenience methods to run lexer, parser, semantic analysis, OLLIR and Jasmin
 * stages, capture outputs (code, ASTs, logs, execution results) and assert expected outcomes.
 * When assertions fail, stored history is appended to failure messages to aid debugging.</p>
 */
public class CompilerAssertion {
    CompilerHistory history;
    @Rule
    public TestInfo testInfo;

    /**
     * Create a new {@code CompilerAssertion} backed by a {@link CompilerHistory}.
     *
     * @param config optional configuration map forwarded to compiler components and history
     */
    public CompilerAssertion(Map<String, String> config) {
        this.history = new CompilerHistory(config);
        this.testInfo = new TestInfo();
        this.history.setTestInfo(this.testInfo);
        reset();

    }

    /**
     * Reset the internal {@link CompilerHistory} state, discarding previously captured artifacts.
     */
    public void reset() {
        history.reset();
    }


    /**
     * Run lexical analysis on a file and capture the source path in history.
     *
     * @param file JMM source file
     * @return lexer result or {@code null} if an exception occurred (an assertion failure is produced)
     */
    public JmmLexerResult lex(File file) {
        return lex(new FileProvider("Jmm", file));
    }


    /**
     * Run lexical analysis on a resource file and capture the source path in history.
     *
     * @param resourcePath    path to a resource file containing JMM source
     * @param resourceBaseDir base directory to retrieve an actual file reference. If null, it considers that the resource cannot be accessed via File system and will create a temporary file if needed.
     * @return lexer result or {@code null} if an exception occurred (an assertion failure is produced)
     */
    public JmmLexerResult lexResource(String resourcePath, String resourceBaseDir) {
        return lex(new ResourceProvider("Jmm", resourcePath, resourceBaseDir));
    }

    public JmmLexerResult lexResourceNoCheck(String resourcePath, String resourceBaseDir) {
        return lexNoCheck(new ResourceProvider("Jmm", resourcePath, resourceBaseDir));
    }

    /**
     * Run lexical analysis on a provided JMM source string and capture it in history.
     *
     * @param jmmCode JMM source code
     * @return lexer result or {@code null} if an exception occurred
     */
    public JmmLexerResult lexCodeSnippet(String jmmCode) {
        return lex(new TextProvider("Jmm", jmmCode, "jmm"));
    }

    public JmmLexerResult lexCodeSnippetNoCheck(String jmmCode) {
        return lexNoCheck(new TextProvider("Jmm", jmmCode, "jmm"));
    }


    public JmmLexerResult lex(ContentProvider provider) {
        JmmLexerResult lexerResult = this.lexNoCheck(provider);
        checkNoErrors("Lexical", lexerResult);
        return lexerResult;
    }


    public JmmLexerResult lexNoCheck(ContentProvider provider) {
        return this.lexicalAnalysis(provider);
    }

    private JmmLexerResult lexicalAnalysis(ContentProvider provider) {
        reset();
        history.setJmm(provider);
        try {
            JmmLexer lexer = TestUtils.getJmmLexer();
            var lex = lexer.lex(provider.getContent(), history.getCompilerConfig());
            this.report(lex);
            return lex;
        } catch (Exception e) {
            exposeException(e, "Lexical Analysis");
            //Dummy return to satisfy return type, test will already have failed due to the exception
            return new JmmLexerResult(null,
                    List.of(Report.newError(Stage.LEXICAL, -1, -1, "Lexical analysis failed with an exception", e)),
                    Map.of());
        }
    }

    private void report(StageResult result) {
        result.reports().forEach(r -> history.reportln(r.toString()));
    }

    /**
     * Parse tokens using the parser's default starting rule.
     *
     * @param lexer lexer result containing tokens
     * @return parser result or {@code null} if an exception occurred
     */
    public JmmParserResult parse(JmmLexerResult lexer) {
        return parse(lexer, TestUtils.getJmmParser().getDefaultRule());
    }

    /**
     * Parse tokens starting from the specified rule and store the AST textual representation.
     *
     * @param lexer        lexer result
     * @param startingRule parser starting rule name
     * @return parser result or {@code null} if an exception occurred
     */
    public JmmParserResult parse(JmmLexerResult lexer, String startingRule) {
        JmmParserResult parserResult = syntacticAnalysis(lexer, startingRule);
        checkNoErrors("Parsing", parserResult);
        assertNotNull("Parsing from rule " + startingRule + " should produce a non-null node.", parserResult.rootNode());
        history.setAst(parserResult.rootNode().toTree());
        return parserResult;
    }

    public JmmParserResult parseWithErrors(JmmLexerResult lexer) {
        return parseWithErrors(lexer, TestUtils.getJmmParser().getDefaultRule());
    }

    public JmmParserResult parseWithErrors(JmmLexerResult lexer, String startingRule) {
        JmmParserResult parserResult = syntacticAnalysis(lexer, startingRule);
        checkErrors("Parsing", parserResult);
        return parserResult;
    }

    private JmmParserResult syntacticAnalysis(JmmLexerResult lexer, String startingRule) {
        try {
            JmmParserResult parse = TestUtils.getJmmParser().parse(lexer, startingRule, history.getCompilerConfig());
            this.report(parse);
            return parse;
        } catch (Exception e) {
            exposeException(e, "Parsing from rule " + startingRule);
            return new JmmParserResult(null,
                    List.of(Report.newError(Stage.SYNTATIC, -1, -1, "Syntactic analysis failed with an exception", e)),
                    Map.of());
        }
    }

    /**
     * Build and check the symbol table; store its textual representation in history.
     *
     * @param parserResult parser result to analyze
     * @return semantics result containing the symbol table
     */
    public JmmSemanticsResult symbolTable(JmmParserResult parserResult) {
        JmmSemanticsResult analysisResult = symbolTableNoCheck(parserResult);
        checkNoErrors("Symbol Table", analysisResult);
        history.setSymbolTable(analysisResult.getSymbolTable().toString());
        return analysisResult;
    }

    /**
     * Build the symbol table without asserting the absence of errors.
     *
     * @param parserResult parser result
     * @return semantics result or {@code null} if an exception occurred
     */
    public JmmSemanticsResult symbolTableNoCheck(JmmParserResult parserResult) {
        try {
            JmmSemanticsResult jmmSemanticsResult = TestUtils.getJmmAnalysis().buildSymbolTable(parserResult);
            this.report(jmmSemanticsResult);
            return jmmSemanticsResult;
        } catch (Exception e) {
            exposeException(e, "Symbol Table");
            return null;
        }
    }

    /**
     * Run semantic analysis and perform error checks.
     *
     * @param parserResult parser result
     * @return semantics result after analysis
     */
    public JmmSemanticsResult analyse(JmmParserResult parserResult) {
        return analyse(symbolTable(parserResult));

    }

    /**
     * Run semantic analysis and perform error checks.
     *
     * @param symbolTableResult semantics result with symbol table
     * @return semantics result after analysis
     */
    public JmmSemanticsResult analyse(JmmSemanticsResult symbolTableResult) {

        JmmSemanticsResult analysis = analyseNoCheck(symbolTableResult);
        checkNoErrors("Semantic Analysis", analysis);
        return analysis;

    }

    /**
     * Run semantic analysis expecting errors (used for negative tests).
     *
     * @param symbolTableResult semantics result
     * @return semantics result after analysis
     */
    public JmmSemanticsResult analyseMustFail(JmmSemanticsResult symbolTableResult) {
        JmmSemanticsResult analysis = analyseNoCheck(symbolTableResult);
        checkErrors("Semantic Analysis", analysis);
        return analysis;
    }

    public JmmSemanticsResult analyseNoCheck(JmmParserResult parserResult) {
        return analyseNoCheck(symbolTableNoCheck(parserResult));
    }

    public JmmSemanticsResult analyseNoCheck(JmmSemanticsResult symbolTableResult) {
        try {
            JmmSemanticsResult jmmSemanticsResult = TestUtils.getJmmAnalysis().semanticAnalysis(symbolTableResult);
            this.report(jmmSemanticsResult);
            return jmmSemanticsResult;
        } catch (Exception e) {
            exposeException(e, "Semantic Analysis");
            return null;
        }
    }

    /**
     * Generate OLLIR from semantics and assert no generation errors.
     *
     * @param semanticsResult semantics result
     * @return ollir result
     */
    public OllirResult ollir(JmmSemanticsResult semanticsResult) {
        OllirResult ollirResult = ollirNoCheck(semanticsResult);
        checkNoErrors("Ollir Generation", ollirResult);
        return ollirResult;
    }

    /**
     * Read OLLIR from a resource file, capture its path in history and return an OllirResult.
     *
     * @param file OLLIR file
     * @return ollir result or {@code null} on error
     */
    public OllirResult ollir(File file) {
        return ollir(new FileProvider("Ollir", file));
    }

    public OllirResult ollirResource(String resourcePath, String resourceBaseDir) {
        return ollir(new ResourceProvider("Ollir", resourcePath, resourceBaseDir));
    }

    /**
     * Use the provided OLLIR code string to create an OllirResult and capture it in history.
     *
     * @param code ollir code string
     * @return ollir result or {@code null} on error
     */
    public OllirResult ollirSnippet(String code) {
        return ollir(new TextProvider("Ollir", code, "ollir"));
    }

    private OllirResult ollir(ContentProvider ollir) {
        reset();
        history.setOllir(ollir);
        try {
            OllirResult ollirResult = new OllirResult(ollir.getContent(), history.getCompilerConfig());
            checkNoErrors("Ollir Code Reading", ollirResult);
            return ollirResult;
        } catch (Exception e) {
            exposeException(e, "Ollir Code Result");
            return null;
        }
    }


    public OllirResult ollirNoCheck(JmmSemanticsResult semanticsResult) {

        var optimizedSemantics = optimizeNoCheck(semanticsResult);
        OllirResult ollirResult = null;
        try {
            JmmOptimization optimization = getJmmOptimization();
            ollirResult = optimization.toOllir(optimizedSemantics);
            history.setOllir(new TextProvider("Ollir", ollirResult.getOllirCode(), "ollir"));
            report(ollirResult);
        } catch (Exception e) {
            exposeException(e, "Ollir Generation");
        }
        if (ollirResult != null && ollirResult.noErrors()) {
            ollirResult = optimizeNoCheck(ollirResult);
            if (ollirResult != null)
                report(ollirResult);
        }

        return ollirResult;
    }


    private JmmSemanticsResult optimizeNoCheck(JmmSemanticsResult semanticsResult) {
        try {
            // Always run all stages, each method has access to the config
            // to decide if optimizations should be enabled or not
            JmmSemanticsResult optimize = getJmmOptimization().optimize(semanticsResult);
            var node = optimize.getRootNode();
            history.setAstOpt(node != null ? node.toTree() : null);
            report(optimize);
            return optimize;
        } catch (Exception e) {
            exposeException(e, "AST Optimization");
            return null;
        }
    }

    private OllirResult optimizeNoCheck(OllirResult ollirResult) {
        try {

            JmmOptimization optimization = getJmmOptimization();
            ollirResult = optimization.optimize(ollirResult);
            history.setOllir(new TextProvider("Ollir", ollirResult.getOllirCode(), "ollir"));
            report(ollirResult);
            return ollirResult;
        } catch (Exception e) {
            exposeException(e, "Ollir Optimization");
            return null;
        }
    }


    /**
     * Convert OLLIR to Jasmin and assert no errors.
     *
     * @param ollirResult ollir result
     * @return jasmin result
     */
    public JasminResult jasmin(OllirResult ollirResult) {
        JasminResult jasminResult = jasminNoCheck(ollirResult);
        checkNoErrors("Jasmin Generation", jasminResult);
        return jasminResult;
    }

    /**
     * Convert OLLIR to Jasmin without asserting errors and store the generated Jasmin code.
     *
     * @param ollirResult ollir result
     * @return jasmin result or {@code null} on error
     */
    public JasminResult jasminNoCheck(OllirResult ollirResult) {
        try {
            JasminResult jasminResult = TestUtils.getJasminBackend().toJasmin(ollirResult);
            history.setJasmin(new TextProvider("Jasmin", jasminResult.getJasminCode(), "j"));
            report(jasminResult);
            return jasminResult;
        } catch (Exception e) {
            exposeException(e, "Jasmin Generation");
            return null;
        }
    }


    /**
     * Run Jasmin-generated code and assert execution had no errors.
     *
     * @param jasminResult jasmin result with runnable code
     * @return execution result
     */
    public ExecutionResult runJasmin(JasminResult jasminResult, List<String> classPaths) {
        ExecutionResult result = runJasminNoCheck(jasminResult, classPaths);
        checkNoErrors("Jasmin Execution", result);
        return result;
    }


    /**
     * Run Jasmin-generated code without asserting errors and store execution details in history.
     *
     * @param jasminResult jasmin result
     * @return execution result or {@code null} on error
     */
    public ExecutionResult runJasminNoCheck(JasminResult jasminResult, List<String> classPaths) {
        try {
            var execResult = jasminResult.run(Collections.emptyList(), classPaths);
//            var output = SpecsStrings.normalizeFileContents(execResult.getStdout(), true);
            history.setExecutionResult(execResult);
            history.setJasmin(new TextProvider("Jasmin", jasminResult.getJasminCode(), "j"));
//            assertEquals("Jasmin output", expected, output);
            report(execResult);
            return execResult;
        } catch (Exception e) {
            exposeException(e, "Running Jasmin code");
            return null;
        }
    }


    /**
     * Assert that a stage produced at least one error.
     *
     * @param phase  human-friendly phase name
     * @param result stage result to check
     */
    public void checkErrors(String phase, StageResult result) {
        assertTrue(phase + " should have errors.", result.hasErrors());
    }

    /**
     * Assert that a stage produced no errors. On failure, appends debug info from history.
     *
     * @param phase  human-friendly phase name
     * @param result stage result to check
     */
    public void checkNoErrors(String phase, StageResult result) {
        List<Report> errors = result.getErrors();
        var errorsStr = errors.stream().map(Report::toString).collect(Collectors.joining("\n\t- ", "\n\t- ", ""));
        assertEquals(phase + " should not have errors, but the following errors were found:" + errorsStr, 0, errors.size());
    }

    /**
     * Assert a boolean condition is true. On failure, a detailed debug message is appended.
     *
     * @param message   assertion message (may contain placeholders)
     * @param condition boolean condition expected to be true
     */
    public void assertTrue(String message, boolean condition) {
        assertEquals(message, condition, true, Boolean.toString(true), Boolean.toString(condition));
    }

    /**
     * Assert that a value matches a Hamcrest matcher. On mismatch, a detailed debug message is appended.
     *
     * @param message assertion message
     * @param actual  value under test
     * @param matcher Hamcrest matcher
     * @param <T>     value type
     */
    public <T> void assertThat(String message, T actual, Matcher<? super T> matcher) {
        if (!matcher.matches(actual)) {
            var expectedMsg = StringDescription.asString(matcher);
            Description description = new StringDescription();
            matcher.describeMismatch(actual, description);
            assertEquals(message,
                    matcher.matches(actual), true,
                    expectedMsg, description.toString());
        }
    }

    /**
     * Assert that a value is not null. On failure, a detailed debug message is appended.
     *
     * @param message assertion message
     * @param value   value expected to be non-null
     */
    public void assertNotNull(String message, Object value) {

        assertEquals(message, value != null, true, "Not null", "null");
    }

    /**
     * Assert equality between expected and actual. On failure, history debug info is appended.
     *
     * @param message  assertion message (may contain placeholders)
     * @param expected expected value
     * @param actual   actual value
     */
    public void assertEquals(String message, Object expected, Object actual) {
        assertEquals(message, expected, actual, expected != null ? expected.toString() : null, actual != null ? actual.toString() : null);
    }

    /**
     * Assert inequality between expected and actual.
     *
     * @param message  assertion message
     * @param expected expected value
     * @param actual   actual value
     */
    public void assertNotEquals(String message, Object expected, Object actual) {
        assertNotEquals(message, expected, actual, expected != null ? expected.toString() : null, actual != null ? actual.toString() : null);
    }

    /**
     * Assert equality with custom textual representations for the expected and actual values.
     * On failure, a debug report generated from {@link CompilerHistory} is appended to the message.
     *
     * @param message         assertion message (may contain placeholders)
     * @param expected        expected value
     * @param actual          actual value
     * @param expectedMessage string representation of expected
     * @param actualMessage   string representation of actual
     */
    public void assertEquals(String message, Object expected, Object actual, String expectedMessage, String actualMessage) {
        //first check if it asserts
        if (expected == null && actual == null) {
            return;
        }
        if (expected != null) {
            if (expected.equals(actual))
                return;
        }

        if (expectedMessage == null) {
            expectedMessage = "null";
        }
        if (actualMessage == null) {
            actualMessage = "null";
        }

        //it is now time to fail!
        message = message.replace("${expected}", String.valueOf(expected))
                .replace("${actual}", String.valueOf(actual));
        message += history.buildDebugInfoMessage(message, expectedMessage, actualMessage);
        message += "\n - Assertion Result:";
        //we know that it will fail
        Assert.assertEquals(message, expected, actual);
    }

    /**
     * Assert inequality with custom textual representations.
     *
     * @param message         assertion message
     * @param expected        expected value
     * @param actual          actual value
     * @param expectedMessage textual expected
     * @param actualMessage   textual actual
     */
    public void assertNotEquals(String message, Object expected, Object actual, String expectedMessage, String actualMessage) {
        //first check if it asserts
        if (expected != null) {
            if (!expected.equals(actual))
                return;
        } else if (actual != null) {
            return;
        }

        if (expectedMessage == null) {
            expectedMessage = "null";
        }
        if (actualMessage == null) {
            actualMessage = "null";
        }

        //it is now time to fail!
        message = message.replace("${expected}", String.valueOf(expected))
                .replace("${actual}", String.valueOf(actual));
        message += history.buildDebugInfoMessage(message, expectedMessage, actualMessage);
        message += "\n - Assertion Result:";//we know that it will fail
        //we know that it will fail
        Assert.assertNotEquals(message, expected, actual);

    }

    /**
     * Fail the test with the provided message and appended history debug info.
     *
     * @param s failure message
     */
    public void fail(String s) {
        Assert.fail(s + history.buildDebugInfoMessage(s, "N/A", "N/A"));
    }


    /**
     * Capture an exception and fail the test, including history debug info.
     *
     * @param e     exception caught
     * @param phase human-friendly phase name where the exception occurred
     */
    public void exposeException(Exception e, String phase) {
        java.io.StringWriter sw = new java.io.StringWriter();
        java.io.PrintWriter pw = new java.io.PrintWriter(sw);

        pw.println("Exception caught during " + phase + ":\n");
        pw.print("\tException: ");
        pw.println(e.getMessage());
        if (e.getCause() != null) {
            pw.print("\tCaused by: ");
            pw.println(e.getCause().getMessage());
        }
        e.printStackTrace(pw);
        pw.flush();
        var actual = sw.toString();
        pw.println(history.buildDebugInfoMessage("Exception caught during " + phase + ": " + e.getMessage(), "Not to have exceptions.", actual));
        Assert.fail(sw.toString());
    }


    /**
     * Set comparing OLLIR snippets and descriptions used to produce side-by-side reports.
     *
     * @param comparingOllir            comparing ollir snippet
     * @param comparingOllirDescription description for the comparing snippet
     * @param thisOllirDescription      description for the main snippet
     */
    public void setComparingOllir(String comparingOllir, String comparingOllirDescription, String thisOllirDescription) {
        history.setComparingOllir(comparingOllir, comparingOllirDescription, thisOllirDescription);
    }

    /**
     * Append message to the captured user log (no newline).
     *
     * @param message text to append
     */
    public void log(String message) {
        history.log(message);
    }

    /**
     * Append message to the captured user log with a newline.
     *
     * @param message text to append
     */
    public void logln(String message) {
        history.logln(message);
    }


    public void setDescription(String description) {
        history.setTestDescription(description);
    }

    public String getTestDescription() {
        return history.getTestDescription();
    }

    /**
     * Replace the configuration map used by the underlying history and compiler components.
     *
     * @param config new configuration map
     */
    public void setConfig(Map<String, String> config) {
        history.setConfig(config);
    }

    /**
     * @return configuration map associated with this assertion helper
     */
    public Map<String, String> getConfig() {
        return history.getCompilerConfig();
    }
}