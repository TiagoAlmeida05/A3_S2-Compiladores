package pt.up.fe.comp2026.analysis;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp.jmm.analysis.table.reflection.Importer;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmKind;
import pt.up.fe.comp2026.symboltable.JmmSymbolTable;

import javax.swing.text.html.Option;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

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

        JmmType targetType;
        try {
            targetType = typeUtils.getExprType(target);
        } catch (Exception e) {
            return null;
        }

        if (!(targetType instanceof JmmClassType classType)) {
            return null;
        }

        String typeName = classType.name();
        boolean isThisClass = typeName.equals(table.getClassName());

        if (isThisClass) {
            var localMethods = table.getMethods(methodName);
            if (!localMethods.isEmpty()) {
                validateArguments(methodCall, localMethods.get(0).parameters(), typeUtils);
                return null;
            }

            String superName = table.getSuperFullyQualifiedName();
            if(superName != null){
                var superST = resolveExternalSymbolTable(jmmTable, superName);

                if(superST.isPresent()) {
                    var superMethods = superST.get().getMethods(methodName);
                    if(!superMethods.isEmpty()){
                        var matchedMethod = findMatchingOverload(methodCall, superMethods);
                        if(matchedMethod != null) {
                            validateArguments(methodCall, matchedMethod.parameters(), typeUtils);
                            return null;
                        } else {
                            addReport(Report.newError(Stage.SEMANTIC,
                                    NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                                    "No overload of inherited method '" + methodName + "' matches the provided arguments", null));
                            return null;
                        }
                    }
                }
            }

            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                    "Method '" + methodName + "' does not exist in the current class or its superclass", null));

        } else {
            String resolveName = table.getImportedFullyQualifiedName(typeName).orElse(typeName);
            var importedST = jmmTable.getImportedSymbolTable(resolveName);

            if(importedST.isPresent()) {
                var methods = importedST.get().getMethods(methodName);
                if(methods.isEmpty()) {
                    addReport(Report.newError(Stage.SEMANTIC,
                            NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                            "Method '" + methodName + "' not found in imported class '" + resolveName + "´", null));
                    return null;
                }

                var matchedMethod = findMatchingOverload(methodCall, methods);

                if (matchedMethod == null) {
                    addReport(Report.newError(Stage.SEMANTIC,
                            NodeUtils.getLine(methodCall), NodeUtils.getColumn(methodCall),
                            "No overload of method '" + methodName + "' matches the provided arguments", null));
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

        if (className.equals(table.getClassName()) || jmmTable.isImplicitImport(className)) return null;

        boolean isImported = table.getImports().stream()
                .anyMatch(i -> i.equals(className) || i.endsWith("." + className));

        if (!isImported) {
            addReport(Report.newError(Stage.SEMANTIC,
                    NodeUtils.getLine(newObject), NodeUtils.getColumn(newObject),
                    "Class '" + className + "' is not imported", null));
        }
        return null;
    }

    private void validateArguments(JmmNode methodCall, List<Symbol> parameters, TypeUtils typeUtils) {
        var argListNodes = methodCall.getChildren(ARG_LIST);
        var args = argListNodes.isEmpty() ? Collections.<JmmNode>emptyList() : argListNodes.get(0).getChildren();

        if(args.size() != parameters.size()) {
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

            if(!argType.equals(paramType)) {
                addReport(Report.newError(Stage.SEMANTIC,
                        NodeUtils.getLine(args.get(i)), NodeUtils.getColumn(args.get(i)),
                        "Type mismatch: arguments " + (i + 1) + " expects " + paramType.print() +
                        " but got " + argType.print(), null));
            }
        }
    }

    private Optional<SymbolTable> resolveExternalSymbolTable (JmmSymbolTable jmmTable, String className) {
        var importer = Importer.fromThisClassPath();

        var st = importer.getSymbolTableOf(className);
        if(st.isPresent()) return st;

        st = importer.tryImplicitImport(className);
        if(st.isPresent()) return st;

        var fqn = jmmTable.getImportedFullyQualifiedName(className);
        if(fqn.isPresent()){
            return importer.getSymbolTableOf(fqn.get());
        }
        return Optional.empty();
    }

    private MethodSymbol findMatchingOverload(JmmNode methodCall, List<MethodSymbol> methods){
        var argListNodes = methodCall.getChildren(ARG_LIST);
        var args = argListNodes.isEmpty() ? Collections.<JmmNode>emptyList() : argListNodes.get(0).getChildren();

        for (var method : methods) {
            if (method.parameters().size() == args.size()){
                return method;
            }
        }
        return null;
    }
}
