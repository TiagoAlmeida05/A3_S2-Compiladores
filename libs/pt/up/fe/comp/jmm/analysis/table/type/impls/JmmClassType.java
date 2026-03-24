package pt.up.fe.comp.jmm.analysis.table.type.impls;

import pt.up.fe.comp.jmm.analysis.table.type.JmmType;

import java.util.Objects;

//alterar "Type" para ser mais proximo daquilo que eles vao ter de trabalhar
//idea: Type is interface, then record for ClassType, PrimitiveType (que recebe um valor de enum) e ArrayType
// fica mais proximo do OLLIR
// Interface iria ter:
public record JmmClassType(String fullyQualifiedName, boolean isImported, boolean staticRef) implements JmmType {
    /**
     * Build a Type with the three properties (name, staticRef, and isImported)
     *
     * @param fullyQualifiedName
     * @param isImported
     * @param staticRef
     */
    public JmmClassType {
        Objects.requireNonNull(fullyQualifiedName);
    }


    /**
     * Create a new type that represents an instance of the 'fullyQualifiedName' class
     * <br/>
     * E.g. to invoke virtual methods (a.foo(), where a is an instance of A)
     *
     * @param fullyQualifiedName
     * @param isImported
     * @return
     */
    public static JmmClassType ofInstance(String fullyQualifiedName, boolean isImported) {
        return new JmmClassType(fullyQualifiedName, isImported, false);
    }

    public static JmmClassType ofInstance(Class<?> cls, boolean isImported) {
        return new JmmClassType(cls.getName(), isImported, false);
    }

    /**
     * Create a new type that represents a static reference to the 'fullyQualifiedName' class.
     * <br/>
     * E.g. to invoke static methods (A.foo(), where A is a class)
     *
     * @param fullyQualifiedName
     * @param isImported
     * @return
     */
    public static JmmClassType ofStaticReference(String fullyQualifiedName, boolean isImported) {
        return new JmmClassType(fullyQualifiedName, isImported, true);
    }

    public static JmmClassType ofStaticReference(Class<?> cls, boolean isImported) {
        return new JmmClassType(cls.getName(), isImported, true);
    }


    public String name() {
        var fullyQualifiedName = fullyQualifiedName();
        if (!fullyQualifiedName.contains(".")) {
            return fullyQualifiedName;
        }
        return fullyQualifiedName.substring(fullyQualifiedName.lastIndexOf('.') + 1);
    }

    public String packageName() {
        var fullyQualifiedName = fullyQualifiedName();
        if (!fullyQualifiedName.contains(".")) {
            return "";
        }
        return fullyQualifiedName.substring(0, fullyQualifiedName.lastIndexOf('.'));
    }

    public String print() {
        return fullyQualifiedName;
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isPrimitive() {
        return false;
    }

    @Override
    public boolean isClass() {
        return true;
    }

}
