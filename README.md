# Compiler Project

## Elements of the group

- Carolina Lima Roque - up202305062
- Filipe Lemos Paiva - up202304284
- Tiago Pinto Cardose de Almeida - up202303450

## Participation

- up202305062 - 33.3%
- up202304284 - 33.3%
- up202303450 - 33.3%

## Autoavaliação

### Projeto

- Nota: 19

### Elementos

- up202305062 - 19
- up202304284 - 19
- up202303450 - 19

## Funcionalidades Implementadas

## CP1

### Declarations

We implemented support for extended class and member declarations.

##### Parser:

- Fields and methods can be declared in arbitrary order inside the class body.
- Field declarations support optional initialization (type ID = expr;).
- Method declarations support visibility modifiers (public, private, protected) and static modifier.

#### Semantic Analysis:

- A complete symbol table is built before analysis, allowing fields and methods to be used regardless of declaration
  order.
- Field initializers are type-checked against declared field types (VarInitCheckVisitor).
- Duplicate field and method declarations are detected.
- Method visibility is stored in the symbol table.
- Method overloading is supported and validated in the symbol table builder.

### Statements

We implemented full support for extended control-flow statements.

#### Parser:

- if statements support optional else branch.
- for loop supports initialization, condition, and iteration expressions.
- do-while loop is fully supported.
- Enhanced assignment and compound assignment operations are supported inside statements.

#### Semantic Analysis:

- if, while, do-while, and for conditions are checked to be of boolean type.
- return statements are validated against the method return type.
- Methods with non-void return types are checked to ensure at least one return exists.
- for loop initialization and iteration expressions are type-checked.
- Invalid or non-boolean loop conditions are reported.
- Assignments inside for statements are validated for type compatibility.

### Entities and Operations

We extended the language with additional operators and expressions.

#### Parser:

- Unary operators: prefix +, -, ++, --, and logical !.
- Binary arithmetic operators: +, -, *, /, %.
- Relational operators: ==, !=, <, >, <=, >=.
- Logical operators: &&, ||.

#### Semantic Analysis:

- TypeCheckVisitor ensures strict type rules for expressions:
    - Arithmetic operators require int.
    - Logical operators require boolean.
    - Relational operators enforce correct operand types.
    - == and != require compatible operand types.
- Unary operators are validated:
    - ! requires boolean.
    - +, - require int.
    - Prefix ++/-- require int.
- Type mismatches are reported with precise error messages.

### Calls

We implemented method invocation features including static resolution, inheritance, and constructor calls.

#### Parser:

- Method calls support arguments (method(expr, ...)).
- Implicit this calls are supported (method() without explicit receiver).
- Object instantiation supports constructor arguments (new Class(expr, ...)).

#### Semantic Analysis:

Implemented mainly in CallCheckVisitor and TypeUtils:

- Method overloading resolution based on parameter types and argument types.
- Support for implicit this method calls.
- Static method handling and resolution.
- Inheritance support (method lookup in superclass using symbol table importer).
- Method calls on imported classes validated against external symbol tables.
- Constructor calls validated against argument types.
- Argument count and type checking for all call

### Arrays

We implemented support for arrays and multidimensional arrays.

#### Parser:

- Array types with multiple dimensions (int[][], int[][][], etc.).
- Array access (arr[i]).
- Array initialization (new int[]{...}).
- Array creation with multiple dimensions (new int[x][y]...).

#### Semantic Analysis:

Implemented in ArrayCheckVisitor and AssignmentCheckVisitor:

- Array access requires integer index.
- .length is only valid on arrays.
- Array assignment enforces correct element type.
- Array initializer elements must match base type.
- Array expressions cannot be used in arithmetic operations.
- Assignment compatibility includes multidimensional arrays.
- Void type is not allowed in arrays.

## CP2

### Declarations

We implemented full OLLIR generation for class declarations, fields, and methods.

**OLLIR Generation:**

- Class fields are generated at the beginning of the class body using .field public declarations, retrieved directly
  from
  the symbol table.
- Method visibility modifiers (public, private, protected) are correctly emitted in .method declarations by reading the
  VISIBILITY child nodes.
- Field initializers are generated inside the class constructor: buildConstructor iterates over fields that have
  initializer expressions in their VAR_DECL nodes and emits the corresponding putfield instructions after the
  invokespecial call to the superclass constructor.

### Statements

We implemented direct OLLIR generation for all supported control-flow statements.

**OLLIR Generation:**

- if without else is correctly handled: visitIfStmt checks getNumChildren() >= 3 before emitting the else branch,
  preserving the original control flow with labels.
- for loops are directly translated to OLLIR in visitForStmt, generating init, loop label, condition check, body,
  iteration, and jump-back instructions using FOR_ASSIGN_INIT and FOR_ASSIGN_ITER node kinds.
- do-while loops are directly translated to OLLIR in visitDoWhileStmt, generating the body first, then the condition
  check
  with a conditional jump back to the body label.

### Entities and Operations

We implemented OLLIR generation for all extended arithmetic, relational, and logical operators.

**OLLIR Generation:**

- Binary arithmetic operators (+, -, *, /, %) and relational operators (<, >, <=, >=, ==, !=) are generated in
  visitBinExpr using typed OLLIR binary expressions with temporaries.
- Logical && and || are generated with short-circuit evaluation using conditional jumps and labels, correctly computing
  the result into a temporary boolean variable.
- Unary - and + are generated in visitUnaryOp, with - producing a subtraction from zero; + is a no-op returning the
  operand directly.
- Logical ! is generated as a !.bool OLLIR instruction.
- Prefix ++ and -- operators are generated in visitPrefixOp, computing the new value into a temporary and writing it
  back
  to the variable or array element.

### Calls

We implemented correct OLLIR generation for all call variants.

**OLLIR Generation:**

- Static method calls are resolved in resolveInvokeKind: if the callee is the class itself, a fully qualified import
  without a local variable binding, or a type marked as staticRef, invokestatic is emitted.
- new with constructor arguments is handled in visitNewObject: arguments are evaluated, the object is allocated with
  new(
  ClassName), and invokespecial is called with the argument list.
- Implicit this calls are handled in visitImplicitThisCall, always emitting invokevirtual(this.ClassName, "method", ...)
  with the correct return type.
- For known methods (found in the symbol table of the class or an imported class), parameter types are used to correctly
  type the argument expressions in the generated OLLIR.

### Arrays

We implemented OLLIR generation for array initializers, multidimensional arrays, and array element access and
assignment.

**OLLIR Generation:**

- Array initializers ({e1, e2, ...}) are handled in visitArrayInitExpr: a new(array, N) instruction is emitted, followed
  by individual element stores using indexed assignment syntax.
- Multidimensional array instantiation is supported in visitNewArray: the OLLIR array type is built dynamically (e.g.
  .array.array.i32 for 2D), and new(array, size0, size1, ...) is emitted with all dimension sizes.
- Reading elements from (multidimensional) arrays is handled in visitArrayAccess, generating a typed temporary with
  array[index].type syntax.
- Writing elements to arrays (including fields) is handled in visitArrayAssignStmt, with correct handling for field
  arrays (preceded by a getfield into a temporary before the indexed assignment).

### Optimization

We implemented AST-level optimizations applied iteratively until a fixed point is reached, enabled when the optimize
flag is set.

**Implementation:**

- **Constant Folding:** foldConstants traverses all BINARY_EXPR nodes and replaces them with a literal when both
  operands are
  compile-time constants. Supported operations include all arithmetic (+, -, *, /, %),
  relational (<, >, <=, >=, ==, !=),
  and boolean (&&, ||) operators.
- **Constant Propagation:** propagateConstants tracks variables assigned to literals within each method and replaces
  subsequent VAR_REF_EXPR uses with the corresponding literal. Variables are correctly invalidated when reassigned or
  used
  inside if-else or while blocks.
- Both transformations are applied repeatedly in a fixed-point loop (while (changed)) in transformAst, ensuring that
  folding enables further propagation and vice versa.
- **Branch Elimination:** eliminateBranches evaluates the condition of every if-else statement statically. If the
  condition is
  a known boolean (including evaluation through !, &&, ||), the dead branch is removed and the live branch's statements
  are inlined in place.
- **Dead Code Elimination:** eliminateDeadCode performs a backward liveness analysis per method. Assignments to local
  variables that are never subsequently read, have no side effects (no method calls on the RHS), and are not fields or
  parameters are removed. The analysis correctly handles if-else (merging live sets from both branches) and while (
  conservatively marking all referenced variables as live).

## CP3

### Declarations

We implemented full Jasmin generation for class declarations, fields, and methods.

**Jasmin Generation:**

- Class fields are generated at the beginning of the class body using .field declarations in generateClassUnit, with the
  correct access modifier retrieved via types.getModifier(field.getFieldAccessModifier()) and the JVM type descriptor
  via
  types.getTypeDescriptor(field.getFieldType()).
- Method visibility modifiers (public, private, protected) are correctly emitted in .method declarations by reading the
  access modifier from the OLLIR method object. Constructor methods without an explicit modifier default to public.
- Field initialization code inside the class constructor is handled by the OLLIR layer (generated in CP2's
  buildConstructor), and the Jasmin backend correctly translates the resulting putfield instructions via
  generatePutField,
  which emits aload_0, the value, and the putfield instruction with the correct owner class and descriptor.

### Statements

We implemented correct Jasmin generation for all extended control-flow statements, as these are fully lowered to labels
and conditional jumps in OLLIR.

**Jasmin Generation:**

- if without else is correctly handled: the OLLIR conditional jump structure (generated in CP2) is translated by
  generateSingleOpCond and generateOpCond, emitting ifne or comparison instructions (if_icmplt, if_icmpeq, etc.)
  followed
  by the target label, preserving the original control flow.
  -for loops are correctly translated: since CP2 generates for loops directly as OLLIR with init, condition check, body,
  and jump-back, the Jasmin backend handles them transparently through generateGoto (for goto instructions) and the
  conditional jump handlers.
- do-while loops are correctly translated in the same way: the body executes first, followed by the condition check and
  a
  conditional jump back, all handled by the existing label and jump infrastructure.

### Entities and Operations

We implemented Jasmin generation for all extended arithmetic, relational, and logical operators.

**Jasmin Generation:**

- Additional arithmetic operators (+, -, *, /, %) are generated in generateBinaryOp using the JVM type prefix from
  types.getTypePrefix combined with the operation name (iadd, isub, imul, idiv, irem).
- Relational operators (<, >, <=, >=, ==, !=) are generated using if_icmplt, if_icmpgt, if_icmple, if_icmpge, if_icmpeq,
  if_icmpne with true/end labels and iconst_0/iconst_1 to produce a boolean result on the stack.
- Logical && and || are generated using iand and ior respectively.
- Logical ! is generated in both generateBinaryOp (for LOGICAL_NOT in binary context) and generateUnaryOp, using an
  ifeq/ifne pattern with labels to produce iconst_0 or iconst_1.
- Integer literals are optimized: values from -1 to 5 use iconst_*, -128 to 127 use bipush, -32768 to 32767 use sipush,
  and all others use ldc.

### Calls

We implemented correct Jasmin generation for all call variants.

**Jasmin Generation:**

- Static method calls are generated in getStaticCall, emitting invokestatic ClassName/methodName(args)return. The class
  name is resolved via types.resolveClassName and the argument/return descriptors are resolved first via reflection (
  resolveParamDescriptorsViaReflection, resolveReturnDescriptorViaReflection) and fall back to OLLIR type descriptors.
- new with constructor arguments is handled in generateNew (for object allocation with new ClassName and dup) and
  getSpecialCall (for the invokespecial ClassName/<init>(args)V call), with arguments pushed before the call.
- Implicit this calls and all instance method calls are handled in getVirtualCall, emitting invokevirtual
  ClassName/methodName(args)return. The caller is loaded first, followed by all arguments. Descriptors are resolved from
  the OLLIR class methods when available, falling back to reflection.

### Arrays

We implemented Jasmin generation for array initializers, multidimensional arrays, and array element access and
assignment.

**Jasmin Generation:**

- Array initializers are handled transparently: since CP2 generates them as a new(array, N) followed by individual
  indexed
  assignments, the Jasmin backend handles these through generateNew (newarray int/anewarray) and generateAssign with
  ArrayOperand destinations (emitting iastore/aastore).
- Multidimensional array instantiation is handled in generateNew: when the size operands list has more than one element,
  multianewarray descriptor N is emitted with the correct JVM array descriptor (e.g. [[I) and the number of dimensions.
- Reading elements from (multidimensional) arrays is handled in generateArrayOperand, which loads the array reference (
  from a local variable or via getfield if it is a class field), loads the index, and emits iaload, aaload, or baload
  depending on the element type.
- Writing elements to arrays is handled in generateAssign when the destination is an ArrayOperand, loading the array
  reference (with field handling), the index, the value, and emitting iastore, aastore, or bastore.

## Declaration of AI Tools Used

Please choose one of the two options about AI tools use. In case tools where used, enumerate which ones, and the
specific use.

Finally, check the box regarding responsibility for the work.

AI tools/services used in this work:

[] No AI tools were used.
[X] The following tools were used:

Durante o desenvolvimento do projeto foram utilizadas ferramentas de IA (Chatgpt e Claude) para apoio à compreensão de
conceitos
relacionados com a matéria do projeto, OLLIR, Jasmin e debugging de código. Todas as decisões de implementação,
desenvolvimento,
integração e validação dos resultados foram realizadas pelos elementos do grupo. As ferramentas de IA foram utilizadas
apenas como suporte ao estudo, esclarecimento de dúvidas e revisão de código

[X] All content has been reviewed, understood, validated, and we assume full responsibility for the work in this
repository.

# Repository Structure

The base repository has several folders, the main ones are:

- `src`: The source folder for the project, you will work here.
- `test`: Folder for your own tests.
- `test-public`: Public tests, similar to the majority of the private tests that will be used for evaluation. **Do not
  change the contents of this folder.** This folder will be modified by automatic updates during the semester.

The remaining folders are:

- `libs`: Libraries in JAR format, required for the project.
- `libs-jmm`: Java code that can be imported in your Java-- classes. Contains a `java` folder, with the source code, and
  a `compiled` folder with the same classes, in compiled format. The build system automatically compiles the files
  inside the `java` folder and stores them in the `compiled` folder.

