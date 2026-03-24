package pt.up.fe.comp.jmm.analysis.table;

import pt.up.fe.comp.jmm.analysis.table.type.JmmType;

import java.util.List;
import java.util.Objects;

public record Signature(String name, List<? extends JmmType> parameters) {
    public Signature {
        Objects.requireNonNull(name);
        Objects.requireNonNull(parameters);
    }

    public static Signature of(String name, List<? extends JmmType> parameters) {
        return new Signature(name, parameters);
    }
}
