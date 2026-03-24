package pt.up.fe.comp.jmm.ast.antlr;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;
import pt.up.fe.comp.jmm.ast.Kind;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.ReportType;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.specs.util.SpecsSystem;

import java.util.*;
import java.util.function.Function;

public record AntlrParser(Function<String, ? extends Kind> stringToKind, Map<String, String> config) {

    /**
     * Creates a new converter to convert Antlr AST to JmmNode AST
     *
     * @param stringToKind a function that converts string into an instance of {@link Kind}
     * @param config       the configuration map
     */


    /**
     * Overloading method that uses an empty configuration map
     *
     * @param stringToKind a function that converts string into an instance of {@link Kind}
     */
    public AntlrParser(Function<String, ? extends Kind> stringToKind) {
        this(stringToKind, new HashMap<>());
    }

    /**
     * Parses the code using the given parser rule.
     *
     * @param lex
     * @param parser
     * @param ruleName
     * @param config
     * @return If there were no errors and a root node was generated, creates a JmmParserResult with the node, otherwise creates an error JmmParserResult without root node
     */
    /**
     *
     */
    public JmmParserResult parse(Parser parser, String ruleName) {

        parser.removeErrorListeners();
        var parserListener = new JmmErrorListener(Stage.SYNTATIC);
        parser.addErrorListener(parserListener);

        var node = (ParseTree) SpecsSystem.invoke(parser, ruleName);


        if (parserListener.hasErrors()) {
            return new JmmParserResult(null, parserListener.getReports(), this.config);
        }

        //possibly surround with try catch to catch exceptions during conversion
        var root = AntlrToJmmNodeConverter.convert(node, parser, stringToKind);

        return new JmmParserResult(root, parserListener.getReports(), config);
    }
}
