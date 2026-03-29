package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class AssignmentCheckVisitor extends AnalysisVisitor {

    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.ASSIGN_STMT, this::visitAssignStmt);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var signature = TypeUtils.with(table).getMethodDeclSignature(method);
        currentMethod = table.getMethod(signature).orElse(null);
        return null;
    }

    private Void visitAssignStmt(JmmNode assignStmt, SymbolTable table) {
        if (currentMethod == null) return null;

        var varName = assignStmt.get("var");
        var valueExpr = assignStmt.getChild(0);

        JmmType leftType = getVarType(varName, table);
        if (leftType == null) return null;

        JmmType rightType;
        try {
            rightType = TypeUtils.with(table).getExprType(valueExpr);
        } catch (Exception e) {
            return null;
        }

        if (!isAssignable(leftType, rightType, table)) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(assignStmt), NodeUtils.getColumn(assignStmt),
                    "Incompatible types: cannot assign " + rightType.print() + " to " + leftType.print(),
                    null));
        }
        return null;
    }

    private JmmType getVarType(String name, SymbolTable table) {
        if (currentMethod != null) {
            var param = currentMethod.getParameter(name);
            if (param.isPresent()) return param.get().type();
            var local = currentMethod.getLocalVariable(name);
            if (local.isPresent()) return local.get().type();
        }
        return table.getField(name).map(s -> s.type()).orElse(null);
    }

    private boolean isAssignable(JmmType leftType, JmmType rightType, SymbolTable table) {
        if (rightType instanceof JmmClassType rc && rc.name().equals("unknown")) return true;

        if (leftType.equals(rightType)) return true;

        // Primitivos diferentes são sempre incompatíveis
        if (leftType.isPrimitive() || rightType.isPrimitive()) return false;

        if (!(leftType instanceof JmmClassType leftClass) || !(rightType instanceof JmmClassType rightClass))
            return false;

        String leftName = leftClass.name();
        String rightName = rightClass.name();
        if (leftName.equals(rightName)) return true;

        String currentFQN = table.getFullyQualifiedName();
        String currentSimple = table.getClassName();
        String superFQN = table.getSuperFullyQualifiedName();

        // Se o lado direito é 'this' (tipo da classe atual)
        if (rightName.equals(currentFQN) || rightName.equals(currentSimple)) {
            // Só é válido se o lado esquerdo é a própria classe ou a superclasse
            if (leftName.equals(currentFQN) || leftName.equals(currentSimple)) return true;
            if (superFQN != null) {
                String superSimple = superFQN.contains(".")
                        ? superFQN.substring(superFQN.lastIndexOf('.') + 1)
                        : superFQN;
                if (leftName.equals(superFQN) || leftName.equals(superSimple)) return true;
            }
            return false;
        }

        // Para classes externas (ex: java.lang), usa reflexão Java
        try {
            var leftCls = Class.forName(toFQN(leftName));
            var rightCls = Class.forName(toFQN(rightName));
            return leftCls.isAssignableFrom(rightCls);
        } catch (Exception e) {
            return true; // se não consegue verificar, é permissivo
        }
    }

    // Resolve nomes simples de java.lang (ex: "NullPointerException" → "java.lang.NullPointerException")
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
