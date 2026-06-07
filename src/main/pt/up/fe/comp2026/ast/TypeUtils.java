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
        if (METHOD_TYPE.check(typeNode)) {
            return convertType(typeNode.getChild(0));
        }
        if (VOID_TYPE.check(typeNode)) {
            return JmmPrimitiveType.VOID;
        }
        if (ARRAY_TYPE.check(typeNode)) {
            return new JmmArrayType(convertType(typeNode.getChild(0)), 1);
        }
        if (INT_TYPE.check(typeNode)) {
            return JmmPrimitiveType.INT;
        }
        if (BOOLEAN_TYPE.check(typeNode)) {
            return JmmPrimitiveType.BOOLEAN;
        }

        var name = typeNode.get("name");
        var qualifiedName = table.getImportedFullyQualifiedName(name).orElse(name.equals(table.getClassName()) ? table.getFullyQualifiedName() : name);
        return JmmClassType.ofInstance(qualifiedName, false);
    }


    public JmmType getExprType(JmmNode expr) {
        return switch (expr.getKind()) {
            case INTEGER_LITERAL -> intType();
            case BOOLEAN_LITERAL -> JmmPrimitiveType.BOOLEAN;
            case THIS_EXPR -> JmmClassType.ofInstance(table.getFullyQualifiedName(), false);
            case BINARY_EXPR -> getBinExprType(expr);
            case UNARY_OP -> {
                String op = expr.get("op");
                yield op.equals("!") ? JmmPrimitiveType.BOOLEAN : intType();
            }
            case PREFIX_OP -> intType();
            case PRIORITY_EXPR -> getExprType(expr.getChild(0));
            case VAR_REF_EXPR -> getVarExprType(expr);
            case NEW_OBJECT_EXPR -> {
                String className = expr.get("name");
                var qualifiedName = table.getImportedFullyQualifiedName(className)
                        .orElse(className.equals(table.getClassName()) ? table.getFullyQualifiedName() : className);
                yield JmmClassType.ofInstance(qualifiedName, false);
            }
            case ARRAY_INIT_EXPR -> new JmmArrayType(JmmPrimitiveType.INT, 1);
            case NEW_INT_ARRAY_EXPR -> {
                System.out.println("NEW_INT_ARRAY children=" + expr.getNumChildren()
                        + " attrs=" + expr.getAttributes()
                        + " attrValues=" + expr.getAttributes().stream()
                        .map(a -> a + "=" + expr.get(a)).toList());
                int explicitDims = expr.getNumChildren(); // dimensões com tamanho
                int extraDims = 0;
                try {
                    extraDims = expr.getObjectAsList("extraBrackets", String.class).size();
                } catch (Exception ignored) {
                }
                int totalDims = explicitDims + extraDims;
                JmmType result = JmmPrimitiveType.INT;
                for (int i = 0; i < totalDims; i++) {
                    result = new JmmArrayType(result, 1);
                }
                yield result;
            }
            case NEW_ARRAY_EXPR -> {
                String className = expr.get("name");
                var qualifiedName = table.getImportedFullyQualifiedName(className)
                        .orElse(className.equals(table.getClassName()) ? table.getFullyQualifiedName() : className);
                int explicitDims = expr.getNumChildren();
                int extraDims = 0;
                try {
                    extraDims = expr.getObjectAsList("extraBrackets", String.class).size();
                } catch (Exception ignored) {
                }
                int totalDims = explicitDims + extraDims;
                JmmType result = JmmClassType.ofInstance(qualifiedName, false);
                for (int i = 0; i < totalDims; i++) {
                    result = new JmmArrayType(result, 1);
                }
                yield result;
            }
            case ARRAY_LENGTH_EXPR -> intType();
            case ARRAY_ACCESS_EXPR -> {
                var arrayType = getExprType(expr.getChild(0));
                if (arrayType instanceof JmmArrayType arr) {
                    if (arr.dimension() > 1) {
                        yield new JmmArrayType(arr.itemType(), arr.dimension() - 1);
                    }
                    yield arr.itemType();
                }
                yield intType();
            }
            case METHOD_CALL_EXPR -> getMethodCallType(expr);
            case IMPLICIT_THIS_CALL_EXPR -> getImplicitThisCallType(expr);
            case VAR_ACCESS -> getVarAccessType(expr);
            default -> throw new UnsupportedOperationException(
                    "Can't compute type for expression kind '" + expr.getKind() + "'");
        };
    }

    public Signature getMethodDeclSignature(JmmNode methodDecl) {
        METHOD_DECL.check(methodDecl);
        var methodName = methodDecl.get("name");

        var paramListNodes = methodDecl.getChildren(PARAM_LIST);
        var paramNodes = paramListNodes.isEmpty()
                ? methodDecl.getChildren(PARAM)
                : paramListNodes.getFirst().getChildren(PARAM);

        var params = paramNodes.stream()
                .map(param -> convertType(param.getChild(0)))
                .toList();

        return new Signature(methodName, params);
    }

    private JmmType getBinExprType(JmmNode binaryExpr) {
        String operator = binaryExpr.get("op");
        return switch (operator) {
            case "+", "-", "*", "/", "%" -> intType();
            case "<", ">", "<=", ">=", "==", "!=" -> JmmPrimitiveType.BOOLEAN;
            case "&&", "||" -> JmmPrimitiveType.BOOLEAN;
            default -> throw new RuntimeException("Unknown operator '" + operator + "'");
        };
    }

    private JmmType getVarExprType(JmmNode varRefExpr) {
        String name = varRefExpr.get("name");

        var methodNode = varRefExpr.getAncestor(METHOD_DECL).orElse(null);
        if (methodNode != null) {
            var methodName = methodNode.get("name");

            var method = table.getMethods().stream()
                    .filter(m -> m.name().equals(methodName))
                    .findFirst()
                    .orElse(null);
            if (method != null) {
                var param = method.getParameter(name);
                if (param.isPresent()) return param.get().type();

                var local = method.getLocalVariable(name);
                if (local.isPresent()) return local.get().type();
            }
        }

        var field = table.getField(name);
        if (field.isPresent()) return field.get().type();

        var importedName = table.getImportedFullyQualifiedName(name);
        if (importedName.isPresent()) {
            return JmmClassType.ofInstance(importedName.get(), true);
        }

        if (table.getImports().stream().anyMatch(imp ->
                imp.endsWith("." + name) || imp.equals(name))) {
            return JmmClassType.ofInstance(name, true);
        }

        if (name.equals(table.getClassName()) || table.getFullyQualifiedName().endsWith("." + name)) {
            return JmmClassType.ofInstance(table.getFullyQualifiedName(), true); // staticRef=true
        }

        throw new RuntimeException("Variable not found: " + name);
    }

    private JmmType getMethodCallType(JmmNode methodCallExpr) {
        var target = methodCallExpr.getChild(0);
        var methodName = methodCallExpr.get("method");

        JmmType targetType;
        try {
            targetType = getExprType(target);
        } catch (Exception e) {
            return JmmClassType.ofInstance("unknown", false);
        }

        if (targetType instanceof JmmClassType classType) {
            var typeName = classType.name();
            boolean isThisClass = typeName.equals(table.getClassName()) || typeName.equals(table.getFullyQualifiedName());

            if (isThisClass) {
                var localMethod = table.getMethods().stream()
                        .filter(m -> m.name().equals(methodName))
                        .findFirst();
                if (localMethod.isPresent()) {
                    return localMethod.get().returnType();
                }

                String superFQN = table.getSuperFullyQualifiedName();
                if (superFQN == null) superFQN = table.getSuper();

                if (superFQN != null) {
                    JmmType refType = getMethodReturnTypeViaReflection(superFQN, methodName);
                    if (refType != null) return refType;
                }
            } else {
                String fqn = table.getImportedFullyQualifiedName(typeName).orElse(typeName);
                JmmType refType = getMethodReturnTypeViaReflection(fqn, methodName);
                if (refType != null) return refType;
            }

            if (classType.staticRef() || table.getImports().stream()
                    .anyMatch(imp -> imp.equals(typeName) || imp.endsWith("." + typeName))) {
                return JmmClassType.ofInstance("unknown", true);
            }
        }

        return JmmClassType.ofInstance("unknown", false);
    }

    private JmmType getImplicitThisCallType(JmmNode implicitCallExpr) {
        var methodName = implicitCallExpr.get("method");

        var localMethod = table.getMethods().stream()
                .filter(m -> m.name().equals(methodName))
                .findFirst();
        if (localMethod.isPresent()) {
            return localMethod.get().returnType();
        }
        String superFQN = table.getSuperFullyQualifiedName();
        if (superFQN == null) superFQN = table.getSuper();

        if (superFQN != null) {
            JmmType refType = getMethodReturnTypeViaReflection(superFQN, methodName);
            if (refType != null) return refType;
        }

        return JmmClassType.ofInstance("unknown", false);
    }

    private JmmType getMethodReturnTypeViaReflection(String className, String methodName) {
        try {
            Class<?> clazz = Class.forName(className);
            for (java.lang.reflect.Method m : clazz.getMethods()) {
                if (m.getName().equals(methodName)) {
                    Class<?> ret = m.getReturnType();
                    if (ret == int.class) return JmmPrimitiveType.INT;
                    if (ret == boolean.class) return JmmPrimitiveType.BOOLEAN;
                    if (ret == void.class) return JmmPrimitiveType.VOID;
                    if (ret.isArray()) {
                        if (ret.getComponentType() == int.class) return new JmmArrayType(JmmPrimitiveType.INT, 1);
                        return new JmmArrayType(JmmClassType.ofInstance(ret.getComponentType().getName(), true), 1);
                    }
                    return JmmClassType.ofInstance(ret.getName(), true);
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private JmmType getVarAccessType(JmmNode varAccess) {
        return JmmClassType.ofInstance("unknown", false);
    }
}
