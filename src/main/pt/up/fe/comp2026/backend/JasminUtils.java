package pt.up.fe.comp2026.backend;

import org.specs.comp.ollir.AccessModifier;
import org.specs.comp.ollir.Descriptor;
import org.specs.comp.ollir.type.ArrayType;
import org.specs.comp.ollir.type.BuiltinType;
import org.specs.comp.ollir.type.ClassType;
import org.specs.comp.ollir.type.Type;
import pt.up.fe.comp.jmm.analysis.table.reflection.Importer;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.HashMap;
import java.util.Map;

public class JasminUtils {

    private final OllirResult ollirResult;

    private final Map<String, String> fullClassnames;

    private final Importer importer;

    public JasminUtils(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
        this.importer = Importer.fromThisClassPath();
        // Build imports table
        fullClassnames = new HashMap<>();

        // Predefined classnames
        fullClassnames.put("this", ollirResult.getOllirClass().getClassName());
        // This will be get caught as STRING OLLIR element type.
        // And classes cannot be named String, since it is an OLLIR reserved keyword
        //imports.put("String", "java/lang/String");

        for (var fullImport : ollirResult.getOllirClass().getImports()) {
            var splitted = fullImport.split("\\.");

            // Last element will be the key
            var key = splitted[splitted.length - 1];
            fullClassnames.put(key, fullImport.replace('.', '/'));
        }
    }


    public String getTypePrefix(Type type) {
        System.out.println("[TODO] JasminUtils.getTypePrefix(): Assumes it is always int, needs to be expanded");
        return "i";
    }

    public String getTypeDescriptor(Type type) {
        if (type instanceof ArrayType arrayType) {
            return "[" + getTypeDescriptor(arrayType.getElementType());
        }

        if (type instanceof BuiltinType builtinType) {
            return switch (builtinType.getKind()) {
                case INT32 -> "I";
                case BOOLEAN -> "Z";
                case VOID -> "V";
                case STRING -> "Ljava/lang/String;";
                default -> throw new RuntimeException(
                        "Not implemented for builtin type '" + builtinType.getKind() + "'");
            };
        }

        if (type instanceof ClassType classType) {
            var className = classType.getName().replace('.', '/');
            return "L" + className + ";";
        }

        throw new RuntimeException("Not implemented for type '" + type + "'");
    }


    public String getModifier(AccessModifier accessModifier) {
        return switch (accessModifier) {
            case PUBLIC -> "public ";
            case PRIVATE -> "private ";
            case PROTECTED -> "protected ";
            default -> "";
        };
    }

    public String getLoad(Descriptor reg) {
        var prefix = getTypePrefix(reg.getVarType());
        var value = reg.getVirtualReg();

        return prefix + "load " + value;
    }

    public String getStore(Descriptor reg) {
        var prefix = getTypePrefix(reg.getVarType());
        var value = reg.getVirtualReg();

        return prefix + "store " + value;
    }


}
