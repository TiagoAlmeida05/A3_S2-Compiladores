package pt.up.fe.comp.jmm.analysis.table.type.impls;

import pt.up.fe.comp.jmm.analysis.table.type.JmmType;

import java.util.Arrays;
import java.util.Optional;

public enum JmmPrimitiveType implements JmmType {
    //in JMM project
    INT,
    BOOLEAN,
    VOID,
    //other java primitives
    BYTE,
    SHORT,
    CHAR,
    LONG,
    FLOAT,
    DOUBLE;

    /**
     *
     * @param s
     * @return the Enum value if exists, empty otherwise
     */
    public static Optional<JmmPrimitiveType> fromString(String s) {
        if (s == null) {
            return Optional.empty();
        }
        var upper = s.toUpperCase();
        return Arrays.stream(JmmPrimitiveType.values())
                .filter(p -> p.name().equals(upper))
                .findFirst();

    }

    public static boolean isPrimitive(String s) {
        return fromString(s).isPresent();
    }

    public String print() {
        return name().toLowerCase();
    }

    @Override
    public boolean isArray() {
        return false;
    }

    @Override
    public boolean isPrimitive() {
        return true;
    }

    @Override
    public boolean isClass() {
        return false;
    }
}
