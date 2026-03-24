package pt.up.fe.comp2026.ast;

import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;


/**
 * Utility methods regarding types.
 */
public class TypeUtils {


    private final JmmSymbolTable table;

    public TypeUtils(SymbolTable table) {
        this.table = (JmmSymbolTable) table;
    }

    public static TypeUtils with(SymbolTable table) {
        return new TypeUtils(table);
    }

    public static JmmPrimitiveType intType() {
        return JmmPrimitiveType.INT;
    }

    public JmmType convertType(JmmNode typeNode) {
        if (typeNode.getKind().equals("MethodType")) {
            return convertType(typeNode.getChild(0));
        }
        if (typeNode.getKind().equals("VoidType")) {
            return JmmPrimitiveType.VOID;
        }
        if (typeNode.getKind().equals("ArrayType")) {
            var baseType = convertType(typeNode.getChild(0));
            return new JmmArrayType(baseType, 1);
        }

        var name = typeNode.get("name");
        var primitive = JmmPrimitiveType.fromString(name);
        if (primitive.isPresent()) {
            return primitive.get();
        }

        var qualifiedName = table.getImportedFullyQualifiedName(name)
                .orElse(name.equals(table.getClassName()) ? table.getFullyQualifiedName() : name);

        return JmmClassType.ofInstance(qualifiedName, false);

    }


    /**
     * Gets the {@link JmmType} of an arbitrary expression.
     *
     * @param expr
     * @return
     */
    public JmmType getExprType(JmmNode expr) {
        return switch (expr.getKind()) {
            case INTEGER_LITERAL -> intType();
            case BINARY_EXPR -> getBinExprType(expr);
            case VAR_REF_EXPR -> getVarExprType(expr);
            case BOOLEAN_LITERAL -> JmmPrimitiveType.BOOLEAN;
            case THIS_EXPR -> JmmClassType.ofInstance(table.getFullyQualifiedName(), false);
            case NEW_OBJECT_EXPR -> {
                String className = expr.get("name");
                yield JmmClassType.ofInstance(className, false);
            }
            case NEW_INT_ARRAY_EXPR -> new JmmArrayType(JmmPrimitiveType.INT, 1);
            case UNARY_OP, PREFIX_OP -> getExprType(expr.getChild(0));
            case PRIORITY_EXPR -> getExprType(expr.getChild(0));

            case ARRAY_LENGTH_EXPR -> JmmPrimitiveType.INT;

            default ->
                    throw new UnsupportedOperationException("Can't compute type for expression kind '" + expr.getKind() + "'");
        };
    }

    public Signature getMethodDeclSignature(JmmNode methodDecl) {
        // Ensure given node is a MethodDecl
        METHOD_DECL.check(methodDecl);

        // Get name of the method
        var methodName = methodDecl.get("name");

        //System.out.println("[TODO] TypeUtils.getMethodDeclSignature(): Supporting only methods with a single parameter that is an int, needs to be expanded");
        var params = methodDecl.getChildren(PARAM).stream()
                .map(param -> convertType(param.getChild(0)))
                .toList();

        // Create method signature with method name and types of parameters
        return new Signature(methodName, params);
    }


    private JmmType getBinExprType(JmmNode binaryExpr) {

        // Get operator
        String operator = binaryExpr.get("op");

        return switch (operator) {
            case "+", "*" -> intType();
            case "<", "&&" -> JmmPrimitiveType.BOOLEAN;
            default ->
                    throw new RuntimeException("Unknown operator '" + operator + "' of expression '" + binaryExpr + "'");
        };
    }

    private JmmType getVarExprType(JmmNode varRefExpr) {
        System.out.println("[TODO] TypeUtils.getVarExprType(): Implement type inference for VarExpr. You will need to determine in which method the VarRef is and use the symbol table");

        String name = varRefExpr.get("name");

        var methodNode = varRefExpr.getAncestor(METHOD_DECL)
                .orElseThrow(() -> new RuntimeException("VarRef outside method"));

        var signature = getMethodDeclSignature(methodNode);
        var method = table.getMethod(signature)
                .orElseThrow(() -> new RuntimeException("Method not found"));

        var param = method.getParameter(name);
        if (param.isPresent()) {
            return param.get().type();
        }

        var local = method.getLocalVariable(name);
        if (local.isPresent()) {
            return local.get().type();
        }

        var field = table.getField(name);
        if (field.isPresent()) {
            return field.get().type();
        }

        throw new RuntimeException("Variable not found: " + name);
    }

}