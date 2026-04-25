package pt.up.fe.comp2026.optimization;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.ast.AJmmVisitor;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.ast.NodeUtils;
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
//        setDefaultVisit(this::defaultVisit);
    }


    private String simpleVarDecl(JmmNode varDecl, Void unused) {
        return varDecl.get("name") + ollirTypes.toOllirType(varDecl.getObject("typeNode", JmmNode.class)) + ";";
    }

    private String visitParam(JmmNode varDecl, Void unused) {
        return varDecl.get("name") + ollirTypes.toOllirType(varDecl.getObject("typeNode", JmmNode.class));
    }


    private String visitPackageDecl(JmmNode packageDecl, Void unused) {
        return "package " + String.join(".", packageDecl.getObjectAsList("path", String.class)) + ";\n";
    }


    private String visitAssignStmt(JmmNode node, Void unused) {
        // TODO: Several hard-coded things, should be rewritten

        var rhs = exprVisitor.visit(node.getChild(0));

        // code to compute self
        // statement has type of lhs
        var varName = node.get(JmmAttributes.ASSIGN_STMT.VAR);

        JmmType thisType = types.getExprType(node.getChild(0));
        String typeString = ollirTypes.toOllirType(thisType);
        var varCode = ollirTypes.sanitizeId(varName) + typeString;


        var code = new StringBuilder();

        // code to compute the children
        code.append(rhs.getComputation());

        code.append(varCode);
        code.append(SPACE);

        code.append(ASSIGN);
        code.append(typeString);
        code.append(SPACE);

        code.append(rhs.getCode());

        code.append(END_STMT);

        return code.toString();
    }


    private String visitArrayAssignStmt(JmmNode node, Void unused) {
        var varName = node.get("name");

        // index expression (child 0) and value expression (child 1)
        var indexResult = exprVisitor.visit(node.getChild(0));
        var valueResult = exprVisitor.visit(node.getChild(1));

        // Resolve element type (array type -> item type)
        JmmType arrayType = types.getExprType(node.getChild(0));
        JmmType elemType = (arrayType instanceof JmmArrayType arr) ? arr.itemType() : TypeUtils.intType();
        String elemTypeStr = ollirTypes.toOllirType(elemType);

        var code = new StringBuilder();
        code.append(indexResult.getComputation());
        code.append(valueResult.getComputation());

        // arrayVar[index.i32].i32 :=.i32 value.i32;
        code.append(ollirTypes.sanitizeId(varName));
        code.append("[").append(indexResult.getCode()).append("]");
        code.append(elemTypeStr);
        code.append(SPACE).append(ASSIGN).append(elemTypeStr).append(SPACE);
        code.append(valueResult.getCode());
        code.append(END_STMT);

        return code.toString();
    }

    private String visitExprStmt(JmmNode node, Void unused) {
        var result = exprVisitor.visit(node.getChild(0));
        var code = new StringBuilder();
        code.append(result.getComputation());
        // If the expression itself is a bare invocation it ends with ;\n already,
        // otherwise we need to emit the code (e.g. a standalone temp assignment).
        if (!result.getCode().isEmpty()) {
            code.append(result.getCode()).append(END_STMT);
        }
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

        // Compute condition
        code.append(condResult.getComputation());

        // if (cond) goto then_N;
        code.append("if (")
                .append(condResult.getCode())
                .append(") goto ")
                .append(thenLabel)
                .append(END_STMT);

        // Else body (child 2 if present, otherwise empty)
        if (node.getNumChildren() >= 3) {
            code.append(visit(node.getChild(2)));
        }

        // goto endif_N;
        code.append("goto ").append(endifLabel).append(END_STMT);

        // then_N:
        code.append(thenLabel).append(":\n");
        code.append(visit(node.getChild(1)));

        // endif_N:
        code.append(endifLabel).append(":\n");

        return code.toString();
    }

    private String visitWhileStmt(JmmNode node, Void unused) {
        String loopLabel = ollirTypes.nextTemp("loop");
        String bodyLabel = ollirTypes.nextTemp("body");
        String endLabel = ollirTypes.nextTemp("endloop");

        var condResult = exprVisitor.visit(node.getChild(0));

        var code = new StringBuilder();

        // loop_label:
        code.append(loopLabel).append(":\n");
        code.append(condResult.getComputation());

        // if (cond) goto body_label;
        code.append("if (").append(condResult.getCode()).append(") goto ").append(bodyLabel).append(END_STMT);

        // goto end_label;
        code.append("goto ").append(endLabel).append(END_STMT);

        // body_label:
        code.append(bodyLabel).append(":\n");
        code.append(visit(node.getChild(1)));

        // goto loop_label;
        code.append("goto ").append(loopLabel).append(END_STMT);

        // end_label:
        code.append(endLabel).append(":\n");

        return code.toString();
    }

    private String visitMethodDecl(JmmNode node, Void unused) {

        currentMethod = table.getMethod(TypeUtils.with(table).getMethodDeclSignature(node)).orElseThrow();

        // Reset temporaries for each new method so names start from 0
        ollirTypes.resetTemporaries();

        StringBuilder code = new StringBuilder(".method ");

        boolean isPublic = NodeUtils.getBooleanAttribute(node, "isPublic", "false");

        if (isPublic) {
            code.append("public ");
        }

        if (node.getObject("isStatic", Boolean.class)) {
            code.append("static ");
        }

        // name
        var name = ollirTypes.sanitizeId(node.get("name"));
        code.append(name);

        // params
        // TODO: Hardcoded for a single parameter, needs to be expanded
        var paramNodes = node.getChildren(PARAM);
        String paramsCode = paramNodes.stream()
                .map(this::visit)
                .collect(Collectors.joining(", "));
        code.append("(").append(paramsCode).append(")");


        // type
        var retType = ollirTypes.toOllirType(currentMethod.returnType());//table.getReturnType(node.get("name")));
        code.append(retType);
        code.append(L_BRACKET);

        var stmts = node.getChildren(STMT);
        if (!stmts.isEmpty()) {
            // rest of its children stmts
            var stmtsCode = stmts.stream().map(this::visit).collect(Collectors.joining("\n   ", "   ", ""));
            code.append(stmtsCode);
        }

        code.append(R_BRACKET);
        code.append(NL);

        currentMethod = null;

        return code.toString();
    }

    private String visitClass(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        code.append(NL);
        code.append(table.getClassName());

        code.append(L_BRACKET);
        code.append(NL);
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
        return """
                .construct %s().V {
                    invokespecial(this, "<init>").V;
                }
                """.formatted(table.getClassName());
    }

    private String visitProgram(JmmNode node, Void unused) {

        StringBuilder code = new StringBuilder();

        node.getChildren().stream().map(this::visit).forEach(code::append);

        return code.toString();
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
