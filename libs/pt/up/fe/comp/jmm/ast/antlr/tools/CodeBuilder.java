package pt.up.fe.comp.jmm.ast.antlr.tools;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.Kind;
import pt.up.fe.specs.util.SpecsIo;

import java.util.List;
import java.util.Map;

public class CodeBuilder {

    private final Map<String,String> templates;
    private static final String TEMPLATE_EXTENSION = ".template";
    private static final String TEMPLATE_BASE = "templates/antlr_conversion/";
    private static final String ANTLR_DIR = TEMPLATE_BASE+"antlr/";
    private static final String LEXER_DIR = TEMPLATE_BASE+"lexer/";
    private static final String PARSER_DIR = TEMPLATE_BASE+"parser/";
    private static final String AST_DIR = TEMPLATE_BASE+"ast/";
    private static final String SEMANTICS_DIR = TEMPLATE_BASE+"semantics/";



    private static final String NODE_KIND = AST_DIR + "Kind.java";
    private static final String NODE_CLASS = AST_DIR + "Node.java";
    private static final String NODE_FACTORY_CLASS = AST_DIR + "NodeFactory.java";
    private static final String NODE_ATTRIBUTES_CLASS = AST_DIR + "NodeAttributes.java";
    private static final String NODE_ATTRIBUTES_ENUM = AST_DIR + "NodeAttributesEnum";
    private static final String NODE_ATTRIBUTES_NO_VALUES = AST_DIR + "NodeAttributesEnumNoValues";

    private static final String LEXER_CLASS = LEXER_DIR + "Lexer.java";

    private static final String ERROR_LISTENER_CLASS = PARSER_DIR + "ErrorListener.java";
    private static final String PARSER_RESULT_CLASS = PARSER_DIR + "ParserResult.java";
    private static final String ANTLR_TO_NODE_CLASS = PARSER_DIR + "AntlrToNodeConverter.java";
    private static final String PARSER_CLASS = PARSER_DIR + "Parser.java";

    private static final String SEMANTICS_RESULT_CLASS = SEMANTICS_DIR + "SemanticsResult.java";

    private final String antlrPackage;
    private final String antlrPrefix;
    private final String nodePackage;
    private final String nodePrefix;
    private final String interfacesPackage;
    private final String nodeKind;
    private final String nodeName;


    public CodeBuilder(String antlrPackage, String antlrPrefix, String nodePackage, String nodePrefix, String nodeKind, String nodeName){
        this.templates = new java.util.HashMap<>();
        this.antlrPackage = antlrPackage;
        this.nodePackage = nodePackage;
        this.nodePrefix = nodePrefix;
        this.antlrPrefix = antlrPrefix;
        this.nodeKind = nodeKind;
        this.nodeName = nodeName;
        var iNodePackage = JmmNode.class.getPackage().getName();
        var kindClass = Kind.class.getSimpleName();
        var kindPath = Kind.class.getName();
        this.interfacesPackage = iNodePackage.substring(0,iNodePackage.lastIndexOf('.')); //to remove .ast
    }


    // AST
    public String nodeKindClass(List<String> enumValues, String root){
        var classTemplate = getTemplate(NODE_KIND);
        var rootEnum = nameToEnum(root);
        var enumValuesString = String.join(",\n    ",enumValues);
        return classTemplate
                .replace("${ENUM_VALUES}",enumValuesString)
                .replace("${ROOT}",rootEnum);
    }

    public String nodesAttributesClass(List<String> nodeAttributesEnums){
        var enums = String.join("\n\n  ",nodeAttributesEnums);
        return getTemplate(NODE_ATTRIBUTES_CLASS)
                .replace("${ATTRIBUTES_ENUMS}",enums);
    }

    public String nodeClass() {
        return getTemplate(NODE_CLASS);
    }

    public String nodeFactoryClass() {
        return getTemplate(NODE_FACTORY_CLASS);
    }

    // LEXER

    public  String lexerClass(){
        return getTemplate(LEXER_CLASS);
    }

    // PARSER

    public String errorListenerClass() {
        return getTemplate(ERROR_LISTENER_CLASS);
    }

    public String parserResultClass() {
        return getTemplate(PARSER_RESULT_CLASS);
    }

    public String antlrToNodeClass(){ return getTemplate(ANTLR_TO_NODE_CLASS); }

    public String parserClass(String defaultRule) { return getTemplate(PARSER_CLASS)
            .replace("${DEFAULT_RULE}", defaultRule);
    }

    public String semanticsResultClass() {
        return getTemplate(SEMANTICS_RESULT_CLASS);
    }


    //Helper Functions
    /**
     * Lazy loading of templates (specially for enums with many values)
     * @param location
     * @return
     */
    public String getTemplate(String location){
        location+=TEMPLATE_EXTENSION;
        if (!templates.containsKey(location)){

            var template = SpecsIo.getResource(location)
                    .replace("${ANTLR_PACKAGE}", this.antlrPackage)
                    .replace("${ANTLR_PREFIX}", this.antlrPrefix)
                    .replace("${PACKAGE}", this.nodePackage)
                    .replace("${NODE_PACKAGE}", this.nodePackage)
                    .replace("${NODE_PREFIX}", this.nodePrefix)
                    .replace("${NODE_NAME}", this.nodeName)
                    .replace("${NODE_KIND}", this.nodeKind)
                    .replace("${INTERFACES_PACKAGE}", this.interfacesPackage);
            templates.put(location,template);
        }
        return templates.get(location);

    }

    public String nodeKindEnumValue(String name, String baseNode){
        var enum_value = nameToEnum(name)+"(\""+name+"\"";
        if (baseNode != null) {
            enum_value+=","+ nameToEnum(baseNode);
        }
        return enum_value + ")";
    }



    public String nodeAttributeEnum(String nodeName, List<String> attributes){
        var camelCaseName =  CodeBuilder.nameToEnum(nodeName);

        if (attributes.isEmpty()){
            return getTemplate(NODE_ATTRIBUTES_NO_VALUES)
                    .replace("${NODE}",camelCaseName);
        }
        var allValues = String.join(",\n    ",attributes);
        return getTemplate(NODE_ATTRIBUTES_ENUM)
                .replace("${NODE}",camelCaseName)
                .replace("${ATTRIBUTES}",allValues);
    }

    public String attributeEnumValue(String attrName, String type){
        return "/** "+type + " " + attrName+" **/\n"
                + "    "
                +CodeBuilder.nameToEnum(attrName)
                +"(\""+attrName+"\")";
    }

    public static String nameToEnum(String camelCase){
        return camelCase
                .replaceAll("([a-z])([A-Z])", "$1_$2")
                .replaceAll("([A-Z])([A-Z][a-z])", "$1_$2")
                .toUpperCase();
    }


}
