package pt.up.fe.comp.jmm.analysis.table.type;

import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmArrayType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmClassType;
import pt.up.fe.comp.jmm.analysis.table.type.impls.JmmPrimitiveType;

//alterar "Type" para ser mais proximo daquilo que eles vao ter de trabalhar
//idea: Type is interface, then record for ClassType, PrimitiveType (que recebe um valor de enum) e ArrayType
// fica mais proximo do OLLIR
// Interface iria ter:
public interface JmmType {

    String print();

    boolean isArray();

    boolean isPrimitive();

    boolean isClass();

    default JmmArrayType asArray() {
        if (this instanceof JmmArrayType)
            return (JmmArrayType) this;
        else
            throw new UnsupportedOperationException("Cannot cast type " + this + " to array type.");
    }
    
    default JmmPrimitiveType asPrimitive() {
        if (this instanceof JmmPrimitiveType)
            return (JmmPrimitiveType) this;
        else
            throw new UnsupportedOperationException("Cannot cast type " + this + " to primitive type.");
    }

    default JmmClassType asClass() {
        if (this instanceof JmmClassType)
            return (JmmClassType) this;
        else
            throw new UnsupportedOperationException("Cannot cast type " + this + " to class type.");
    }
}
