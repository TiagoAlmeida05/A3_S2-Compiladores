package pt.up.fe.comp2026.optimization;


import org.specs.comp.ollir.Method;
import org.specs.comp.ollir.Operand;
import pt.up.fe.comp.jmm.analysis.JmmSemanticsResult;
import pt.up.fe.comp.jmm.ollir.JmmOptimization;
import pt.up.fe.comp.jmm.ollir.OllirResult;

import java.util.Collections;
import java.util.List;
import java.util.Map;

public class JmmOptimizationImpl implements JmmOptimization {

    @Override
    public OllirResult toOllir(JmmSemanticsResult semanticsResult) {

        // Create visitor that will generate the OLLIR code
        var visitor = new OllirGeneratorVisitor(semanticsResult.getSymbolTable());

        // Visit the AST and obtain OLLIR code
        var ollirCode = visitor.visit(semanticsResult.getRootNode());

//        System.out.println("\nOLLIR:\n\n" + ollirCode);

        return new OllirResult(semanticsResult, ollirCode, Collections.emptyList());
    }

    @Override
    public JmmSemanticsResult transformAst(JmmSemanticsResult semanticsResult) {

        //TODO: Do your AST-based optimizations here
        return semanticsResult;
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
