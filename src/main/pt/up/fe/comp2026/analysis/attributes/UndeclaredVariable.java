package pt.up.fe.comp2026.analysis.attributes;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class UndeclaredVariable extends AnalysisVisitor {
    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {

        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {

        String methodName = method.get("name");


        currentMethod = table
                .getMethods(methodName)
                .stream()
                .findFirst()  // pega o primeiro método com esse nome
                .orElseThrow(() -> new RuntimeException(
                        "Método não encontrado na tabela de símbolos: " + methodName));

        visit(method, table);

        currentMethod = null;

        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        var varRefName = varRefExpr.get("name");

        if (currentMethod.getParameter(varRefName).isPresent())
            return null;
        if (currentMethod.getLocalVariable(varRefName).isPresent())
            return null;

        var message = String.format("Variable '%s' does not exist.", varRefName);
        addReport(Report.newError(Stage.SEMANTIC, NodeUtils.getLine(varRefExpr),
                NodeUtils.getColumn(varRefExpr), message, null)
        );
        return null;
    }

}
