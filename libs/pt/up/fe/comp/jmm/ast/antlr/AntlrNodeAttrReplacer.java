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

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.antlr.v4.runtime.Parser;
import org.antlr.v4.runtime.tree.ParseTree;

import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;

public class AntlrNodeAttrReplacer extends PreorderJmmVisitor<Void, Void> {

    private final Map<ParseTree, JmmNode> antlrToJmm;

    public AntlrNodeAttrReplacer(Map<ParseTree, JmmNode> antlrToJmm) {
        this.antlrToJmm = antlrToJmm;
    }

    @Override
    protected void buildVisitor() {
        setDefaultVisit(this::replaceAntlrNode);
    }

    private Void replaceAntlrNode(JmmNode node, Void dummy) {

        // Iterate over attributes, looking for ParseTree instances
        for (var attr : node.getAttributes()) {
            var value = node.getObject(attr);

            if (!(value instanceof ParseTree)) {
                continue;
            }

            var jmmNode = antlrToJmm.get(value);

            if (jmmNode == null) {
                System.out.println("Could not find JmmNode for ANTLR node " + value);
                continue;
            }

            node.putObject(attr, jmmNode);

        }

        return null;
    }

}
