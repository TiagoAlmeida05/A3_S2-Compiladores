package pt.up.fe.comp2026.backend;

import org.specs.comp.ollir.AccessModifier;
import org.specs.comp.ollir.Descriptor;
import org.specs.comp.ollir.Element;
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
        fullClassnames = new HashMap<>();

        fullClassnames.put("this", ollirResult.getOllirClass().getClassName());

        for (var fullImport : ollirResult.getOllirClass().getImports()) {
            var splitted = fullImport.split("\\.");
            var key = splitted[splitted.length - 1];
            fullClassnames.put(key, fullImport.replace('.', '/'));
        }
    }


    public String getTypePrefix(Type type) {
        if (type instanceof ArrayType) return "a";
        if (type instanceof ClassType) return "a";
        if (type instanceof BuiltinType builtinType) {
            return switch (builtinType.getKind()) {
                case INT32, BOOLEAN -> "i";
                default -> throw new RuntimeException("Not implemented for " + builtinType.getKind());
            };
        }
        throw new RuntimeException("Not implemented for type: " + type);
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
            var name = classType.getName();
            // Se não está nos imports e coincide com a própria classe, usa o FQN
            String className;
            if (fullClassnames.containsKey(name)) {
                className = fullClassnames.get(name);
            } else if (name.equals(ollirResult.getOllirClass().getClassName())
                    || name.equals(ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/'))) {
                className = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
            } else {
                className = name.replace('.', '/');
            }
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

    public String getClassNameFromElement(Element element) {
        var type = element.getType();
        if (type instanceof ClassType classType) {
            var name = classType.getName();
            return fullClassnames.getOrDefault(name, name.replace('.', '/'));
        }
        if (type instanceof ArrayType) {
            return getTypeDescriptor(type);
        }
        throw new RuntimeException("Cannot get class name from element of type: " + type);
    }

    /**
     * Resolves a simple class name (e.g. "ArrayAsArgument", "io") to its
     * fully qualified slash-separated form (e.g. "pt/up/fe/.../ArrayAsArgument").
     * Falls back to the name itself if not found in imports.
     */
    public String resolveClassName(String name) {
        // "this" maps to the current class
        if (name.equals("this")) {
            return ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
        }
        return fullClassnames.getOrDefault(name, name.replace('.', '/'));
    }
}