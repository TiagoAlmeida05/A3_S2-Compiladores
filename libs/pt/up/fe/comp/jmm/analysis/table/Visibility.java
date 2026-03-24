package pt.up.fe.comp.jmm.analysis.table;

import java.util.Objects;

public enum Visibility {
    PUBLIC,
    PRIVATE,
    PROTECTED,
    PACKAGE_PROTECTED;
    public static Visibility fromString(String visibility){
        Objects.requireNonNull(visibility);
        if(visibility.isEmpty()){
            return PACKAGE_PROTECTED;
        }
        return Visibility.valueOf(visibility.toUpperCase());
    }
}
