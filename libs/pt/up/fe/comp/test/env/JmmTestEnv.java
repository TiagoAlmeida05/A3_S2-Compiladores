package pt.up.fe.comp.test.env;

import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.Kind;
import pt.up.fe.comp.jmm.parser.JmmParserResult;

import java.io.File;
import java.util.Collections;
import java.util.Map;

/**
 * Test environment helper for JMM-related tests that builds on {@link CompilerTestEnv}.
 *
 * <p>This class provides convenience methods to parse code snippets and run the semantic
 * analysis pipeline using a configured resource base path. It delegates actual work to the
 * underlying {@link CompilerAssertion} instance exposed by {@link CompilerTestEnv} and captures
 * intermediate artifacts in the shared test history.</p>
 */
public class JmmTestEnv extends CompilerTestEnv {
    protected final String basePath;
    protected final String resourcesLocation;

    /**
     * Create a new JMM test environment that resolves resource files relative to the provided
     * base path.
     *
     * @param basePath base resource path to prepend to filenames (e.g. {@code "fixtures/"}).
     */
    public JmmTestEnv(String basePath, String resourcesLocation, Map<String, String> config) {
        super(config);
        this.basePath = basePath;
        this.resourcesLocation = resourcesLocation.endsWith("/") ? resourcesLocation : resourcesLocation + "/";
    }

    public JmmTestEnv(String basePath, String resourcesLocation) {
        this(basePath, resourcesLocation, Collections.emptyMap());
    }

    /**
     * Parse a code snippet starting from a specific parser rule. This method runs the lexer and
     * the parser and returns the produced {@link JmmParserResult}.
     *
     * @param code      JMM source code to parse
     * @param startRule parser starting rule name
     * @return parser result produced by the parser
     */
    public JmmParserResult parseSnippet(String code, String startRule) {
        var lex = super.lexCodeSnippet(code);
        return super.parse(lex, startRule);
    }

    public JmmParserResult parseSnippet(String code, Kind startRule) {
        var nodeName = startRule.getKey();
        return this.parseSnippet(code, nodeName.substring(0, 1).toLowerCase() + nodeName.substring(1));
    }


    public JmmParserResult parseSnippetWithErrors(String code, Kind startRule) {
        var nodeName = startRule.getKey();
        return this.parseSnippetWithErrors(code, nodeName.substring(0, 1).toLowerCase() + nodeName.substring(1));
    }

    public JmmParserResult parseSnippetWithErrors(String code, String startRule) {
        var lex = super.lexCodeSnippetNoCheck(code);
        if (lex.hasErrors())
            return new JmmParserResult(null, lex.reports(), lex.config());
        return super.parseWithErrors(lex, startRule);
    }


    public JmmParserResult parseSnippetWithErrors(String code) {
        var lex = super.lexCodeSnippetNoCheck(code);
        if (lex.hasErrors())
            return new JmmParserResult(null, lex.reports(), lex.config());
        return super.parseWithErrors(lex);
    }


    /**
     * Parse a code snippet using the parser's default starting rule. This method runs the lexer
     * and the parser and returns the produced {@link JmmParserResult}.
     *
     * @param code JMM source code to parse
     * @return parser result produced by the parser
     */
    public JmmParserResult parseSnippet(String code) {
        var lex = super.lexCodeSnippet(code);
        return super.parse(lex);
    }

    public JmmParserResult parseResource(String filename) {
        return this.parseResource(filename, this.basePath, this.resourcesLocation);
    }

    public JmmParserResult parseResource(String filename, String basePath, String resourcesLocation) {
        var resourcePath = basePath + filename;
        var lex = super.lexResource(resourcePath, resourcesLocation);
        return super.parse(lex);
    }

    public JmmParserResult parse(File file) {
        var lex = super.lex(file);
        return super.parse(lex);
    }

    /**
     * Build the symbol table for a resource file identified by {@code filename} resolved against
     * the environment's base path, and assert whether the step should produce errors.
     *
     * @param resourceBaseName resource filename relative to the environment base path
     * @param shouldFail       if {@code true}, the method asserts that symbol table construction
     *                         produced errors; otherwise it asserts no errors occurred
     * @return semantics result containing the symbol table and reports
     */
    public JmmSemanticsResult symbolTable(String resourceBaseName, boolean shouldFail) {
        return this.symbolTable(resourceBaseName, shouldFail, false);
    }

    public JmmSemanticsResult symbolTable(File jmmFile, boolean shouldFail) {
        return this.symbolTable(jmmFile, shouldFail, false);
    }

    public JmmSemanticsResult symbolTable(JmmParserResult parserResult, boolean shouldFail) {
        return this.symbolTable(parserResult, shouldFail, false);
    }

    public JmmSemanticsResult symbolTableFromSnippet(String code, boolean shouldFail) {
        return this.symbolTableFromSnippet(code, shouldFail, false);
    }

    public JmmSemanticsResult symbolTableFromSnippet(String code, boolean shouldFail, boolean continueIfDidntFail) {
        var parser = this.parseSnippet(code);
        return this.symbolTable(parser, shouldFail, continueIfDidntFail);
    }

    /**
     * Build the symbol table for a resource file identified by {@code filename} resolved against
     * the environment's base path. The {@code continueIfDidntFail} flag can be used to allow
     * continuation in tests when an expected failure did not happen.
     *
     * @param resourceBaseName    resource filename relative to the environment base path
     * @param shouldFail          if {@code true}, the method asserts that symbol table construction
     *                            produced errors; otherwise it asserts no errors occurred
     * @param continueIfDidntFail if {@code true} and {@code shouldFail} is {@code true},
     *                            the method returns the result if no errors occurred instead
     *                            of immediately failing the test
     * @return semantics result containing the symbol table and reports
     */
    private JmmSemanticsResult symbolTable(String resourceBaseName, boolean shouldFail, boolean continueIfDidntFail) {
        var parser = this.parseResource(resourceBaseName);
        return this.symbolTable(parser, shouldFail, continueIfDidntFail);
    }

    /**
     * Build the symbol table for a given Jmm file. The {@code continueIfDidntFail} flag can be used to allow
     *
     * @param file
     * @param shouldFail
     * @param continueIfDidntFail
     * @return
     */
    private JmmSemanticsResult symbolTable(File file, boolean shouldFail, boolean continueIfDidntFail) {
        var parser = this.parse(file);
        return this.symbolTable(parser, shouldFail, continueIfDidntFail);
    }

    private JmmSemanticsResult symbolTable(JmmParserResult parserResult, boolean shouldFail, boolean continueIfDidntFail) {
        var symbolTable = super.symbolTableNoCheck(parserResult);
        if (shouldFail) {
            if (!symbolTable.hasErrors() && continueIfDidntFail)
                return symbolTable;
            super.checkErrors("Symbol Table", symbolTable);
        } else {
            super.checkNoErrors("Symbol Table", symbolTable);
        }
        return symbolTable;
    }


    /**
     * Run semantic analysis for the given resource file and assert results according to
     * {@code shouldFail}.
     *
     * @param resourceBaseName resource resourceBaseName relative to the environment base path
     * @param shouldFail       if {@code true}, the method asserts that semantic analysis produced
     *                         errors; otherwise it asserts that no errors occurred
     * @return semantics result after running semantic analysis
     */
    public JmmSemanticsResult semantics(String resourceBaseName, boolean shouldFail) {
        var symbolTable = this.symbolTable(resourceBaseName, shouldFail, true);
        return semantics(symbolTable, shouldFail);
    }

    public JmmSemanticsResult semantics(File file, boolean shouldFail) {
        var symbolTable = this.symbolTable(file, shouldFail, true);
        return semantics(symbolTable, shouldFail);
    }

    public JmmSemanticsResult semanticsFromSnippet(String code, boolean shouldFail) {
        var symbolTable = this.symbolTableFromSnippet(code, shouldFail, true);
        return semantics(symbolTable, shouldFail);
    }

    public JmmSemanticsResult semantics(JmmSemanticsResult symbolTable, boolean shouldFail) {
        var semantics = super.analyseNoCheck(symbolTable);
        if (shouldFail) {
            super.checkErrors("Symbol Table", semantics);
        } else {
            super.checkNoErrors("Symbol Table", semantics);
        }
        return semantics;
    }


    public String qualifiedNameFor(String resourceName) {
        return resourcesPackage() + "." + resourceName;
    }

    public String resourcesPackage() {
        var resPack = (basePath == null) ? "" : basePath.replaceAll("/", ".");

        return resPack.endsWith(".") ? resPack.substring(0, resPack.length() - 1) : resPack;
    }
}