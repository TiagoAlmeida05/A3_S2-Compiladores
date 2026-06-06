package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.reflection.Importer;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.ARG_LIST;

public class CallCheckVisitor extends AnalysisVisitor {

    private MethodSymbol currentMethod;

    @Override
    public void buildVisitor() {
        addVisit(JmmKind.METHOD_DECL, this::visitMethodDecl);
        addVisit(JmmKind.METHOD_CALL_EXPR, this::visitMethodCall);
        addVisit(JmmKind.IMPLICIT_THIS_CALL_EXPR, this::visitImplicitThisCall);
        addVisit(JmmKind.NEW_OBJECT_EXPR, this::visitNewObject);
    }

    private MethodSymbol findMatchingOverload(JmmNode methodCall, List<MethodSymbol> methods, TypeUtils typeUtils) {
        var argListNodes = methodCall.getChildren(ARG_LIST);
        var args = argListNodes.isEmpty() ? Collections.<JmmNode>emptyList() : argListNodes.get(0).getChildren();

        // 1ª passagem: match exacto por número de args E tipos
        for (var method : methods) {
            if (method.parameters().size() != args.size()) continue;

            boolean allMatch = true;
            for (int i = 0; i < args.size(); i++) {
                try {
                    JmmType argType = typeUtils.getExprType(args.get(i));
                    if (!argType.equals(method.parameters().get(i).type())) {
                        allMatch = false;
                        break;
                    }
                } catch (Exception e) {
                    allMatch = false;
                    break;
                }
            }
            if (allMatch) return method;
        }
        for (var method : methods) {
            if (method.parameters().size() == args.size()) {
                return method;
            }
        }
        return null;
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

        JmmType targetType;
        try {
            targetType = typeUtils.getExprType(target);
        } catch (Exception e) {
            return null;
        }

        if (!(targetType instanceof JmmClassType classType)) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                    "Cannot call method '" + methodName + "' on a primitive or array type.", null));
            return null;
        }

        String typeName = classType.name();
        boolean isThisClass = typeName.equals(table.getClassName());

        if (isThisClass) {
            var localMethods = table.getMethods(methodName);
            var matchedLocal = findMatchingOverload(methodCall, localMethods, typeUtils);
            if (matchedLocal != null) {
                validateArguments(methodCall, matchedLocal.parameters(), typeUtils);
                return null;
            }

            String superName = table.getSuperFullyQualifiedName();
            if (superName != null) {
                var superST = resolveExternalSymbolTable(jmmTable, superName);
                if (superST.isPresent()) {
                    var superMethods = superST.get().getMethods(methodName);
                    if (!superMethods.isEmpty()) {
                        var matchedMethod = findMatchingOverload(methodCall, superMethods, typeUtils);
                        if (matchedMethod != null) {
                            validateArguments(methodCall, matchedMethod.parameters(), typeUtils);
                            return null;
                        } else {
                            addReport(Report.newError(Stage.SEMANTIC,
                                    NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                                    "No overload of inherited method '" + methodName + "' matches the provided arguments", null));
                            return null;
                        }
                    }
                } else {
                    return null;
                }
            }

            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                    "Method '" + methodName + "' does not exist in the current class or its superclass", null));

        } else {
            String resolveName = table.getImportedFullyQualifiedName(typeName).orElse(typeName);
            var importedST = resolveExternalSymbolTable(jmmTable, resolveName);

            if (importedST.isPresent()) {
                var methods = importedST.get().getMethods(methodName);

                if (methods.isEmpty()) {
                    if (resolveName.startsWith("java.") || resolveName.startsWith("pt.up.fe.")) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                                "Method '" + methodName + "' not found in imported class '" + resolveName + "'", null));
                    }
                    return null;
                }

                var matchedMethod = findMatchingOverload(methodCall, methods, typeUtils);

                if (matchedMethod == null) {
                    if (resolveName.startsWith("java.") || resolveName.startsWith("pt.up.fe.")) {
                        addReport(Report.newError(Stage.SEMANTIC,
                                NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                                "No overload of method '" + methodName + "' matches the provided arguments", null));
                    }
                    return null;
                }

                validateArguments(methodCall, matchedMethod.parameters(), typeUtils);
            }
        }
        return null;
    }

    private Void visitNewObject(JmmNode newObject, SymbolTable table) {
        var className = newObject.get("name");
        var jmmTable = (JmmSymbolTable) table;
        var typeUtils = TypeUtils.with(table);

        if (className.equals(table.getClassName()) || jmmTable.isImplicitImport(className)) return null;

        boolean isImported = table.getImports().stream()
                .anyMatch(i -> i.equals(className) || i.endsWith("." + className));

        if (!isImported) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(newObject), NodeUtils.getColumn(newObject),
                    "Class '" + className + "' is not imported", null));
            return null;
        }

        String resolveName = table.getImportedFullyQualifiedName(className).orElse(className);

        var importer = pt.up.fe.comp.jmm.analysis.table.reflection.Importer.fromThisClassPath();
        var importedST = importer.getSymbolTableOf(resolveName);
        if (importedST.isEmpty()) {
            importedST = importer.tryImplicitImport(resolveName);
        }

        if (importedST.isEmpty()) return null;

        var constructors = importedST.get().getMethods(className);
        if (constructors.isEmpty()) {
            constructors = importedST.get().getMethods("<init>");
        }

        if (constructors.isEmpty()) return null;

        var argListNodes = newObject.getChildren(ARG_LIST);
        var args = argListNodes.isEmpty() ? Collections.<JmmNode>emptyList() : argListNodes.get(0).getChildren();

        MethodSymbol matched = null;
        for (var constructor : constructors) {
            if (constructor.parameters().size() == args.size()) {
                matched = constructor;
                break;
            }
        }

        if (matched == null) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(newObject), NodeUtils.getColumn(newObject),
                    "Constructor of '" + className + "' expects different number of arguments", null));
            return null;
        }
        validateArguments(newObject, matched.parameters(), typeUtils);
        return null;
    }

    private Void visitImplicitThisCall(JmmNode methodCall, SymbolTable table) {
        if (currentMethod == null) return null;

        var methodName = methodCall.get("method");
        var jmmTable = (JmmSymbolTable) table;
        var typeUtils = TypeUtils.with(table);

        var localMethods = table.getMethods(methodName);
        var matchedLocal = findMatchingOverload(methodCall, localMethods, typeUtils);
        if (matchedLocal != null) {
            validateArguments(methodCall, matchedLocal.parameters(), typeUtils);
            return null;
        }

        String superName = table.getSuperFullyQualifiedName();
        if (superName != null) {
            var superST = resolveExternalSymbolTable(jmmTable, superName);
            if (superST.isPresent()) {
                var superMethods = superST.get().getMethods(methodName);
                var matchedSuper = findMatchingOverload(methodCall, superMethods, typeUtils);
                if (matchedSuper != null) {
                    validateArguments(methodCall, matchedSuper.parameters(), typeUtils);
                    return null;
                }
            } else {
                return null;
            }
        }

        if (localMethods.isEmpty()) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                    "Method '" + methodName + "' does not exist in the current class or its superclass", null));
        } else {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                    "No overload of method '" + methodName + "' matches the provided arguments", null));
        }

        return null;
    }

    private void validateArguments(JmmNode methodCall, List<Symbol> parameters, TypeUtils typeUtils) {
        var argListNodes = methodCall.getChildren(ARG_LIST);
        var args = argListNodes.isEmpty() ? Collections.<JmmNode>emptyList() : argListNodes.get(0).getChildren();

        if (args.size() != parameters.size()) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                    "Method expects " + parameters.size() + " arguments, but got" + args.size(), null));
            return;
        }

        for (int i = 0; i < args.size(); i++) {
            JmmType argType;
            try {
                argType = typeUtils.getExprType(args.get(i));
            } catch (Exception e) {
                continue;
            }

            var paramType = parameters.get(i).type();

            if (!argType.equals(paramType)) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(args.get(i)), NodeUtils.getColumn(args.get(i)),
                        "Type mismatch: arguments " + (i + 1) + " expects " + paramType.print() +
                                " but got " + argType.print(), null));
            }
        }
    }

    private Optional<SymbolTable> resolveExternalSymbolTable(JmmSymbolTable jmmTable, String className) {
        var importer = Importer.fromThisClassPath();

        var st = importer.getSymbolTableOf(className);
        if (st.isPresent()) return st;

        st = importer.tryImplicitImport(className);
        if (st.isPresent()) return st;

        var fqn = jmmTable.getImportedFullyQualifiedName(className);
        if (fqn.isPresent()) {
            return importer.getSymbolTableOf(fqn.get());
        }
        return Optional.empty();
    }
}
