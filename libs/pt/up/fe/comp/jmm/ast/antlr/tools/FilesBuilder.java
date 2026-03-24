package pt.up.fe.comp.jmm.ast.antlr.tools;

import pt.up.fe.specs.util.SpecsIo;

import java.io.File;
import java.util.List;

class FilesBuilder {
    private final String nodePackage;
    private final String nodePrefix;
    private final File outputDir;
    private final CodeBuilder codeBuilder;
    private final String nodeKind;
    private final String nodeName;

    public FilesBuilder(String antlrPackage, String antlrPrefix, String nodePackage, String nodePrefix, String nodeKind, String nodeName, String outputDir) {
        this.nodePackage = nodePackage;
        this.nodePrefix = nodePrefix;
        this.outputDir = new File(outputDir);
        this.nodeKind = nodeKind;
        this.nodeName = nodeName;
        this.codeBuilder = new CodeBuilder(antlrPackage, antlrPrefix, nodePackage, nodePrefix, nodeKind, nodeName);
    }

    private File outputFile(String localPackage, String fileName){
        File packageDir = new File(this.outputDir, this.nodePackage.replace(".", File.separator));
        File localPackageDir = new File(packageDir, localPackage.replace(".", File.separator));
        return new File(localPackageDir, fileName+".java");
    }


    public File generateParserFile(String baseName,String content) {
        return generateFile("parser", baseName, content);
    }
    public File generateLexerFile(String baseName,String content) {
        return generateFile("lexer", baseName, content);
    }
    public File generateAstFile(String baseName,String content) {
        return generateFile("ast", baseName, content);
    }
    public File generateAntlrFile(String baseName,String content) {
        return generateFile("antlr", baseName, content);
    }

    public File generateSemanticsFile(String baseName,String content) {
        return generateFile("semantics", baseName, content);
    }
    public File generateFile(String localPackage, String baseName, String content){
        var fileName = this.nodePrefix+baseName;
        File outFile = outputFile(localPackage, fileName);
        SpecsIo.write(outFile, content);
        CompClassesGenerator.logSubPhase("Generated file: "+ outFile);
        return outFile;
    }


    public void generateKindClass(List<RuleInfo> nodes, String root){
        var allValues = nodes.stream().map(node->
                codeBuilder.nodeKindEnumValue(node.name(), node.baseNode())).toList();
        generateAstFile("Kind", codeBuilder.nodeKindClass(allValues, root));
    }

    public void generateNodeClass(){
        generateAstFile("Node", codeBuilder.nodeClass());
    }

    public void generateNodesAttributesClass(List<RuleInfo> nodes){
        var enums = nodes.stream()
                .map(this::generateNodeAttributesEnum)
                .toList();
        generateAstFile("Attributes",codeBuilder.nodesAttributesClass(enums));

    }

    public String generateNodeAttributesEnum(RuleInfo node){
        var enumValues = node.attributes().stream()
                .map(attr->codeBuilder.attributeEnumValue(attr.name(), attr.type()))
                .toList();
        return codeBuilder.nodeAttributeEnum(node.name(), enumValues);
    }

    public void generateNodeFactoryClass() {
        generateAstFile("NodeFactory", codeBuilder.nodeFactoryClass());

    }


    public void generateAstClassFiles(List<RuleInfo> nodes, String root){
        generateKindClass(nodes, root);
        generateNodeClass();
        generateNodesAttributesClass(nodes);
        generateNodeFactoryClass();
    }

    public void generateLexerClass() {
        generateLexerFile("Lexer", codeBuilder.lexerClass());
    }

    public void generateErrorListenerClass(){
        generateParserFile("ErrorListener", codeBuilder.errorListenerClass());
    }

    public void generateParserResultClass(){
        generateParserFile("ParserResult", codeBuilder.parserResultClass());
    }

    public void generateAntlrToNodeConverterClass(){
        generateParserFile("AntlrTo"+this.nodeName+"Converter", codeBuilder.antlrToNodeClass());
    }

    public void generateParserClass(String root){
        generateParserFile("Parser", codeBuilder.parserClass(root));
    }

    public void generateSemanticsResultClass(){
        generateSemanticsFile( "SemanticsResult", codeBuilder.semanticsResultClass());
    }


    public void generateParserClassFiles(String root){
        generateErrorListenerClass();
        generateParserResultClass();
        generateAntlrToNodeConverterClass();
        generateParserClass(root);
    }

    public void generateSemanticsClassFiles(){
        generateSemanticsResultClass();
    }

}
