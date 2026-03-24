package pt.up.fe.comp.jmm.ast;

import pt.up.fe.specs.util.providers.KeyProvider;

import java.util.*;

public interface Kind extends KeyProvider<String> {
    Optional<Kind> extend();

    default boolean isInstance(Kind ofThisKind){
        return getHierarchy().contains(ofThisKind);
    }

    default List<Kind> getHierarchy(){
        var hierarchy = new ArrayList<Kind>();
        var thisConcreteKind = this;
        hierarchy.add(thisConcreteKind);
        var extend = this.extend();
        while (extend.isPresent()){
            hierarchy.add(extend.get());
            var ext = extend.get();
            extend = ext.extend();
        }
        return hierarchy;
    }

    static  Optional<Kind> fromString(String st, Class<Kind> kind){
        for(var ck : kind.getEnumConstants()){
            if(ck.getKey().equals(st)){
                return Optional.of(ck);
            }
        }
        return Optional.empty();
    }

    /**
     * Tests if the given JmmNode has the same kind as this type.
     *
     * @param node
     * @return
     */
    default boolean check(JmmNode node) {
        return node.isInstance(this);
    }

    /**
     * Performs a check and throws if the test fails. Otherwise, does nothing.
     *
     * @param node
     */
    default void checkOrThrow(JmmNode node) {

        if (!check(node)) {
            throw new RuntimeException("Node '" + node + "' is not a '" + getKey() + "'");
        }
    }

    /**
     * Performs a check on all kinds to test and returns false if none matches. Otherwise, returns true.
     *
     * @param node
     * @param kindsToTest
     * @return
     */
    public static boolean check(JmmNode node, Kind... kindsToTest) {

        for (Kind k : kindsToTest) {

            // if any matches, return successfully
            if (k.check(node)) {

                return true;
            }
        }

        return false;
    }

    /**
     * Performs a check an all kinds to test and throws if none matches. Otherwise, does nothing.
     *
     * @param node
     * @param kindsToTest
     */
    public static void checkOrThrow(JmmNode node, Kind... kindsToTest) {
        if (!check(node, kindsToTest)) {
            // throw if none matches
            throw new RuntimeException("Node '" + node + "' is not any of " + Arrays.asList(kindsToTest));
        }
    }
}