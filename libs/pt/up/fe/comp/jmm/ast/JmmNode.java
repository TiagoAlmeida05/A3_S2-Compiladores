package pt.up.fe.comp.jmm.ast;

import pt.up.fe.comp.jmm.utils.Attributes;
import pt.up.fe.specs.util.SpecsCollections;

import java.util.*;
import java.util.stream.Stream;

/**
 * This interface represents a node in the Jmm AST.
 *
 * @author COMP2021
 */
public interface JmmNode extends Attributes {


    /**
     * @return the kind of this node (e.g. MethodDeclaration, ClassDeclaration, etc.)
     */
    Kind getKind();

    /**
     * Creates a new node, with a new kind, but makes a shallow copy of the attributes of the current node.
     *
     * @param newKind
     */
    JmmNode copy(Kind newKind);

    default JmmNode copy() {
        return copy(getKind());
    }

    /**
     * @return a ordered collection of Strings with the hierarchy of the node, starting from the most specific class
     * (e.g. a node that is an expr and a binaryExpr will return ['binaryExpr', 'expr']
     */
    default List<Kind> getHierarchy() {
        var list = new ArrayList<Kind>();
        list.add(getKind());
        var parent = getKind().extend();
        while (parent.isPresent()){
            list.add(parent.get());
            parent = parent.get().extend();
        }
        return list;
    }


    /**
     * @return the parent of the current node, or null if this is the root node
     */
    default JmmNode getParent() {
        throw new RuntimeException("Not implemented for this class: " + getClass());
    }

    /**
     * @param kind
     * @return the first ancestor of the given kind, or Optional.empty() if no ancestor of that kind was found
     */
    default Optional<JmmNode> getAncestor(Kind kind) {
        var currentParent = getParent();
        while (currentParent != null) {

            if (currentParent.isInstance(kind)) {
                return Optional.of(currentParent);
            }
            currentParent = currentParent.getParent();
        }
        return Optional.empty();
    }

//    /**
//     * Helper method which calls .toString() over the argument 'kind'.
//     *
//     * @param kind
//     * @return
//     */
//    default Optional<JmmNode> getAncestor(Kind kind) {
//        return getAncestor(kind.toString());
//    }

    /**
     * @return the children of the node or an empty list if there are no children. The returned list is a copy of the
     * underlying list, so changes to this list will not be reflected on the AST. To change the AST please use
     * the node methods
     */
    List<JmmNode> getChildren();

    /**
     * @param kind
     * @return the children that are of the given kind
     */
    default List<JmmNode> getChildren(Kind kind) {
        return getChildren().stream()
                .filter(node -> node.isInstance(kind))
                .toList();
    }

//    /**
//     * Helper method which calls .toString() over the argument 'kind'.
//     *
//     * @param kind
//     * @return
//     */
//    default List<JmmNode> getChildren(Object kind) {
//        return getChildren(kind.toString());
//    }


    /**
     * @return the children of the node, as a stream
     */
    default Stream<JmmNode> getChildrenStream() {
        return getChildren().stream();
    }

    /**
     * @return the descendants of the node, as a stream
     */
    default Stream<JmmNode> getDescendantsStream() {
        return getChildrenStream().flatMap(c -> c.getDescendantsAndSelfStream());
    }


    /**
     * @return the current node and its descendants, as a stream
     */
    default Stream<JmmNode> getDescendantsAndSelfStream() {
        return Stream.concat(Stream.of((JmmNode) this), getDescendantsStream());
    }

    /**
     * @return the descendants of the node, as a list
     */
    default List<JmmNode> getDescendants() {
        return getDescendantsStream().toList();
    }

    /**
     * @param kind
     * @return the descendants of the node of the given kind, as a list
     */
    default List<JmmNode> getDescendants(Kind kind) {
        return getDescendantsStream().filter(node -> node.isInstance(kind)).toList();
    }
//
//    /**
//     * Helper method which calls .toString() over the argument 'kind'.
//     *
//     * @param kind
//     * @return
//     */
//    default List<JmmNode> getDescendants(Object kind) {
//        return getDescendants(kind.toString());
//    }

    /**
     * @param index
     * @return the child at the given index
     */
    default JmmNode getChild(int index) {
        return getChildren().get(index);
    }


    /**
     * @return the number of children of the node
     */
    default int getNumChildren() {
        return getChildren().size();
    }

    /**
     * Adds a new node at the end of the children list
     *
     * @param child
     */
    default void add(JmmNode child) {
        add(child, getNumChildren());
    }

    /**
     * Inserts a node at the given position, shifts nodes from the position by one.
     *
     * @param child
     * @param index
     */
    void add(JmmNode child, int index);

    /**
     * Replaces a node at the given position. If the given node already has a parent, swaps positions.
     *
     * @param newNode
     * @param index
     */
    default void setChild(JmmNode newNode, int index) {
        throw new RuntimeException("Not implemented for this class: " + getClass());
    }

    static <T> List<JmmNode> convertChildren(T[] children) {
        if (children == null) {
            return new ArrayList<>();
        }

        JmmNode[] jmmChildren = SpecsCollections.convert(children, new JmmNode[children.length],
                child -> (JmmNode) child);

        return Arrays.asList(jmmChildren);
    }

    /**
     * @return a String with a tree representation of this node and its descendants
     */
    default String toTree() {
        var tree = new StringBuilder();
        toTree(tree, "");
        return tree.toString();
    }

    default void toTree(StringBuilder tree, String prefix) {
        tree.append(prefix).append(toString()).append("\n");

        for (var child : getChildren()) {
            child.toTree(tree, prefix + "   ");
        }
    }

    /**
     * Removes the child at the specified position.
     *
     * @param index
     * @return the node that has been removed
     */
    default JmmNode removeChild(int index) {
        return removeChild(index);
    }

    /**
     * Removes the given child.
     *
     * @param node
     * @return the index of the node that has been removed, or -1 if node could not be found
     */
    default int removeChild(JmmNode node) {
        throw new RuntimeException("Not implemented for this class: " + getClass());
    }

    /**
     * Removes this node from the tree.
     */
    default void delete() {
        throw new RuntimeException("Not implemented for this class: " + getClass());
    }

    /**
     * @return the index of this token in its parent token, or -1 if it does not have a parent
     */
    default int getIndexOfSelf() {
        var parent = getParent();
        if (parent == null) {
            return -1;
        }

        return parent.getChildren().indexOf(this);
    }

    /**
     * Removes the parent from this node. If this node does not have a parent, throws an exception.
     */
    default void removeParent() {
        throw new RuntimeException("Not implemented for this class: " + getClass());
    }

    default void setParent(JmmNode parent) {
        throw new RuntimeException("Not implemented for this class: " + getClass());
    }

    /**
     * Replaces the current node with the given node. If the given node already has a parent, swaps nodes.
     *
     * @param newNode
     */
    default void replace(JmmNode newNode) {
        var parent = getParent();
        if (parent == null) {
            System.out.println("Tried to replace node, but it does not have a parent. Base node:\n" + this
                    + "\nNew node:\n" + newNode);
            return;
        }

        int currentNodeIndex = getIndexOfSelf();

        parent.setChild(newNode, currentNodeIndex);
    }

    /**
     * @param kind
     * @return true if the node is an instance of the given kind
     */
    default boolean isInstance(Kind kind) {
        return getHierarchy().contains(kind);
    }

//    /**
//     * Helper method which calls .toString() over the argument 'kind'.
//     *
//     * @param kind
//     * @return true if the node is an instance of the given kind
//     */
//    default boolean isInstance(Object kind) {
//        return isInstance(kind.toString());
//    }


    default int getLine() {
        return getInteger("lineStart", -1);
    }

    default int getColumn() {
        return getInteger("colStart", -1);
    }

}
