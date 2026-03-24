/**
 * Copyright 2023 SPeCS.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License. under the License.
 */

package pt.up.fe.comp.jmm.ast.antlr;

import java.lang.reflect.Modifier;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.Token;
import org.antlr.v4.runtime.tree.ParseTree;
import org.antlr.v4.runtime.tree.TerminalNode;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmNodeImpl;
import pt.up.fe.comp.jmm.ast.NodePosition;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.comp.jmm.ast.Kind;
public class AntlrToJmmNodeConverter {

    private final Map<ParseTree, JmmNode> antlrToJmm;
    private final Function<String, ? extends Kind> stringToKind;

    public AntlrToJmmNodeConverter(Function<String,? extends Kind> stringToKind) {
        this.antlrToJmm = new HashMap<>();
        this.stringToKind = stringToKind;
    }

    public static JmmNode convert(ParseTree node, Parser parser, Function<String,? extends Kind> stringToKind) {
        var converter = new AntlrToJmmNodeConverter(stringToKind);
        var jmmNode = converter.convertPrivate(node, parser);

        // Now that all nodes have been converted, replace attributes that are ANTLR nodes with the equivalent JmmNode
        converter.replaceAntlrNodeAttrs(jmmNode, parser);

        return jmmNode;
    }

    private void replaceAntlrNodeAttrs(JmmNode root, Parser parser) {
        new AntlrNodeAttrReplacer(antlrToJmm).visit(root);
    }

    private JmmNode convertPrivate(ParseTree node, Parser parser) {
        // Get kind
        var kind = getKind(node, parser);

        var jmmNode = new JmmNodeImpl(this.stringToKind.apply(kind));

        // Add to map
        antlrToJmm.put(node, jmmNode);

        // Get attributes
        addAttributes(jmmNode, node, parser);

        // Get children
        addChildren(jmmNode, node, parser);

        return jmmNode;
    }


    private List<String> getHierarchy(ParseTree node) {
        // If terminal node, it has no hierarchy
        if (node instanceof TerminalNode) {
            return Collections.emptyList();
        }

        // If terminal node, it has no hierarchy
        if (!(node instanceof ParserRuleContext)) {
            System.out.println("Don't know how to handle nodes of this class: " + node.getClass());
            return Collections.emptyList();
        }

        // Get hierarchy
        var classes = getNodeClasses(node);

        return classes.stream()
                .map(aClass -> getKind(aClass.asSubclass(ParserRuleContext.class)))
                .collect(Collectors.toList());
    }

    private void addChildren(JmmNodeImpl jmmNode, ParseTree node, Parser parser) {

        for (int i = 0; i < node.getChildCount(); i++) {
            var child = node.getChild(i);

            // Ignore terminal nodes that do not have a symbolic name
            if (child instanceof TerminalNode) {
                continue;
                /*
                var token = ((TerminalNode) child).getSymbol();
                if (parser.getVocabulary().getSymbolicName(token.getType()) == null) {
                    continue;
                }
                */
            }

            jmmNode.add(convertPrivate(child, parser));
        }
    }

    private void addAttributes(JmmNodeImpl jmmNode, ParseTree node, Parser parser) {

        // System.out.println("S NAME: " + parser.getSourceName());
        // Add line and column
        var startPosition = parser.getTokenStream().get(node.getSourceInterval().a);
        var endPosition = parser.getTokenStream().get(node.getSourceInterval().b);

        jmmNode.put(NodePosition.LINE_START.getKey(), Integer.toString(startPosition.getLine()));
        jmmNode.put(NodePosition.COL_START.getKey(), Integer.toString(startPosition.getCharPositionInLine()));

        jmmNode.put(NodePosition.LINE_END.getKey(), Integer.toString(endPosition.getLine()));
        jmmNode.put(NodePosition.COL_END.getKey(), Integer.toString(endPosition.getCharPositionInLine()));

        if (node instanceof TerminalNode) {
            var token = ((TerminalNode) node).getSymbol();
            jmmNode.put("value", token.getText());
            return;
        }

        SpecsCheck.checkArgument(node instanceof ParserRuleContext,
                () -> "Expected node '" + node.getClass() + "' to be an instance of " + ParserRuleContext.class);

        // Get all classes up to ParserRuleContext
        var nodeClasses = getNodeClasses(node);

        var fields = nodeClasses.stream()
                // Get declaring fields of node classes
                .flatMap(nodeClass -> Arrays.stream(nodeClass.getDeclaredFields()))
                // Only those that are public
                .filter(field -> Modifier.isPublic(field.getModifiers()))
                .toList();

        for (var field : fields) {

            var name = field.getName();

            try {
                // for (var field : node.getClass().getFields()) {
                if (!field.getType().isAssignableFrom(Token.class)) {
                    var value = processValue(field.get(node));
                    jmmNode.putObject(name, value);
                    continue;
                }

                var token = (Token) field.get(node);

                // If no token for the given field, skip
                if (token == null) {
                    continue;
                }

                var literalValue = token.getText();

                SpecsCheck.checkNotNull(literalValue, () -> "Could not extract value from token");

                jmmNode.put(name, literalValue);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Could not access field '" + name + "' from node " + node);
            }
        }

    }

    private Object processValue(Object value) {
        // If Token, convert to String
        if (value instanceof Token) {
            return ((Token) value).getText();
        }

        // If List, convert elements
        if (value instanceof List) {
            return ((List<?>) value).stream()
                    .map(this::processValue)
                    .collect(Collectors.toList());
        }

        // Return as-is
        return value;
    }

    private List<Class<?>> getNodeClasses(ParseTree node) {
        var nodeClasses = new ArrayList<Class<?>>();
        Class<?> currentNodeClass = node.getClass();
        while (!currentNodeClass.equals(ParserRuleContext.class)) {
            nodeClasses.add(currentNodeClass);
            currentNodeClass = currentNodeClass.getSuperclass();
        }
        return nodeClasses;
    }

    private String getKind(ParseTree node, Parser parser) {
        // Tokens are terminal nodes
        if (node instanceof TerminalNode) {
            var token = ((TerminalNode) node).getSymbol();
            return parser.getVocabulary().getSymbolicName(token.getType());
        }

        if (!(node instanceof ParserRuleContext)) {
            throw new RuntimeException("Expected node to be of class '" + ParserRuleContext.class
                    + "', but got '" + node.getClass() + "'");
        }

        return getKind(((ParserRuleContext) node).getClass());
    }

    private String getKind(Class<? extends ParserRuleContext> nodeClass) {

        String className = nodeClass.getSimpleName();

        // Rules end with context
        if (!className.endsWith("Context")) {
            throw new RuntimeException("Expected classname to end with 'Context' " + nodeClass.getSimpleName());
        }

        return className.substring(0, className.length() - "Context".length());
    }

}
