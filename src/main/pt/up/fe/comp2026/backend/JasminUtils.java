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
import java.util.List;
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
        fullClassnames.put("String", "java/lang/String");
        fullClassnames.put("Object", "java/lang/Object");
        fullClassnames.put("Integer", "java/lang/Integer");
        fullClassnames.put("Boolean", "java/lang/Boolean");
        fullClassnames.put("Double", "java/lang/Double");
        fullClassnames.put("Float", "java/lang/Float");
        fullClassnames.put("Long", "java/lang/Long");

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
            String className;
            if (fullClassnames.containsKey(name)) {
                className = fullClassnames.get(name);
            } else if (name.equals(ollirResult.getOllirClass().getClassName())
                    || name.equals(ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/'))) {
                className = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
            } else {
                if (!name.contains("/") && !name.contains(".")) {
                    try {
                        Class.forName("java.lang." + name);
                        className = "java/lang/" + name;
                    } catch (ClassNotFoundException e) {
                        className = name.replace('.', '/');
                    }
                } else {
                    className = name.replace('.', '/');
                }
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

    public String resolveClassName(String name) {
        if (name.equals("this")) {
            return ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
        }
        String ownClassName = ollirResult.getOllirClass().getClassName();
        String ownFQN = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
        if (name.equals(ownClassName) || name.equals(ownFQN)) {
            return ownFQN;
        }
        return fullClassnames.getOrDefault(name, name.replace('.', '/'));
    }

    public List<String> resolveParamDescriptorsViaReflection(String className, String methodName, int argCount) {
        try {
            String fqn = className.replace('/', '.');
            Class<?> clazz = Class.forName(fqn);
            for (var method : clazz.getMethods()) {
                if (!method.getName().equals(methodName)) continue;
                if (method.getParameterCount() != argCount) continue;
                var result = new java.util.ArrayList<String>();
                for (var param : method.getParameterTypes()) {
                    result.add(toJvmDescriptor(param));
                }
                return result;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String toJvmDescriptor(Class<?> type) {
        if (type == int.class) return "I";
        if (type == boolean.class) return "Z";
        if (type == void.class) return "V";
        if (type == long.class) return "J";
        if (type == double.class) return "D";
        if (type == float.class) return "F";
        if (type == char.class) return "C";
        if (type == byte.class) return "B";
        if (type == short.class) return "S";
        if (type.isArray()) return "[" + toJvmDescriptor(type.getComponentType());
        return "L" + type.getName().replace('.', '/') + ";";
    }

    public String resolveReturnDescriptorViaReflection(String className, String methodName, int argCount) {
        try {
            String fqn = className.replace('/', '.');
            Class<?> clazz = Class.forName(fqn);
            for (var method : clazz.getMethods()) {
                if (!method.getName().equals(methodName)) continue;
                if (method.getParameterCount() != argCount) continue;
                return toJvmDescriptor(method.getReturnType());
            }
        } catch (Exception ignored) {
        }
        return null;
    }
}