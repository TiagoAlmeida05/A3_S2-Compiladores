package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class ArrayCheckVisitor extends AnalysisVisitor {

    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.ARRAY_ACCESS_EXPR, this::visitArrayAccess);
        addVisit(JmmKind.ARRAY_LENGTH_EXPR, this::visitArrayLength);
        addVisit(JmmKind.ARRAY_ASSIGN_STMT, this::visitArrayAssign);
        addVisit(JmmKind.ARRAY_INIT_EXPR, this::visitArrayInit);
        addVisit(JmmKind.NEW_INT_ARRAY_EXPR, this::visitNewArrayExpr);
        addVisit(JmmKind.NEW_ARRAY_EXPR, this::visitNewArrayExpr);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var signature = TypeUtils.with(table).getMethodDeclSignature(method);
        currentMethod = table.getMethod(signature).orElse(null);
        return null;
    }

    private Void visitArrayAccess(JmmNode node, SymbolTable table) {
        if (currentMethod == null) return null;
        var typeUtils = TypeUtils.with(table);

        var target = node.getChild(0);
        var index = node.getChild(1);

        JmmType targetType, indexType;
        try {
            targetType = typeUtils.getExprType(target);
        } catch (Exception e) {
            return null;
        }
        try {
            indexType = typeUtils.getExprType(index);
        } catch (Exception e) {
            return null;
        }

        // target tem de ser array
        if (!(targetType instanceof JmmArrayType)) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(node), NodeUtils.getColumn(node),
                    "Array access on non-array type '" + targetType.print() + "'", null));
        }

        // índice tem de ser int
        if (!indexType.equals(JmmPrimitiveType.INT)) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(index), NodeUtils.getColumn(index),
                    "Array index must be int, but got '" + indexType.print() + "'", null));
        }

        return null;
    }

    private Void visitArrayLength(JmmNode node, SymbolTable table) {
        if (currentMethod == null) return null;
        var typeUtils = TypeUtils.with(table);

        var target = node.getChild(0);

        JmmType targetType;
        try {
            targetType = typeUtils.getExprType(target);
        } catch (Exception e) {
            return null;
        }

        if (!(targetType instanceof JmmArrayType)) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(node), NodeUtils.getColumn(node),
                    "Cannot access .length on non-array type '" + targetType.print() + "'", null));
        }

        return null;
    }

    private Void visitArrayInit(JmmNode node, SymbolTable table) {
        if (currentMethod == null) return null;
        var typeUtils = TypeUtils.with(table);

        // Os filhos são os elementos da lista: new int[]{e1, e2, e3}
        var elements = node.getChildren();

        JmmType expectedType = JmmPrimitiveType.INT;

        for (var element : elements) {
            JmmType elemType;
            try {
                elemType = typeUtils.getExprType(element);
            } catch (Exception e) {
                continue;
            }

            if (!elemType.equals(expectedType)) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(element), NodeUtils.getColumn(element),
                        "Array initializer element has wrong type: expected '" +
                                expectedType.print() + "' but got '" + elemType.print() + "'", null));
            }
        }

        return null;
    }

    // Verifica: L[i] = val — L deve ser array, i deve ser int, val deve ter tipo correto
    private Void visitArrayAssign(JmmNode node, SymbolTable table) {
        if (currentMethod == null) return null;
        var typeUtils = TypeUtils.with(table);

        var target = node.getChild(0); // o array
        var index = node.getChild(1); // o índice
        var value = node.getChild(2); // o valor a atribuir

        JmmType targetType, indexType, valueType;
        try {
            targetType = typeUtils.getExprType(target);
        } catch (Exception e) {
            return null;
        }
        try {
            indexType = typeUtils.getExprType(index);
        } catch (Exception e) {
            return null;
        }
        try {
            valueType = typeUtils.getExprType(value);
        } catch (Exception e) {
            return null;
        }

        if (!(targetType instanceof JmmArrayType arrayType)) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(node), NodeUtils.getColumn(node),
                    "Array store on non-array type '" + targetType.print() + "'", null));
            return null;
        }

        if (!indexType.equals(JmmPrimitiveType.INT)) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(index), NodeUtils.getColumn(index),
                    "Array index must be int, but got '" + indexType.print() + "'", null));
        }

        var elementType = arrayType.itemType();
        if (valueType instanceof JmmClassType vc && vc.name().equals("unknown")) return null;
        if (!valueType.equals(elementType)) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(value), NodeUtils.getColumn(value),
                    "Type mismatch in array store: expected '" + elementType.print() +
                            "' but got '" + valueType.print() + "'", null));
        }

        return null;
    }

    private Void visitNewArrayExpr(JmmNode node, SymbolTable table) {
        if (currentMethod == null) return null;
        var typeUtils = TypeUtils.with(table);

        int explicitDims = 0;
        for (var child : node.getChildren()) {
            JmmType sizeType;
            try {
                sizeType = typeUtils.getExprType(child);
            } catch (Exception e) {
                continue;
            }

            if (!sizeType.equals(JmmPrimitiveType.INT)) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(child), NodeUtils.getColumn(child),
                        "Array size must be of type int, but got '" + sizeType.print() + "'", null));
            } else {
                explicitDims++;
            }
        }

        int extraDims = 0;
        try {
            extraDims = node.getObjectAsList("extraBrackets", String.class).size();
        } catch (Exception ignored) {
        }

        int totalDims = explicitDims + extraDims;

        // Cria o tipo final usando o construtor correto
        JmmType arrayType = new JmmArrayType(JmmPrimitiveType.INT, totalDims);

        // Guarda o tipo no node
        node.put("exprType", String.valueOf(arrayType));

        return null;
    }
}