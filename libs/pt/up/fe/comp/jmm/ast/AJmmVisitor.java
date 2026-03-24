package pt.up.fe.comp.jmm.ast;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiFunction;

import pt.up.fe.specs.util.SpecsCheck;

/**
 * 
 * @author Joao Bispo
 *
 * @param <D>
 * @param <R>
 */
public abstract class AJmmVisitor<D, R> implements JmmVisitor<D, R> {

    private final Map<Kind, BiFunction<JmmNode, D, R>> visitMap;
    private BiFunction<JmmNode, D, R> defaultVisit;

    public AJmmVisitor() {
        this.visitMap = new HashMap<>();

        // Initialize visitors
        buildVisitor();
    }

    protected abstract void buildVisitor();

    @Override
    public void addVisit(Kind kind, BiFunction<JmmNode, D, R> method) {
        this.visitMap.put(kind, method);
    }

    @Override
    public void setDefaultVisit(BiFunction<JmmNode, D, R> defaultVisit) {
        this.defaultVisit = defaultVisit;
    }

    /**
     * 
     * @param kind
     * @return the visit method to use, or default if no visit method was found
     */
    protected BiFunction<JmmNode, D, R> getVisit(JmmNode node) {

        // Iterate over node hierarchy, in order, until a visitor is found
        for (var kind : node.getHierarchy()) {
            var visitMethod = visitMap.get(kind);

            if (visitMethod != null) {
                return visitMethod;
            }
        }

        SpecsCheck.checkNotNull(defaultVisit,
                () -> "Could not find a suitable visit method for node of kind " + node.getKind()
                        + ", and no default visitor is set");

        return defaultVisit;
    }

    @Override
    public R visit(JmmNode jmmNode, D data) {
        SpecsCheck.checkNotNull(jmmNode, () -> "Node should not be null");

        return getVisit(jmmNode).apply(jmmNode, data);
    }

    protected R visitAllChildren(JmmNode node, D data) {
        for (var child : node.getChildren()) {
            visit(child, data);
        }

        return null;

    }
}