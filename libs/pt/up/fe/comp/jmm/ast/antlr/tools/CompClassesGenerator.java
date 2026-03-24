package pt.up.fe.comp.jmm.ast.antlr.tools;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.util.*;
import java.util.stream.Collectors;

public class CompClassesGenerator {
    private final String antlrPackage;
    private final String antlrPrefix;
    private final FilesBuilder fileBuilder;
    private Class<?> parserCls;
    private final String nodeKind;
    private final String nodeName;
    private static boolean debug;


    public CompClassesGenerator(String antlrPackage, String antlrPrefix, String nodePackage, String nodePrefix, String outputDir) {
        this.antlrPackage = antlrPackage;
        this.antlrPrefix = antlrPrefix;
        this.nodeKind = nodePrefix + "Kind";
        this.nodeName = nodePrefix + "Node";
//        this.codeBuilder = new CodeBuilder(antlrPackage, antlrPrefix, nodePackage, nodePrefix, nodeKind, nodeName);
        this.fileBuilder = new FilesBuilder(antlrPackage, antlrPrefix, nodePackage, nodePrefix, nodeKind, nodeName, outputDir);
    }

    private int phaseCounter = 0;

    private int phase() {
        return phaseCounter++;
    }

    private void logPhase(String message) {
        if (debug)
            System.out.println("[" + phase() + "] " + message);
    }

    static void logSubPhase(String message) {
        if (debug)
            System.out.println("   -> " + message);
    }

    static void logSubPhaseNoLn(String message) {
        if (debug)
            System.out.print("   -> " + message);
    }

    private void generate(boolean debug) {
        CompClassesGenerator.debug = debug;
        phaseCounter = 0;
        int phase = 1;

        var baseNames = antlrPackage + "." + antlrPrefix;
        var parserName = baseNames + "Parser";
        try {
            logPhase("Trying to load class " + parserName + "...");
            ClassLoader loader = Thread.currentThread().getContextClassLoader();
            this.parserCls = loader.loadClass(parserName);
            logSubPhase("Success!");

            logPhase("Gathering Node Kinds using Context Classes!");

            var rules = getRulesNames();
            logSubPhase(String.format("Found %d rules: %s", rules.size(), String.join(", ", rules)));

            List<Class<?>> contextClasses = Arrays.stream(parserCls.getDeclaredClasses()).filter(cls -> cls.getName().endsWith("Context")).toList();
            var usedBy = getUses(contextClasses, rules);
            var roots = usedBy.entrySet().stream()
                    .filter(e -> e.getValue().isEmpty())
                    .map(Map.Entry::getKey)
                    .toList();
            if (roots.isEmpty()) {
                throw new RuntimeException("Could not find root rule (those not used by other rules).");
            }
            var root = roots.getFirst();
            if (roots.size() > 1) {
                logSubPhase("Multiple root rules found: " + String.join(", ", roots) + ". Using first rule '" + root + "' as root.");
            } else {
                logSubPhase("Found root rule: " + root);
            }

            var nodes = buildNodes(contextClasses);
            logPhase("Generating ast package files");
            fileBuilder.generateKindClass(nodes, root);
            fileBuilder.generateNodesAttributesClass(nodes);
//            fileBuilder.generateAstClassFiles(nodes);

//            logPhase("Generating lexer package files");
//            fileBuilder.generateLexerClass();
//
//            logPhase("Generating parser package files");
//            fileBuilder.generateParserClassFiles(root);
//
//            logPhase("Generating semantics package files");
//            fileBuilder.generateSemanticsClassFiles();

        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Problem finding the Parser:" + e.getMessage());
        }
    }

    private boolean isTokenName(String name) {
        try {
            parserCls.getDeclaredField(name);
            return true;
        } catch (NoSuchFieldException e) {
            return false;
        }
    }

    private List<RuleInfo> buildNodes(List<Class<?>> contextClasses) {

        var nodes = new ArrayList<RuleInfo>();
        for (Class<?> contextClass : contextClasses) {
            var name = contextClass.getSimpleName().substring(0, contextClass.getSimpleName().lastIndexOf("Context"));
            var attributes = Arrays.stream(contextClass.getDeclaredFields())
                    .filter(f -> !isTokenName(f.getName()))
                    .map(this::toAttribute).toList();
            String baseNode = null;
            var extendedContext = contextClass.getSuperclass();
            //if not default Context, then it extends a rule
            if (!extendedContext.isAssignableFrom(ParserRuleContext.class)) {
                baseNode = extendedContext.getSimpleName();
                baseNode = baseNode.substring(0, baseNode.lastIndexOf("Context"));
            }
            nodes.add(new RuleInfo(name, attributes, baseNode));
        }
        logSubPhase(String.format("Found %d node kinds: %s", nodes.size(),
                nodes.stream().map(RuleInfo::name).collect(Collectors.joining(", "))));
        return nodes;
    }

    private String getTypeOf(Class<?> cls) {
        if (cls.isPrimitive()) {
            return cls.getName();
        }
        if (cls.isAssignableFrom(Token.class)) {
            return "String";
        }

        var name = cls.getSimpleName();
        if (name.endsWith("Context")) {
            var nodeKind = name.substring(0, name.lastIndexOf("Context"));

            return this.nodeName
                    + "(" + this.nodeKind + "."
                    + CodeBuilder.nameToEnum(nodeKind)
                    + ")";
        }
        return cls.getName();
    }

    private Attribute toAttribute(Field field) {

        Class<?> type = field.getType();
        String typeStr = "";
        if (type.isAssignableFrom(List.class)) {
            var params = (ParameterizedType) field.getGenericType();
            var itemType = (Class<?>) params.getActualTypeArguments()[0];
            typeStr = "List<" + getTypeOf(itemType) + ">";
        } else {
            typeStr = getTypeOf(type);
        }

        return new Attribute(typeStr, field.getName());
    }


    public Set<String> getRulesNames() {
        var rules = new HashSet<String>();

        try {
            Field f = parserCls.getField("ruleNames");
            Object value = f.get(null);
            if (value instanceof String[] ruleNames) {
                rules.addAll(Arrays.asList(ruleNames));
            }
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Could not find list of rule names.");
        }
        return rules;
    }

    public Map<String, Set<String>> getUses(List<Class<?>> contextClasses, Set<String> rules) {
        var usedBy = new HashMap<String, Set<String>>();
        for (var rule : rules) {
            usedBy.put(rule, new HashSet<>());
        }
        for (Class<?> nodeContext : contextClasses) {
            for (Method m : nodeContext.getDeclaredMethods()) {
                Class<?> ret = m.getReturnType();
                //only want *Context return types (or list of those)
                var name = ret.getSimpleName();
                if (name.endsWith("Context")) {
                    usedBy.get(m.getName())
                            .add(name.substring(0, name.lastIndexOf("Context")));
                }
            }
        }

        return usedBy;
    }

    public static void main(String[] args) {
        if (args.length < 5) {
            throw new RuntimeException("usage: <antlr.parser.package> <antlr_prefix> <project.node.package> <node_prefix> <output_dir> [debug]");
        }
        var parserPackage = args[0];
        var parserPrefix = args[1];
        var nodePackage = args[2];
        var nodePrefix = args[3];
        var outputDir = args[4];
        var debug = args.length >= 6 && "DEBUG".equalsIgnoreCase(args[5]);

        var kindGenerator = new CompClassesGenerator(parserPackage, parserPrefix, nodePackage, nodePrefix, outputDir);
        kindGenerator.generate(debug);
    }
}
