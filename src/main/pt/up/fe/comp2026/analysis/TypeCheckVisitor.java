package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;

public class TypeCheckVisitor extends AnalysisVisitor {

    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.BINARY_EXPR, this::visitBinaryExpr);
        addVisit(JmmKind.UNARY_OP, this::visitUnaryOp);
        addVisit(JmmKind.PREFIX_OP, this::visitPrefixOp);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var signature = TypeUtils.with(table).getMethodDeclSignature(method);
        currentMethod = table.getMethod(signature).orElse(null);
        return null;
    }

    private Void visitBinaryExpr(JmmNode binaryExpr, SymbolTable table) {
        if (currentMethod == null) return null;

        String op = binaryExpr.get("op");
        var typeUtils = TypeUtils.with(table);

        var left = binaryExpr.getChild(0);
        var right = binaryExpr.getChild(1);

        try {
            var leftType = typeUtils.getExprType(left);
            var rightType = typeUtils.getExprType(right);

            switch (op) {
                // Operadores aritméticos: ambos têm de ser int
                case "+", "-", "*", "/", "%" -> {
                    if (!leftType.equals(JmmPrimitiveType.INT)) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(left), NodeUtils.getColumn(left),
                                "Operator '" + op + "' expects int, but got " + leftType.print(), null));
                    }
                    if (!rightType.equals(JmmPrimitiveType.INT)) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(right), NodeUtils.getColumn(right),
                                "Operator '" + op + "' expects int, but got " + rightType.print(), null));
                    }
                }
                // Operadores relacionais: ambos têm de ser int
                case "<", ">", "<=", ">=" -> {
                    if (!leftType.equals(JmmPrimitiveType.INT)) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(left), NodeUtils.getColumn(left),
                                "Operator '" + op + "' expects int, but got " + leftType.print(), null));
                    }
                    if (!rightType.equals(JmmPrimitiveType.INT)) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(right), NodeUtils.getColumn(right),
                                "Operator '" + op + "' expects int, but got " + rightType.print(), null));
                    }
                }
                // Operadores lógicos: ambos têm de ser boolean
                case "&&", "||" -> {
                    if (!leftType.equals(JmmPrimitiveType.BOOLEAN)) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(left), NodeUtils.getColumn(left),
                                "Operator '" + op + "' expects boolean, but got " + leftType.print(), null));
                    }
                    if (!rightType.equals(JmmPrimitiveType.BOOLEAN)) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(right), NodeUtils.getColumn(right),
                                "Operator '" + op + "' expects boolean, but got " + rightType.print(), null));
                    }
                }
                case "==", "!=" -> {
                    if (!leftType.equals(JmmPrimitiveType.INT) && !leftType.equals(JmmPrimitiveType.BOOLEAN)) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(left), NodeUtils.getColumn(left),
                                "Operator '" + op + "' expects int or boolean, but got " + leftType.print(), null));
                    }
                    if (!rightType.equals(JmmPrimitiveType.INT) && !rightType.equals(JmmPrimitiveType.BOOLEAN)) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(right), NodeUtils.getColumn(right),
                                "Operator '" + op + "' expects int or boolean, but got " + rightType.print(), null));
                    }
                    if (leftType.equals(JmmPrimitiveType.INT) && rightType.equals(JmmPrimitiveType.BOOLEAN) ||
                            leftType.equals(JmmPrimitiveType.BOOLEAN) && rightType.equals(JmmPrimitiveType.INT)) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(binaryExpr), NodeUtils.getColumn(binaryExpr),
                                "Operator '" + op + "' requires both operands to have the same type", null));
                    }
                }
            }
        } catch (Exception e) {
            // Se não conseguimos determinar o tipo, ignoramos (vai ser apanhado por outro visitor)
        }

        return null;
    }

    private Void visitUnaryOp(JmmNode unaryOp, SymbolTable table) {
        if (currentMethod == null) return null;

        String op = unaryOp.get("op");
        var operand = unaryOp.getChild(0);
        var typeUtils = TypeUtils.with(table);

        try {
            var operandType = typeUtils.getExprType(operand);

            if (op.equals("!") && !operandType.equals(JmmPrimitiveType.BOOLEAN)) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(operand), NodeUtils.getColumn(operand),
                        "Operator '!' expects boolean, but got " + operandType.print(), null));
            }

            if ((op.equals("-") || op.equals("+")) && !operandType.equals(JmmPrimitiveType.INT)) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(operand), NodeUtils.getColumn(operand),
                        "Operator '" + op + "' expects int, but got " + operandType.print(), null));
            }
        } catch (Exception e) {
            // ignorar se tipo não for resolvível
        }

        return null;
    }

    private Void visitPrefixOp(JmmNode prefixOp, SymbolTable table) {
        if (currentMethod == null) return null;

        var operand = prefixOp.getChild(0);
        var typeUtils = TypeUtils.with(table);

        try {
            var operandType = typeUtils.getExprType(operand);
            if (!operandType.equals(JmmPrimitiveType.INT)) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(operand), NodeUtils.getColumn(operand),
                        "Operator '++/--' expects int, but got " + operandType.print(), null));
            }
        } catch (Exception e) {
            // ignorar se tipo não for resolvível
        }

        return null;
    }
}
