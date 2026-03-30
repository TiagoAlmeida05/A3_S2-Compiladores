package pt.up.fe.comp2026.analysis.attributes;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class VarInitCheckVisitor extends AnalysisVisitor {

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.VAR_DECL, this::visitVardDecl);
    }

    private Void visitVardDecl(JmmNode varDecl, SymbolTable table) {
        if (varDecl.getChildren().size() > 1){
            var typeUtils = TypeUtils.with(table);
            var typeNode = varDecl.getChild(0);
            var declaredType = typeUtils.convertType(typeNode);
            var exprNode = varDecl.getChild(1);

            try {
                var exprType = typeUtils.getExprType(exprNode);
                if (!exprType.equals(declaredType)) {
                    addReport(Report.newError(Stage.SEMANTIC,
                            NodeUtils.getLine(varDecl), NodeUtils.getColumn(varDecl),
                            "Type mismatch in initializer: cannot convert " + exprType.print() +
                                    " to " + declaredType.print(), null));
                }
            } catch (Exception e) {

            }
        }
        return null;
    }
}
