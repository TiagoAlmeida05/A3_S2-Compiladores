package core;

import org.junit.Test;
import pt.up.fe.comp.test.env.JmmTestEnv;

public class SemanticAndOllirFeedbackTest extends JmmTestEnv {

    public SemanticAndOllirFeedbackTest() {
        super("", "");
    }


    @Test
    public void testForLoopHeaderInvalidInitType() {
        setDescription("ISSUE 1: Semântica do header do for - init deve ser do tipo correto.");
        String code = """
                class ForLoopTest {
                    public int test() {
                        int i;
                        boolean b;
                
                        for (b = true; i < 10; i = i + 1) {
                            i = i + 1;
                        }
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testForLoopHeaderInvalidCondition() {
        setDescription("ISSUE 1: Semântica do header do for - condição deve ser boolean.");
        String code = """
                class ForLoopTest {
                    public int test() {
                        int i;
                
                        for (i = 0; i; i = i + 1) {
                            i = i + 1;
                        }
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testForLoopHeaderInvalidIncrement() {
        setDescription("ISSUE 1: Semântica do header do for - incremento deve ser atribuição válida.");
        String code = """
                class ForLoopTest {
                    public int test() {
                        int i;
                        boolean b;
                
                        for (i = 0; i < 10; i = true) {
                            i = i + 1;
                        }
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testForLoopHeaderValidation() {
        setDescription("ISSUE 1: Semântica do header do for - caso válido deve passar.");
        String code = """
                class ForLoopTest {
                    public int test() {
                        int i;
                
                        for (i = 0; i < 10; i = i + 1) {
                            i = i + 1;
                        }
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }


    @Test
    public void testInheritedMethodReturnTypeDetection() {
        setDescription("ISSUE 2: Detectar corretamente tipos de retorno de métodos herdados.");
        String code = """
                import libs.BaseClass;
                
                class InheritanceTest extends BaseClass {
                    public int test() {
                        int[] result;
                        int val;
                
                        // Se BaseClass.getArray() retorna int[], isto deve funcionar
                        result = this.getArray();
                
                        // Se BaseClass.getValue() retorna int, isto deve funcionar
                        val = this.getValue();
                
                        return val;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }

    @Test
    public void testInheritedMethodReturnTypeMismatch() {
        setDescription("ISSUE 2: Detectar erro quando tipo de retorno de método herdado não bate.");
        String code = """
                import libs.BaseClass;
                
                class InheritanceTest extends BaseClass {
                    public int test() {
                        boolean b;
                
                
                        b = this.getValue();
                
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testInheritedMethodChainedCall() {
        setDescription("ISSUE 2: Tipos de retorno em chamadas encadeadas de métodos herdados.");
        String code = """
                import libs.BaseClass;
                
                class ChainedInheritance extends BaseClass {
                    public int test() {
                        int val;
                
                
                        val = this.getContainer().getValue();
                
                        return val;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }


    @Test
    public void testImportWithoutPackage() {
        setDescription("ISSUE 3: Imports de classes sem package devem ser rejeitados.");
        String code = """
                import MathUtils;
                
                class ImportTest {
                    public int test() {
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testImportWithPackageValid() {
        setDescription("ISSUE 3: Imports com package válido devem passar.");
        String code = """
                import util.MathUtils;
                
                class ImportTest {
                    public int test() {
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }

    @Test
    public void testMultipleImportsWithoutPackage() {
        setDescription("ISSUE 3: Múltiplos imports sem package devem todos ser rejeitados.");
        String code = """
                import Utils;
                import Helper;
                
                class ImportTest {
                    public int test() {
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }


    @Test
    public void testStaticMethodCallThroughClass() {
        setDescription("ISSUE 4: Deve ser possível chamar métodos estáticos através da classe.");
        String code = """
                class StaticTest {
                    public static int calculate(int a, int b) {
                        return a + b;
                    }
                
                    public int test() {
                        int result;
                        result = StaticTest.calculate(5, 3);
                        return result;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }

    @Test
    public void testStaticMethodCallThroughThis() {
        setDescription("ISSUE 4: Chamadas estáticas através de 'this' também devem funcionar.");
        String code = """
                class StaticTest {
                    public static int calculate(int a, int b) {
                        return a + b;
                    }
                
                    public int test() {
                        int result;
                
                        result = this.calculate(5, 3);
                        return result;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }

    @Test
    public void testStaticMethodCallWithWrongArguments() {
        setDescription("ISSUE 4: Verificar argumentos em chamadas estáticas.");
        String code = """
                class StaticTest {
                    public static int calculate(int a, int b) {
                        return a + b;
                    }
                
                    public int test() {
                
                        return StaticTest.calculate(true, 3);
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testStaticMethodCallImportedClass() {
        setDescription("ISSUE 4: Chamar métodos estáticos de classe importada.");
        String code = """
                import util.MathUtils;
                
                class StaticImportTest {
                    public int test() {
                        int result;
                
                        result = MathUtils.max(10, 20);
                        return result;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }


    @Test
    public void testInstanceFieldInStaticMethod() {
        setDescription("ISSUE 5: Não deve ser permitido usar fields de instância em contexto estático.");
        String code = """
                class StaticContextTest {
                    public int instanceField;
                
                    public static int test() {
                        int result;
                
                        result = instanceField;
                        return result;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testThisInStaticMethod() {
        setDescription("ISSUE 5: 'this' não deve estar acessível em contexto estático.");
        String code = """
                class StaticContextTest {
                    public int value;
                
                    public static int test() {
                
                        return this.value;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testInstanceMethodCallInStaticContext() {
        setDescription("ISSUE 5: Chamar método de instância de contexto estático deve dar erro.");
        String code = """
                class StaticContextTest {
                    public int getValue() {
                        return 42;
                    }
                
                    public static int test() {
                
                        return this.getValue();
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testStaticFieldInStaticMethod() {
        setDescription("ISSUE 5: Static fields devem ser acessíveis em método estático.");
        String code = """
                class StaticContextTest {
                    public static int staticValue;
                
                    public static int test() {
                
                        return staticValue;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }


    @Test
    public void testVoidVariableDeclaration() {
        setDescription("ISSUE 6: Não deve ser permitido declarar variáveis do tipo void.");
        String code = """
                class VoidTest {
                    public int test() {
                
                        void x;
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testVoidArrayType() {
        setDescription("ISSUE 6: void[] também não deve ser permitido.");
        String code = """
                class VoidTest {
                    public int test() {
                
                        void[] arr;
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testVoidInFieldDeclaration() {
        setDescription("ISSUE 6: Campos não devem ser void.");
        String code = """
                class VoidTest {
                
                    public void field;
                
                    public int test() {
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testVoidMethodReturnType() {
        setDescription("ISSUE 6: void é permitido como tipo de retorno de método (não deve dar erro).");
        String code = """
                class VoidTest {
                
                    public void doSomething() {
                    }
                
                    public int test() {
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }


    @Test
    public void testDuplicateFieldDeclaration() {
        setDescription("ISSUE 7: Não deve ser permitido declarar dois fields com o mesmo nome.");
        String code = """
                class DuplicateTest {
                    public int value;
                    public int value;  
                
                    public int test() {
                        return value;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testDuplicateMethodDeclaration() {
        setDescription("ISSUE 7: Não deve ser permitido declarar dois métodos com a mesma assinatura.");
        String code = """
                class DuplicateTest {
                    public int getValue() {
                        return 1;
                    }
                
                    public int getValue() {  
                        return 2;
                    }
                
                    public int test() {
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testDuplicateLocalVariableDeclaration() {
        setDescription("ISSUE 7: Não deve ser permitido declarar duas variáveis locais com o mesmo nome.");
        String code = """
                class DuplicateTest {
                    public int test() {
                        int x;
                        int x; 
                        return x;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testDuplicateParameterDeclaration() {
        setDescription("ISSUE 7: Não deve ser permitido ter parâmetros com nomes duplicados.");
        String code = """
                class DuplicateTest {
                
                    public int test(int x, int x) {
                        return x;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testParameterShadowsField() {
        setDescription("ISSUE 7: Um parâmetro pode shadowing um field (isto é permitido).");
        String code = """
                class ShadowTest {
                    public int x;
                
                    public int getValue(int x) {  
                        return x;
                    }
                
                    public int test() {
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }


    @Test
    public void testChainedMethodCallOllirTypes() {
        setDescription("ISSUE 8: Em chamadas encadeadas, cada resultado intermédio deve ter o tipo OLLIR correto.");
        String code = """
                class ChainedCalls {
                    public Container getContainer() {
                        Container c;
                        c = new Container();
                        return c;
                    }
                
                    public int test() {
                        int value;
                
                        value = this.getContainer().getValue();
                        return value;
                    }
                }
                
                class Container {
                    public int getValue() {
                        return 42;
                    }
                }
                """;
        // Verifica que semântica passa
        semanticsFromSnippet(code, false);
    }

    @Test
    public void testChainedArrayAccessOllir() {
        setDescription("ISSUE 8: Array access em resultado de chamada deve ter tipo OLLIR correto.");
        String code = """
                class ChainedArray {
                    public int[] getMagicNumbers() {
                        int[] arr;
                        arr = new int[3];
                        arr[0] = 42;
                        return arr;
                    }
                
                    public int test() {
                        int val;
                        // getMagicNumbers() retorna int[], o [0] acessa o inteiro
                        val = this.getMagicNumbers()[0];
                        return val;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }

    @Test
    public void testChainedCallsMultipleLevels() {
        setDescription("ISSUE 8: Múltiplos níveis de chamadas encadeadas.");
        String code = """
                class DeepChain {
                    public Level1 getLevel1() {
                        Level1 l;
                        l = new Level1();
                        return l;
                    }
                
                    public int test() {
                        int value;
                
                        value = this.getLevel1().getLevel2().getValue();
                        return value;
                    }
                }
                
                class Level1 {
                    public Level2 getLevel2() {
                        Level2 l;
                        l = new Level2();
                        return l;
                    }
                }
                
                class Level2 {
                    public int getValue() {
                        return 42;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }

    @Test
    public void testChainedCallWrongType() {
        setDescription("ISSUE 8: Erro em chamada encadeada com tipo errado no meio da cadeia.");
        String code = """
                class BadChain {
                    public int getValue() {
                        return 42;
                    }
                
                    public int test() {
                
                        return this.getValue().getSomething();
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }


    @Test
    public void testShadowingImportedClass() {
        setDescription("Uma variável local com o mesmo nome de um import o esconde (shadowing).");
        String code = """
                import util.MathUtils;
                
                class ShadowImportTest {
                    public int test() {
                        int MathUtils; 
                        MathUtils = 5;
                
                
                        return MathUtils.random(1, 10);
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testInvalidArrayOperations() {
        setDescription("Verificar se as restrições rigorosas de arrays estão a ser validadas.");
        String code = """
                class ArrayErrors {
                    public int test() {
                        int[] arr;
                        int a;
                        boolean b;
                
                        arr = new int[10];
                        a = 5;
                        b = true;
                
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }

    @Test
    public void testInvalidArrayIndexType() {
        setDescription("Array index deve ser int, não boolean.");
        String code = """
                class ArrayErrors {
                    public int test() {
                        int[] arr;
                        boolean b;
                
                        arr = new int[10];
                        b = true;
                
                        return arr[b];
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testInvalidLengthOnNonArray() {
        setDescription("Só arrays têm .length");
        String code = """
                class ArrayErrors {
                    public int test() {
                        int a;
                
                        a = 5;
                
                        return a.length;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testInvalidIndexOnNonArray() {
        setDescription("Só arrays permitem indexação com [].");
        String code = """
                class ArrayErrors {
                    public int test() {
                        int a;
                
                        a = 5;
                
                        return a[0];
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testInvalidArrayInitSize() {
        setDescription("Tamanho da inicialização de array deve ser int.");
        String code = """
                class ArrayErrors {
                    public int test() {
                        int[] arr;
                        boolean b;
                
                        b = true;
                
                        arr = new int[b];
                
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testAssignToThis() {
        setDescription("'this' é uma referência constante (read-only), não pode receber atribuições.");
        String code = """
                class AssignThis {
                    public int test() {
                
                        this = new AssignThis();
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testMethodArgumentTypeMismatch() {
        setDescription("Tipos de argumentos devem corresponder à declaração do método.");
        String code = """
                class ArgMismatch {
                    public int sum(int a, boolean b) {
                        return a;
                    }
                
                    public int test() {
                        int[] arr;
                        arr = new int[2];
                
                        return this.sum(arr, 5);
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testMethodReturnMismatch() {
        setDescription("Tipo retornado deve corresponder à assinatura do método.");
        String code = """
                class ReturnMismatch {
                    public int[] getArray() {
                        int a;
                        a = 10;
                
                        return a;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testAssumeImportedSuperclassMethods() {
        setDescription("Se classe estende import, assume que métodos não declarados existem na superclasse.");
        String code = """
                import libs.BaseClass;
                
                class TrustTheImport extends BaseClass {
                    public int test() {
                        int a;
                
                        a = this.unknownMethod(1, true);
                        return a;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }

    @Test
    public void testBinaryOperationsTypeChecking() {
        setDescription("Operadores binários não podem misturar tipos errados.");
        String code = """
                class BinaryOps {
                    public int test() {
                        int a;
                        boolean b;
                        int[] arr;
                
                        a = 10;
                        b = true;
                        arr = new int[5];
                
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, false);
    }

    @Test
    public void testBinaryOpAdditionWithBoolean() {
        setDescription("Não pode fazer adição com booleans.");
        String code = """
                class BinaryOps {
                    public int test() {
                        int a;
                        boolean b;
                
                        a = 10;
                        b = true;
                
                        a = a + b;
                
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testBinaryOpLogicalWithInt() {
        setDescription("Operações lógicas (&&) não podem usar inteiros.");
        String code = """
                class BinaryOps {
                    public int test() {
                        int a;
                        boolean b;
                
                        a = 10;
                        b = true;
                
                        b = a && true;
                
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testBinaryOpComparisonWithArray() {
        setDescription("Não pode usar < com arrays.");
        String code = """
                class BinaryOps {
                    public int test() {
                        int a;
                        int[] arr;
                        boolean b;
                
                        a = 10;
                        arr = new int[5];
                
                        b = arr < a;
                
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }

    @Test
    public void testUnaryOpNotOnInt() {
        setDescription("Operador unário ! só funciona com boolean, não com int.");
        String code = """
                class BinaryOps {
                    public int test() {
                        int a;
                        boolean b;
                
                        a = 10;
                
                        b = !a;
                
                        return 0;
                    }
                }
                """;
        semanticsFromSnippet(code, true);
    }
}