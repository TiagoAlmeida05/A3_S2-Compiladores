package pt.up.fe.comp2026.analysis.attributes;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.analysis.AnalysisVisitor;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class UndeclaredVariable extends AnalysisVisitor {
    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.VAR_REF_EXPR, this::visitVarRefExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var signature = TypeUtils.with(table).getMethodDeclSignature(method);
        currentMethod = table.getMethod(signature).orElse(null);
        return null;
    }

    private Void visitVarRefExpr(JmmNode varRefExpr, SymbolTable table) {
        if (currentMethod == null) return null;

        var name = varRefExpr.get("name");
        if (currentMethod.getParameter(name).isPresent()) return null;
        if (currentMethod.getLocalVariable(name).isPresent()) return null;
        if (table.getField(name).isPresent()) {
            if (currentMethod.isStatic()) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(varRefExpr),
                        NodeUtils.getColumn(varRefExpr),
                        String.format("Cannot access instance field '%s' from a static method.", name),
                        null));
            }
            return null;
        }
        if (table.getImports().stream()
                .anyMatch(i -> i.equals(name) || i.endsWith("." + name))) return null;

        addReport(Report.newError(Stage.SEMANTIC,
                NodeUtils.getLine(varRefExpr),
                NodeUtils.getColumn(varRefExpr),
                String.format("Variable '%s' does not exist.", name),
                null));
        return null;
    }


}
