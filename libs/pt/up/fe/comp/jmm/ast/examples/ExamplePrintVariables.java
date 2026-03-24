package pt.up.fe.comp.jmm.ast.examples;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp.jmm.ast.Kind;

/**
 * Counts the occurrences of each node kind.
 * 
 * @author JBispo
 *
 */
public class ExamplePrintVariables extends PreorderJmmVisitor<Boolean, Boolean> {

    private final Kind varNodeKind;
    private final String varNameAttribute;
    private final String varLineAttribute;

    public ExamplePrintVariables(Kind varNodeKind, String varNameAttribute, String varLineAttribute) {
        this.varNodeKind = varNodeKind;
        this.varNameAttribute = varNameAttribute;
        this.varLineAttribute = varLineAttribute;
    }

    @Override
    public void buildVisitor() {
        addVisit(varNodeKind, this::printId); // Method reference
    }

    private Boolean printId(JmmNode node, Boolean dummy) {
        System.out.println(
                "Var '" + node.get(varNameAttribute) + "' in line " + node.get(varLineAttribute) + ", parent of "
                        + node.getParent().getKind());

        return true;
    }

}
