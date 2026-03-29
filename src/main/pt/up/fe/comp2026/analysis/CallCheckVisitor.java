package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp2026.ast.TypeUtils;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

public class CallCheckVisitor extends AnalysisVisitor {

    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.METHOD_CALL_EXPR, this::visitMethodCall);
        addVisit(JmmKind.NEW_OBJECT_EXPR, this::visitNewObject);
    }

    private Void visitMethodDecl(JmmNode method, SymbolTable table) {
        var signature = TypeUtils.with(table).getMethodDeclSignature(method);
        currentMethod = table.getMethod(signature).orElse(null);
        return null;
    }

    private Void visitMethodCall(JmmNode methodCall, SymbolTable table) {
        if (currentMethod == null) return null;

        var target = methodCall.getChild(0);
        var methodName = methodCall.get("method");
        var jmmTable = (JmmSymbolTable) table;

        var typeUtils = TypeUtils.with(table);
        var targetType = typeUtils.getExprType(target);

        if (!(targetType instanceof JmmClassType classType)) return null;

        String typeName = classType.name();
        boolean isThisClass = typeName.equals(table.getFullyQualifiedName())
                || typeName.equals(table.getClassName());

        if (isThisClass) {
            var localMethods = table.getMethods(methodName);
            if (localMethods.isEmpty()) {
                // Verifica na superclasse
                String superFQN = table.getSuperFullyQualifiedName();
                if (superFQN != null && methodExistsInClass(superFQN, methodName)) return null;
                // Verifica via symbol table da superclasse
                if (superFQN != null) {
                    var superST = jmmTable.getImportedSymbolTable(superFQN);
                    if (superST.isPresent() && !superST.get().getMethods(methodName).isEmpty()) return null;
                }
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                        "Method '" + methodName + "' does not exist in class", null));
                return null;
            }

            // Verifica número de argumentos
            var argListNodes = methodCall.getChildren(ARG_LIST);
            int argCount = argListNodes.isEmpty() ? 0 : argListNodes.getFirst().getChildren().size();
            var method = localMethods.getFirst();
            int paramCount = method.parameters().size();

            if (argCount != paramCount) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                        "Method '" + methodName + "' expects " + paramCount + " args but got " + argCount, null));
                return null;
            }

            // Verifica tipos dos argumentos
            if (!argListNodes.isEmpty()) {
                var args = argListNodes.getFirst().getChildren();
                var params = method.parameters();
                for (int i = 0; i < args.size(); i++) {
                    try {
                        var argType = typeUtils.getExprType(args.get(i));
                        var paramType = params.get(i).type();
                        if (!argType.equals(paramType)) {
                            addReport(Report.newError(Stage.SEMANTIC,
                                    NodeUtils.getLine(args.get(i)), NodeUtils.getColumn(args.get(i)),
                                    "Argument " + (i + 1) + " of '" + methodName + "' expects "
                                            + paramType.print() + " but got " + argType.print(), null));
                        }
                    } catch (Exception e) {
                        // tipo não resolvível, ignora
                    }
                }
            }
        } else {
            String resolvedName = table.getImportedFullyQualifiedName(typeName).orElse(typeName);

            var importedST = jmmTable.getImportedSymbolTable(resolvedName);
            if (importedST.isPresent()) {
                if (importedST.get().getMethods(methodName).isEmpty()) {
                    addReport(Report.newError(Stage.SEMANTIC,
                            NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                            "Method '" + methodName + "' does not exist in class '" + resolvedName + "'", null));
                }
            } else {
                // Verifica via reflexão — método existe, nº de args e tipos
                var argListNodes = methodCall.getChildren(ARG_LIST);
                int argCount = argListNodes.isEmpty() ? 0 : argListNodes.getFirst().getChildren().size();

                Class<?> cls = resolveClass(resolvedName);
                if (cls != null) {
                    // Filtra métodos com o nome correto
                    var candidates = java.util.Arrays.stream(cls.getMethods())
                            .filter(m -> m.getName().equals(methodName))
                            .toList();

                    if (candidates.isEmpty()) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                                "Method '" + methodName + "' does not exist in class '" + resolvedName + "'", null));
                        return null;
                    }

                    // Verifica se algum overload bate com o número de argumentos
                    boolean argCountOk = candidates.stream()
                            .anyMatch(m -> m.getParameterCount() == argCount);

                    if (!argCountOk) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                                "Method '" + methodName + "' does not accept " + argCount + " arguments", null));
                        return null;
                    }

                    // Verifica se algum overload bate com os tipos dos argumentos
                    if (!argListNodes.isEmpty()) {
                        var args = argListNodes.getFirst().getChildren();
                        boolean anyMatch = candidates.stream()
                                .filter(m -> m.getParameterCount() == argCount)
                                .anyMatch(m -> {
                                    var paramTypes = m.getParameterTypes();
                                    for (int i = 0; i < args.size(); i++) {
                                        try {
                                            var argType = typeUtils.getExprType(args.get(i));
                                            String paramName = paramTypes[i].getSimpleName();
                                            if (!jmmTypeMatchesJava(argType, paramName)) return false;
                                        } catch (Exception e) {
                                            return true; // não resolve, é permissivo
                                        }
                                    }
                                    return true;
                                });

                        if (!anyMatch) {
                            addReport(Report.newError(Stage.SEMANTIC,
                                    NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                                    "No matching overload of '" + methodName + "' for given argument types", null));
                        }
                    }
                }
            }
        }

        return null;
    }

    private Void visitNewObject(JmmNode newObject, SymbolTable table) {
        if (currentMethod == null) return null;

        var className = newObject.get("name");
        var jmmTable = (JmmSymbolTable) table;

        if (className.equals(table.getClassName())) return null;
        if (jmmTable.isImplicitImport(className)) return null;

        boolean isImported = table.getImports().stream()
                .anyMatch(i -> i.equals(className) || i.endsWith("." + className));

        if (!isImported) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(newObject), NodeUtils.getColumn(newObject),
                    "Class '" + className + "' is not imported", null));
        }

        return null;
    }

    // Verifica via reflexão se um metodo existe numa classe Java
    private boolean methodExistsInClass(String className, String methodName) {
        try {
            Class<?> cls = resolveClass(className);
            if (cls == null) return true; // não consegue verificar, permissivo
            for (var m : cls.getMethods()) {
                if (m.getName().equals(methodName)) return true;
            }
            return false; // classe encontrada mas método não existe → erro
        } catch (Exception e) {
            return true;
        }
    }

    private Class<?> resolveClass(String className) {
        try { return Class.forName(className); } catch (ClassNotFoundException ignored) {}
        try { return Class.forName("java.lang." + className); } catch (ClassNotFoundException ignored) {}
        try { return Class.forName("java.util." + className); } catch (ClassNotFoundException ignored) {}
        return null;
    }

    private boolean jmmTypeMatchesJava(pt.up.fe.comp.jmm.analysis.table.type.JmmType jmmType, String javaSimpleName) {
        return switch (javaSimpleName) {
            case "int", "Integer"     -> jmmType.equals(JmmPrimitiveType.INT);
            case "boolean", "Boolean" -> jmmType.equals(JmmPrimitiveType.BOOLEAN);
            case "void", "Void"       -> jmmType.equals(JmmPrimitiveType.VOID);
            default -> true; // tipos de objeto, permissivo
        };
    }


}
