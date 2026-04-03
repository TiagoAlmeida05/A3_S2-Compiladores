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

import static pt.up.fe.comp2026.jmm.ast.JmmKind.RETURN_STMT;


public class StatementCheckVisitor extends AnalysisVisitor {

    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.IF_ELSE_STMT, this::visitIfElse);
        addVisit(JmmKind.WHILE_STMT, this::visitWhile);
        addVisit(JmmKind.RETURN_STMT, this::visitReturn);
        addVisit(JmmKind.DO_WHILE_STMT, this::visitDoWhile);
        addVisit(JmmKind.FOR_STMT, this::visitFor);
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

    private Void visitDoWhile(JmmNode doWhileStmt, SymbolTable table) {
        if (currentMethod == null) return null;
        var cond = doWhileStmt.getChild(1);
        checkBoolean(cond, table, "Do-while condition");
        return null;
    }

    private Void visitFor(JmmNode forStmt, SymbolTable table) {
        if (currentMethod == null) return null;
        
        for (var child : forStmt.getChildren()) {
            if (JmmKind.FOR_VAR_INIT.check(child) || JmmKind.FOR_ASSIGN_INIT.check(child)) continue;
            if (JmmKind.FOR_COMPOUND_ITER.check(child) || JmmKind.FOR_ASSIGN_ITER.check(child) || JmmKind.FOR_EXPR_ITER.check(child))
                continue;
            if (JmmKind.BLOCK_STMT.check(child) || JmmKind.IF_ELSE_STMT.check(child) || JmmKind.WHILE_STMT.check(child)
                    || JmmKind.DO_WHILE_STMT.check(child) || JmmKind.FOR_STMT.check(child)
                    || JmmKind.ASSIGN_STMT.check(child) || JmmKind.EXPR_STMT.check(child)
                    || JmmKind.RETURN_STMT.check(child) || JmmKind.RETURN_VOID_STMT.check(child)) continue;
            checkBoolean(child, table, "For condition");
            break;
        }

        // Verificar tipo do init
        for (var child : forStmt.getChildren()) {
            if (!JmmKind.FOR_VAR_INIT.check(child) && !JmmKind.FOR_ASSIGN_INIT.check(child)) continue;
            var varName = child.get("name");
            var valueNode = child.getChild(0);
            var leftType = getLocalType(varName);
            if (leftType == null) continue;
            try {
                var rightType = TypeUtils.with(table).getExprType(valueNode);
                if (!leftType.print().equals(rightType.print())) {
                    addReport(Report.newError(Stage.SEMANTIC,
                            NodeUtils.getLine(child), NodeUtils.getColumn(child),
                            "Incompatible types in for init: cannot assign " + rightType.print() + " to " + leftType.print(), null));
                }
            } catch (Exception ignored) {
            }
        }

        return null;
    }

    private JmmType getLocalType(String name) {
        if (currentMethod == null) return null;
        var param = currentMethod.getParameter(name);
        if (param.isPresent()) return param.get().type();
        var local = currentMethod.getLocalVariable(name);
        if (local.isPresent()) return local.get().type();
        return null;
    }
}
