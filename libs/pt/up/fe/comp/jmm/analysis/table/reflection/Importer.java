package pt.up.fe.comp.jmm.analysis.table.reflection;

import pt.up.fe.comp.jmm.analysis.table.*;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class Importer {

    public URL[] classPaths;
    private final ClassLoader classLoader;

    private Importer(URL[] classPaths) {
        this.classPaths = classPaths;
        this.classLoader = new URLClassLoader(this.classPaths);
    }

    private Importer() {
//        this.classPaths = ;
//        String classpath = System.getProperty("java.class.path");
//        String[] classpathEntries = classpath.split(File.pathSeparator);
        //to URL
        this.classLoader = Importer.class.getClassLoader();
    }


    public static Importer newFrom(String classPaths) {
        return newFrom(List.of(classPaths));
    }

    /**
     * Creates an instance of this class using the current classpath.
     *
     * @return
     */
    public static Importer fromThisClassPath() {
        return new Importer();
    }


    public static Importer newFrom(List<String> classPaths) {
        var cps = classPaths.stream().map(cp -> {
            try {
                var f = new File(cp);
                if (!f.exists()) {
                    throw new MalformedURLException("File not found: " + f.getAbsolutePath());
                }
                return (f.toURI().toURL());
            } catch (MalformedURLException e) {
                throw new RuntimeException("One of the paths is invalid.", e);
            }
        }).toArray(URL[]::new);
        return new Importer(cps);
    }

    /**
     * Check if the provided simple class name is an implicit imported class (e.g. String).
     *
     * @param className
     * @return
     */
    public boolean isImplicitImport(String className) {
        return loadImplicit(className).isPresent();
    }

    /**
     * Check if class with qualified name exists in the current classpath.
     *
     * @param qualifiedName
     * @return
     */
    public boolean inClassPath(String qualifiedName) {
        return tryClassOf(qualifiedName).isPresent();
    }

    public Optional<Class<?>> loadImplicit(String className) {
        try {
            var cls = classLoader.loadClass(className);
            return Optional.of(cls);
        } catch (ClassNotFoundException e) {
            try {
                var cls = classLoader.loadClass("java.lang." + className);
                return Optional.of(cls);
            } catch (ClassNotFoundException e2) {
                return Optional.empty();
            }
        }
    }

    /**
     * Tries to get the symbol table for the given class name, if the class is an implicit import.
     *
     * @param className
     * @return
     */
    public Optional<SymbolTable> tryImplicitImport(String className) {
        return loadImplicit(className).map(this::getSymbolTableOf);
    }


    /**
     * Tries to get the symbol table for the given class name, using the fully qualified name.
     *
     * @param qualifiedName
     * @return
     */
    public Optional<SymbolTable> getSymbolTableOf(String qualifiedName) {
        var cls = tryClassOf(qualifiedName);
        return cls.map(this::getSymbolTableOf);
    }

    /**
     * Trys to get the Java Class<?> corresponding to the given qualified name.
     *
     * @param qualifiedName
     * @return
     */
    public Optional<Class<?>> tryClassOf(String qualifiedName) {
        try {
            var cls = this.classLoader.loadClass(qualifiedName);
            return Optional.of(cls);
        } catch (ClassNotFoundException e) {
            return Optional.empty();
        }
    }


    /**
     * Builds a symbol table for the given class.
     * Note: fields and methods of the super class(es) are also included in the list of methods,
     * therefore no need to go for the "super of the super"
     *
     * @param cls
     * @return
     */
    public SymbolTable getSymbolTableOf(Class<?> cls) {
        var fullyQualifiedName = cls.getName();
        String superFullyQualifiedName = null;
        if (cls.getSuperclass() != null) {
            superFullyQualifiedName = cls.getSuperclass().getName();
        }
        var fieldsMap = new HashMap<String, Symbol>();
        Field[] fieldsToProcess;
//        if(declaredElementsOnly){
        fieldsToProcess = Arrays.stream(cls.getDeclaredFields())
                .filter(f -> !Modifier.isPrivate(f.getModifiers()))
                .toArray(Field[]::new);
//        }else{
//            fieldsToProcess = cls.getFields();
//        }
        var fields = buildFields(fieldsToProcess);
        fields.forEach(field -> fieldsMap.put(field.name(), field));
//        addFields(st, cls.getDeclaredFields());
        var methodsMap = new HashMap<Signature, MethodSymbol>();
        Method[] methodsToProcess = Arrays.stream(cls.getMethods())
                .filter(m -> !Modifier.isPrivate(m.getModifiers()))
                .toArray(Method[]::new);
//                cls.getMethods();
        var methods = buildMethods(methodsToProcess);
        methods.forEach(method -> methodsMap.put(method.signature(), method));
//        addMethods(st, cls.getDeclaredMethods());
        return new ImportedSymbolTable(cls.getName(),
                superFullyQualifiedName,
                fieldsMap,
                methodsMap);
    }

    private static List<Symbol> buildFields(Field[] fields) {
        return Arrays.stream(fields)
                .filter(f -> !Modifier.isPrivate(f.getModifiers()))
                .map(field -> buildSymbol(field.getType(), field.getName()))
                .toList();
    }

    private List<MethodSymbol> buildMethods(Method[] methods) {
        return Arrays.stream(methods)
                .filter(m -> !Modifier.isPrivate(m.getModifiers()))
                .map(Importer::buildMethod)
                .toList();
    }

    private static MethodSymbol buildMethod(Method m) {
        int modifiers = m.getModifiers();
        Visibility visibility;
        if (Modifier.isPrivate(modifiers)) {
            visibility = Visibility.PRIVATE;
        } else if (Modifier.isProtected(modifiers)) {
            visibility = Visibility.PROTECTED;
        } else if (Modifier.isPublic(modifiers)) {
            visibility = Visibility.PUBLIC;
        } else visibility = Visibility.PACKAGE_PROTECTED;
        return new MethodSymbol(m.getName(),
                convertType(m.getReturnType()),
                convertParameters(m.getParameters()),
                List.of(),
                Modifier.isStatic(modifiers),
                visibility);
    }

    private static List<Symbol> convertParameters(Parameter[] parameters) {
        return Arrays.stream(parameters)
                .map(p -> buildSymbol(p.getType(), p.getName()))
                .toList();
    }


    private static Symbol buildSymbol(Class<?> type, String name) {
        return new Symbol(convertType(type), name);
    }

    private static JmmType convertType(Class<?> type) {
        int dimensions = 0;
        while (type.isArray()) {
            dimensions++;
            type = type.getComponentType();
        }

        JmmType baseType;
        if (type.isPrimitive()) {
            baseType = JmmPrimitiveType.fromString(type.getName()).orElseThrow();
        } else {
            baseType = JmmClassType.ofInstance(type.getName(), true);
        }
        if (dimensions > 0) {
            return new JmmArrayType(baseType, dimensions);
        }
        return baseType;
    }
}
