package pt.up.fe.comp.jmm.utils;


import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.NodeAttribute;
import pt.up.fe.specs.util.SpecsCollections;

import java.util.*;

/**
 * Interface that allows a class to support generic attributes.
 */
public interface FutureAttributes {

    /**
     * @return the names of the attributes that are present in this object
     */
    Collection<NodeAttribute> getAttributes();

    /**
     * @param attribute
     * @return true if the object contains the given attribute
     */
    default boolean hasAttribute(NodeAttribute attribute) {
        return getAttributes().contains(attribute);
    }


    /**
     * @param attribute
     * @returns the value of an attribute, or throws exception if attribute is not available.
     * <p>
     * To see all the attributes iterate the list provided by
     * {@link Attributes#getAttributes()}
     */
    Object getObject(NodeAttribute attribute);

    /**
     * Sets the value of an attribute, or adds the attribute if not present.
     *
     * @param attribute
     * @param value
     * @returns the previous value assigned to the given attribute, or null if value was assigned before
     */
    Object putObject(NodeAttribute attribute, Object value);


    /**
     * Convenience method which casts the attribute to the given class.
     *
     * @param attribute
     * @param attributeClass
     * @param <T>
     * @return
     */
    default <T> T getObject(NodeAttribute attribute, Class<T> attributeClass) {
        return attributeClass.cast(getObject(attribute));
    }

    /**
     * Attempts to retrieve and convert the value of the corresponding attribute into a list.
     * <p>
     * Currently supports values which are arrays or a Collection.
     *
     * @param attribute
     * @return
     */
    default List<Object> getObjectAsList(NodeAttribute attribute) {
        var value = getObject(attribute);

        if (value.getClass().isArray()) {
            return Arrays.asList((Object[]) value);
        }

        if (value instanceof Collection) {
            return new ArrayList<>((Collection<?>) value);
        }

        throw new RuntimeException("Could not convert object of class '" + value.getClass() + "' in a list");
    }


    /**
     * Convenience method which casts the elements of the list to the given class.
     *
     * @param attribute
     * @param elementClass
     * @param <T>
     * @return
     */
    default <T> List<T> getObjectAsList(NodeAttribute attribute, Class<T> elementClass) {
        return SpecsCollections.cast(getObjectAsList(attribute), elementClass);
    }

    /**
     * @param attribute
     * @return the value of the attribute wrapped around an Optional, or Optional.empty() if there is no value for the
     * given attribute
     */
    default Optional<Object> getOptionalObject(NodeAttribute attribute) {
        if (!hasAttribute(attribute)) {
            return Optional.empty();
        }

        return Optional.ofNullable(getObject(attribute));
    }

    /**
     * @param attribute
     * @returns the value of an attribute as a String. To see all the attributes iterate the list provided by
     * {@link JmmNode#getAttributes()}
     */
    default String get(NodeAttribute attribute) {
        return getObject(attribute).toString();
    }

    /**
     * @param attribute
     * @return the value of the attribute as a String, wrapped around an Optional, or Optional.empty() if there is no
     * value for the given attribute
     */
    default Optional<String> getOptional(NodeAttribute attribute) {
        return getOptionalObject(attribute).map(value -> value.toString());
    }

    default int getInteger(NodeAttribute attribute, int defaultVal) {
        return getOptional(attribute).map(Integer::parseInt).orElse(defaultVal);
    }

    default boolean getBoolean(NodeAttribute attribute, boolean defaultVal) {
        return getOptional(attribute).map(Boolean::parseBoolean).orElse(defaultVal);
    }


    /**
     * Sets the value of an attribute that is a String.
     *
     * @param attribute
     * @param value
     */
    default String put(NodeAttribute attribute, String value) {
        return (String) putObject(attribute, value);
    }
}
