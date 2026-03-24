package pt.up.fe.comp.jmm.ast.antlr;

import java.util.List;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PostorderJmmVisitor;

public class JmmNodeCleanup extends PostorderJmmVisitor<Void, Void> {
    private final List<String> ignoreList;

    public JmmNodeCleanup(List<String> ignoreList) {
        this.ignoreList = ignoreList;
    }

    @Override
    public void buildVisitor() {
        setDefaultVisit((node, dummy) -> this.defaultVisit(node, dummy));
    }

    private Void defaultVisit(JmmNode node, Void dummy) {

        if (!this.ignoreList.contains(node.getKind())) {
            return null;
        }

        var parent = node.getParent();
        if (parent == null) {
            System.err.println(
                    "Warning: the root node cannot be used in the ingnore list. The AST will keep this node.");
            return null;
        }

        var children = node.getChildren();
        var position = parent.getChildren().indexOf(node);
        for (var child : children) {
            parent.add(child, position++);
        }
        node.delete();
        return null;
    }

}
