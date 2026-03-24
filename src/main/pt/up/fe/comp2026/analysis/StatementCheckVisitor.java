package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;


public class StatementCheckVisitor extends AnalysisVisitor {

    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.IF_ELSE_STMT, this::visitIfElse);
        addVisit(JmmKind.WHILE_STMT, this::visitWhile);
        addVisit(JmmKind.RETURN_STMT, this::visitReturn);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var signature = TypeUtils.with(table).getMethodDeclSignature(method);
        currentMethod = table.getMethod(signature).orElse(null);
        if (currentMethod == null) return null;

        // Verifica que métodos não-void têm pelo menos um return
        if (!currentMethod.returnType().equals(JmmPrimitiveType.VOID)) {
            boolean hasReturn = method.getChildren().stream()
                    .anyMatch(c -> RETURN_STMT.check(c));

            // Verifica também dentro de blocos filhos
            if (!hasReturn) {
                hasReturn = !method.getDescendants(RETURN_STMT).isEmpty();
            }

            if (!hasReturn) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(method), NodeUtils.getColumn(method),
                        "Method '" + currentMethod.name() + "' must return a value of type "
                                + currentMethod.returnType().print(),
                        null));
            }
        }
        return null;
    }


    private Void visitIfElse(JmmNode ifElse, SymbolTable table) {
        if (currentMethod == null) return null;
        var cond = ifElse.getChild(0);
        checkBoolean(cond, table, "If condition");
        return null;
    }

    private Void visitWhile(JmmNode whileStmt, SymbolTable table) {
        if (currentMethod == null) return null;
        var cond = whileStmt.getChild(0);
        checkBoolean(cond, table, "While condition");
        return null;
    }

    private Void visitReturn(JmmNode returnStmt, SymbolTable table) {
        if (currentMethod == null) return null;

        var returnType = currentMethod.returnType();
        var valueExpr = returnStmt.getChild(0);

        JmmType exprType;
        try {
            exprType = TypeUtils.with(table).getExprType(valueExpr);
        } catch (Exception e) {
            return null;
        }

        if (!isCompatible(returnType, exprType, table)) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(returnStmt), NodeUtils.getColumn(returnStmt),
                    "Return type mismatch: expected " + returnType.print() + " but got " + exprType.print(),
                    null));
        }
        return null;
    }

    private void checkBoolean(JmmNode cond, SymbolTable table, String context) {
        try {
            var condType = TypeUtils.with(table).getExprType(cond);
            if (!condType.equals(JmmPrimitiveType.BOOLEAN)) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(cond), NodeUtils.getColumn(cond),
                        context + " must be boolean, but got " + condType.print(),
                        null));
            }
        } catch (Exception e) {
            // tipo não resolvível, ignora
        }
    }

    private boolean isCompatible(JmmType expected, JmmType actual, SymbolTable table) {
        if (expected.equals(actual)) return true;
        if (expected.isPrimitive() || actual.isPrimitive()) return false;

        // Verifica herança via reflexão (mesmo padrão do AssignmentCheckVisitor)
        try {
            var expectedCls = Class.forName(toFQN(expected.print()));
            var actualCls = Class.forName(toFQN(actual.print()));
            return expectedCls.isAssignableFrom(actualCls);
        } catch (Exception e) {
            return true;
        }
    }

    private String toFQN(String name) {
        if (name.contains(".")) return name;
        try {
            Class.forName("java.lang." + name);
            return "java.lang." + name;
        } catch (ClassNotFoundException e) {
            return name;
        }
    }
}
