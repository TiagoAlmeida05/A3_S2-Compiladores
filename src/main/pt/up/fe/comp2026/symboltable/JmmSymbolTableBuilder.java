package pt.up.fe.comp2026.symboltable;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.Visibility;
import pt.up.fe.comp.jmm.analysis.table.reflection.Importer;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.Stage;
import pt.up.fe.comp2026.ast.NodeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.*;

import static pt.up.fe.comp2026.jmm.ast.JmmKind.*;

public class JmmSymbolTableBuilder {

    private final JmmNode root;
    private final Importer importer;
    public String className;
    private final List<Report> reports;
    private final List<String> imports;
    private final Map<String, String> declaredClasses;

    /**
     * Only build() can create new instances, this ensures that each instance is used only once,
     * and we do not have to worry about "cleaning state".
     */
    private JmmSymbolTableBuilder(JmmNode root) {
        this.root = root;
        reports = new ArrayList<>();
        imports = new ArrayList<>();
        declaredClasses = new HashMap<>();
        this.importer = Importer.fromThisClassPath();
    }

    private static Report newError(JmmNode node, String message) {
        return Report.newError(
                Stage.SEMANTIC,
                NodeUtils.getLine(node),
                NodeUtils.getColumn(node),
                message,
                null);
    }

    public static SymbolTableBuilderResult build(JmmNode root) {
        return new JmmSymbolTableBuilder(root).buildInternal();
    }

    private SymbolTableBuilderResult buildInternal() {
        var packageDecls = root.getChildren(PACKAGE_DECL);
        if (packageDecls.size() > 1) {
            reports.add(newError(packageDecls.get(1), "Found more than one package declaration"));
        }

        String packagePath = "";
        if (!packageDecls.isEmpty()) {
            var packageDecl = packageDecls.getFirst();
            var qnDecl = packageDecl.getChild(0);
            var packagePathList = qnDecl.getObjectAsList("parts", String.class);
            packagePath = String.join(".", packagePathList);
        }

        for (var imp : root.getChildren(IMPORT_DECL)) {
            var qN = imp.getChild(0); // qualifiedName
            var importPathList = qN.getObjectAsList("parts", String.class);

            if (importPathList.size() == 1) {
                reports.add(newError(imp, "Cannot import class without a package: " + importPathList.get(0)));
                continue;
            }

            var importPath = String.join(".", importPathList);

            if (!imports.contains(importPath)) {
                imports.add(importPath);
            }

            if ((importPath.startsWith("java.") || importPath.startsWith("pt.up.fe."))
                    && importer.tryClassOf(importPath).isEmpty()) {
                reports.add(newError(imp,
                        "Imported class does not exist: " + importPath));
            }
        }

        var classDecls = root.getChildren(CLASS_DECL);
        SpecsCheck.checkArgument(!classDecls.isEmpty(), () -> "Expected at least one class declaration.");

        var classDecl = classDecls.get(0);

        for (int i = 1; i < classDecls.size(); i++) {
            String extraClassName = classDecls.get(i).get("name");
            if (!imports.contains(extraClassName)) {
                imports.add(extraClassName);
            }
        }

        this.className = classDecl.get("name");
        var fullyQualifiedName = packagePath.isEmpty() ? className : packagePath + "." + className;

        for (String importPath : imports) {
            String importedSimpleName = importPath.contains(".")
                    ? importPath.substring(importPath.lastIndexOf('.') + 1)
                    : importPath;
            if (importedSimpleName.equals(className)) {
                reports.add(newError(classDecl,
                        "Class '" + className + "' conflicts with imported class '" + importPath + "'"));
                break;
            }
        }

        if (declaredClasses.containsKey(className)) {
            reports.add(newError(root, "'" + className + "' is already defined in this compilation unit"));
        }
        declaredClasses.put(className, fullyQualifiedName);

        String superQualifiedName = null;
        if (classDecl.hasAttribute("superclass")) {
            var superName = classDecl.get("superclass");

            if (superName.equals(className)) {
                reports.add(newError(classDecl,
                        "Class '" + className + "' cannot extend itself"));
            }

            boolean isImplicitlyImported = importer.isImplicitImport(superName);

            boolean isImported = imports.stream()
                    .anyMatch(i -> i.equals(superName) || i.endsWith("." + superName));

            if (!isImported && !isImplicitlyImported) {
                reports.add(newError(classDecl,
                        "Superclass '" + superName + "' is not imported"));
            }

            superQualifiedName = resolveClassName(superName);
        }

        var fields = buildFields(classDecl);
        var methods = buildMethods(classDecl);

        if (superQualifiedName != null) {
            var superST = importer.getSymbolTableOf(superQualifiedName);
            if (superST.isEmpty()) {
                superST = importer.tryImplicitImport(superQualifiedName);
            }

            if (superST.isPresent()) {
                for (MethodSymbol superMethod : superST.get().getMethods()) {
                    boolean isOverridden = methods.stream()
                            .anyMatch(m -> m.name().equals(superMethod.name()));

                    if (!isOverridden) {
                        methods.add(superMethod);
                    }
                }
            }
        }

        var symbolTable = new JmmSymbolTable(imports, fullyQualifiedName, superQualifiedName, fields, methods, importer);

        return new SymbolTableBuilderResult(symbolTable, reports);
    }


    private List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();

        for (JmmNode varDecl : classDecl.getChildren(VAR_DECL)) {
            var name = varDecl.get(JmmAttributes.VAR_DECL.NAME);

            if (fields.stream().anyMatch(f -> f.name().equals(name))) {
                reports.add(newError(varDecl, "Field '" + name + "' already declared"));
                continue;
            }

            JmmNode typeNode = varDecl.getChildren().stream()
                    .filter(child -> !VISIBILITY.check(child))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No type found for var " + name));

            var type = buildType(typeNode);

            if (isVoidType(type)) {
                reports.add(newError(varDecl, "Field '" + name + "' cannot be of type void."));
            }

            fields.add(new Symbol(type, name));
        }

        return fields;
    }

    private JmmType buildType(JmmNode typeNode) {
        if (METHOD_TYPE.check(typeNode)) {
            return buildType(typeNode.getChild(0));
        }

        if (INT_TYPE.check(typeNode)) {
            return JmmPrimitiveType.INT;
        }

        if (VOID_TYPE.check(typeNode)) {
            return JmmPrimitiveType.VOID;
        }

        if (BOOLEAN_TYPE.check(typeNode)) {
            return JmmPrimitiveType.BOOLEAN;
        }

        if (CLASS_TYPE.check(typeNode)) {
            var simpleName = typeNode.get("name");
            // verifica imports ou a própria classe
            var qualifiedName = resolveClassName(simpleName);
            boolean isImported = imports.stream()
                    .anyMatch(i -> i.equals(simpleName) || i.endsWith("." + simpleName));

            return JmmClassType.ofInstance(qualifiedName, isImported);

        }

        if (ARRAY_TYPE.check(typeNode)) {
            var base = buildType(typeNode.getChild(0));
            int dimension = typeNode.getInteger("dimension", 1);
            return JmmArrayType.of(base, dimension);
        }

        throw new RuntimeException("Unknown type: " + typeNode);
    }

    private String resolveClassName(String simpleName) {
        // Verificar se é a própria classe
        if (simpleName.equals(className)) {
            return declaredClasses.get(className);
        }
        // Verifica se está nos imports
        var imported = imports.stream()
                .filter(i -> i.equals(simpleName) || i.endsWith("." + simpleName))
                .findFirst();
        if (imported.isPresent()) {
            return imported.get();
        }

        return simpleName;
    }

    private List<Symbol> buildLocals(JmmNode method, List<Symbol> params) {
        List<Symbol> locals = new ArrayList<>();
        Set<String> names = new HashSet<>();

        for (var p : params) {
            names.add(p.name());
        }

        for (var varDecl : method.getChildren(VAR_DECL)) {
            String varName = varDecl.get(JmmAttributes.VAR_DECL.NAME);

            JmmNode typeNode = varDecl.getChildren().stream()
                    .filter(child -> !VISIBILITY.check(child))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No type found for var " + varName));

            JmmType varType = buildType(typeNode);

            if (isVoidType(varType)) {
                reports.add(newError(varDecl, "Local variable '" + varName + "' cannot be of type void."));
            }

            if (names.contains(varName)) {
                reports.add(newError(varDecl, "Duplicate local variable name '" + varName +
                        "' in method '" + method.get("name") + "'"));
            } else {
                locals.add(new Symbol(varType, varName));
                names.add(varName);
            }
        }

        return locals;
    }


    private List<MethodSymbol> buildMethods(JmmNode classDecl) {
        List<MethodSymbol> methods = new ArrayList<>();

        for (JmmNode methodNode : classDecl.getChildren(METHOD_DECL)) {
            MethodSymbol newMethod = buildMethod(methodNode);

            boolean isDuplicate = false;
            for (MethodSymbol existingMethod : methods) {
                if (existingMethod.name().equals(newMethod.name()) &&
                        existingMethod.parameters().size() == newMethod.parameters().size()) {

                    boolean paramsMatch = true;
                    for (int i = 0; i < newMethod.parameters().size(); i++) {
                        if (!existingMethod.parameters().get(i).type().equals(newMethod.parameters().get(i).type())) {
                            paramsMatch = false;
                            break;
                        }
                    }

                    if (paramsMatch) {
                        isDuplicate = true;
                        break;
                    }
                }
            }

            if (isDuplicate) {
                reports.add(newError(methodNode, "Duplicate method declaration: " + newMethod.name()));
            } else {
                methods.add(newMethod);
            }
        }

        return methods;
    }

    private MethodSymbol buildMethod(JmmNode method) {
        String methodName = method.get("name");

        var returnTypeNode = method.getChildren(METHOD_TYPE).stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("No return type in method: " + methodName));
        JmmType returnType = buildType(returnTypeNode);

        var paramListNodes = method.getChildren(PARAM_LIST);
        var paramNodes = paramListNodes.isEmpty()
                ? method.getChildren(PARAM)
                : paramListNodes.getFirst().getChildren(PARAM);

        List<Symbol> params = new ArrayList<>();
        for (var paramNode : paramNodes) {
            String paramName = paramNode.get(JmmAttributes.PARAM.NAME);

            JmmNode typeNode = paramNode.getChildren().stream()
                    .filter(child -> !VISIBILITY.check(child))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No type found for param " + paramName));

            JmmType paramType = buildType(typeNode);

            boolean exists = params.stream().anyMatch(s -> s.name().equals(paramName));
            if (exists) {
                reports.add(newError(paramNode,
                        "Duplicate parameter name '" + paramName + "' in method '" + methodName + "'"));
            } else {
                params.add(new Symbol(paramType, paramName));
            }
        }

        List<Symbol> locals = buildLocals(method, params);

        var visibilityNodes = method.getChildren(VISIBILITY);
        Visibility visibility = Visibility.PACKAGE_PROTECTED;
        if (!visibilityNodes.isEmpty()) {
            visibility = Visibility.fromString(visibilityNodes.getFirst().get("value"));
        }
        boolean isStatic = method.getBoolean(JmmAttributes.METHOD_DECL.IS_STATIC, false);

        if (isStatic) {
            for (JmmNode descendant : method.getDescendants()) {
                if (THIS_EXPR.check(descendant)) {
                    reports.add(newError(descendant, "Cannot use 'this' in a static context"));
                }
            }
        }

        return new MethodSymbol(methodName, returnType, params, locals, isStatic, visibility);
    }
    private boolean isVoidType(JmmType type) {
        if (type.equals(JmmPrimitiveType.VOID)) {
            return true;
        }
        if (type instanceof JmmArrayType arr) {
            return isVoidType(arr.itemType());
        }
        return false;
    }
}