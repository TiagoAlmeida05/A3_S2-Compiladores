package pt.up.fe.comp2026.optimization;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.*;

public class JmmOptimizationImpl implements JmmOptimization {

    private SymbolTable symbolTable;

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

        this.symbolTable = semanticsResult.getSymbolTable();
        var root = semanticsResult.getRootNode();
        boolean changed = true;

        while (changed) {
            changed = foldConstants(root);
            changed |= propagateConstants(root);
            eliminateBranches(root);
            changed |= eliminateDeadCode(root);
        }

        return semanticsResult;
    }

    private boolean eliminateDeadCode(JmmNode root) {
        boolean changed = false;
        for (var methodDecl : findAllNodes(root, "METHOD_DECL")) {
            changed |= eliminateDeadCodeBackward(methodDecl, methodDecl, new HashSet<>());
        }
        return changed;
    }

    private boolean eliminateDeadCodeBackward(JmmNode node, JmmNode methodDecl, Set<String> liveOut) {
        boolean changed = false;
        String kind = node.getKind().toString().toUpperCase();

        if (kind.contains("BLOCK_STMT") || kind.contains("METHOD_DECL")) {
            List<JmmNode> children = new ArrayList<>(node.getChildren());

            for (int i = children.size() - 1; i >= 0; i--) {
                var child = children.get(i);
                String childKind = child.getKind().toString().toUpperCase();

                if (childKind.contains("ASSIGN_STMT")) {
                    boolean isArrayAssign = childKind.contains("ARRAY_ASSIGN_STMT");
                    String varName = getVarName(child);
                    boolean hasSideEffects = isArrayAssign ? rhsHasSideEffects(child) : rhsHasSideEffects(child.getChild(0));
                    boolean isField = isFieldAssign(varName, methodDecl);
                    boolean isParam = isParam(varName, methodDecl);

                    boolean escapesMethod = isArrayAssign && (isField || isParam);

                    if (!varName.isEmpty() && !isField && !escapesMethod && !hasSideEffects && !liveOut.contains(varName)) {
                        child.getParent().removeChild(child);
                        changed = true;
                    } else {
                        if (!isArrayAssign) {
                            liveOut.remove(varName);
                            collectLiveVarsFromNode(child.getChild(0), liveOut);
                        } else {
                            liveOut.add(varName);
                            for (var c : child.getChildren()) {
                                collectLiveVarsFromNode(c, liveOut);
                            }
                        }
                    }
                } else {
                    changed |= eliminateDeadCodeBackward(child, methodDecl, liveOut);
                }
            }
        } else if (kind.contains("IF_ELSE_STMT")) {
            Set<String> thenLive = new HashSet<>(liveOut);
            Set<String> elseLive = new HashSet<>(liveOut);

            if (node.getNumChildren() > 1) changed |= eliminateDeadCodeBackward(node.getChild(1), methodDecl, thenLive);
            if (node.getNumChildren() > 2) changed |= eliminateDeadCodeBackward(node.getChild(2), methodDecl, elseLive);

            liveOut.clear();
            liveOut.addAll(thenLive);
            liveOut.addAll(elseLive);
            collectLiveVarsFromNode(node.getChild(0), liveOut);

        } else if (kind.contains("WHILE_STMT")) {
            collectLiveVarsFromNode(node, liveOut);
            if (node.getNumChildren() > 1) {
                changed |= eliminateDeadCodeBackward(node.getChild(1), methodDecl, liveOut);
            }
        } else {
            collectLiveVarsFromNode(node, liveOut);
        }

        return changed;
    }

    private void collectLiveVarsFromNode(JmmNode node, Set<String> live) {
        String kind = node.getKind().toString().toUpperCase();

        if (kind.contains("VAR_REF_EXPR")) {
            live.add(node.get("name"));
            return;
        }

        if (kind.contains("ARRAY_ASSIGN_STMT")) {
            if (node.getAttributes().contains("var")) {
                live.add(node.get("var"));
            } else if (node.getAttributes().contains("name")) {
                live.add(node.get("name"));
            }
        }

        for (var child : node.getChildren()) {
            collectLiveVarsFromNode(child, live);
        }
    }

    private String getVarName(JmmNode node) {
        if (node.getAttributes().contains("var")) return node.get("var");

        if (node.getNumChildren() > 0) {
            var firstChild = node.getChild(0);
            if (firstChild.getKind().toString().toUpperCase().contains("VAR_REF_EXPR")) {
                return firstChild.get("name");
            }
        }
        return "";
    }

    private boolean isFieldAssign(String varName, JmmNode methodDecl) {
        if (symbolTable == null) return false;
        var methodOpt = symbolTable.getMethods().stream()
                .filter(m -> m.name().equals(methodDecl.get("name")))
                .findFirst();

        if (methodOpt.isEmpty()) return false;

        var method = methodOpt.get();
        if (method.getLocalVariable(varName).isPresent()) return false;
        if (method.getParameter(varName).isPresent()) return false;
        return symbolTable.getField(varName).isPresent();
    }

    private boolean isParam(String varName, JmmNode methodDecl) {
        if (symbolTable == null) return false;
        return symbolTable.getMethods().stream()
                .filter(m -> m.name().equals(methodDecl.get("name")))
                .findFirst()
                .map(method -> method.getParameter(varName).isPresent())
                .orElse(false);
    }

    private boolean rhsHasSideEffects(JmmNode expr) {
        String kind = expr.getKind().toString().toUpperCase();
        if (kind.contains("METHOD_CALL") || kind.contains("CALL_EXPR")) return true;

        for (var child : expr.getChildren()) {
            if (rhsHasSideEffects(child)) return true;
        }
        return false;
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
                case "+" -> { replaceWithIntLiteral(node, lv + rv); return true; }
                case "-" -> { replaceWithIntLiteral(node, lv - rv); return true; }
                case "*" -> { replaceWithIntLiteral(node, lv * rv); return true; }
                case "/" -> { if (rv != 0) { replaceWithIntLiteral(node, lv / rv); return true; } }
                case "%" -> { if (rv != 0) { replaceWithIntLiteral(node, lv % rv); return true; } }
                case "<"  -> { replaceWithBoolLiteral(node, lv < rv);  return true; }
                case ">"  -> { replaceWithBoolLiteral(node, lv > rv);  return true; }
                case "<=" -> { replaceWithBoolLiteral(node, lv <= rv); return true; }
                case ">=" -> { replaceWithBoolLiteral(node, lv >= rv); return true; }
                case "==" -> { replaceWithBoolLiteral(node, lv == rv); return true; }
                case "!=" -> { replaceWithBoolLiteral(node, lv != rv); return true; }
            }
        }

        if (left.getKind().toString().toUpperCase().contains("BOOLEAN_LITERAL") &&
                right.getKind().toString().toUpperCase().contains("BOOLEAN_LITERAL")) {

            boolean lv = left.get("value").equals("true");
            boolean rv = right.get("value").equals("true");

            switch (op) {
                case "&&" -> { replaceWithBoolLiteral(node, lv && rv); return true; }
                case "||" -> { replaceWithBoolLiteral(node, lv || rv); return true; }
            }
        }

        return changed;
    }

    private void replaceWithIntLiteral(JmmNode node, int value) {
        var newNode = node.getChild(0).copy();
        newNode.put("value", String.valueOf(value));
        node.replace(newNode);
    }

    private void replaceWithBoolLiteral(JmmNode node, boolean value) {
        JmmNode boolChild = null;
        for (int i = 0; i < node.getNumChildren(); i++) {
            if (node.getChild(i).getKind().toString().toUpperCase().contains("BOOLEAN_LITERAL")) {
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
        boolean changed = false;
        for (var methodDecl : findAllNodes(node, "METHOD_DECL")) {
            changed |= propagateInMethod(methodDecl);
        }
        return changed;
    }

    private boolean propagateInMethod(JmmNode methodDecl) {
        var stmts = methodDecl.getChildren().stream()
                .filter(c -> c.getKind().toString().toUpperCase().contains("STMT") ||
                        c.getKind().toString().toUpperCase().contains("RETURN"))
                .toList();

        var constants = new HashMap<String, JmmNode>();
        boolean changed = false;

        for (var stmt : stmts) {
            changed |= propagateInStmt(stmt, constants);
        }
        return changed;
    }

    private boolean propagateInStmt(JmmNode stmt, Map<String, JmmNode> constants) {
        boolean changed = false;
        String kind = stmt.getKind().toString().toUpperCase();

        if (kind.contains("ASSIGN_STMT") && !kind.contains("ARRAY_ASSIGN_STMT")) {
            String varName = stmt.get("var");
            changed |= propagateInExpr(stmt.getChild(0), constants);

            if (isLiteral(stmt.getChild(0))) constants.put(varName, stmt.getChild(0));
            else constants.remove(varName);

        } else if (kind.contains("ARRAY_ASSIGN_STMT")) {
            changed |= propagateInChildren(stmt, constants);

        } else if (kind.contains("IF_ELSE_STMT")) {
            changed |= propagateInExpr(stmt.getChild(0), constants);

            var killedIf = new HashSet<String>();
            if (stmt.getNumChildren() > 1) killedIf.addAll(collectAssignedVars(stmt.getChild(1)));
            if (stmt.getNumChildren() > 2) killedIf.addAll(collectAssignedVars(stmt.getChild(2)));

            if (stmt.getNumChildren() > 1) {
                changed |= propagateInBlock(stmt.getChild(1), new HashMap<>(constants));
            }

            if (stmt.getNumChildren() > 2) {
                changed |= propagateInBlock(stmt.getChild(2), new HashMap<>(constants));
            }

            for (var v : killedIf) {
                constants.remove(v);
            }

        } else if (kind.contains("WHILE_STMT")) {
            var killedWhile = stmt.getNumChildren() > 1 ? collectAssignedVars(stmt.getChild(1)) : new HashSet<String>();
            for (var v : killedWhile) constants.remove(v);

            changed |= propagateInExpr(stmt.getChild(0), constants);
            if (stmt.getNumChildren() > 1) {
                changed |= propagateInBlock(stmt.getChild(1), new HashMap<>(constants));
            }

        } else if (kind.contains("BLOCK_STMT")) {
            for (var child : stmt.getChildren()) changed |= propagateInStmt(child, constants);

        } else {
            changed |= propagateInChildren(stmt, constants);
        }

        return changed;
    }

    private boolean propagateInBlock(JmmNode block, Map<String, JmmNode> constants) {
        if (block.getKind().toString().toUpperCase().contains("BLOCK_STMT")) {
            boolean changed = false;
            for (var child : block.getChildren()) {
                changed |= propagateInStmt(child, constants);
            }
            return changed;
        }
        return propagateInStmt(block, constants);
    }

    private boolean propagateInChildren(JmmNode node, Map<String, JmmNode> constants) {
        boolean changed = false;
        for (int i = 0; i < node.getNumChildren(); i++) {
            changed |= propagateInExpr(node.getChild(i), constants);
        }
        return changed;
    }

    private boolean propagateInExpr(JmmNode expr, Map<String, JmmNode> constants) {
        boolean changed = false;
        for (int i = 0; i < expr.getNumChildren(); i++) {
            changed |= propagateInExpr(expr.getChild(i), constants);
        }

        if (expr.getKind().toString().toUpperCase().contains("VAR_REF_EXPR")) {
            String name = expr.get("name");
            if (constants.containsKey(name)) {
                expr.replace(constants.get(name).copy());
                return true;
            }
        }
        return changed;
    }

    private boolean isLiteral(JmmNode node) {
        String k = node.getKind().toString().toUpperCase();
        return k.contains("INTEGER_LITERAL") || k.contains("BOOLEAN_LITERAL");
    }

    private Set<String> collectAssignedVars(JmmNode node) {
        var result = new HashSet<String>();
        collectAssignedVarsHelper(node, result);
        return result;
    }

    private void collectAssignedVarsHelper(JmmNode node, Set<String> acc) {
        String kind = node.getKind().toString().toUpperCase();
        if (kind.contains("ASSIGN_STMT") && !kind.contains("ARRAY_ASSIGN_STMT")) {
            acc.add(node.get("var"));
        }
        for (var child : node.getChildren()) {
            collectAssignedVarsHelper(child, acc);
        }
    }

    private List<JmmNode> findAllNodes(JmmNode root, String kindFragment) {
        var result = new ArrayList<JmmNode>();
        findAllNodesHelper(root, kindFragment, result);
        return result;
    }

    private void findAllNodesHelper(JmmNode node, String kindFragment, List<JmmNode> acc) {
        if (node.getKind().toString().toUpperCase().contains(kindFragment)) {
            acc.add(node);
        }
        for (var child : node.getChildren()) {
            findAllNodesHelper(child, kindFragment, acc);
        }
    }

    private void eliminateBranches(JmmNode node) {
        for (int i = 0; i < node.getNumChildren(); i++) {
            eliminateBranches(node.getChild(i));
        }

        for (int i = 0; i < node.getNumChildren(); i++) {
            var child = node.getChild(i);
            if (!child.getKind().toString().toUpperCase().contains("IF_ELSE_STMT")) continue;

            Boolean condValue = evaluateConstantBool(child.getChild(0));
            if (condValue == null) continue;

            var keepBlock = condValue ? child.getChild(1) : child.getChild(2);
            var replacements = new ArrayList<JmmNode>();

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

        if (kind.contains("BOOLEAN_LITERAL")) return node.get("value").equals("true");

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
        if (r == -1) return ollirResult;

        var classUnit = ollirResult.getOllirClass();
        for (var method : classUnit.getMethods()) {
            if (method.isConstructMethod()) continue;
            allocateRegisters(method, r);
        }
        return ollirResult;
    }

    private void allocateRegisters(Method method, int r) {
        var varTable = method.getVarTable();
        Set<String> paramNames = new HashSet<>();
        Map<String, Integer> color = new HashMap<>();

        for (Map.Entry<String, Descriptor> entry : varTable.entrySet()) {
            String varName = entry.getKey();
            Descriptor desc = entry.getValue();
            if (desc.getScope() == VarScope.PARAMETER || varName.equals("this")) {
                paramNames.add(varName);
                color.put(varName, desc.getVirtualReg());
            }
        }

        List<String> locals = new ArrayList<>();
        for (String name : varTable.keySet()) {
            if (!paramNames.contains(name)) {
                locals.add(name);
            }
        }
        if (locals.isEmpty()) return;

        var instructions = method.getInstructions();
        int n = instructions.size();
        Map<Instruction, Integer> instrIndex = new IdentityHashMap<>();
        for (int i = 0; i < n; i++) instrIndex.put(instructions.get(i), i);

        List<Set<String>> use = new ArrayList<>();
        List<Set<String>> def = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            use.add(new HashSet<>());
            def.add(new HashSet<>());
        }

        for (int i = 0; i < n; i++) {
            collectUseDef(instructions.get(i), use.get(i), def.get(i));
        }

        List<Set<Integer>> succs = new ArrayList<>();
        for (int i = 0; i < n; i++) succs.add(new HashSet<>());

        for (int i = 0; i < n; i++) {
            var instr = instructions.get(i);
            var successors = instr.getSuccessors();
            if (successors != null && !successors.isEmpty()) {
                for (var succ : successors) {
                    Integer idx = instrIndex.get(succ);
                    if (idx != null) succs.get(i).add(idx);
                }
            } else if (i + 1 < n) {
                succs.get(i).add(i + 1);
            }
        }

        List<Set<String>> liveIn  = new ArrayList<>();
        List<Set<String>> liveOut = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            liveIn.add(new HashSet<>());
            liveOut.add(new HashSet<>());
        }

        boolean changed = true;
        while (changed) {
            changed = false;
            for (int i = n - 1; i >= 0; i--) {
                Set<String> newOut = new HashSet<>();
                for (int s : succs.get(i)) newOut.addAll(liveIn.get(s));

                Set<String> newIn = new HashSet<>(use.get(i));
                Set<String> outMinusDef = new HashSet<>(newOut);
                outMinusDef.removeAll(def.get(i));
                newIn.addAll(outMinusDef);

                if (!newIn.equals(liveIn.get(i)) || !newOut.equals(liveOut.get(i))) {
                    changed = true;
                    liveIn.set(i, newIn);
                    liveOut.set(i, newOut);
                }
            }
        }

        Map<String, Set<String>> interferes = new HashMap<>();
        for (String v : varTable.keySet()) interferes.put(v, new HashSet<>());

        for (int i = 0; i < n; i++) {
            Set<String> live = new HashSet<>(liveOut.get(i));
            for (String d : def.get(i)) {
                for (String o : live) {
                    if (o.equals(d)) continue;
                    interferes.get(d).add(o);
                    interferes.get(o).add(d);
                }
            }
            List<String> liveInList = new ArrayList<>(liveIn.get(i));
            for (int a = 0; a < liveInList.size(); a++) {
                for (int b = a + 1; b < liveInList.size(); b++) {
                    String va = liveInList.get(a), vb = liveInList.get(b);
                    interferes.get(va).add(vb);
                    interferes.get(vb).add(va);
                }
            }
        }

        List<String> order = new ArrayList<>(locals);
        order.sort((a, b) -> interferes.get(b).size() - interferes.get(a).size());

        boolean isStatic = method.isStaticMethod();
        int firstLocal = (isStatic ? 0 : 1) + method.getParams().size();

        for (String v : order) {
            Set<Integer> usedColors = new HashSet<>();
            for (String neighbour : interferes.get(v)) {
                if (color.containsKey(neighbour)) {
                    usedColors.add(color.get(neighbour));
                }
            }

            int c = firstLocal;
            while (usedColors.contains(c)) c++;
            color.put(v, c);
            varTable.get(v).setVirtualReg(c);
        }
    }

    private void collectUseDef(Instruction instr, Set<String> use, Set<String> def) {
        if (instr instanceof AssignInstruction assign) {
            var dest = assign.getDest();
            if (dest instanceof Operand op) {
                def.add(op.getName());
            }
            collectUseFromInstruction(assign.getRhs(), use);
        } else {
            collectUseFromInstruction(instr, use);
        }
    }

    private void collectUseFromInstruction(Instruction instr, Set<String> use) {
        if (instr instanceof BinaryOpInstruction bin) {
            addElementToUse(bin.getLeftOperand(), use);
            addElementToUse(bin.getRightOperand(), use);

        } else if (instr instanceof SingleOpInstruction single) {
            addElementToUse(single.getSingleOperand(), use);

        } else if (instr instanceof UnaryOpInstruction unary) {
            addElementToUse(unary.getOperand(), use);

        } else if (instr instanceof CallInstruction call) {
            addElementToUse(call.getCaller(), use);
            if (call.getArguments() != null) {
                for (var arg : call.getArguments()) addElementToUse(arg, use);
            }

        } else if (instr instanceof ReturnInstruction ret) {
            if (ret.hasReturnValue()) {
                ret.getOperand().ifPresent(elem -> addElementToUse(elem, use));
            }

        } else if (instr instanceof GetFieldInstruction getField) {
            addElementToUse(getField.getObject(), use);

        } else if (instr instanceof PutFieldInstruction putField) {
            addElementToUse(putField.getObject(), use);
            addElementToUse(putField.getValue(), use);

        } else if (instr instanceof OpCondInstruction opCond) {
            collectUseFromInstruction(opCond.getCondition(), use);

        } else if (instr instanceof SingleOpCondInstruction singleCond) {
            addElementToUse(singleCond.getCondition().getSingleOperand(), use);

        } else if (instr instanceof AssignInstruction assign) {
            addElementToUse(assign.getDest(), use);
            collectUseFromInstruction(assign.getRhs(), use);
        }
    }

    private void addElementToUse(Element elem, Set<String> use) {
        if (elem instanceof Operand op) {
            use.add(op.getName());
        }
    }
}