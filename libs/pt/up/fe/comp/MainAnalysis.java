package pt.up.fe.comp;

import java.util.*;

import pt.up.fe.comp.jmm.analysis.JmmAnalysis;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.examples.ExamplePostorderVisitor;
import pt.up.fe.comp.jmm.ast.examples.ExamplePreorderVisitor;
import pt.up.fe.comp.jmm.ast.examples.ExampleVisitor;
import pt.up.fe.comp.jmm.ast.Kind;
import pt.up.fe.comp.jmm.lexer.JmmLexer;
import pt.up.fe.comp.jmm.lexer.JmmLexerResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.specs.util.SpecsIo;

public class MainAnalysis implements JmmAnalysis { // }, JmmOptimization, JasminBackend {

    public static void main(String[] args) {
        System.out.println("Executing with args: " + Arrays.toString(args));

        if (args[0].contains("fail")) {
            throw new RuntimeException("It's supposed to fail");
        }
        Map<String, String> config = Collections.emptyMap();
        var fileContents = SpecsIo.read(args[0]);
        System.out.println("Executing with input file: " + args[0]);
        String code = SpecsIo.read(args[0]);
        JmmLexerResult lexResult = TestUtils.getJmmLexer().lex(code, config);
        if (lexResult.hasErrors()) {
            throw new RuntimeException("Lexing errors found: " + lexResult.reports());
        }
        JmmParserResult parserResult = TestUtils.getJmmParser().parse(lexResult);

        // for CP2: symbol table generation and semantic analysis
        var analysis = new MainAnalysis();

        analysis.semanticAnalysis(parserResult);
    }

    private enum MKind implements Kind {
        IDENTIFIER("Identifier");
        private final String name;

        MKind(String name) {
            this.name = name;
        }

        @Override
        public String getKey() {
            return name;
        }

        @Override
        public Optional<Kind> extend() {
            return Optional.empty();
        }

        @Override
        public boolean isInstance(Kind ofThisKind) {
            return ofThisKind == IDENTIFIER;
        }
    }

    @Override
    public JmmSemanticsResult buildSymbolTable(JmmParserResult parserResult) {
        //TestUtils.getNumReports(parserResult.reports(), ReportType.ERROR) > 0
        if (parserResult.hasErrors()) {
            return null;
        }

        if (parserResult.rootNode() == null) {
            return null;
        }

        // JmmNode node = parserResult.getRootNode().sanitize();
        JmmNode node = parserResult.rootNode();

        System.out.println("VISITOR");
        ExampleVisitor visitor = new ExampleVisitor(MKind.IDENTIFIER, "id");
        System.out.println(visitor.visit(node, ""));

        System.out.println("PREORDER VISITOR");
        var preOrderVisitor = new ExamplePreorderVisitor(MKind.IDENTIFIER, "id");
        System.out.println(preOrderVisitor.visit(node, ""));

        System.out.println("POSTORDER VISITOR");
        var postOrderVisitor = new ExamplePostorderVisitor();
        var kindCount = new HashMap<String, Integer>();
        postOrderVisitor.visit(node, kindCount);
        System.out.println("Kinds count: " + kindCount);

        // No Symbol Table being calculated yet, no new reports
        return new JmmSemanticsResult(parserResult, null, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult semanticAnalysis(JmmSemanticsResult semanticsResult) {
        return semanticsResult;
    }

}