package pt.up.fe.comp.test.env;

import org.specs.comp.ollir.*;
import org.specs.comp.ollir.inst.*;
import org.specs.comp.ollir.type.*;
import pt.up.fe.comp.jmm.ollir.OllirResult;
import pt.up.fe.comp.jmm.parser.JmmParserResult;
import pt.up.fe.specs.util.SpecsCollections;
import pt.up.fe.specs.util.SpecsStrings;
import pt.up.fe.specs.util.exceptions.NotImplementedException;

import java.io.File;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Test environment utilities for working with OLLIR inside unit tests.
 *
 * <p>This helper extends {@link CompilerTestEnv} and provides convenience methods to:
 * - convert JMM sources to OLLIR;
 * - load OLLIR from resource files;
 * - query and assert properties of OLLIR ClassUnit and Method structures;</p>
 *
 * <p>Methods may raise test failures via {@link #exposeException(Exception, String)} when
 * expected artifacts are not present.</p>
 */
public class OllirTestEnv extends JmmTestEnv {


    /**
     * Create an OLLIR test environment that resolves resources relative to {@code basePath}.
     *
     * @param basePath base resource path (e.g. {@code "fixtures/ollir/"})
     */
    public OllirTestEnv(String basePath, String resourcesLocation, Map<String, String> config) {
        super(basePath, resourcesLocation, config);
    }

    public OllirTestEnv(String basePath, String resourcesLocation) {
        super(basePath, resourcesLocation);
    }

    /**
     * Convert a JMM resource file to an {@link OllirResult} and assert successful pipeline execution.
     *
     * @param jmmFileName resource filename relative to the environment base path
     * @return produced {@link OllirResult}
     */
    public OllirResult jmmToOllir(String jmmFileName) {
        return jmmToOllir(jmmFileName, true);
    }

    /**
     * Convert a JMM resource file to an {@link OllirResult} without asserting successful pipeline execution.
     *
     * @param jmmFileName resource filename relative to the environment base path
     * @return produced {@link OllirResult}
     */
    public OllirResult toOllirNoCheck(String jmmFileName) {
        return jmmToOllir(jmmFileName, false);
    }

    /**
     * Load OLLIR contents from a resource file and wrap it in an {@link OllirResult}.
     *
     * @param ollirResourceName resource filename relative to the environment base path
     * @return wrapped {@link OllirResult}
     */
    public OllirResult loadOllir(String ollirResourceName) {
        return super.ollirResource(basePath + ollirResourceName, resourcesLocation);
    }

    public OllirResult loadOllir(File file) {
        return super.ollir(file);
    }

    /**
     * Convert a JMM resource file to OLLIR, optionally asserting success.
     *
     * @param jmmResourceName resource filename relative to the environment base path
     * @param check           if {@code true} assert that each compilation stage produced no errors
     * @return produced {@link OllirResult}
     */
    public OllirResult jmmToOllir(String jmmResourceName, boolean check) {
        var parser = super.parseResource(jmmResourceName);
        return jmmToOllir(parser, check);
    }

    public OllirResult jmmToOllir(File file, boolean check) {
        var parser = super.parse(file);
        return jmmToOllir(parser, check);
    }

    /**
     * Convert a parser result to OLLIR. If {@code check} is {@code true} the pipeline stages
     * are asserted to produce no errors.
     *
     * @param parser parser result to feed the pipeline
     * @param check  whether to assert successful stages
     * @return produced {@link OllirResult}
     */
    protected OllirResult jmmToOllir(JmmParserResult parser, boolean check) {
        if (check) {
            var semantics = analyse(parser);
            return ollir(semantics);
        }
        var semantics = analyseNoCheck(parser);
        return ollirNoCheck(semantics);
    }


    /**
     * Retrieve a {@link Method} by name from an {@link OllirResult}.
     *
     * @param result     the OLLIR result containing the class
     * @param methodName target method name
     * @return requested {@link Method}
     * @throws AssertionError via {@link #exposeException(Exception, String)} if the method is not found
     */
    public Method getMethod(OllirResult result, String methodName) {
        ClassUnit classUnit = result.getOllirClass();

        for (var method : classUnit.getMethods()) {
            if (method.getMethodName().equals(methodName)) {
                return method;
            }
        }
        exposeException(new RuntimeException("Could not find OLLIR method with name '" + methodName + "'"), "OLLIR");
        //unreachable
        return null;
    }

    /**
     * Convert an OLLIR {@link Type} to its string representation used in tests.
     *
     * @param ollirType type to convert
     * @return textual representation (e.g. {@code "int"}, {@code "bool"}, {@code "String[]"})
     */
    public String toString(Type ollirType) {

        if (ollirType instanceof BuiltinType builtinType) {
            return switch (builtinType.getKind()) {
                case BOOLEAN -> "bool";
                case INT32 -> "int";
                case STRING -> "String";
                case VOID -> "void";
            };
        }

        if (ollirType instanceof ArrayType arrayType) {
            return toString(arrayType.getElementType())
                    + SpecsStrings.buildLine("[]", arrayType.getNumDimensions());
        }

        if (ollirType instanceof ClassType classType) {
            if (classType.getKind() == ClassKind.CLASS || classType.getKind() == ClassKind.THIS) {
                exposeException(new RuntimeException("Not implemented for " + classType.getKind()), "OLLIR");
            }

            return classType.getName();
        }

        exposeException(new NotImplementedException(ollirType), "OLLIR");
        //unreachable
        return null;
    }


    /**
     * If {@code type} is a {@link BuiltinType} return its {@link BuiltinKind}, otherwise return the original type.
     * Useful for assertions that need to compare primitive kinds.
     *
     * @param type input type
     * @return {@link BuiltinKind} for builtin types, or {@code type} unchanged
     */
    public static Object toBuiltinKind(Type type) {
        if (type instanceof BuiltinType builtinType) {
            return builtinType.getKind();
        }

        return type;
    }

    /**
     * Assert that there exists an assignment instruction whose RHS is an instance of {@code c}.
     *
     * @param c      instruction class expected on the RHS
     * @param method method to inspect
     */
    public void assertAssignRhs(Class<? extends Instruction> c, Method method) {

        var inst = method.getInstructions().stream()
                .filter(i -> i instanceof AssignInstruction)
                .map(instr -> (AssignInstruction) instr)
                .filter(assign -> c.isInstance(assign.getRhs()))
                .findFirst();

        assertTrue("Could not find a " + c.getName() + " in method " + method.getMethodName(),
                inst.isPresent());
    }

    /**
     * Assert that at least one instruction of type {@code c} exists in the method and return the collected instances.
     *
     * @param c      instruction class to search for
     * @param method method to inspect
     * @param <T>    instruction type
     * @return list of found instances
     */
    public <T> List<T> assertInstExists(Class<T> c, Method method) {
        var inst = getInstructions(c, method);
        //System.out.println("INSTS:\n" + inst);
        assertTrue("Could not find a " + c.getName() + " in method " + method.getMethodName(), !inst.isEmpty());

        return inst;
    }

    /**
     * Collect instructions (and nested instruction elements) of type {@code c} from a method.
     *
     * @param c      type to collect
     * @param method method to inspect
     * @param <T>    element type
     * @return list of matching elements
     */
    public static <T> List<T> getInstructions(Class<T> c, Method method) {

        return method.getInstructions().stream()
                // Unfold instruction inside AssignInstrucion
                .flatMap(
                        i -> i instanceof AssignInstruction ? Stream.of(i, ((AssignInstruction) i).getRhs())
                                : Stream.of(i)
                )
                // Unfold instruction inside OpCondInstruction
                .flatMap(
                        i -> i instanceof OpCondInstruction ? Stream.of(i, ((OpCondInstruction) i).getCondition())
                                : Stream.of(i)
                )
                .filter(c::isInstance)
                .map(c::cast)
                .collect(Collectors.toList());
    }

    /**
     * Assert that a method with {@code methodName} exists in the given OLLIR result.
     *
     * @param methodName target method name
     * @param ollir      OLLIR result to inspect
     * @return found {@link Method}
     */
    public Method assertMethodExists(String methodName, OllirResult ollir) {
        Method method = ollir.getOllirClass().getMethods().stream()
                .filter(m -> m.getMethodName().equals(methodName))
                .findFirst()
                .orElse(null);

        assertNotNull("Could not find method " + methodName,
                method);

        return method;
    }

    /**
     * Assert that the method contains a return instruction.
     *
     * @param method method to inspect
     * @param ollir  containing OLLIR result (used for additional debug context)
     */
    public void assertReturnExists(Method method, OllirResult ollir) {

        var retInst = method.getInstructions().stream()
                .filter(inst -> inst instanceof ReturnInstruction)
                .findFirst();

        assertTrue("Could not find a return instruction in method " + method.getMethodName()
                + ollir.getOllirCode(), retInst.isPresent());
    }

    /**
     * Assert the number of operations of the given {@link OperationType} in the method matches {@code number}.
     *
     * @param opType      operation type to count
     * @param number      expected count
     * @param method      method to inspect
     * @param ollirResult OLLIR context used for debug messages
     */
    public void assertNumberOfOperations(OperationType opType, int number, Method method, OllirResult ollirResult) {
        var ops = getOperationInstances(opType, method);
        assertTrue("Expected " + number + " " + opType + " operations, found " + ops.size(),
                ops.size() == number);
    }


    /**
     * Assert that at least one operation of {@code opType} exists in the method.
     *
     * @param opType operation type to search
     * @param method method to inspect
     */
    public void assertHasOperation(OperationType opType, Method method) {

        var binOpsOfOperation = getOperationInstances(opType, method);

        assertTrue(
                "Could not find binary operation of type " + opType.name() + " in method " + method.getMethodName(),
                !binOpsOfOperation.isEmpty());
    }

    /**
     * Collect all {@link OpInstruction} instances of a given {@link OperationType} from a method.
     *
     * @param opType operation type to filter
     * @param method method to inspect
     * @return list of matching {@link OpInstruction} instances
     */
    public static List<OpInstruction> getOperationInstances(OperationType opType, Method method) {

        var ops = getInstructions(OpInstruction.class, method);

        return ops.stream()
                .filter(op -> op.getOperation().getOpType() == opType)
                .collect(Collectors.toList());
    }

    /**
     * Collect OLLIR nodes from a class unit that match the provided predicate.
     *
     * @param classUnit class unit to inspect
     * @param filter    predicate that tests nodes
     * @return list of matching nodes
     */
    public static List<Node> getOllirNodes(ClassUnit classUnit, Predicate<Node> filter) {
        var nodes = new ArrayList<Node>();

        for (var method : classUnit.getMethods()) {
            getOllirNodes(method, filter, nodes);
        }
        // assertTrue(filterMessage, !nodes.isEmpty());
        // if (nodes.isEmpty()) {
        // throw new RuntimeException();
        // }

        return nodes;
    }

    /**
     * Collect OLLIR nodes from a method that match the provided predicate.
     *
     * @param method method to inspect
     * @param filter predicate that tests nodes
     * @return list of matching nodes
     */
    public static List<Node> getOllirNodes(Method method, Predicate<Node> filter) {
        var nodes = new ArrayList<Node>();
        getOllirNodes(method, filter, nodes);
        return nodes;
    }

    private static void getOllirNodes(Method method, Predicate<Node> filter, List<Node> filteredNodes) {
        for (var inst : method.getInstructions()) {
            getOllirNodes(inst, filter, filteredNodes);
        }

    }

    private static void getOllirNodes(Node currentNode, Predicate<Node> filter, List<Node> filteredNodes) {
        // Check if node passes the filter
        if (filter.test(currentNode)) {
            filteredNodes.add(currentNode);
        }

        // Special cases
        if (currentNode instanceof AssignInstruction assign) {
            getOllirNodes(assign.getRhs(), filter, filteredNodes);
        }
    }

    /**
     * Extract operands/elements from an instruction. Returns an empty list for instructions
     * with no operands.
     *
     * @param inst instruction to inspect
     * @return list of operand elements
     */
    public List<Element> getElements(Instruction inst) {
//        System.out.println("BEING CALLED FOR: " + inst.getInstType());
        // inst.show();
        if (inst instanceof SingleOpInstruction singleOpInstruction) {
            return Collections.singletonList(singleOpInstruction.getSingleOperand());
        }

        if (inst instanceof OpInstruction opInst) {
            return opInst.getOperands();
        }

        if (inst instanceof AssignInstruction assign) {
            return SpecsCollections.concat(assign.getDest(), getElements(assign.getRhs()));
        }

        if (inst instanceof CallInstruction call) {
            var operands = call.getArguments();
            return operands != null ? operands : Collections.emptyList();
        }

        if (inst instanceof ReturnInstruction retInst) {
            return retInst.getOperand().map(Arrays::asList).orElse(Collections.emptyList());
        }

        if (inst instanceof CondBranchInstruction branchInst) {
            return branchInst.getOperands();
        }

        if (inst instanceof GotoInstruction) {
            return Collections.emptyList();
        }

        exposeException(new NotImplementedException(inst.getClass()), "OLLIR");
//        System.out.println("CpUtils.getElements(Instruction): not yet implement for " + inst.getClass());
        return Collections.emptyList();
    }


    /**
     * Assert that the given literal string exists somewhere among the method elements.
     *
     * @param literal literal text to search for
     * @param method  method to inspect
     * @param result  OLLIR result used for context
     */
    public void assertFindLiteral(String literal, Method method, OllirResult result) {
        var elements = getElements(method.getInstructions());
/*
        for(var el : elements) {
            System.out.println("ELEM: " + el.getClass());
            if(el instanceof LiteralElement literalElement) {
                System.out.println("LIT: " + literalElement.getLiteral());
            }
        }
 */
/*
        var insts = new ArrayList<Instruction>();
        insts.addAll(getInstructions(SingleOpInstruction.class, method));
        insts.addAll(getInstructions(CallInstruction.class, method));

        var insts = CpUtils.assertInstExists(SingleOpInstruction.class, method, result);
*/
        boolean foundLiteral = false;
        for (var element : elements) {

            if (!(element instanceof LiteralElement literalElement)) {
                continue;
            }

            if (!literalElement.getLiteral().equals(literal)) {
                continue;
            }

            foundLiteral = true;
            break;
        }

        assertTrue("Expected to find literal " + literal, foundLiteral);
    }

    /**
     * Assert that a literal appears {@code expectedCount} times in method elements.
     *
     * @param literal       literal text to count
     * @param method        method to inspect
     * @param result        OLLIR result used for context
     * @param expectedCount expected occurrence count
     */
    public void assertLiteralCount(String literal, Method method, OllirResult result, int expectedCount) {
        var finalCount = getLiteralCount(literal, method, result);
        assertTrue("Expected to find literal " + literal + " " + expectedCount + " times, found " + finalCount, finalCount == expectedCount);
    }

    /**
     * Count occurrences of a literal in method elements.
     *
     * @param literal literal text to count
     * @param method  method to inspect
     * @param result  OLLIR result used for context
     * @return number of occurrences found
     */
    public int getLiteralCount(String literal, Method method, OllirResult result) {

        var elements = getElements(method.getInstructions());

        int literalCount = 0;
        for (var element : elements) {

            if (!(element instanceof LiteralElement literalElement)) {
                continue;
            }

            if (!literalElement.getLiteral().equals(literal)) {
                continue;
            }

            literalCount += 1;
        }

        return literalCount;
    }

    /**
     * Assert that the return instruction returns the specified literal.
     *
     * @param literal literal expected in the return instruction
     * @param method  method to inspect
     */
    public void assertLiteralReturn(String literal, Method method) {
        var instructions = method.getInstructions();

        boolean literalReturnFound = false;
        for (var instruction : instructions) {

            if (!(instruction instanceof ReturnInstruction returnInstruction)) {
                continue;
            }

            if (returnInstruction.getOperand().isEmpty()) {
                continue;
            }

            if (!(returnInstruction.getOperand().get() instanceof LiteralElement literalReturn)) {
                continue;
            }

            if (!literalReturn.getLiteral().equals(literal)) {
                continue;
            }

            literalReturnFound = true;
            break;
        }

        assertTrue("Expected to find literal return " + literal, literalReturnFound);
    }

    private List<Element> getElements(List<Instruction> instructions) {
        return instructions.stream()
                .flatMap(inst -> getElements(inst).stream())
                .collect(Collectors.toList());
    }

    /**
     * Assert that an assignment exists where the RHS is a literal matching {@code literal}.
     *
     * @param literal literal text expected on the RHS
     * @param method  method to inspect
     */
    public void assertFindAssignmentWithLiteral(String literal, Method method) {
        var insts = assertInstExists(AssignInstruction.class, method);
        boolean foundLiteral = false;
        for (var inst : insts) {
            var rhs = inst.getRhs();

            if (!(rhs instanceof SingleOpInstruction singleOp)) {
                continue;
            }

            if (!(singleOp.getSingleOperand() instanceof LiteralElement literalElement)) {
                continue;
            }

            if (!literalElement.getLiteral().equals(literal)) {
                continue;
            }

            foundLiteral = true;
            break;
        }
        assertTrue("Expected to find assignment to literal " + literal, foundLiteral);
    }

    /**
     * Count the number of distinct virtual registers used in the whole class.
     *
     * @param ollirClass class unit to inspect
     * @return number of distinct registers
     */
    public static int countRegisters(ClassUnit ollirClass) {
        ArrayList<Method> methodList = ollirClass.getMethods();
        if (methodList == null)
            return 0;

        final Set<Integer> registers = new HashSet<>();

        for (Method method : methodList) {
            Map<String, Descriptor> varTable = method.getVarTable();
            for (Descriptor descriptor : varTable.values()) {
                registers.add(descriptor.getVirtualReg());
            }
        }

        return registers.size();
    }

    /**
     * Count the number of distinct virtual registers used in a specific method.
     *
     * @param method method to inspect
     * @return number of distinct registers
     */
    public static int countRegisters(Method method) {

        final Set<Integer> registers = new HashSet<>();

        Map<String, Descriptor> varTable = method.getVarTable();
        for (Descriptor descriptor : varTable.values()) {
            registers.add(descriptor.getVirtualReg());
        }

        return registers.size();
    }

}