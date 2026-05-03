package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;

import java.util.stream.Collectors;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

/**
 * Generates OLLIR code from JmmNodes that are not expressions.
 */
public class OllirGeneratorVisitor extends AJmmVisitor<Void, String> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";
    private final String NL = "\n";
    private final String L_BRACKET = " {\n";
    private final String R_BRACKET = "}\n";


    private final SymbolTable table;

    private final TypeUtils types;
    private final OptUtils ollirTypes;


    private final OllirExprGeneratorVisitor exprVisitor;

    private MethodSymbol currentMethod;

    public OllirGeneratorVisitor(SymbolTable table) {
        this.table = table;
        this.types = new TypeUtils(table);
        this.ollirTypes = new OptUtils(types);
        exprVisitor = new OllirExprGeneratorVisitor(table, ollirTypes);
        currentMethod = null;
    }


    @Override
    protected void buildVisitor() {

        addVisit(PROGRAM, this::visitProgram);
        addVisit(PACKAGE_DECL, this::visitPackageDecl);
        addVisit(CLASS_DECL, this::visitClass);
        addVisit(VAR_DECL, this::simpleVarDecl);
        addVisit(PARAM, this::visitParam);
        addVisit(METHOD_DECL, this::visitMethodDecl);
        addVisit(RETURN_STMT, this::visitReturn);
        addVisit(ASSIGN_STMT, this::visitAssignStmt);
        addVisit(EXPR_STMT, this::visitExprStmt);
        addVisit(IF_ELSE_STMT, this::visitIfStmt);
        addVisit(WHILE_STMT, this::visitWhileStmt);
        addVisit(ARRAY_ASSIGN_STMT, this::visitArrayAssignStmt);
        addVisit(BLOCK_STMT, this::visitBlockStmt);
        addVisit(RETURN_VOID_STMT, this::visitReturnVoid);
//        setDefaultVisit(this::defaultVisit);
    }


    private String simpleVarDecl(JmmNode varDecl, Void unused) {
        return varDecl.get("name") + ollirTypes.toOllirType(varDecl.getObject("typeNode", JmmNode.class)) + ";";
    }

    private String visitParam(JmmNode varDecl, Void unused) {
        var name = ollirTypes.sanitizeId(varDecl.get("name"));
        return name + ollirTypes.toOllirType(varDecl.getObject("typeNode", JmmNode.class));
    }


    private String visitPackageDecl(JmmNode node, Void unused) {
        String fqn = table.getFullyQualifiedName();
        if (fqn != null && fqn.contains(".")) {
            String pkg = fqn.substring(0, fqn.lastIndexOf('.'));
            return "package " + pkg + ";\n\n";
        }
        return "";
    }


    private String visitAssignStmt(JmmNode node, Void unused) {

        var rhs = exprVisitor.visit(node.getChild(0));
        var varName = node.get(JmmAttributes.ASSIGN_STMT.VAR);

        JmmType lhsType = lookupVarType(varName);

        String typeString = ollirTypes.toOllirType(lhsType);

        boolean isField = false;
        if (currentMethod != null) {
            boolean isLocalOrParam = currentMethod.getLocalVariable(varName).isPresent() ||
                    currentMethod.getParameter(varName).isPresent();
            if (!isLocalOrParam) {
                isField = table.getField(varName).isPresent();
            }
        }

        var code = new StringBuilder();
        code.append(rhs.getComputation());

        if (isField) {
            code.append("putfield(this, ")
                    .append(ollirTypes.sanitizeId(varName)).append(typeString)
                    .append(", ")
                    .append(rhs.getCode())
                    .append(").V;\n");
        } else {
            var varCode = ollirTypes.sanitizeId(varName) + typeString;
            code.append(varCode).append(SPACE)
                    .append(ASSIGN).append(typeString).append(SPACE)
                    .append(rhs.getCode())
                    .append(END_STMT);
        }

        return code.toString();
    }

    private boolean isUnknownType(JmmType type) {
        if (type instanceof pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType ct) {
            String fqn = ct.fullyQualifiedName();
            return fqn == null || fqn.equals("unknown") || fqn.isEmpty();
        }
        return false;
    }


    private String visitArrayAssignStmt(JmmNode node, Void unused) {
        var targetNode = node.getChild(0);
        var indexNode = node.getChild(1);
        var valueNode = node.getChild(2);

        var indexResult = exprVisitor.visit(indexNode);
        var valueResult = exprVisitor.visit(valueNode);

        var code = new StringBuilder();
        code.append(indexResult.getComputation());
        code.append(valueResult.getComputation());

        String arrayCode;
        String elemTypeStr;

        if (targetNode.getKind().toString().toUpperCase().contains("VAR_REF_EXPR")) {
            var varName = targetNode.get("name");
            JmmType arrayType = lookupVarType(varName);
            JmmType elemType = (arrayType instanceof JmmArrayType arr)
                    ? arr.itemType() : TypeUtils.intType();

            String arrayTypeStr = ollirTypes.toOllirType(arrayType);
            elemTypeStr = ollirTypes.toOllirType(elemType);
            arrayCode = ollirTypes.sanitizeId(varName) + arrayTypeStr;
        } else {
            OllirExprResult innerResult = exprVisitor.visit(targetNode);
            code.append(innerResult.getComputation());

            JmmType intermediateType = types.getExprType(targetNode);
            JmmType elemType = (intermediateType instanceof JmmArrayType arr)
                    ? arr.itemType() : TypeUtils.intType();

            elemTypeStr = ollirTypes.toOllirType(elemType);
            arrayCode = innerResult.getCode();
        }

        code.append(arrayCode)
                .append("[").append(indexResult.getCode()).append("]")
                .append(elemTypeStr)
                .append(SPACE).append(ASSIGN).append(elemTypeStr).append(SPACE)
                .append(valueResult.getCode())
                .append(END_STMT);

        return code.toString();
    }

    private JmmType lookupVarType(String name) {
        if (currentMethod != null) {
            var param = currentMethod.getParameter(name);
            if (param.isPresent()) return param.get().type();

            var local = currentMethod.getLocalVariable(name);
            if (local.isPresent()) return local.get().type();
        }
        var field = table.getField(name);
        if (field.isPresent()) return field.get().type();
        throw new RuntimeException("Unknown variable: " + name);
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        var result = exprVisitor.visit(node.getChild(0));
        var code = new StringBuilder();
        code.append(result.getComputation());
        return code.toString();
    }


    private String visitReturn(JmmNode node, Void unused) {

        JmmType retType = currentMethod != null
                ? currentMethod.returnType()
                : TypeUtils.intType();
        var expr = exprVisitor.visit(node.getChild(0));


        StringBuilder code = new StringBuilder();

        code.append(expr.getComputation());
        code.append("ret");
        code.append(ollirTypes.toOllirType(retType));
        code.append(SPACE);

        code.append(expr.getCode());

        code.append(END_STMT);

        return code.toString();
    }

    private String visitIfStmt(JmmNode node, Void unused) {
        var condResult = exprVisitor.visit(node.getChild(0));

        String thenLabel = ollirTypes.nextTemp("then");
        String endifLabel = ollirTypes.nextTemp("endif");

        var code = new StringBuilder();

        code.append(condResult.getComputation());

        code.append("if (")
                .append(condResult.getCode())
                .append(") goto ")
                .append(thenLabel)
                .append(END_STMT);

        if (node.getNumChildren() >= 3) {
            code.append(visit(node.getChild(2)));
        }

        code.append("goto ").append(endifLabel).append(END_STMT);

        code.append(thenLabel).append(":\n");
        code.append(visit(node.getChild(1)));

        code.append(endifLabel).append(":\n");

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        String loopLabel = ollirTypes.nextTemp("loop");
        String bodyLabel = ollirTypes.nextTemp("body");
        String endLabel = ollirTypes.nextTemp("endloop");

        var condResult = exprVisitor.visit(node.getChild(0));

        var code = new StringBuilder();

        code.append(loopLabel).append(":\n");
        code.append(condResult.getComputation());

        code.append("if (").append(condResult.getCode()).append(") goto ").append(bodyLabel).append(END_STMT);

        code.append("goto ").append(endLabel).append(END_STMT);

        code.append(bodyLabel).append(":\n");
        code.append(visit(node.getChild(1)));

        code.append("goto ").append(loopLabel).append(END_STMT);

        code.append(endLabel).append(":\n");

        return code.toString();
    }

    private String visitMethodDecl(JmmNode node, Void unused) {

        // Build the lookup directly from the AST parameter nodes to correctly handle
        // overloaded methods. getMethodDeclSignature can misidentify overloads by reading
        // the "param" attribute of PARAM_LIST instead of iterating all PARAM children.
        String methodName = node.get("name");
        var paramListNodes = node.getChildren(PARAM_LIST);
        java.util.List<JmmNode> declaredParamNodes = paramListNodes.isEmpty()
                ? java.util.Collections.emptyList()
                : paramListNodes.get(0).getChildren(PARAM);
        int declaredParamCount = declaredParamNodes.size();
        java.util.List<String> declaredParamNames = declaredParamNodes.stream()
                .map(p -> p.get("name"))
                .toList();

        // Primary: match by name + count + param names (handles all overload cases)
        var methodOpt = table.getMethods().stream()
                .filter(m -> m.name().equals(methodName)
                        && m.parameters().size() == declaredParamCount)
                .filter(m -> {
                    var symParamNames = m.parameters().stream()
                            .map(p -> p.name())
                            .toList();
                    return declaredParamNames.isEmpty() || symParamNames.equals(declaredParamNames);
                })
                .findFirst();
        // Fallback: match by name + count only
        if (methodOpt.isEmpty()) {
            methodOpt = table.getMethods().stream()
                    .filter(m -> m.name().equals(methodName)
                            && m.parameters().size() == declaredParamCount)
                    .findFirst();
        }
        // Last resort: use getMethodDeclSignature
        if (methodOpt.isEmpty()) {
            Signature methodSig = TypeUtils.with(table).getMethodDeclSignature(node);
            methodOpt = table.getMethod(methodSig);
        }
        currentMethod = methodOpt.orElseThrow(() ->
                new RuntimeException("Could not find method: " + methodName));

        exprVisitor.setCurrentMethod(currentMethod);

        ollirTypes.resetTemporaries();

        StringBuilder code = new StringBuilder(".method ");

        code.append("public ");

        if (node.getObject("isStatic", Boolean.class)) {
            code.append("static ");
        }

        // name
        var name = ollirTypes.sanitizeId(node.get("name"));

        if (name.equals("main")) {
            code.append("main(args.array.String).V {\n");
        } else {
            code.append(name);

            // params

            var paramNodes = paramListNodes.isEmpty() ? java.util.Collections.<JmmNode>emptyList() : paramListNodes.get(0).getChildren(PARAM);
            String paramsCode = paramNodes.stream()
                    .map(this::visit)
                    .collect(Collectors.joining(", "));
            code.append("(").append(paramsCode).append(")");

            var retType = ollirTypes.toOllirType(currentMethod.returnType());
            code.append(retType);
            code.append(L_BRACKET);
        }

        var stmts = node.getChildren(STMT);
        if (!stmts.isEmpty()) {

            var stmtsCode = stmts.stream().map(this::visit).collect(Collectors.joining("\n   ", "   ", ""));
            code.append(stmtsCode);
        }

        // Append a fallback return if the method body doesn't end with a ret statement.
        // Required by OLLIR even for unreachable code (e.g. after while(true)).
        if (!methodBodyEndsWithRet(stmts)) {
            String retOllirType = ollirTypes.toOllirType(currentMethod.returnType());
            if (retOllirType.equals(".V")) {
                code.append("   ret.V;\n");
            } else {
                String dummyVal = retOllirType.equals(".i32") ? "0.i32"
                        : retOllirType.equals(".bool") ? "0.bool"
                        : "null" + retOllirType;
                code.append("   ret").append(retOllirType).append(" ").append(dummyVal).append(";\n");
            }
        }

        code.append(R_BRACKET);
        code.append(NL);

        currentMethod = null;

        return code.toString();
    }

    private boolean methodBodyEndsWithRet(java.util.List<JmmNode> stmts) {
        if (stmts.isEmpty()) return false;
        return nodeEndsWithRet(stmts.get(stmts.size() - 1));
    }

    private boolean nodeEndsWithRet(JmmNode node) {
        String kind = node.getKind().toString().toUpperCase();
        if (kind.contains("RETURN_STMT") || kind.contains("RETURN_VOID")) {
            return true;
        }
        if (kind.contains("BLOCK_STMT")) {
            var children = node.getChildren();
            if (!children.isEmpty()) {
                return nodeEndsWithRet(children.get(children.size() - 1));
            }
        }
        return false;
    }

    private String visitClass(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();
        code.append(NL);
        code.append("public ").append(table.getClassName());

        String superFqn = table.getSuperFullyQualifiedName();
        if (superFqn != null) {
            String superSimple = superFqn.contains(".")
                    ? superFqn.substring(superFqn.lastIndexOf('.') + 1)
                    : superFqn;
            code.append(" extends ").append(superSimple);
        }

        code.append(L_BRACKET);
        code.append(NL);

        for (var field : table.getFields()) {
            code.append(".field public ").append(field.name()).append(ollirTypes.toOllirType(field.type())).append(";\n");
        }

        code.append(NL);

        code.append(buildConstructor());
        code.append(NL);

        for (var child : node.getChildren(METHOD_DECL)) {
            var result = visit(child);
            code.append(result);
        }

        code.append(R_BRACKET);

        return code.toString();
    }

    private String buildConstructor() {

        String superFqn = table.getSuperFullyQualifiedName();

        String superName;
        if (superFqn == null) {
            superName = "Object";
        } else {
            superName = superFqn.contains(".")
                    ? superFqn.substring(superFqn.lastIndexOf('.') + 1)
                    : superFqn;
        }

        return """
                .construct ().V {
                    invokespecial(this.%s, "<init>").V;
                }
                """.formatted(superName);
    }

    private String visitProgram(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        for (JmmNode child : node.getChildren(PACKAGE_DECL)) {
            code.append(visit(child));
        }
        for (String imp : table.getImports()) {
            code.append("import ").append(imp).append(";\n");
        }
        for (JmmNode child : node.getChildren(CLASS_DECL)) {
            code.append(visit(child));
        }

        return code.toString();
    }

    private String visitBlockStmt(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        for (JmmNode child : node.getChildren()) {
            code.append(visit(child));
        }
        return code.toString();
    }

    private String visitReturnVoid(JmmNode node, Void unused) {
        return "ret.V;\n";
    }

    /**
     * Default visitor. Visits every child node and return an empty string.
     *
     * @param node
     * @param unused
     * @return
     */
    private String defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return "";
    }
}