package pt.up.fe.comp.jmm.analysis.table;

import pt.up.fe.comp.jmm.analysis.table.type.JmmType;
import pt.up.fe.comp.jmm.utils.Attributes;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MethodSymbol(String name, JmmType returnType,
                           List<Symbol> parameters,
                           List<Symbol> localVariables,
                           boolean isStatic,
                           Visibility visibility) {
    //legacy
    //boolean isVarArgs,
//        ,Map<String, Object> attrs)  {

    public MethodSymbol {
        Objects.requireNonNull(name, "name is null");
        Objects.requireNonNull(returnType, "returnType is null");
        Objects.requireNonNull(parameters, "parameters is null");
        parameters.forEach(param -> Objects.requireNonNull(param, "one of the parameters is null"));
        Objects.requireNonNull(localVariables, "localVariables is null");
        localVariables.forEach(localVar -> Objects.requireNonNull(localVar, "one of the local variables is null"));
        Objects.requireNonNull(visibility, "visibility is null");
    }


    /**
     * Defaults visibility as {@link Visibility#PUBLIC}
     *
     * @param name
     * @param returnType
     * @param parameters
     * @param localVariables
     */
    public MethodSymbol(String name, JmmType returnType, List<Symbol> parameters, List<Symbol> localVariables, boolean isStatic) {
        this(name, returnType, parameters, localVariables, isStatic, Visibility.PUBLIC);
    }


    /**
     * defaults isStatic to false, and visibility as {@link Visibility#PUBLIC}
     *
     * @param name
     * @param returnType
     * @param parameters
     * @param localVariables
     */
    public MethodSymbol(String name, JmmType returnType, List<Symbol> parameters, List<Symbol> localVariables) {
        this(name, returnType, parameters, localVariables, false);
    }


    /**
     * @return the signature of the method
     */
    public Signature signature() {
        return new Signature(name, parameters.stream().map(Symbol::type).toList());
    }


    /**
     * @param name
     * @return a {@link Symbol} representing the parameter, or empty if not found
     */
    public Optional<Symbol> getParameter(String name) {
        return parameters.stream().filter(param -> param.name().equals(name)).findFirst();
    }

    /**
     * @param name
     * @return a {@link Symbol} representing the local variable, or empty if not found
     */
    public Optional<Symbol> getLocalVariable(String name) {
        return localVariables.stream().filter(localVar -> localVar.name().equals(name)).findFirst();
    }


    /**
     * This field should not be directly accessed. Please use one of the methods provided by interface  {@link Attributes}.
     *
     * @throws UnsupportedOperationException since this method should not be used outside the record.
     */
//    @Override
//    public Map<String, Object> attrs(){
//        throw new UnsupportedOperationException("Use one of the methods provided by interface"+Attributes.class.getName()+".");
//    }
//
//    @Override
//    public Collection<String> getAttributes() {
//        return List.of();
//    }
//
//
//    @Override
//    public Object getObject(String attribute) {
//        return null;
//    }
//
//    @Override
//    public Object putObject(String attribute, Object value) {
//        return null;
//    }
}
