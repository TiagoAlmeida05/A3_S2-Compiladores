package pt.up.fe.comp;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast2jasmin.AstToJasmin;
import pt.up.fe.comp.jmm.jasmin.JasminBackend;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.lexer.JmmLexer;
import pt.up.fe.comp.jmm.lexer.JmmLexerResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParser;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.StageResult;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsSystem;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.*;
import java.util.stream.Collectors;

public class TestUtils {

    private static final Properties CONFIG = TestUtils.loadProperties("config.properties");

    public static Properties loadProperties(String filename) {
        try {
            Properties props = new Properties();
            props.load(new StringReader(SpecsIo.read(filename)));
            return props;
        } catch (IOException e) {
            throw new RuntimeException("Error while loading properties file '" + filename + "'", e);
        }
    }
//
//    public static JmmLexerResult lexical(String code) {
//        return lexical(code, Collections.emptyMap());
//    }
//
//
//    public static JmmLexerResult lexical(String code, Map<String, String> config) {
//        JmmLexer lexer = getJmmLexer();
//        return lexer.lex(code, config);
//    }
//
//    public static JmmParserResult parse(JmmLexerResult lexer, String startingRule) {
//        return parse(lexer, startingRule, Collections.emptyMap());
//    }
//
//    public static JmmParserResult parse(JmmLexerResult lexer, String startingRule, Map<String, String> config) {
//        JmmParser parser = getJmmParser();
//        return parser.parse(lexer, startingRule, config);
//    }
//
//    public static JmmParserResult parse(JmmLexerResult lexer) {
//        return parse(lexer, Collections.emptyMap());
//    }
//
//    public static JmmParserResult parse(JmmLexerResult lexer, Map<String, String> config) {
//
//        JmmParser parser = getJmmParser();
//
//        return parser.parse(lexer, config);
//    }

    public static JmmLexer getJmmLexer() {
        return getCompilationStage("LexerClass", JmmLexer.class);
    }

    public static JmmParser getJmmParser() {
        return getCompilationStage("ParserClass", JmmParser.class);
    }

    public static JmmAnalysis getJmmAnalysis() {

        return getCompilationStage("AnalysisClass", JmmAnalysis.class);
    }

    public static JmmOptimization getJmmOptimization() {
        return getCompilationStage("OptimizationClass", JmmOptimization.class);
    }

    public static JasminBackend getJasminBackend() {
        return getCompilationStage("BackendClass", JasminBackend.class);
    }

    public static boolean hasAstToJasminClass() {
        return CONFIG.getProperty("AstToJasminClass") != null;
    }

    public static AstToJasmin getAstToJasmin() {
        return getCompilationStage("AstToJasminClass", AstToJasmin.class);
    }

    public static <T> T getCompilationStage(String stageName, Class<T> baseClass) {

        SpecsSystem.programStandardInit();

        // Get Stage class name
        String stageClassName = getClassFromConfig(stageName);

        try {
            // Get class with main
            Class<?> parserClass = Class.forName(stageClassName);

            // It is expected that the Parser class can be instantiated without arguments
            return baseClass.cast(parserClass.getConstructor().newInstance());

        } catch (Exception e) {
            throw new RuntimeException("Could not instantiate stage '" + stageName + "' from class '" + stageClassName + "'", e);
        }
    }

    private static String getClassFromConfig(String property) {
        // Get class name
        String className = CONFIG.getProperty(property);

        // Check if empty
        if (className.isBlank()) {
            throw new RuntimeException("Possible problem in file 'config.properties', property '" + property
                    + "' is empty. Please provide a fully qualified class name for that compilation stage.");
        }

        return className;
    }

//
//    /**
//     * Only calls the `JmmAnalysis` stage to analyse the AST.
//     *
//     * @param parserResult
//     * @return
//     */
//    public static JmmSemanticsResult analyse(JmmParserResult parserResult) {
//
//        JmmAnalysis analysis = getJmmAnalysis();
//
//        var analysisResult = analysis.semanticAnalysis(parserResult);
//
//        // If any of the error reports have an exception, re-throw it
//        var exception = analysisResult.reports().stream()
//                .filter(r -> r.getException().isPresent())
//                .map(r -> r.getException().get())
//                .findFirst()
//                .orElse(null);
//
//        if (exception != null) {
//            throw new RuntimeException("Exception during semantic analysis", exception);
//        }
//
//        return analysisResult;
//    }
//
//    /**
//     * Receives a string o Java-- code and calls JmmParser and JmmAnalysis stages to generate and analyse the AST.
//     * Assumes there is no configuration.
//     *
//     * @param jmmCode
//     * @return
//     * @deprecated Should use {@link #analyseResource(String)}  instead
//     */
//    @Deprecated
//    public static JmmSemanticsResult analyse(String jmmCode) {
//        return analyse(jmmCode, Collections.emptyMap());
//    }
//
//    public static JmmSemanticsResult analyseResource(String jmmFilename) {
//        return analyseResource(jmmFilename, Collections.emptyMap());
//    }
//
//    public static JmmSemanticsResult analyseResource(String jmmFilename, Map<String, String> config) {
//        File f = new File(jmmFilename);
//        System.out.print("Processing file: ");
//        if (f.exists()) {
//            System.out.println("file://" + jmmFilename);
//        } else {
//            System.out.println(jmmFilename);
//        }
//
//        String jmmCode = SpecsIo.getResource(jmmFilename);
//        return analyse(jmmCode, config);
//    }
//
//
//    /**
//     * Receives a string o Java-- code and calls JmmParser and JmmAnalysis stages to generate and analyse the AST.
//     *
//     * @param jmmCode
//     * @param config
//     * @return
//     */
//    public static JmmSemanticsResult analyse(String jmmCode, Map<String, String> config) {
//        var lexerResult = lexical(jmmCode, config);
//        if (getNumErrors(lexerResult.reports()) > 0) {
//            JmmParserResult parseResults = new JmmParserResult(null, lexerResult.reports(), config);
//            return new JmmSemanticsResult(parseResults, null, Collections.emptyList());
//        }
//        var parseResults = TestUtils.parse(lexerResult, config);
//
//        // If there are errors, return immediately
//        if (getNumErrors(parseResults.reports()) > 0) {
//            return new JmmSemanticsResult(parseResults, null, Collections.emptyList());
//        }
//        //noErrors(parseResults.getReports());
//        return analyse(parseResults);
//    }
//
//    /**
//     * Only calls the `JmmOptimization` stage to optimize and generate the OLLIR code.
//     *
//     * @param semanticsResult
//     * @return
//     */
//    public static OllirResult optimize(JmmSemanticsResult semanticsResult) {
//
//        JmmOptimization optimization = getJmmOptimization();
//
//        semanticsResult = optimization.optimize(semanticsResult);
//
//        var ollirResult = optimization.toOllir(semanticsResult);
//
//        ollirResult = optimization.optimize(ollirResult);
//
//        return ollirResult;
//
//    }
//
//    public static OllirResult optimize(String jmmCode, Map<String, String> config) {
//        return optimize(jmmCode, config, true);
//    }
//
//    /**
//     * Receives a string o Java-- code and calls JmmParser and JmmAnalysis stages to generate and analyse the AST, and
//     * JmmOptimization to optimize and generate OLLIR code.
//     *
//     * @param jmmCode
//     * @param config
//     * @return
//     */
//    public static OllirResult optimize(String jmmCode, Map<String, String> config, boolean checkAnalysisReports) {
//        var semanticsResult = analyse(jmmCode, config);
//
//        // Check semantic analysis
//        if (checkAnalysisReports) {
//            noErrors(semanticsResult.reports());
//        }
//
//        return optimize(semanticsResult);
//    }
//
//    /**
//     * Receives a string o Java-- code and calls JmmParser and JmmAnalysis stages to generate and analyse the AST, and
//     * JmmOptimization to optimize and generate OLLIR code. Assumes there is no configuration.
//     *
//     * @param jmmCode
//     * @return
//     */
//    public static OllirResult optimize(String jmmCode) {
//        return optimize(jmmCode, Collections.emptyMap(), false);
//    }
//
//    /**
//     * Only calls the `JasminBackend` stage to generate Jasmin code.
//     *
//     * @param ollirResult
//     * @return
//     */
//    public static JasminResult backend(OllirResult ollirResult) {
//        JasminBackend backend = getJasminBackend();
//
//        var jasminResult = backend.toJasmin(ollirResult);
//
//        return jasminResult;
//
//    }
//
//    /**
//     * Only calls the `JasminBackend` stage to generate Jasmin code.
//     *
//     * @param ollirResult
//     * @return
//     */
//    public static JasminResult backend(JmmSemanticsResult semanticsResult) {
//        var astToJasmin = getAstToJasmin();
//
//        // Optimize
//        semanticsResult = astToJasmin.optimize(semanticsResult);
//
//        // Convert
//        var jasminResult = astToJasmin.toJasmin(semanticsResult);
//
//        return jasminResult;
//    }
//
//    /**
//     * Receives a string o Java-- code and calls all the stages to generate Jasmin code. Assumes there is no
//     * configuration.
//     *
//     * @param jmmCode
//     * @return
//     */
//    public static JasminResult backend(String jmmCode) {
//        return backend(jmmCode, Collections.emptyMap());
//    }
//
//    public static JasminResult backend(String code, Map<String, String> config) {
//        return backend(code, config, true);
//    }
//
//    /**
//     * Receives a string o Java-- code and calls all the stages to generate Jasmin code.
//     *
//     * @param jmmCode
//     * @return
//     */
//    public static JasminResult backend(String code, Map<String, String> config, boolean checkAnalysisReports) {
//        // AstToJasmin path has priority
//        if (hasAstToJasminClass()) {
//            var semanticsResult = analyse(code, config);
//            noErrors(semanticsResult.reports());
//            return backend(semanticsResult);
//        }
//
//        // Otherwise, run OLLIR path
//        var ollirResult = optimize(code, config, checkAnalysisReports);
//        noErrors(ollirResult.reports());
//        return backend(ollirResult);
//    }
//
//    /**
//     * Checks if there are no Error reports. Throws exception if there is at least one Report of type Error.
//     */
//    public static void noErrors(List<Report> reports) {
//        reports.stream()
//                .filter(report -> report.getType() == ReportType.ERROR)
//                .findFirst()
//                .ifPresent(report -> {
//                    if (report.getException().isPresent()) {
//                        throw new RuntimeException("Found at least one error report: " + report,
//                                report.getException().get());
//                    }
//
//                    throw new RuntimeException("Found at least one error report: " + report);
//                });
//    }
//
//    /**
//     * Overload that accepts a ReportsProvider.
//     *
//     * @param provider
//     */
//    public static void noErrors(StageResult provider) {
//        noErrors(provider.reports());
//    }
//
//    /**
//     * Checks if there are Error reports. Throws exception is there are no reports of type Error.
//     */
//    public static void mustFail(List<Report> reports) {
//        var errorReports = reports.stream()
//                .filter(report -> report.getType() == ReportType.ERROR)
//                .toList();
//
//        if (errorReports.isEmpty()) {
//            throw new RuntimeException("Could not find any Error report");
//        }
//
//        // Print reports
//        System.out.println(errorReports.stream().map(Report::toString).collect(Collectors.joining("\n")));
//    }
//
//    /**
//     * Overload that accepts a ReportsProvider.
//     *
//     * @param provider
//     */
//    public static void mustFail(StageResult provider) {
//        mustFail(provider.reports());
//    }
//
//    public static long getNumReports(List<Report> reports, ReportType type) {
//        return reports.stream()
//                .filter(report -> report.getType() == type)
//                .count();
//    }
//
//    public static long getNumErrors(List<Report> reports) {
//        return getNumReports(reports, ReportType.ERROR);
//    }

//    public static String getLibsClasspath() {
//        // return "test/fixtures/libs/compiled";
//        return "libs-jmm/compiled";
//    }
//
//    public static String runJasmin(String jasminCode) {
//        // return new JasminResult(jasminCode).run();
//        return runJasmin(jasminCode, Collections.emptyMap());
//    }
//
//    public static String runJasmin(String jasminCode, List<String> args) {
//        // return new JasminResult(jasminCode).run(args);
//        return runJasmin(jasminCode, args, Collections.emptyMap());
//    }
//
//    public static String runJasmin(String jasminCode, Map<String, String> config) {
//        return new JasminResult(jasminCode, config).run().getFullOutput();
//    }
//
//    public static String runJasmin(String jasminCode, List<String> args, Map<String, String> config) {
//        return new JasminResult(jasminCode, config).run(args).getFullOutput();
//    }

    /**
     * Converts an even number of Strings to a key->value map of Strings.
     *
     * <p>
     * E.g. if args is ["key1", "value1", "key2", "value2"], returns a map {"key1": "value1", "key2": "value2"}
     *
     * @param args
     * @return
     */
    public static Map<String, String> toConfig(List<String> args) {
        SpecsCheck.checkArgument(args.size() % 2 == 0,
                () -> "Expected an even number of arguments, got " + args.size() + ": " + args);

        // Using LinkedHashMap to keep order of arguments
        var config = new LinkedHashMap<String, String>();
        for (int i = 0; i < args.size(); i += 2) {
            config.put(args.get(i), args.get(i + 1));
        }

        return config;
    }

    /**
     * Convenience method that receives a variable number of arguments.
     *
     * @param args
     * @return
     */
    public static Map<String, String> toConfig(String... args) {
        return toConfig(Arrays.asList(args));
    }
//
//    /**
//     * Finds an exception of the given class inside the chain of causes of the given exception. If the given exception
//     * is an instance of the given class, returns the exception itself. Returns null if no exception of the given class
//     * is found.
//     *
//     * @param <T>
//     * @param e
//     * @param expectedClass
//     * @return
//     */
//    public static <T extends Throwable> T getException(Throwable e, Class<T> expectedClass) {
//        Throwable currentException = e;
//
//        while (currentException != null) {
//            if (expectedClass.isInstance(currentException)) {
//                return expectedClass.cast(currentException);
//            }
//
//            currentException = currentException.getCause();
//        }
//
//        return null;
//    }
//
//    public static void parseVerbose(String code, String grammarRule) {
//        if (grammarRule.isEmpty()) {
//            throw new RuntimeException(
//                    "Name of grammar rule is empty, please define it in the static field at the beginning of the class if test is to be executed");
//        }
//        var lexResult = TestUtils.lexical(code);
//        //CompilerAssertion.noErrors(code, grammarRule, lexResult.reports());
//        TestUtils.noErrors(lexResult.reports());
//
//        var result = TestUtils.parse(lexResult, grammarRule);

    /// /        CompilerAssertion.noErrors(code, grammarRule, result.reports());
//
//        TestUtils.noErrors(result.reports());
//
//        SpecsCheck.checkNotNull(result.rootNode(),
//                () -> "There were no error reports, but root node is null. Please add error reports when a problem happens.");
//
//        var testName = getTestName(Set.of("parseVerbose"));
//        System.out.println("Test " + testName + "\n");
//        System.out.println("Code:\n\n" + code + "\n");
//        System.out.println("AST:\n\n" + result.rootNode().toTree());
//        System.out.println("\n---------\n");
//    }
//
//    private static String getTestName(Collection<String> ignoreList) {
//
//        // Get stack trace
//        StackTraceElement[] stackTraceElements = Thread.currentThread().getStackTrace();
//
//        // This is usually the index for starting to search from the method that called this one
//        int currentIndex = 2;
//        String methodName = "";
//
//        while (methodName.isEmpty() || ignoreList.contains(methodName)) {
//            var callerMethod = stackTraceElements[currentIndex];
//            currentIndex++;
//            methodName = callerMethod.getMethodName();
//        }
//
//        return methodName;
//    }
//    public static void parseVerbose(String code) {
//        parseVerbose(code, TestUtils.getJmmParser().getDefaultRule());
//    }

}