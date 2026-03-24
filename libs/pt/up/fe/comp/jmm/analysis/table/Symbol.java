package pt.up.fe.comp.jmm.analysis.table;

import pt.up.fe.comp.jmm.analysis.table.type.JmmType;

import java.util.Objects;

public record Symbol(JmmType type, String name) {

    public Symbol {
        Objects.requireNonNull(type);
        Objects.requireNonNull(name);
    }

    public String print() {

        return type().print() + " " + name();
    }
}
