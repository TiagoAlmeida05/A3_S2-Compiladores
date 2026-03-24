package pt.up.fe.comp.jmm.analysis.table.reflection;

import pt.up.fe.comp.jmm.analysis.table.MethodSymbol;
import pt.up.fe.comp.jmm.analysis.table.Signature;
import pt.up.fe.comp.jmm.analysis.table.Symbol;
import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.type.JmmType;

import java.util.*;
import java.util.stream.Collectors;

record ImportedSymbolTable(String classQualifiedName,
                           String superQualifiedName, Map<String,Symbol> fields,
                           Map<Signature, MethodSymbol> methods) implements SymbolTable {

    @Override
    public String getFullyQualifiedName() {
        return this.classQualifiedName;
    }

    @Override
    public String getSuperFullyQualifiedName() {
        return this.superQualifiedName;
    }

    @Override
    public List<String> getImports() {
        return List.of();
    }

    @Override
    public List<Symbol> getFields() {
        return fields.values().stream().toList();
    }

    @Override
    public Optional<Symbol> getField(String name) {
        return Optional.ofNullable(fields.getOrDefault(name,null));
    }

    @Override
    public List<MethodSymbol> getMethods() {
        return methods.values().stream().toList();
    }

    @Override
    public List<MethodSymbol> getMethods(String name) {
        return methods.values().stream().filter(m->m.name().equals(name)).toList();
    }

    @Override
    public Optional<MethodSymbol> getMethod(Signature signature) {
        return Optional.ofNullable(methods.getOrDefault(signature,null));
    }

    @Override
    public Optional<String> getImportedFullyQualifiedName(String simpleName) {
       throw new UnsupportedOperationException("This method should not be used in a Symbol Table of an Imported Class.");
    }


    @Override
    public Collection<String> getAttributes() {
        throw new UnsupportedOperationException("Symbol Table for imports does not support Attributes.");
    }

    @Override
    public Object getObject(String attribute) {
        throw new UnsupportedOperationException("Symbol Table for imports does not support Attributes.");
    }

    @Override
    public Object putObject(String attribute, Object value) {
        throw new UnsupportedOperationException("Symbol Table for imports does not support Attributes.");
    }
}
