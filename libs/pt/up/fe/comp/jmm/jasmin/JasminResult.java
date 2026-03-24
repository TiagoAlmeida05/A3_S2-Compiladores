package pt.up.fe.comp.jmm.jasmin;

import pt.up.fe.comp.TestUtils;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.StageResult;
import pt.up.fe.specs.util.SpecsCollections;
import pt.up.fe.specs.util.SpecsIo;
import pt.up.fe.specs.util.SpecsSystem;
import pt.up.fe.specs.util.system.OutputType;
import pt.up.fe.specs.util.system.ProcessOutputAsString;
import pt.up.fe.specs.util.system.StreamToString;
import pt.up.fe.specs.util.utilities.StringLines;

import java.io.*;
import java.util.*;
import java.util.function.Consumer;

/**
 * A semantic analysis returns the analysed tree and the generated symbol table.
 */
public class JasminResult implements StageResult {

    private static Long HUMAN_DELAY_MS = 250l;
    private static Long TIMEOUT_NS = 5_000_000_000l;

    private final String className;
    private final String jasminCode;
    private final List<Report> reports;
    private final Map<String, String> config;

    public JasminResult(String className, String jasminCode, List<Report> reports, Map<String, String> config) {
        this.className = className;
        this.jasminCode = jasminCode;
        this.reports = reports;
        this.config = config;
    }

    public JasminResult(String className, String jasminCode, List<Report> reports) {
        this(className, jasminCode, reports, new HashMap<>());
    }

    public JasminResult(OllirResult ollirResult, String jasminCode, List<Report> reports) {
        this(ollirResult.getOllirClass().getPackage().replace(".", "/") + "/" + ollirResult.getOllirClass().getClassName(),
                jasminCode,
                SpecsCollections.concat(ollirResult.reports(), reports), ollirResult.config());
    }

    /**
     * Overload for groups of two elements that are directly generating Jasmin code from the AST.
     *
     * @param semanticsResult
     * @param jasminCode
     * @param reports
     */
    public JasminResult(JmmSemanticsResult semanticsResult, String jasminCode, List<Report> reports) {
        this(semanticsResult.getSymbolTable().packageName().replace(".", "/") + "/" +
                        semanticsResult.getSymbolTable().getClassName(),
                jasminCode,
                SpecsCollections.concat(semanticsResult.reports(), reports), semanticsResult.config());
    }

    public JasminResult(String jasminCode, Map<String, String> config) {
        this("DummyClass", jasminCode, new ArrayList<>(), config);
    }

    //
    public JasminResult(String jasminCode) {
        this(jasminCode, new HashMap<>());
    }

    public static JasminResult newError(String className, Report errorReport) {
        return new JasminResult(className, null, new ArrayList<>(Arrays.asList(errorReport)));
    }

    public String getClassName() {
        return className;
    }

    public String getJasminCode() {
        return this.jasminCode;
    }

    @Override
    public List<Report> reports() {
        return this.reports;
    }

    public Map<String, String> config() {
        return config;
    }

    /**
     * Compiles the generated Jasmin code using the Jasmin tool.
     *
     * @param outputDir the folder where the class file will written
     * @return a reference to the .class file
     */
    public File compile(File outputDir) {
//        File jasminFile = new File(SpecsIo.getTempFolder("jasmin"), getClassName() + ".j");
        File jasminFile = new File(outputDir + "/" + getClassName() + ".j");
        SpecsIo.write(jasminFile, getJasminCode());
        return JasminUtils.assemble(jasminFile, outputDir);
    }

    /**
     * Compiles the generated Jasmin code using the Jasmin tool.
     *
     * @return the compiled class file
     */
    public File compile() {
        File outputDir = SpecsIo.getTempFolder("jasmin");
//        File outputDir = new File("temp_jasmin");

        // Clean all class files in folder
        SpecsIo.deleteFolderContents(outputDir);
//        return compile(outputDir);
        compile(outputDir);
        return outputDir;
    }

//    /**
//     * Compiles and runs the current Jasmin code.
//     *
//     * @param args      arguments for the Jasmin program
//     * @param classpath additional paths for the classpath
//     * @param input     input to give to the program that will run
//     * @return the output that is printed by the Jasmin program
//     */
//    public String run(List<String> args, List<String> classpath, String input) {
//        return runWithFullOutput(args, classpath, input).getOutput();
//    }

    /**
     * Compiles and runs the current Jasmin code.
     *
     * @param args      arguments for the Jasmin program
     * @param classpath additional paths for the classpath
     * @param input     input to give to the program that will run
     * @return the output that is printed by the Jasmin program
     *///runWithFullOutput
    public ExecutionResult run(List<String> args, List<String> classpath, String input) {
        // Compile
        var workingDir = compile();
        var classpathArg = workingDir.getAbsolutePath();

        if (!classpath.isEmpty()) {
            var sep = File.pathSeparator;
            for (var classpathElement : classpath) {
                classpathArg += sep + classpathElement;
            }
        }
//        var classFile = compile();
//
//        var classpathArg = classFile.getParentFile().getAbsolutePath();
//        if (!classpath.isEmpty()) {
//            var sep = System.getProperty("path.separator");
//            for (var classpathElement : classpath) {
//                classpathArg += sep + classpathElement;
//            }
//        }
//        var classname = SpecsIo.removeExtension(classFile.getName());
        var classname = this.className;
        var command = new ArrayList<String>();
        command.add("java");
        command.add("-cp");
        command.add(classpathArg);
        command.add(classname);
        command.addAll(args);

        // Build process
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.directory(SpecsIo.getWorkingDir());
        Consumer<OutputStream> stdin = null;
        if (input != null && !input.isEmpty()) {
            stdin = outputStream -> {
                try (PrintWriter pw = new PrintWriter(new BufferedWriter(new OutputStreamWriter(outputStream)))) {
                    for (var line : StringLines.getLines(input)) {
                        // Simulate person typing (1s between each iteration)
                        SpecsSystem.sleep(HUMAN_DELAY_MS);
                        pw.println(line);
                        pw.flush();
                    }
                }

            };
        }

        var stdout = new StreamToString(true, true, OutputType.StdOut);
        var stderr = new StreamToString(true, true, OutputType.StdErr);

        var output = SpecsSystem.runProcess(builder, stdout, stderr, stdin, TIMEOUT_NS);

        // var output2 = SpecsSystem.runProcess(command, true, true);
        var processedOutput = new ProcessOutputAsString(output.getReturnValue(), output.getStdOut(),
                output.getStdErr());
        var execResult = new ExecutionResult(className, args, input, workingDir, Arrays.stream(classpathArg.split(File.pathSeparator)).toList(), processedOutput, config);
        return execResult;
    }

    public ExecutionResult run(List<String> args, List<String> classpath) {
        return run(args, classpath, null);
    }

    /**
     * Compiles and runs the current Jasmin code.
     *
     * @param args arguments for the Jasmin program
     * @return the output that is printed by the Jasmin program
     */
//    public ExecutionResult run(List<String> args) {
//        return run(args, List.of(TestUtils.getLibsClasspath()));
//    }
//
//    /**
//     * Compiles and runs the current Jasmin code.
//     *
//     * @return the output that is printed by the Jasmin program
//     */
//    public ExecutionResult run() {
//        return run(Collections.emptyList());
//    }
//
//    public ExecutionResult run(String input) {
//        return run(Collections.emptyList(), List.of(TestUtils.getLibsClasspath()), input);
//    }
//
//    public ExecutionResult run(List<String> args, String input) {
//        return run(args, Arrays.asList(TestUtils.getLibsClasspath()), input);
//    }


//    public ProcessOutputAsString runWithFullOutput(List<String> args, List<String> classpath) {
//        return runWithFullOutput(args, classpath, null);
//    }

    /**
     * Compiles and runs the current Jasmin code.
     *
     * @param args arguments for the Jasmin program
     * @return the output that is printed by the Jasmin program
     */
//    public ProcessOutputAsString runWithFullOutput(List<String> args) {
//        return runWithFullOutput(args, Arrays.asList(TestUtils.getLibsClasspath()));
//    }
//
//    /**
//     * Compiles and runs the current Jasmin code.
//     *
//     * @return the output that is printed by the Jasmin program
//     */
//    public ProcessOutputAsString runWithFullOutput() {
//        return runWithFullOutput(Collections.emptyList());
//    }
//
//    public ProcessOutputAsString runWithFullOutput(String input) {
//        return runWithFullOutput(Collections.emptyList(), Arrays.asList(TestUtils.getLibsClasspath()), input);
//    }
//
//    public ProcessOutputAsString runWithFullOutput(List<String> args, String input) {
//        return runWithFullOutput(args, Arrays.asList(TestUtils.getLibsClasspath()), input);
//    }
}