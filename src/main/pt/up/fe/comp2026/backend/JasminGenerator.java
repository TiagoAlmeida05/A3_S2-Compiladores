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

/**
 * Generates Jasmin code from an OllirResult.
 * <p>
 * One JasminGenerator instance per OllirResult.
 */
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

    private String generateClassUnit(ClassUnit classUnit) {
        var code = new StringBuilder();

        var nameWithPackage = ollirResult.getOllirClass().getClassFullyQualifiedName().replace('.', '/');
        code.append(".class public ").append(nameWithPackage).append(NL).append(NL);

        var superClass = ollirResult.getOllirClass().getSuperClass();
        var fullSuperClass = (superClass == null || superClass.equals("Object"))
                ? "java/lang/Object"
                : types.resolveClassName(superClass);
        code.append(".super ").append(fullSuperClass).append(NL).append(NL);

        for (var field : ollirResult.getOllirClass().getFields()) {
            var accessModifier = types.getModifier(field.getFieldAccessModifier());
            var fieldName = field.getFieldName().trim();
            var fieldType = types.getTypeDescriptor(field.getFieldType()).trim();
            code.append(".field ").append(accessModifier).append(fieldName)
                    .append(" ").append(fieldType).append(NL);
        }

        code.append(NL);

        var defaultConstructor = """
                ;default constructor
                .method public <init>()V
                    .limit stack 1
                    .limit locals 1
                    aload_0
                    invokespecial %s/<init>()V
                    return
                .end method
                """.formatted(fullSuperClass);
        code.append(defaultConstructor);

        for (var method : ollirResult.getOllirClass().getMethods()) {
            if (method.isConstructMethod()) {
                continue;
            }
            code.append(apply(method));
        }

        return code.toString();
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
            var instCode = StringLines.getLines(apply(inst)).stream()
                    .collect(Collectors.joining(NL + TAB, TAB, NL));
            bodyCode.append(instCode);

            if (inst instanceof CallInstruction call && !isInsideAssignment) {
                if (!isVoidType(call.getReturnType())) {
                    bodyCode.append(TAB).append("pop").append(NL);
                }
            }
        }

        code.append(TAB).append(".limit stack 99").append(NL);
        code.append(TAB).append(".limit locals 99").append(NL);
        code.append(TAB).append(bodyCode);
        code.append(".end method\n");

        currentMethod = null;
        return code.toString();
    }

    private String generateAssign(AssignInstruction assign) {
        try {
            isInsideAssignment = true;

            var code = new StringBuilder();
            var lhs = assign.getDest();

            // --- LHS é um array store (ex: a[i] = valor) ---
            if (lhs instanceof ArrayOperand arrayOp) {
                var arrayReg = currentMethod.getVarTable().get(arrayOp.getName()).getVirtualReg();
                code.append(loadRef(arrayReg)).append(NL);

                var indexOp = arrayOp.getIndexOperands().get(0);
                code.append(apply(indexOp));

                code.append(apply(assign.getRhs()));

                code.append("iastore").append(NL);
                return code.toString();
            }

            // --- RHS é um NewInstruction (ex: tmp0 := new(array, size)) ---
            // IMPORTANTE: este bloco tem de vir ANTES do bloco CallInstruction,
            // porque NewInstruction pode ser subclasse de CallInstruction no OLLIR.
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

            // --- RHS é uma CallInstruction ---
            if (assign.getRhs() instanceof CallInstruction rhsCall) {
                String methodName = null;
                try {
                    methodName = ((LiteralElement) rhsCall.getMethodName()).getLiteral().replace("\"", "");
                } catch (Exception e) {
                    // getMethodName() lançou exceção — é arraylength ou similar sem nome
                }

                if (methodName == null) {
                    // arraylength via CallInstruction
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

                // Call normal com nome de método
                code.append(apply(assign.getRhs()));

                var operand = (Operand) lhs;
                var reg = currentMethod.getVarTable().get(operand.getName());

                var realDesc = getRealReturnDescriptor(
                        methodName,
                        rhsCall.getArguments().stream()
                                .map(a -> types.getTypeDescriptor(a.getType()))
                                .collect(Collectors.joining()),
                        rhsCall.getReturnType());
                if (realDesc.startsWith("[") || realDesc.startsWith("L")) {
                    code.append("astore ").append(reg.getVirtualReg()).append(NL);
                    return code.toString();
                }

                code.append(types.getStore(reg)).append(NL);
                return code.toString();
            }

            // --- Atribuição normal (ex: a := tmp0) ---
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

        code.append(apply(binaryOp.getLeftOperand()));
        code.append(apply(binaryOp.getRightOperand()));

        var typePrefix = types.getTypePrefix(binaryOp.getOperation().getTypeInfo());

        var op = switch (binaryOp.getOperation().getOpType()) {
            case ADD -> "add";
            case SUB -> "sub";
            case MUL -> "mul";
            case DIV -> "div";
            case REM -> "rem";
            default -> throw new NotImplementedException(binaryOp.getOperation().getOpType());
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

            // Forçar iload para o tamanho — o tipo na var table pode estar errado (array em vez de int)
            if (sizeOperand instanceof Operand op && !op.getName().equals("array")) {
                var reg = currentMethod.getVarTable().get(op.getName());
                if (reg != null) {
                    int n = reg.getVirtualReg();
                    code.append("iload").append(n < 4 ? "_" : " ").append(n).append(NL);
                } else {
                    code.append(apply(sizeOperand));
                }
            } else {
                // LiteralElement ou outro — usa apply normalmente
                code.append(apply(sizeOperand));
            }

            code.append("newarray int").append(NL);
        } else {
            var className = types.getTypeDescriptor(type)
                    .replace("L", "").replace(";", "");
            code.append("new ").append(className).append(NL);
            code.append("dup").append(NL);
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

        var argsDescriptor = call.getArguments().stream()
                .map(a -> types.getTypeDescriptor(a.getType()))
                .collect(Collectors.joining());

        var returnDescriptor = getRealReturnDescriptor(methodName, argsDescriptor, call.getReturnType());

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
        var returnDescriptor = types.getTypeDescriptor(call.getReturnType());

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
}