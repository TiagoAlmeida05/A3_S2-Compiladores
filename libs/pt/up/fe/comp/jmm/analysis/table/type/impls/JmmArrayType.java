package pt.up.fe.comp.jmm.analysis.table.type.impls;


import pt.up.fe.comp.jmm.analysis.table.type.JmmType;

import java.util.Objects;

public record JmmArrayType(JmmType itemType, int dimension) implements JmmType {
    public JmmArrayType {
        Objects.requireNonNull(itemType, "itemType is null");
        assert !(itemType instanceof JmmArrayType) : "itemType cannot be of Array type. Use dimension property for multidimensional arrays";
        assert dimension > 0 : "Array dimension must be a positive integer, but " + 0 + " was given.";
    }

    public static JmmArrayType of(JmmType itemType) {
        return JmmArrayType.of(itemType, 1);
    }

    public static JmmArrayType of(JmmType itemType, int dimension) {
        return new JmmArrayType(itemType, dimension);
    }

    @Override
    public String print() {

        return itemType.print() + "[]".repeat(dimension);
    }

    @Override
    public boolean isArray() {
        return true;
    }

    @Override
    public boolean isPrimitive() {
        return itemType.isPrimitive();
    }

    @Override
    public boolean isClass() {
        return false;
    }
}

