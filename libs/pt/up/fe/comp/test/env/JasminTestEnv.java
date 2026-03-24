package pt.up.fe.comp.test.env;

import pt.up.fe.comp.jmm.jasmin.ExecutionResult;
import pt.up.fe.comp.jmm.jasmin.JasminResult;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.specs.util.SpecsStrings;
import pt.up.fe.specs.util.utilities.LineStream;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Pattern;

/**
 * Utilities to help test Jasmin code generation and execution in unit tests.
 *
 * <p>This class provides helpers to:
 * <ul>
 *   <li>obtain Jasmin output from OLLIR or JMM sources;</li>
 *   <li>execute generated Jasmin classes and optionally assert their standard output;</li>
 *   <li>assert and search patterns in produced Jasmin code using regular expressions;</li>
 *   <li>retrieve method snippets and bytecode indices from Jasmin text.</li>
 * </ul>
 *
 * <p>Most operations delegate to {@link pt.up.fe.comp.test.env.CompilerTestEnv}
 *  to run compilation stages and assert pipeline results. Methods in this class
 * append test failures using the underlying assertion utilities.</p>
 */
public class JasminTestEnv extends OllirTestEnv {

    /**
     * Regular expression matching conditional branch Jasmin instructions with a following label.
     * Example matches: {@code if_icmpeq _1}, {@code ifeq 10}
     */
    public static final String IF_REGEX = "((if_icmpeq|if_icmpne|if_icmplt|if_icmpge|if_icmpgt|if_icmple|ifeq|ifne|iflt|ifge|ifgt|ifle)\\s+\\w+)";

    /**
     * Regular expression matching goto Jasmin instructions with a following label.
     * Example matches: {@code goto _2}
     */
    public static final String GOTO_REGEX = "(goto\\s+\\w+)";

    /**
     * Prefix used to build a field declaration regular expression.
     */
    static final String FIELD_PREFIX = "\\.field\\s+((public|private)\\s+)?(')?";

    /**
     * Suffix used to build a field declaration regular expression.
     */
    static final String FIELD_SUFFIX = "(')?\\s+";
    private List<String> classPaths;

    /**
     * Create a new {@code JasminTestEnv} that resolves resources relative to {@code basePath}.
     *
     * @param basePath base resource path (e.g. {@code "fixtures/jasmin/"})
     */
    public JasminTestEnv(String basePath, String resourcesLocation, Map<String, String> config) {
        super(basePath, resourcesLocation, config);
        this.classPaths = new ArrayList<>();
    }

    public JasminTestEnv(String basePath, String resourcesLocation) {
        this(basePath, resourcesLocation, Collections.emptyMap());
    }

    public void addClassPath(String classPath) {
        this.classPaths.add(classPath);
    }

    public void addClassPaths(List<String> classPaths) {
        this.classPaths.addAll(classPaths);
    }

    public void clearClassPaths() {
        this.classPaths.clear();
    }

    /**
     * Produce a {@link JasminResult} by converting an OLLIR resource file to Jasmin.
     *
     * @param ollirResourceBaseName resource  ollir filename relative to the environment base path
     * @return generated {@link JasminResult}
     */
    public JasminResult getJasminFromOllir(String ollirResourceBaseName) {
        var ollir = super.ollirResource(basePath + ollirResourceBaseName, resourcesLocation);
        return super.jasmin(ollir);
    }

    public JasminResult getJasminFromOllir(File ollirFile) {
        var ollir = super.ollir(ollirFile);
        return super.jasmin(ollir);
    }


    /**
     * Produce a {@link JasminResult} by compiling a JMM resource file through the full pipeline.
     *
     * @param jmmBaseName resource filename relative to the environment base path
     * @return generated {@link JasminResult}
     */
    public JasminResult getJasminFromJmm(String jmmBaseName) {
        var ollir = super.jmmToOllir(jmmBaseName, true);
        return super.jasmin(ollir);
    }

    public JasminResult getJasminFromJmm(File file) {
        var ollir = super.jmmToOllir(file, true);
        return super.jasmin(ollir);
    }

    /**
     * Execute Jasmin produced from an OLLIR resource and return execution details.
     *
     * @param ollirBaseName resource filename relative to the environment base path
     * @return execution result
     */
    public ExecutionResult executeFromOllir(String ollirBaseName) {
        return executeFromOllir(ollirBaseName, null);
    }

    public ExecutionResult executeFromOllir(File ollirFile) {
        return executeFromOllir(ollirFile, null);
    }


    /**
     * Execute Jasmin produced from an OLLIR resource and optionally assert its stdout.
     *
     * @param ollirBaseName  resource filename relative to the environment base path
     * @param expectedOutput expected normalized stdout (or {@code null} to skip assertion)
     * @return execution result
     */
    public ExecutionResult executeFromOllir(String ollirBaseName, String expectedOutput) {
        var jasminResult = getJasminFromOllir(ollirBaseName);
        return execute(jasminResult, expectedOutput);
    }

    public ExecutionResult executeFromOllir(File ollirFile, String expectedOutput) {
        var jasminResult = getJasminFromOllir(ollirFile);
        return execute(jasminResult, expectedOutput);
    }

    /**
     * Execute Jasmin produced from a JMM resource and return execution details.
     *
     * @param filename resource filename relative to the environment base path
     * @return execution result
     */
    public ExecutionResult executeFromJmm(String filename) {
        return executeFromJmm(filename, null);
    }


    public ExecutionResult executeFromJmm(File file) {
        return executeFromJmm(file, null);
    }

    /**
     * Execute Jasmin produced from a JMM resource and optionally assert its stdout.
     *
     * @param filename       resource filename relative to the environment base path
     * @param expectedOutput expected normalized stdout (or {@code null} to skip assertion)
     * @return execution result
     */
    public ExecutionResult executeFromJmm(String filename, String expectedOutput) {
        var jasminResult = getJasminFromJmm(filename);
        return execute(jasminResult, expectedOutput);
    }

    public ExecutionResult executeFromJmm(File file, String expectedOutput) {
        var jasminResult = getJasminFromJmm(file);
        return execute(jasminResult, expectedOutput);
    }

    /**
     * Execute the given {@link JasminResult} and return execution details.
     *
     * @param jasminResult Jasmin result to execute
     * @return execution result
     */
    public ExecutionResult execute(JasminResult jasminResult) {
        return execute(jasminResult, null);
    }

    /**
     * Execute the given {@link JasminResult} and optionally assert its stdout.
     *
     * @param jasminResult   Jasmin result to execute
     * @param expectedOutput expected normalized stdout (or {@code null} to skip assertion)
     * @return execution result
     */
    public ExecutionResult execute(JasminResult jasminResult, String expectedOutput) {
        var result = runJasmin(jasminResult, classPaths);
        if (expectedOutput != null) {
            var output = SpecsStrings.normalizeFileContents(result.getStdout(), true);
            assertEquals("Not the expected execution outcome.", expectedOutput, output);
        }
        return result;
    }

//
//    public static void testOllirToJasmin(String resource, String expectedOutput) {
//        SpecsCheck.checkArgument(resource.endsWith(".ollir"), () -> "Expected resource to end with .ollir: " + resource);
//        System.out.println("=====================================================");
//        System.out.println("Testing OLLIR to Jasmin (execution) for resource: " + resource);
//        var ollirResult = new OllirResult(SpecsIo.getResource(resource), Collections.emptyMap());
//
//        var result = TestUtils.backend(ollirResult);
//        System.out.println("Generated Jasmin code:\n" + result.getJasminCode());
//        ProjectTestUtils.runJasmin(result, null);
//        System.out.println("Finished testing OLLIR to Jasmin for resource: " + resource);
//        System.out.println("=====================================================\n");
//    }

    /**
     * Verifies if the given code matches (contains) the given regex.
     *
     * @param description human-friendly description used in failure messages
     * @param result      jasmin result to check
     * @param regex       regular expression to match (string form)
     */
    public void matches(String description, JasminResult result, String regex) {
        matches(description, result, regex, false);
    }

    /**
     * Verifies if the given Jasmin result matches the provided regex, optionally inverting the assertion.
     *
     * @param description    human-friendly description used in failure messages
     * @param result         jasmin result to check
     * @param regex          regular expression to match (string form)
     * @param shouldNotMatch if {@code true} assert the regex does NOT match
     */
    public void matches(String description, JasminResult result, String regex, boolean shouldNotMatch) {
        // Add beginning of the line
        regex = "^(\\s)*" + regex;
        matches(description, result, Pattern.compile(regex, Pattern.MULTILINE), shouldNotMatch);
    }

    /**
     * Verify if the given Jasmin result matches the compiled regex.
     *
     * @param description human-friendly description used in failure messages
     * @param result      jasmin result to check
     * @param regex       compiled pattern to match
     */
    public void matches(String description, JasminResult result, Pattern regex) {
        matches(description, result, regex, false);
    }

    /**
     * Verify if the given Jasmin result matches the compiled regex, optionally inverting the assertion.
     *
     * @param description    human-friendly description used in failure messages
     * @param result         jasmin result to check
     * @param regex          compiled pattern to match
     * @param shouldNotMatch if {@code true} assert the regex does NOT match
     */
    public void matches(String description, JasminResult result, Pattern regex, boolean shouldNotMatch) {
        var matches = SpecsStrings.matches(cleanJasmin(result.getJasminCode()), regex);
        if (description == null || description.isEmpty()) {
            description = "code";
        }
        if (shouldNotMatch) {
            assertTrue("Expected " + description + " to NOT match /" + regex + "/", !matches);
        } else {
            assertTrue("Expected " + description + " to match /" + regex + "/", matches);
        }

    }


    /**
     * Verify if the provided Jasmin code matches {@code regex}.
     *
     * @param description human-friendly description used in failure messages
     * @param jasminCode  jasmin source text to check
     * @param regex       regular expression (string)
     */
    public void matches(String description, String jasminCode, String regex) {
        matches(description, jasminCode, Pattern.compile(regex));
    }

    /**
     * Verify if the provided Jasmin code matches {@code regex}.
     *
     * @param description human-friendly description used in failure messages
     * @param jasminCode  jasmin source text to check
     * @param regex       compiled pattern to match
     */
    public void matches(String description, String jasminCode, Pattern regex) {
        var matches = SpecsStrings.matches(cleanJasmin(jasminCode), regex);
        if (description == null || description.isEmpty()) {
            description = "code";
        }
        assertTrue("Expected " + description + " to match /" + regex + "/", matches);
    }

    /**
     * Assert a field declaration exists in the Jasmin result with the given name and type.
     *
     * @param jasminResult jasmin result to inspect
     * @param fieldName    field name (regex)
     * @param fieldType    field type string (e.g. {@code I}, {@code Ljava/lang/String;})
     */
    public void jasminHasField(JasminResult jasminResult, String fieldName, String fieldType) {
        var regex = FIELD_PREFIX + fieldName + FIELD_SUFFIX + fieldType;
        matches("a field", jasminResult, regex);
    }

    /**
     * Assert a field declaration exists in the Jasmin result with any name and the given type.
     *
     * @param jasminResult jasmin result to inspect
     * @param fieldType    field type string
     */
    public void jasminHasField(JasminResult jasminResult, String fieldType) {
        jasminHasField(jasminResult, "\\w+", fieldType);
    }

    /**
     * Extract the Jasmin text corresponding to a method named {@code methodName}.
     *
     * @param jasminResult jasmin result to search
     * @param methodName   name or regex for the method (if {@code null} any method is matched)
     * @return the matched method text block
     */
    public String getJasminMethod(JasminResult jasminResult, String methodName) {
        var code = cleanJasmin(jasminResult.getJasminCode());

        if (methodName == null) {
            methodName = "\\w+";
        }

        var regex = "\\.method\\s+((public|private)\\s)?+" + methodName + "((.)+?)\\.end\\s+method";
        // var regex = "\\.method\\s+((public|private)\\s+)?" + methodName + "((.|\\s)+?)\\.end\\s+method";

        var results = SpecsStrings.getRegex(code, regex);

        assertTrue("Could not find method '" + methodName + "'", !results.isEmpty());

        return results.get(2);
    }

    /**
     * Extract the first method Jasmin text block from the provided result.
     *
     * @param jasminResult jasmin result to inspect
     * @return the matched method text block
     */
    public String getJasminMethod(JasminResult jasminResult) {
        return getJasminMethod(jasminResult, null);
    }

    /**
     * Find the bytecode index for an instruction starting with {@code instructionPrefix} inside the Jasmin code.
     *
     * @param instructionPrefix instruction text prefix to search for
     * @param jasminCode        jasmin source text to search in
     * @return parsed bytecode index
     */
    public Integer getBytecodeIndex(String instructionPrefix, String jasminCode) {
        try (var lines = LineStream.newInstance(cleanJasmin(jasminCode))) {

            while (lines.hasNextLine()) {
                var line = lines.nextLine().strip();

                if (!line.startsWith(instructionPrefix)) {
                    continue;
                }

                var substring = line.substring(instructionPrefix.length()).strip();

                if (substring.startsWith("_")) {
                    substring = substring.substring(1);
                }

                return Integer.parseInt(substring);
            }

            throw new RuntimeException("Could not find instruction with prefix " + instructionPrefix);
            // fail("Could not find instruction with prefix " + instructionPrefix + " in code:\n\n" + jasminCode);
        } catch (Exception e) {
            fail("getBytecodeIndex('" + instructionPrefix + "'): " + e.getMessage() + "\n\n" + jasminCode);
            // throw new RuntimeException(
            // "Exception while looking for instruction " + instructionPrefix + " in code:\n\n" + jasminCode);
        }

        return null;
    }


    private final Pattern LIMIT_LOCALS = Pattern.compile("\\.limit\\s+locals\\s+([0-9]+)\\s+");

    private final Pattern LIMIT_STACK = Pattern.compile("\\.limit\\s+stack\\s+([0-9]+)\\s+");

    /**
     * @return compiled pattern matching Jasmin locals limit directive
     */
    public Pattern getLimitLocalsRegex() {
        return LIMIT_LOCALS;
    }

    /**
     * @return compiled pattern matching Jasmin stack limit directive
     */
    public Pattern getLimitStackRegex() {
        return LIMIT_STACK;
    }

    /**
     * Build a regex that matches local load/store instructions for the given number of locals.
     *
     * @param numLocals number of local variables expected
     * @return regex string matching local load/store instruction for the last local index
     */
    public String getLocalsRegex(int numLocals) {
        return "(a|i)(store|load)(_|\\s+)" + (numLocals - 1);
    }

    /**
     * Count occurrences of a literal word in the Jasmin code.
     *
     * @param jasminResult Jasmin result to inspect
     * @param word         literal substring to count
     * @return number of occurrences found
     */
    public int countOccurences(JasminResult jasminResult, String word) {
        String code = jasminResult.getJasminCode();
        return (code.length() - code.replace(word, "").length()) / word.length();
    }

    /**
     * Count occurrences matching a regex in the Jasmin code.
     *
     * @param jasminResult Jasmin result to inspect
     * @param regexString  regular expression string to search
     * @return number of matching occurrences
     */
    public int countOccurrencesRegex(JasminResult jasminResult, String regexString) {

        String jasminCode = jasminResult.getJasminCode();
        var matches = SpecsStrings.getRegexGroups(jasminCode, regexString, 0);

        return matches.size();
    }


    /**
     * Return a cleaned version of Jasmin code suitable for regex matching.
     *
     * <p>Currently returns the input unchanged. Commented code kept for future cleaning logic.</p>
     *
     * @param jasminCode original Jasmin source
     * @return cleaned Jasmin source (currently identical to input)
     */
    public static String cleanJasmin(String jasminCode) {
        return jasminCode;
        // Disabled; this might interfere with code such as LFoo;
        /*
        // Remove comments form Jasmin
        var code = new StringBuilder();

        for (var line : StringLines.getLines(jasminCode)) {
            var index = line.indexOf(';');

            if (index == -1) {
                code.append(line).append("\n");
                continue;
            }

            // Remove comment
            code.append(line.substring(0, index)).append("\n");
        }

        return code.toString();
         */
    }
}