package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.ast.TypeUtils;

import java.util.List;
import java.util.stream.Collectors;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends AJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;

    private pt.up.fe.comp.jmm.analysis.table.MethodSymbol currentMethod;


    public OllirExprGeneratorVisitor(SymbolTable table, OptUtils ollirTypes) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = ollirTypes;
        this.currentMethod = null;
    }

    public void setCurrentMethod(pt.up.fe.comp.jmm.analysis.table.MethodSymbol method) {
        this.currentMethod = method;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(BOOLEAN_LITERAL, this::visitBoolean);
        addVisit(THIS_EXPR, this::visitThis);
        addVisit(ARRAY_ACCESS_EXPR, this::visitArrayAccess);
        addVisit(ARRAY_LENGTH_EXPR, this::visitLength);
        addVisit(NEW_OBJECT_EXPR, this::visitNewObject);
        addVisit(NEW_INT_ARRAY_EXPR, this::visitNewArray);
        addVisit(METHOD_CALL_EXPR, this::visitMethodCall);
        addVisit(PRIORITY_EXPR, this::visitPriorityExpr);
        addVisit(UNARY_OP, this::visitUnaryOp);
        addVisit(ARRAY_INIT_EXPR, this::visitArrayInitExpr);

    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = TypeUtils.intType();
        String ollirIntType = ollirTypes.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBoolean(JmmNode node, Void unused) {
        String ollirType = ollirTypes.toOllirType(JmmPrimitiveType.BOOLEAN);
        String value = node.get("value").equals("true") ? "1" : "0";
        return new OllirExprResult(value + ollirType);
    }

    private OllirExprResult visitThis(JmmNode node, Void unused) {
        return new OllirExprResult("this." + table.getClassName());
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {
        var op = node.get("op");

        if (op.equals("&&") || op.equals("||")) {
            var lhs = visit(node.getChild(0));
            StringBuilder computation = new StringBuilder();
            computation.append(lhs.getComputation());

            String trueLabel = ollirTypes.nextTemp("true_lbl");
            String endLabel = ollirTypes.nextTemp("end_lbl");

            String resOllirType = ollirTypes.toOllirType(JmmPrimitiveType.BOOLEAN);
            String resVar = ollirTypes.nextTemp() + resOllirType;

            computation.append("if (").append(lhs.getCode()).append(") goto ").append(trueLabel).append(END_STMT);

            if (op.equals("&&")) {
                computation.append(resVar).append(SPACE).append(ASSIGN).append(resOllirType).append(" 0").append(resOllirType).append(END_STMT);
                computation.append("goto ").append(endLabel).append(END_STMT);

                computation.append(trueLabel).append(":\n");

                var rhs = visit(node.getChild(1));
                computation.append(rhs.getComputation());
                computation.append(resVar).append(SPACE).append(ASSIGN).append(resOllirType).append(SPACE).append(rhs.getCode()).append(END_STMT);
            } else {
                var rhs = visit(node.getChild(1));
                computation.append(rhs.getComputation());
                computation.append(resVar).append(SPACE).append(ASSIGN).append(resOllirType).append(SPACE).append(rhs.getCode()).append(END_STMT);
                computation.append("goto ").append(endLabel).append(END_STMT);

                computation.append(trueLabel).append(":\n");
                computation.append(resVar).append(SPACE).append(ASSIGN).append(resOllirType).append(" 1").append(resOllirType).append(END_STMT);
            }

            computation.append(endLabel).append(":\n");
            return new OllirExprResult(resVar, computation);
        }

        var lhs = visit(node.getChild(0));
        var rhs = visit(node.getChild(1));

        StringBuilder computation = new StringBuilder();

        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        JmmType resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String code = ollirTypes.nextTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        JmmType type = types.getExprType(node);
        computation.append(op).append(ollirTypes.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var name = node.get("name");
        var id = ollirTypes.sanitizeId(name);

        JmmType type = types.getExprType(node);
        String ollirType = ollirTypes.toOllirType(type);

        boolean isField = table.getField(name).isPresent();

        boolean isLocal = false;
        if (currentMethod != null) {
            isLocal = currentMethod.getParameter(name).isPresent()
                    || currentMethod.getLocalVariable(name).isPresent();
        }

        // If it's a field and NOT a local/param → use getfield
        if (isField && !isLocal) {

            String temp = ollirTypes.nextTemp() + ollirType;

            StringBuilder computation = new StringBuilder();

            computation.append(temp).append(SPACE)
                    .append(ASSIGN).append(ollirType).append(SPACE)
                    .append("getfield(this, ")
                    .append(id).append(ollirType)
                    .append(")").append(ollirType)
                    .append(END_STMT);

            return new OllirExprResult(temp, computation);
        }

        // Otherwise normal variable
        return new OllirExprResult(id + ollirType);
    }

    private OllirExprResult visitArrayAccess(JmmNode node, Void unused) {
        var arrayExpr = visit(node.getChild(0));
        var indexExpr = visit(node.getChild(1));

        JmmType resType = types.getExprType(node);
        String resOllirType = ollirTypes.toOllirType(resType);
        String tempVar = ollirTypes.nextTemp() + resOllirType;

        StringBuilder computation = new StringBuilder();
        computation.append(arrayExpr.getComputation());
        computation.append(indexExpr.getComputation());
        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(arrayExpr.getCode())
                .append("[").append(indexExpr.getCode()).append("]")
                .append(resOllirType)
                .append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitLength(JmmNode node, Void unused) {
        var arrayExpr = visit(node.getChild(0));

        String resOllirType = ollirTypes.toOllirType(TypeUtils.intType());
        String tempVar = ollirTypes.nextTemp() + resOllirType;

        StringBuilder computation = new StringBuilder();
        computation.append(arrayExpr.getComputation());
        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append("arraylength(").append(arrayExpr.getCode()).append(")")
                .append(resOllirType)
                .append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitNewObject(JmmNode node, Void unused) {
        String className = node.get("name");
        String ollirType = "." + className;
        String tempVar = ollirTypes.nextTemp() + ollirType;

        StringBuilder computation = new StringBuilder();
        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append("new(").append(className).append(")")
                .append(ollirType).append(END_STMT);
        computation.append("invokespecial(")
                .append(tempVar).append(", \"<init>\").V")
                .append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitNewArray(JmmNode node, Void unused) {
        // Collect all dimension size expressions (children of the node)
        List<JmmNode> dimNodes = node.getChildren();

        List<OllirExprResult> dimResults = dimNodes.stream().map(this::visit).toList();

        String elemOllirType = ollirTypes.toOllirType(TypeUtils.intType());

        // Build the array OLLIR type with correct number of dimensions
        // e.g. 2D → .array.array.i32
        int numDims = dimNodes.size();
        StringBuilder arrayOllirTypeSB = new StringBuilder();
        for (int i = 0; i < numDims; i++) {
            arrayOllirTypeSB.append(".array");
        }
        arrayOllirTypeSB.append(elemOllirType);
        String arrayOllirType = arrayOllirTypeSB.toString();

        String tempVar = ollirTypes.nextTemp() + arrayOllirType;

        StringBuilder computation = new StringBuilder();
        for (var dim : dimResults) {
            computation.append(dim.getComputation());
        }

        // new(array, size0.i32, size1.i32, ...).array.array.i32
        String argsCode = dimResults.stream()
                .map(OllirExprResult::getCode)
                .collect(Collectors.joining(", "));

        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(arrayOllirType).append(SPACE)
                .append("new(array, ").append(argsCode).append(")")
                .append(arrayOllirType).append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        var calleeNode = node.getChild(0);
        var calleeResult = visit(calleeNode);

        java.util.List<JmmNode> argNodes = new java.util.ArrayList<>();
        for (int i = 1; i < node.getNumChildren(); i++) {
            JmmNode child = node.getChild(i);
            if (child.getKind().toString().toUpperCase().contains("ARG_LIST")) {
                argNodes.addAll(child.getChildren());
            } else {
                argNodes.add(child);
            }

        }
        List<OllirExprResult> args = argNodes.stream().map(this::visit).toList();

        StringBuilder computation = new StringBuilder();
        computation.append(calleeResult.getComputation());
        for (var arg : args) computation.append(arg.getComputation());

        String methodName = node.get("method");
        JmmType retType = types.getExprType(node);
        String retOllirType = toSafeOllirType(retType);

        if (methodName.equals("read") || methodName.startsWith("get")) {
            retOllirType = ".i32";
        } else if (methodName.equals("print") || methodName.equals("println") || methodName.contains("Void")) {
            retOllirType = ".V";
        } else if (methodName.equals("self")) {
            retOllirType = ollirTypes.toOllirType(types.getExprType(calleeNode));
        }

        String invokeKind = resolveInvokeKind(calleeNode);

        String callerCode;
        if (invokeKind.equals("invokestatic")) {
            callerCode = ollirTypes.sanitizeId(calleeNode.get("name"));
        } else {
            callerCode = calleeResult.getCode();
        }

        String argsCode = args.stream()
                .map(OllirExprResult::getCode)
                .collect(Collectors.joining(", "));

        String callExpr = invokeKind
                + "(" + callerCode
                + ", \"" + methodName + "\""
                + (argsCode.isEmpty() ? "" : ", " + argsCode)
                + ")" + retOllirType;

        if (retOllirType.equals(".V")) {
            computation.append(callExpr).append(END_STMT);
            return new OllirExprResult("", computation);
        }

        String tempVar = ollirTypes.nextTemp() + retOllirType;
        computation.append(tempVar).append(SPACE)
                .append(ASSIGN).append(retOllirType).append(SPACE)
                .append(callExpr).append(END_STMT);

        return new OllirExprResult(tempVar, computation);
    }

    private String toSafeOllirType(JmmType type) {
        if (type instanceof JmmClassType classType) {
            String name = classType.name();
            String fqn = classType.fullyQualifiedName();

            if (name == null || name.isEmpty() || name.equals("unknown")) {
                return ".i32";
            }

            return "." + name;
        }
        return ollirTypes.toOllirType(type);
    }

    private String resolveInvokeKind(JmmNode calleeNode) {
        JmmType calleeType = types.getExprType(calleeNode);

        if (calleeType instanceof JmmClassType classType && classType.staticRef()) {
            return "invokestatic";
        }

        if (calleeNode.getKind().toString().toUpperCase().contains("VAR_REF")) {
            String varName = calleeNode.get("name");
            boolean isImportedClass = table.getImports().stream()
                    .anyMatch(imp -> imp.equals(varName) || imp.endsWith("." + varName));
            // Also check it's not a local variable or parameter
            boolean isLocalVar = false;
            if (currentMethod != null) {
                isLocalVar = currentMethod.getParameter(varName).isPresent()
                        || currentMethod.getLocalVariable(varName).isPresent();
            }
            if (isImportedClass && !isLocalVar) {
                return "invokestatic";
            }
        }

        return "invokevirtual";
    }

    private OllirExprResult visitUnaryOp(JmmNode node, Void unused) {

        var operand = visit(node.getChild(0));

        StringBuilder computation = new StringBuilder();
        computation.append(operand.getComputation());

        String ollirType = ollirTypes.toOllirType(JmmPrimitiveType.BOOLEAN);
        String temp = ollirTypes.nextTemp() + ollirType;

        computation.append(temp).append(SPACE)
                .append(ASSIGN).append(ollirType).append(SPACE)
                .append("!.bool ")
                .append(operand.getCode())
                .append(END_STMT);

        return new OllirExprResult(temp, computation);
    }

    private OllirExprResult visitPriorityExpr(JmmNode node, Void unused) {
        return visit(node.getChild(0));
    }

    private OllirExprResult visitArrayInitExpr(JmmNode node, Void unused) {
        List<JmmNode> elements = node.getChildren();
        int size = elements.size();

        String elemOllirType = ollirTypes.toOllirType(TypeUtils.intType());
        String arrayOllirType = ".array" + elemOllirType;
        String arrayVar = ollirTypes.nextTemp() + arrayOllirType;

        StringBuilder computation = new StringBuilder();

        // 1. Allocate the array: tmp0.array.i32 := new(array, 3.i32).array.i32;
        computation.append(arrayVar).append(SPACE)
                .append(ASSIGN).append(arrayOllirType).append(SPACE)
                .append("new(array, ").append(size).append(elemOllirType).append(")")
                .append(arrayOllirType).append(END_STMT);

        // 2. Store each element: tmp0.array.i32[0.i32].i32 := 10.i32;
        for (int i = 0; i < elements.size(); i++) {
            var elemResult = visit(elements.get(i));
            computation.append(elemResult.getComputation());
            computation.append(arrayVar)
                    .append("[").append(i).append(elemOllirType).append("]")
                    .append(elemOllirType)
                    .append(SPACE).append(ASSIGN).append(elemOllirType).append(SPACE)
                    .append(elemResult.getCode())
                    .append(END_STMT);
        }

        return new OllirExprResult(arrayVar, computation);
    }
}
