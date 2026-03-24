package pt.up.fe.comp.jmm.analysis.table;

import pt.up.fe.comp.jmm.utils.Attributes;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public interface SymbolTable extends Attributes {

    /**
     *
     * @return a list with all methods of the class
     */
    List<MethodSymbol> getMethods();

    /**
     * Gets all methods with the provided name.
     *
     */
    List<MethodSymbol> getMethods(String name);

    /**
     * @return a list of fully qualified names of imports
     */
    List<String> getImports();

//    String fullyQualifiedName();

    /**
     * @return the name of the main class
     */
    default String getClassName() {
        var fullyQualifiedName = getFullyQualifiedName();
        if (!fullyQualifiedName.contains(".")) {
            return fullyQualifiedName;
        }
        return fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
    }

    /**
     *
     * @return the fully qualified name for the class
     */
    String getFullyQualifiedName();


    default String packageName() {
        var fullyQualifiedName = getFullyQualifiedName();
        if (!fullyQualifiedName.contains(".")) {
            return "";
        }
        return fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf('.'));
    }


    /**
     *
     * @return the name of the class that the current class extends, or null if the class does not extend another class
     */
    default String getSuper() {
        var superFullyQualifiedName = getSuperFullyQualifiedName();
        if (superFullyQualifiedName == null ||
                !superFullyQualifiedName.contains(".")) {
            return superFullyQualifiedName;
        }
        return superFullyQualifiedName.substring(superFullyQualifiedName.lastIndexOf('.') + 1);
    }

    String getSuperFullyQualifiedName();

    /**
     *
     * @return a list of Symbols that represent the fields of the class
     */
    List<Symbol> getFields();

    /**
     * Get a field with the specified name
     *
     * @param name
     * @return the Symbol of the field if exists, empty otherwise
     */
    Optional<Symbol> getField(String name);


    /**
     * Convert simple name of imported class into qualifiedName
     *
     * @param simpleName
     * @return the fully qualified name for the given simpleName, or empty if not imported
     */
    Optional<String> getImportedFullyQualifiedName(String simpleName);

    /**
     * Get a method with the given signature
     *
     * @param signature (with name and list of type of parameters)
     * @return the method if exists, otherwise empty.
     */
    Optional<MethodSymbol> getMethod(Signature signature);

    /**
     *
     * @return a String with information about the contents of the SymbolTable
     */
    default String print() {
        var builder = new StringBuilder();

        builder.append("Class: ")
                .append(getFullyQualifiedName())
                .append("\n");

        var superClass = getSuperFullyQualifiedName() != null ? getSuperFullyQualifiedName() : "java.lang.Object";
        builder.append("Super: ")
                .append(superClass)
                .append("\n");
        builder.append("\nImports:");
        var imports = getImports();

        if (imports.isEmpty()) {
            builder.append(" <no imports>\n");
        } else {
            builder.append("\n");
            imports.forEach(fullImport -> builder.append(" - ").append(fullImport).append("\n"));
        }

        var fields = getFields();
        builder.append("\nFields:");
        if (fields.isEmpty()) {
            builder.append(" <no fields>\n");
        } else {
            builder.append("\n");
            fields.forEach(field -> builder.append(" - ").append(field.print()).append("\n"));
        }

        var methods = getMethods();
        builder.append("\nMethods: ").append(methods.size()).append("\n");

        methods.stream().sorted(SymbolTable::compareMethods)
                .forEach(method -> {
                    builder.append(" - signature: ").append(method.signature());
                    builder.append("; returnType: ").append(method.returnType().print());

                    // var returnType = getReturnType(method);
                    var params = method.parameters();
                    builder.append("; params: ");

                    if (params.isEmpty()) {
                        builder.append(" <no params>");
                    } else {
                        var paramsString = params.stream().map(Symbol::print)
                                .collect(Collectors.joining(", "));
                        builder.append(paramsString);
                    }

                    /**
                     * Print of local variables with contribution from group comp2022-2c
                     */
                    var localVariables = method.localVariables();
                    builder.append("; local vars: ");

                    if (localVariables.isEmpty()) {
                        builder.append("<no vars>");
                    } else {
                        var localVarsString = localVariables.stream()
                                .map(Symbol::print)
                                .collect(Collectors.joining(", "));
                        builder.append(localVarsString);
                    }

                    builder.append("\n");
                });

        return builder.toString();
    }

    private static int compareMethods(MethodSymbol left, MethodSymbol right) {
        int diff = left.name().compareTo(right.name());
        if (diff != 0) {
            return diff;
        }
        diff = left.parameters().size() - right.parameters().size();
        if (diff != 0) {
            return diff;
        }
        for (int i = 0; i < left.parameters().size(); i++) {
            var param = left.parameters().get(i).type().print();
            var param2 = right.parameters().get(i).type().print();
            diff = param.compareTo(param2);
            if (diff != 0) {
                return diff;
            }

        }
        return 0;
    }

}
