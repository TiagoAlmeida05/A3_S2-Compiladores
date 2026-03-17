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
import pt.up.fe.comp2026.ast.TypeUtils;
import pt.up.fe.comp2026.jmm.ast.JmmAttributes;
import pt.up.fe.specs.util.SpecsCheck;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

    private List<Symbol> buildFields(JmmNode classDecl) {
        List<Symbol> fields = new ArrayList<>();

        for (JmmNode varDecl : classDecl.getChildren(VAR_DECL)) {
            var name = varDecl.get(JmmAttributes.VAR_DECL.NAME);

            // Verifica duplicados
            if (fields.stream().anyMatch(f -> f.name().equals(name))) {
                reports.add(newError(varDecl, "Field '" + name + "' already declared"));
                continue; // ignora este campo duplicado
            }

            var typeNode = varDecl.getChild(0);
            var type = buildType(typeNode);

            fields.add(new Symbol(type, name));
        }

        return fields;
    }

    private JmmType buildType(JmmNode typeNode) {

        if (INT_TYPE.check(typeNode)) {
            return JmmPrimitiveType.INT;
        }

        if (BOOLEAN_TYPE.check(typeNode)) {
            return JmmPrimitiveType.BOOLEAN;
        }

        if (CLASS_TYPE.check(typeNode)) {
            var name = typeNode.get("name");
            // ver abaixo
            return JmmClassType.ofInstance(name, false);
        }

        if (ARRAY_TYPE.check(typeNode)) {
            var base = buildType(typeNode.getChild(0));
            int dimension = typeNode.getInteger("dimension", 1); // se tiveres essa info, senão usa 1
            return JmmArrayType.of(base, dimension);
        }

        throw new RuntimeException("Unknown type: " + typeNode);
    }


    public static SymbolTableBuilderResult build(JmmNode root) {
        return new JmmSymbolTableBuilder(root).buildInternal();
    }

    private SymbolTableBuilderResult buildInternal() {

        var packageDecls = root.getChildren(PACKAGE_DECL);
        if (packageDecls.size() > 1)
            reports.add(newError(packageDecls.get(1), "Found more than one package"));
        var packageDecl = packageDecls.getFirst();
        var packagePathList = packageDecl.getObjectAsList("path", String.class);
        var packagePath = String.join(".", packagePathList);
        //verificação:ver se tem mais um packe«age, tirar o getFirst e ver se tem mais, ver o tamanho, ver se tem mais do que um package encontrado

        var classDecl = root.getObject("classNode", JmmNode.class);
        SpecsCheck.checkArgument(CLASS_DECL.check(classDecl), () -> "Expected a class declaration: " + classDecl);

        this.className = classDecl.get("name");
        var fullyQualifiedName = packagePath + "." + className;

        // Check if className is available
        if (declaredClasses.containsKey(className)) {
            reports.add(newError(root, "'" + className + "' is already defined in this compilation unit"));
        }
        declaredClasses.put(className, fullyQualifiedName);

        var fields = buildFields(classDecl);

        var methods = buildMethods(classDecl);

        var symbolTable = new JmmSymbolTable(imports, fullyQualifiedName, null, fields, methods, importer);

        return new SymbolTableBuilderResult(symbolTable, reports);
    }

    private List<MethodSymbol> buildMethods(JmmNode classDecl) {

        return classDecl.getChildren(METHOD_DECL).stream()
                .map(this::buildMethod)
                .toList();

    }

    private MethodSymbol buildMethod(JmmNode method) {
        var methodName = method.get("name");

        System.out.println("[TODO] JmmSymbolTableBuilder.buildMethod(): Assuming return type of method is always int, and always has a single int parameter, needs to be expanded");
        var returnType = TypeUtils.intType();


        var params = List.of(new Symbol(TypeUtils.intType(), method.getChildren(PARAM).getFirst().get(JmmAttributes.PARAM.NAME)));

        System.out.println("[TODO] JmmSymbolTableBuilder.buildMethod(): Assuming all VarDecls are ints, needs to be expanded");
        var locals = method.getChildren(VAR_DECL).stream()
                .map(varDecl -> new Symbol(TypeUtils.intType(), varDecl.get(JmmAttributes.VAR_DECL.NAME)))
                .toList();

        var visibility = Visibility.PUBLIC;
        var isStatic = method.getBoolean(JmmAttributes.METHOD_DECL.IS_STATIC, false);
        return new MethodSymbol(methodName, returnType, params, locals, isStatic, visibility);
    }


}
