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

    public JmmType getExprType(JmmNode expr) {
        return switch (expr.getKind()) {
            case INTEGER_LITERAL -> intType();
            case BOOLEAN_LITERAL -> JmmPrimitiveType.BOOLEAN;
            case THIS_EXPR -> JmmClassType.ofInstance(table.getFullyQualifiedName(), false);

            case BINARY_EXPR -> getBinExprType(expr);

            // FIX: ! deve devolver boolean, não o tipo do filho
            case UNARY_OP -> {
                String op = expr.get("op");
                yield op.equals("!") ? JmmPrimitiveType.BOOLEAN : intType();
            }
            // ++ e -- operam sobre ints
            case PREFIX_OP -> intType();

            case PRIORITY_EXPR -> getExprType(expr.getChild(0));

            case VAR_REF_EXPR -> getVarExprType(expr);

            case NEW_OBJECT_EXPR -> {
                String className = expr.get("name");
                var qualifiedName = table.getImportedFullyQualifiedName(className).orElse(className);
                yield JmmClassType.ofInstance(qualifiedName, false);
            }

            // NOVO
            case NEW_ARRAY_EXPR -> {
                String className = expr.get("name");
                var qualifiedName = table.getImportedFullyQualifiedName(className).orElse(className);
                yield new JmmArrayType(JmmClassType.ofInstance(qualifiedName, false), 1);
            }

            case NEW_INT_ARRAY_EXPR -> new JmmArrayType(JmmPrimitiveType.INT, 1);

            case ARRAY_LENGTH_EXPR -> intType();

            // NOVO: array[index] → tipo do elemento
            case ARRAY_ACCESS_EXPR -> {
                var arrayType = getExprType(expr.getChild(0));
                if (arrayType instanceof JmmArrayType arr) {
                    yield arr.itemType();
                }
                yield intType(); // fallback
            }

            // NOVO: chamada de metodo obj.method(...)
            case METHOD_CALL_EXPR -> getMethodCallType(expr);

            // NOVO: chamada implícita method(...) === this.method(...)
            case IMPLICIT_THIS_CALL_EXPR -> getImplicitThisCallType(expr);

            // NOVO: acesso a campo obj.field ou classe importada estática
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

    // FIX: cobre todos os operadores
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
            var signature = getMethodDeclSignature(methodNode);
            var method = table.getMethod(signature).orElse(null);
            if (method != null) {
                var param = method.getParameter(name);
                if (param.isPresent()) return param.get().type();

                var local = method.getLocalVariable(name);
                if (local.isPresent()) return local.get().type();
            }
        }

        var field = table.getField(name);
        if (field.isPresent()) return field.get().type();

        // Pode ser uma classe importada usada como receptor estático
        var importedName = table.getImportedFullyQualifiedName(name);
        if (importedName.isPresent()) {
            return JmmClassType.ofInstance(importedName.get(), true);
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
            // Se não conseguimos determinar o tipo do target, assumimos tipo desconhecido
            return JmmClassType.ofInstance("unknown", false);
        }

        if (targetType instanceof JmmClassType classType) {
            var typeName = classType.name();

            // Tenta na symbol table local (classe atual ou superclasse)
            var localMethods = table.getMethods(methodName);
            if (!localMethods.isEmpty()) {
                return localMethods.getFirst().returnType();
            }

            // Tenta numa classe importada
            var importedST = table.getImportedSymbolTable(typeName);
            if (importedST.isPresent()) {
                var methods = importedST.get().getMethods(methodName);
                if (!methods.isEmpty()) {
                    return methods.getFirst().returnType();
                }
            }
        }

        // Fallback para tipos não resolvidos (ex: métodos de classes externas)
        return JmmClassType.ofInstance("unknown", false);
    }

    private JmmType getImplicitThisCallType(JmmNode implicitCallExpr) {
        var methodName = implicitCallExpr.get("method");
        var methods = table.getMethods(methodName);
        if (!methods.isEmpty()) {
            return methods.getFirst().returnType();
        }
        return JmmClassType.ofInstance("unknown", false);
    }

    private JmmType getVarAccessType(JmmNode varAccess) {
        // target.field — não é chamada de método, é acesso a campo
        // Para já, retorna unknown — será refinado nos Calls
        return JmmClassType.ofInstance("unknown", false);
    }
}
