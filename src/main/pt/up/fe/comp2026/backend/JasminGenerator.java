package pt.up.fe.comp2026.backend;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.tree.TreeNode;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp2026.optimization.OptUtils;
import pt.up.fe.specs.util.classmap.FunctionClassMap;
import pt.up.fe.specs.util.exceptions.NotImplementedException;
import pt.up.fe.specs.util.utilities.StringLines;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class JasminGenerator {

    private static final String NL = "\n";
    private static final String TAB = "   ";

    private final OllirResult ollirResult;
    private List<Report> reports;
    private String code;
    private Method currentMethod;
    boolean isInsideAssignment;
    private final JasminUtils types;
    private OptUtils utils;
    private final FunctionClassMap<TreeNode, String> generators;

    private int labelCounter = 0;

    public JasminGenerator(OllirResult ollirResult) {
        this.ollirResult = ollirResult;
        reports = new ArrayList<>();
        code = null;
        currentMethod = null;
        isInsideAssignment = false;
        types = new JasminUtils(ollirResult);
        utils = null;
        this.generators = new FunctionClassMap<>();
        generators.put(ClassUnit.class, this::generateClassUnit);
        generators.put(Method.class, this::generateMethod);
        generators.put(AssignInstruction.class, this::generateAssign);
        generators.put(SingleOpInstruction.class, this::generateSingleOp);
        generators.put(LiteralElement.class, this::generateLiteral);
        generators.put(ArrayOperand.class, this::generateArrayOperand);
        generators.put(Operand.class, this::generateOperand);
        generators.put(BinaryOpInstruction.class, this::generateBinaryOp);
        generators.put(ReturnInstruction.class, this::generateReturn);
        generators.put(NewInstruction.class, this::generateNew);
        generators.put(ArrayLengthInstruction.class, this::generateArrayLength);
        generators.put(CallInstruction.class, this::generateCall);
        generators.put(PutFieldInstruction.class, this::generatePutField);
        generators.put(GetFieldInstruction.class, this::generateGetField);
        generators.put(SingleOpCondInstruction.class, this::generateSingleOpCond);
        generators.put(OpCondInstruction.class, this::generateOpCond);
        generators.put(GotoInstruction.class, this::generateGoto);
        generators.put(UnaryOpInstruction.class, this::generateUnaryOp);
    }

    private String apply(TreeNode node) {
        return generators.apply(node);
    }

    public List<Report> getReports() {
        return reports;
    }

    public String build() {
        if (code == null) {
            code = apply(ollirResult.getOllirClass());
        }
        return code;
    }

    private String escapeName(String name) {
        if (name.equals("field") || name.equals("method") || name.equals("limit") || name.equals("class")) {
            return "'" + name + "'";
        }
        return name;
    }

    private String generateClassUnit(ClassUnit classUnit) {
        var code = new StringBuilder();

        var nameWithPackage = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
        code.append(".class public ").append(nameWithPackage).append(NL).append(NL);

        var superClass = ollirResult.getOllirClass().getSuperClass();
        String fullSuperClass;
        if (superClass == null || superClass.equals("Object")) {
            fullSuperClass = "java/lang/Object";
        } else {
            fullSuperClass = types.resolveClassName(superClass);
            if (fullSuperClass.equals(superClass) && !superClass.contains("/")) {
                fullSuperClass = resolveImport(superClass);
            }
        }
        code.append(".super ").append(fullSuperClass).append(NL).append(NL);

        for (var field : ollirResult.getOllirClass().getFields()) {
            var accessModifier = types.getModifier(field.getFieldAccessModifier());
            var fieldName = escapeName(field.getFieldName().trim());
            var fieldType = types.getTypeDescriptor(field.getFieldType()).trim();
            code.append(".field ").append(accessModifier)
                    .append(fieldName).append(" ").append(fieldType).append(NL);
        }

        code.append(NL);

        var defaultConstructor = ""
                + "; default constructor\n"
                + ".method public <init>()V\n"
                + TAB + ".limit stack 1\n"
                + TAB + ".limit locals 1\n"
                + TAB + "aload_0\n"
                + TAB + "invokespecial " + fullSuperClass + "/<init>()V\n"
                + TAB + "return\n"
                + ".end method\n";
        code.append(defaultConstructor);

        for (var method : ollirResult.getOllirClass().getMethods()) {
            if (method.isConstructMethod()) continue;
            code.append(apply(method));
        }

        return code.toString();
    }

    private String resolveImport(String shortName) {
        for (var imp : ollirResult.getOllirClass().getImports()) {
            var cleanImp = imp.replace('.', '/');
            var parts = cleanImp.split("/");
            if (parts[parts.length - 1].equals(shortName)) {
                return cleanImp;
            }
        }
        if (shortName.equals("Exception") || shortName.equals("String") || shortName.equals("Object") ||
                shortName.equals("Thread") || shortName.equals("RuntimeException") || shortName.equals("System")) {
            return "java/lang/" + shortName;
        }
        return shortName;
    }

    private String generateMethod(Method method) {
        currentMethod = method;
        utils = new OptUtils(null);

        var code = new StringBuilder();

        var modifier = types.getModifier(AccessModifier.PUBLIC);
        var staticMod = method.isStaticMethod() ? "static " : "";
        var methodName = method.getMethodName();

        var params = method.getParams().stream()
                .map(p -> types.getTypeDescriptor(p.getType()))
                .collect(Collectors.joining());

        var returnType = types.getTypeDescriptor(method.getReturnType());

        code.append("\n.method ").append(modifier)
                .append(staticMod)
                .append(methodName)
                .append("(").append(params).append(")").append(returnType).append(NL);

        var bodyCode = new StringBuilder();
        for (var inst : method.getInstructions()) {

            for (var label : method.getLabels(inst)) {
                bodyCode.append(TAB).append(label).append(":").append(NL);
            }

            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            bodyCode.append(instCode);

            if (inst instanceof CallInstruction call && !isInsideAssignment) {
                if (!isVoidType(call.getReturnType())) {
                    bodyCode.append(TAB).append("pop").append(NL);
                }
            }
        }

        int limitLocals = computeLimitLocals(method);
        int limitStack = computeLimitStack(method);

        code.append(TAB).append(".limit stack ").append(limitStack).append(NL);
        code.append(TAB).append(".limit locals ").append(limitLocals).append(NL);
        code.append(TAB).append(bodyCode);
        code.append(".end method\n");

        currentMethod = null;
        return code.toString();
    }

    private int computeLimitLocals(Method method) {
        var varTable = method.getVarTable();
        if (varTable == null || varTable.isEmpty()) {
            return method.isStaticMethod() ? 0 : 1;
        }
        int maxReg = varTable.values().stream()
                .mapToInt(Descriptor::getVirtualReg)
                .max()
                .orElse(0);
        return maxReg + 1;
    }

    private int computeLimitStack(Method method) {
        int max = 0;
        for (var inst : method.getInstructions()) {
            int peak = getInstructionPeakStack(inst);
            if (peak > max) max = peak;
        }
        return Math.max(max, 1);
    }

    private int getInstructionPeakStack(Instruction inst) {
        if (inst instanceof AssignInstruction assign) {
            int base = 0;
            if (assign.getDest() instanceof ArrayOperand) {
                base = 2;
            }
            return base + getExprPeakStack(assign.getRhs());
        } else if (inst instanceof CallInstruction call) {
            return getCallPeakStack(call);
        } else if (inst instanceof ReturnInstruction ret) {
            return ret.hasReturnValue() ? 1 : 0;
        } else if (inst instanceof PutFieldInstruction) {
            return 2;
        } else if (inst instanceof SingleOpCondInstruction) {
            return 1;
        } else if (inst instanceof OpCondInstruction) {
            return 2;
        } else if (inst instanceof GetFieldInstruction) {
            return 1;
        }
        return 1;
    }

    private int getExprPeakStack(Instruction inst) {
        if (inst instanceof CallInstruction call) {
            return getCallPeakStack(call);
        } else if (inst instanceof BinaryOpInstruction) {
            return 2;
        } else if (inst instanceof SingleOpInstruction) {
            return 1;
        } else if (inst instanceof ArrayLengthInstruction) {
            return 1;
        } else if (inst instanceof NewInstruction) {
            return 1;
        } else if (inst instanceof GetFieldInstruction) {
            return 1;
        }
        return 1;
    }

    private int getCallPeakStack(CallInstruction call) {
        int peak = call.getArguments().size();
        var callStr = call.toString().trim().toLowerCase();
        if (!callStr.contains("invokestatic")) {
            peak += 1;
        }
        return peak;
    }

    private String generateAssign(AssignInstruction assign) {
        try {
            isInsideAssignment = true;

            var code = new StringBuilder();
            var lhs = assign.getDest();

            if (lhs instanceof ArrayOperand arrayOp) {
                var arrayReg = currentMethod.getVarTable().get(arrayOp.getName()).getVirtualReg();
                code.append(loadRef(arrayReg)).append(NL);
                var indexOp = arrayOp.getIndexOperands().get(0);
                code.append(apply(indexOp));
                code.append(apply(assign.getRhs()));
                code.append("iastore").append(NL);
                return code.toString();
            }

            if (assign.getRhs() instanceof NewInstruction) {
                code.append(apply(assign.getRhs()));
                var operand = (Operand) lhs;
                var reg = currentMethod.getVarTable().get(operand.getName());
                var lhsType = reg.getVarType();
                if (lhsType instanceof ArrayType || lhsType instanceof ClassType) {
                    code.append("astore ").append(reg.getVirtualReg()).append(NL);
                } else {
                    code.append(types.getStore(reg)).append(NL);
                }
                return code.toString();
            }

            if (assign.getRhs() instanceof CallInstruction rhsCall) {
                String methodName = null;
                try {
                    methodName = ((LiteralElement) rhsCall.getMethodName()).getLiteral().replace("\"", "");
                } catch (Exception e) {
                }

                if (methodName == null) {
                    var operand2 = (Operand) lhs;
                    var reg2 = currentMethod.getVarTable().get(operand2.getName());
                    var caller = rhsCall.getCaller();
                    boolean callerHandled = false;
                    if (caller instanceof Operand callerOp) {
                        var callerName = callerOp.getName();
                        if (!callerName.equals("array")) {
                            var callerReg = currentMethod.getVarTable().get(callerName);
                            if (callerReg != null) {
                                code.append(loadRef(callerReg.getVirtualReg())).append(NL);
                                callerHandled = true;
                            }
                        }
                    }
                    if (!callerHandled) {
                        var args = rhsCall.getArguments();
                        if (!args.isEmpty() && args.get(0) instanceof Operand argOp) {
                            var argReg = currentMethod.getVarTable().get(argOp.getName());
                            if (argReg != null) {
                                code.append(loadRef(argReg.getVirtualReg())).append(NL);
                            } else {
                                code.append(apply(args.get(0)));
                            }
                        } else if (!args.isEmpty()) {
                            code.append(apply(args.get(0)));
                        } else {
                            code.append(apply(caller));
                        }
                    }
                    code.append("arraylength").append(NL);
                    code.append("istore ").append(reg2.getVirtualReg()).append(NL);
                    return code.toString();
                }

                code.append(apply(assign.getRhs()));

                var operand = (Operand) lhs;
                var reg = currentMethod.getVarTable().get(operand.getName());

                String realDesc = null;
                if (rhsCall.getCaller() instanceof Operand callerOp) {
                    var callerClassName = types.resolveClassName(callerOp.getName());
                    realDesc = types.resolveReturnDescriptorViaReflection(
                            callerClassName, methodName, rhsCall.getArguments().size());
                }

                if (realDesc == null) {
                    realDesc = getRealReturnDescriptor(
                            methodName,
                            rhsCall.getArguments().stream()
                                    .map(a -> types.getTypeDescriptor(a.getType()))
                                    .collect(Collectors.joining()),
                            rhsCall.getReturnType());
                }

                if (realDesc.equals("V")) {
                    return code.toString();
                }

                if (realDesc.startsWith("[") || realDesc.startsWith("L")) {
                    code.append("astore ").append(reg.getVirtualReg()).append(NL);
                    return code.toString();
                }

                code.append(types.getStore(reg)).append(NL);
                return code.toString();
            }

            code.append(apply(assign.getRhs()));

            var operand = (Operand) lhs;
            var reg = currentMethod.getVarTable().get(operand.getName());
            code.append(types.getStore(reg)).append(NL);

            return code.toString();
        } finally {
            isInsideAssignment = false;
        }
    }

    private String generateSingleOp(SingleOpInstruction singleOp) {
        return apply(singleOp.getSingleOperand());
    }

    private String generateLiteral(LiteralElement literal) {
        var type = literal.getType();
        if (type instanceof BuiltinType bt) {
            var kind = bt.getKind();
            if (kind == BuiltinKind.INT32 || kind == BuiltinKind.BOOLEAN) {
                try {
                    int value = Integer.parseInt(literal.getLiteral());
                    if (value >= -1 && value <= 5) {
                        return (value == -1 ? "iconst_m1" : "iconst_" + value) + NL;
                    } else if (value >= -128 && value <= 127) {
                        return "bipush " + value + NL;
                    } else if (value >= -32768 && value <= 32767) {
                        return "sipush " + value + NL;
                    }
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return "ldc " + literal.getLiteral() + NL;
    }

    private String generateArrayOperand(ArrayOperand arrayOp) {
        var code = new StringBuilder();
        var reg = currentMethod.getVarTable().get(arrayOp.getName()).getVirtualReg();
        code.append(loadRef(reg)).append(NL);
        code.append(apply(arrayOp.getIndexOperands().get(0)));
        code.append("iaload").append(NL);
        return code.toString();
    }

    private String generateOperand(Operand operand) {
        if (operand.getName().equals("this")) {
            return "aload_0" + NL;
        }
        var reg = currentMethod.getVarTable().get(operand.getName());
        if (reg == null) {
            throw new RuntimeException("Variable not found in var table: " + operand.getName());
        }
        return types.getLoad(reg) + NL;
    }

    private String generateBinaryOp(BinaryOpInstruction binaryOp) {
        var code = new StringBuilder();
        var opType = binaryOp.getOperation().getOpType();

        if (opType == OperationType.LTH) {
            String trueLabel = "lth_true_" + labelCounter;
            String endLabel = "lth_end_" + labelCounter;
            labelCounter++;

            code.append(apply(binaryOp.getLeftOperand()));
            code.append(apply(binaryOp.getRightOperand()));
            code.append("if_icmplt ").append(trueLabel).append(NL);
            code.append("iconst_0").append(NL);
            code.append("goto ").append(endLabel).append(NL);
            code.append(trueLabel).append(":").append(NL);
            code.append("iconst_1").append(NL);
            code.append(endLabel).append(":").append(NL);
            return code.toString();
        }

        if (opType == OperationType.LOGICAL_NOT) {
            String trueLabel = "not_true_" + labelCounter;
            String endLabel = "not_end_" + labelCounter;
            labelCounter++;

            code.append(apply(binaryOp.getLeftOperand())); // o operando único
            code.append("ifeq ").append(trueLabel).append(NL); // se == 0 (false), salta para true
            code.append("iconst_0").append(NL);
            code.append("goto ").append(endLabel).append(NL);
            code.append(trueLabel).append(":").append(NL);
            code.append("iconst_1").append(NL);
            code.append(endLabel).append(":").append(NL);
            return code.toString();
        }

        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        var typePrefix = types.getTypePrefix(binaryOp.getOperation().getTypeInfo());

        var op = switch (opType) {
            case ADD -> "add";
            case SUB -> "sub";
            case MUL -> "mul";
            case DIV -> "div";
            case REM -> "rem";
            default -> throw new NotImplementedException(opType);
        };

        code.append(typePrefix).append(op).append(NL);
        return code.toString();
    }

    private String generateReturn(ReturnInstruction returnInst) {
        var code = new StringBuilder();
        var returnType = returnInst.getReturnType();

        if (returnType instanceof BuiltinType bt && bt.getKind() == BuiltinKind.VOID) {
            code.append("return").append(NL);
            return code.toString();
        }

        returnInst.getOperand().ifPresent(op -> code.append(apply(op)));
        var typePrefix = types.getTypePrefix(returnType);
        code.append(typePrefix).append("return").append(NL);
        return code.toString();
    }

    private String generateNew(NewInstruction newInst) {
        var code = new StringBuilder();
        var type = newInst.getReturnType();

        if (type instanceof ArrayType) {
            var operands = newInst.getOperands();
            var sizeOperand = operands.stream()
                    .filter(op -> !(op instanceof Operand o && o.getName().equals("array")))
                    .findFirst()
                    .orElse(operands.get(operands.size() - 1));

            if (sizeOperand instanceof Operand op && !op.getName().equals("array")) {
                var reg = currentMethod.getVarTable().get(op.getName());
                if (reg != null) {
                    int n = reg.getVirtualReg();
                    code.append("iload").append(n < 4 ? "_" : " ").append(n).append(NL);
                } else {
                    code.append(apply(sizeOperand));
                }
            } else {
                code.append(apply(sizeOperand));
            }

            code.append("newarray int").append(NL);
        } else {
            var className = types.getTypeDescriptor(type);
            className = className.substring(1, className.length() - 1);
            code.append("new ").append(className).append(NL);

        }

        return code.toString();
    }

    private String generateArrayLength(ArrayLengthInstruction inst) {
        var code = new StringBuilder();
        var operand = inst.getOperands().get(0);
        if (operand instanceof Operand op && !op.getName().equals("this")) {
            var reg = currentMethod.getVarTable().get(op.getName());
            if (reg != null) {
                int n = reg.getVirtualReg();
                code.append("aload").append(n < 4 ? "_" : " ").append(n).append(NL);
                code.append("arraylength").append(NL);
                return code.toString();
            }
        }
        code.append(apply(operand));
        code.append("arraylength").append(NL);
        return code.toString();
    }

    private String generateCall(CallInstruction call) {
        var callStr = call.toString().trim().toLowerCase();

        if (callStr.contains("invokevirtual")) {
            return getVirtualCall(call);
        } else if (callStr.contains("invokestatic")) {
            return getStaticCall(call);
        } else if (callStr.contains("invokespecial")) {
            return getSpecialCall(call);
        } else {
            throw new NotImplementedException("Unknown call type in: " + call);
        }
    }

    private String getVirtualCall(CallInstruction call) {
        var code = new StringBuilder();

        var caller = call.getCaller();
        var callerType = caller.getType();
        String className;
        if (callerType instanceof ClassType ct) {
            var resolved = types.resolveClassName(ct.getName());
            var currentClassName = ollirResult.getOllirClass().getClassName();
            var currentFQN = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
            if (resolved.equals(ct.getName()) && (ct.getName().equals(currentClassName) || ct.getName().equals(currentFQN))) {
                className = currentFQN;
            } else {
                className = resolved;
            }
        } else {
            className = types.resolveClassName("this");
        }
        var methodName = ((LiteralElement) call.getMethodName()).getLiteral().replace("\"", "");

        code.append(apply(call.getCaller()));

        for (var arg : call.getArguments()) {
            code.append(apply(arg));
        }

        var args = call.getArguments();
        var resolvedParams = types.resolveParamDescriptorsViaReflection(
                className, methodName, args.size());

        String argsDescriptor;
        if (resolvedParams != null) {
            argsDescriptor = String.join("", resolvedParams);
        } else {
            argsDescriptor = args.stream()
                    .map(a -> types.getTypeDescriptor(a.getType()))
                    .collect(Collectors.joining());
        }

        var returnDescriptor = getRealReturnDescriptor(methodName, argsDescriptor, call.getReturnType());

        if (returnDescriptor.equals(types.getTypeDescriptor(call.getReturnType()))) {
            var resolvedReturn = types.resolveReturnDescriptorViaReflection(
                    className, methodName, call.getArguments().size());
            if (resolvedReturn != null) returnDescriptor = resolvedReturn;
        }

        code.append("invokevirtual ").append(className).append("/")
                .append(methodName).append("(").append(argsDescriptor).append(")")
                .append(returnDescriptor).append(NL);

        return code.toString();
    }

    private String getRealReturnDescriptor(String methodName, String argsDescriptor, Type ollirReturnType) {
        for (var method : ollirResult.getOllirClass().getMethods()) {
            if (!method.getMethodName().equals(methodName)) continue;
            var methodArgsDescriptor = method.getParams().stream()
                    .map(p -> types.getTypeDescriptor(p.getType()))
                    .collect(Collectors.joining());
            if (!methodArgsDescriptor.equals(argsDescriptor)) continue;
            return types.getTypeDescriptor(method.getReturnType());
        }
        return types.getTypeDescriptor(ollirReturnType);
    }

    private String getStaticCall(CallInstruction call) {
        var code = new StringBuilder();

        var caller = (Operand) call.getCaller();
        var className = types.resolveClassName(caller.getName());
        var methodName = ((LiteralElement) call.getMethodName()).getLiteral().replace("\"", "");

        for (var arg : call.getArguments()) {
            code.append(apply(arg));
        }

        var argsDescriptor = call.getArguments().stream()
                .map(a -> types.getTypeDescriptor(a.getType()))
                .collect(Collectors.joining());
        var resolvedReturn = types.resolveReturnDescriptorViaReflection(
                className, methodName, call.getArguments().size());
        var returnDescriptor = resolvedReturn != null
                ? resolvedReturn
                : types.getTypeDescriptor(call.getReturnType());

        code.append("invokestatic ").append(className).append("/")
                .append(methodName).append("(").append(argsDescriptor).append(")")
                .append(returnDescriptor).append(NL);

        return code.toString();
    }

    private String getSpecialCall(CallInstruction call) {
        var code = new StringBuilder();

        var caller = (Operand) call.getCaller();
        String className;
        var callerType = caller.getType();
        if (callerType instanceof ClassType ct) {
            className = types.resolveClassName(ct.getName());
        } else {
            var superClass = ollirResult.getOllirClass().getSuperClass();
            className = superClass == null ? "java/lang/Object" : types.resolveClassName(superClass);
        }
        var methodName = ((LiteralElement) call.getMethodName()).getLiteral().replace("\"", "");

        code.append(apply(call.getCaller()));

        for (var arg : call.getArguments()) {
            code.append(apply(arg));
        }

        var argsDescriptor = call.getArguments().stream()
                .map(a -> types.getTypeDescriptor(a.getType()))
                .collect(Collectors.joining());
        var returnDescriptor = types.getTypeDescriptor(call.getReturnType());

        code.append("invokespecial ").append(className).append("/")
                .append(methodName).append("(").append(argsDescriptor).append(")")
                .append(returnDescriptor).append(NL);

        return code.toString();
    }

    private boolean isVoidType(Type type) {
        if (type instanceof BuiltinType bt) {
            return bt.getKind() == BuiltinKind.VOID;
        }
        return false;
    }

    private String loadRef(int reg) {
        return "aload" + (reg < 4 ? "_" : " ") + reg;
    }

    private String generatePutField(PutFieldInstruction putField) {
        var code = new StringBuilder();
        var object = (Operand) putField.getObject();
        code.append(apply(object));
        var value = putField.getValue();
        code.append(apply(value));
        var ownerClass = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
        var field = putField.getField();
        var fieldName = escapeName(field.getName());
        var fieldDescriptor = types.getTypeDescriptor(field.getType());
        code.append("putfield ").append(ownerClass).append("/")
                .append(fieldName).append(" ").append(fieldDescriptor).append(NL);
        return code.toString();
    }

    private String generateGetField(GetFieldInstruction getField) {
        var code = new StringBuilder();
        var object = (Operand) getField.getObject();
        code.append(apply(object));
        var ownerClass = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
        var field = getField.getField();
        var fieldName = escapeName(field.getName());
        var fieldDescriptor = types.getTypeDescriptor(field.getType());
        code.append("getfield ").append(ownerClass).append("/")
                .append(fieldName).append(" ").append(fieldDescriptor).append(NL);
        return code.toString();
    }

    private String generateSingleOpCond(SingleOpCondInstruction inst) {
        var code = new StringBuilder();
        code.append(apply(inst.getOperands().get(0)));
        code.append("ifne ").append(inst.getLabel()).append(NL);
        return code.toString();
    }

    private String generateOpCond(OpCondInstruction inst) {
        var code = new StringBuilder();
        var left = inst.getOperands().get(0);
        var right = inst.getOperands().get(1);
        code.append(apply(left));
        code.append(apply(right));

        var opType = inst.getCondition().getOperation().getOpType();
        String branchInstr = switch (opType) {
            case LTH -> "if_icmplt";
            case GTH -> "if_icmpgt";
            case LTE -> "if_icmple";
            case GTE -> "if_icmpge";
            case EQ -> "if_icmpeq";
            case NEQ -> "if_icmpne";
            default -> throw new NotImplementedException("OpCond not implemented for: " + opType);
        };
        code.append(branchInstr).append(" ").append(inst.getLabel()).append(NL);
        return code.toString();
    }

    private String generateGoto(GotoInstruction inst) {
        var code = new StringBuilder();
        code.append("goto ").append(inst.getLabel()).append(NL);
        return code.toString();
    }


    private String generateUnaryOp(UnaryOpInstruction unaryOp) {
        var code = new StringBuilder();
        var opType = unaryOp.getOperation().getOpType();

        if (opType == OperationType.LOGICAL_NOT || opType == OperationType.LOGICAL_NOT) {
            String trueLabel = "not_true_" + labelCounter;
            String endLabel = "not_end_" + labelCounter;
            labelCounter++;

            code.append(apply(unaryOp.getOperand()));
            code.append("ifeq ").append(trueLabel).append(NL);
            code.append("iconst_0").append(NL);
            code.append("goto ").append(endLabel).append(NL);
            code.append(trueLabel).append(":").append(NL);
            code.append("iconst_1").append(NL);
            code.append(endLabel).append(":").append(NL);
            return code.toString();
        }

        throw new NotImplementedException("UnaryOp not implemented for: " + opType);
    }
}