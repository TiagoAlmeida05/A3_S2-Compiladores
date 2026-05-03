package pt.up.fe.comp2026.optimization;


import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.Operand;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        var ollirCode = visitor.visit(semanticsResult.getRootNode());

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult transformAst(JmmSemanticsResult semanticsResult) {
        if (!Boolean.parseBoolean(semanticsResult.config().getOrDefault("optimize", "false"))) {
            return semanticsResult;
        }
        var root = semanticsResult.getRootNode();

        boolean changed = true;
        while (changed) {
            changed = foldConstants(root);
            changed |= propagateConstants(root);
            eliminateBranches(root);
        }

        return semanticsResult;
    }

    private boolean foldConstants(JmmNode node) {
        boolean changed = false;

        for (int i = 0; i < node.getNumChildren(); i++) {
            changed |= foldConstants(node.getChild(i));
        }

        if (!node.getKind().toString().toUpperCase().contains("BINARY_EXPR")) return changed;

        var left = node.getChild(0);
        var right = node.getChild(1);
        String op = node.get("op");

        if (left.getKind().toString().toUpperCase().contains("INTEGER_LITERAL") &&
                right.getKind().toString().toUpperCase().contains("INTEGER_LITERAL")) {

            int lv = Integer.parseInt(left.get("value"));
            int rv = Integer.parseInt(right.get("value"));

            switch (op) {
                case "+" -> {
                    replaceWithIntLiteral(node, lv + rv);
                    return true;
                }
                case "-" -> {
                    replaceWithIntLiteral(node, lv - rv);
                    return true;
                }
                case "*" -> {
                    replaceWithIntLiteral(node, lv * rv);
                    return true;
                }
                case "/" -> {
                    if (rv != 0) {
                        replaceWithIntLiteral(node, lv / rv);
                        return true;
                    }
                }
                case "%" -> {
                    if (rv != 0) {
                        replaceWithIntLiteral(node, lv % rv);
                        return true;
                    }
                }
                case "<" -> {
                    replacWithBoolLiteral(node, lv < rv);
                    return true;
                }
                case ">" -> {
                    replacWithBoolLiteral(node, lv > rv);
                    return true;
                }
                case "<=" -> {
                    replacWithBoolLiteral(node, lv <= rv);
                    return true;
                }
                case ">=" -> {
                    replacWithBoolLiteral(node, lv >= rv);
                    return true;
                }
                case "==" -> {
                    replacWithBoolLiteral(node, lv == rv);
                    return true;
                }
                case "!=" -> {
                    replacWithBoolLiteral(node, lv != rv);
                    return true;
                }
            }
        }

        if (left.getKind().toString().toUpperCase().contains("BOOLEAN_LITERAL") &&
                right.getKind().toString().toUpperCase().contains("BOOLEAN_LITERAL")) {

            boolean lv = left.get("value").equals("true");
            boolean rv = right.get("value").equals("true");

            switch (op) {
                case "&&" -> {
                    replacWithBoolLiteral(node, lv && rv);
                    return true;
                }
                case "||" -> {
                    replacWithBoolLiteral(node, lv || rv);
                    return true;
                }
            }
        }

        return changed;
    }

    private void replaceWithIntLiteral(JmmNode node, int value) {
        var left = node.getChild(0);
        var newNode = left.copy();
        newNode.put("value", String.valueOf(value));
        node.replace(newNode);
    }

    private void replacWithBoolLiteral(JmmNode node, boolean value) {
        JmmNode boolChild = null;
        for (int i = 0; i < node.getNumChildren(); i++) {
            String k = node.getChild(i).getKind().toString().toUpperCase();
            if (k.contains("BOOLEAN_LITERAL")) {
                boolChild = node.getChild(i);
                break;
            }
        }
        if (boolChild == null) boolChild = node.getChild(0);

        var newNode = boolChild.copy();
        newNode.put("value", value ? "true" : "false");
        node.replace(newNode);
    }

    private boolean propagateConstants(JmmNode node) {
        return false;
    }

    private void eliminateBranches(JmmNode node) {
        for (int i = 0; i < node.getNumChildren(); i++) {
            eliminateBranches(node.getChild(i));
        }

        for (int i = 0; i < node.getNumChildren(); i++) {
            var child = node.getChild(i);
            if (!child.getKind().toString().toUpperCase().contains("IF_ELSE_STMT")) continue;

            var condNode = child.getChild(0);
            Boolean condValue = evaluateConstantBool(condNode);
            if (condValue == null) continue;

            var keepBlock = condValue ? child.getChild(1) : child.getChild(2);

            var replacements = new java.util.ArrayList<JmmNode>();
            if (keepBlock.getKind().toString().toUpperCase().contains("BLOCK_STMT")) {
                replacements.addAll(keepBlock.getChildren());
            } else {
                replacements.add(keepBlock);
            }

            node.removeChild(i);
            for (int j = 0; j < replacements.size(); j++) {
                node.add(replacements.get(j), i + j);
            }
            i += replacements.size() - 1;
        }
    }

    private Boolean evaluateConstantBool(JmmNode node) {
        String kind = node.getKind().toString().toUpperCase();

        if (kind.contains("BOOLEAN_LITERAL")) {
            return node.get("value").equals("true");
        }

        if (kind.contains("BINARY_EXPR")) {
            String op = node.get("op");
            Boolean left = evaluateConstantBool(node.getChild(0));
            Boolean right = evaluateConstantBool(node.getChild(1));

            if (op.equals("&&")) {
                if (Boolean.FALSE.equals(left) || Boolean.FALSE.equals(right)) return false;
                if (Boolean.TRUE.equals(left) && Boolean.TRUE.equals(right)) return true;
            }
            if (op.equals("||")) {
                if (Boolean.TRUE.equals(left) || Boolean.TRUE.equals(right)) return true;
                if (Boolean.FALSE.equals(left) && Boolean.FALSE.equals(right)) return false;
            }
        }

        if (kind.contains("UNARY_OP") && node.get("op").equals("!")) {
            Boolean inner = evaluateConstantBool(node.getChild(0));
            if (inner != null) return !inner;
        }

        return null;
    }

    @Override
    public OllirResult transformOllir(OllirResult ollirResult) {

        var config = ollirResult.config();
        int r = Integer.parseInt(config.getOrDefault("registerAllocation", "-1"));

        if (r == -1) {
            return ollirResult;
        }

        var classUnit = ollirResult.getOllirClass();

        for (var method : classUnit.getMethods()) {
            if (method.isConstructMethod()) continue;

            allocateRegisters(method, r);
        }

        return ollirResult;
    }

    private void allocateRegisters(Method method, int r) {

        var varTable = method.getVarTable();

        boolean isStatic = method.isStaticMethod();
        int nextReg = isStatic ? 0 : 1;

        Map<String, Integer> regMap = new java.util.HashMap<>();
        List<Integer> freeRegs = new java.util.ArrayList<>();

        for (var param : method.getParams()) {
            if (param instanceof Operand op) {
                int reg = nextReg++;
                varTable.get(op.getName()).setVirtualReg(reg);
                regMap.put(op.getName(), reg);
            }
        }

        for (var entry : varTable.entrySet()) {

            var name = entry.getKey();
            if (name.equals("this")) continue;
            if (regMap.containsKey(name)) continue;

            int reg;

            if (!freeRegs.isEmpty()) {
                reg = freeRegs.remove(0);
            } else {
                reg = nextReg++;
            }

            entry.getValue().setVirtualReg(reg);
            regMap.put(name, reg);

            freeRegs.add(reg);
        }
    }

}
