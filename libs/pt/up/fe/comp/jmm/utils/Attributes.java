package pt.up.fe.comp.jmm.utils;


import org.specs.comp.ollir.Node;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.NodeAttribute;
import pt.up.fe.specs.util.SpecsCollections;

import java.util.*;

/**
 * Interface that allows a class to support generic attributes.
 */
public interface Attributes {

    /**
     * @return the names of the attributes that are present in this object
     */
    Collection<String> getAttributes();

    /**
     * @param attribute
     * @return true if the object contains the given attribute
     */
    default boolean hasAttribute(String attribute) {
        return getAttributes().contains(attribute);
    }

    /**
     * @param attribute
     * @return true if the object contains the given attribute
     */
    default boolean hasAttribute(NodeAttribute attribute) {
        return hasAttribute(attribute.getKey());
    }


    /**
     * @param attribute
     * @return the value of an attribute, or throws exception if attribute is not available.
     * <p>
     * To see all the attributes iterate the list provided by
     * {@link Attributes#getAttributes()}
     */
    Object getObject(String attribute);

    /**
     * @param attribute
     * @return the value of an attribute, or throws exception if attribute is not available.
     * <p>
     * To see all the attributes iterate the list provided by
     * {@link Attributes#getAttributes()}
     */
    default Object getObject(NodeAttribute attribute) {
        return getObject(attribute.getKey());
    }

    /**
     * Sets the value of an attribute, or adds the attribute if not present.
     *
     * @param attribute
     * @param value
     * @return the previous value assigned to the given attribute, or null if value was assigned before
     */
    Object putObject(String attribute, Object value);

    /**
     * Sets the value of an attribute, or adds the attribute if not present.
     *
     * @param attribute
     * @param value
     * @return the previous value assigned to the given attribute, or null if value was assigned before
     */
    default Object putObject(NodeAttribute attribute, Object value) {
        return putObject(attribute.getKey(), value);
    }

    /**
     * Convenience method which casts the attribute to the given class.
     *
     * @param attribute
     * @param attributeClass
     * @param <T>
     * @return
     */
    default <T> T getObject(String attribute, Class<T> attributeClass) {
        return attributeClass.cast(getObject(attribute));
    }

    /**
     * Convenience method which casts the attribute to the given class.
     *
     * @param attribute
     * @param attributeClass
     * @param <T>
     * @return
     */
    default <T> T getObject(NodeAttribute attribute, Class<T> attributeClass) {
        return getObject(attribute.getKey(), attributeClass);
    }

    /**
     * Attempts to retrieve and convert the value of the corresponding attribute into a list.
     * <p>
     * Currently supports values which are arrays or a Collection.
     *
     * @param attribute
     * @return
     */
    default List<Object> getObjectAsList(String attribute) {
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
     * Attempts to retrieve and convert the value of the corresponding attribute into a list.
     * <p>
     * Currently supports values which are arrays or a Collection.
     *
     * @param attribute
     * @return
     */
    default List<Object> getObjectAsList(NodeAttribute attribute) {
        return getObjectAsList(attribute.getKey());
    }


    /**
     * Convenience method which casts the elements of the list to the given class.
     *
     * @param attribute
     * @param elementClass
     * @param <T>
     * @return
     */
    default <T> List<T> getObjectAsList(String attribute, Class<T> elementClass) {
        return SpecsCollections.cast(getObjectAsList(attribute), elementClass);
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
        return getObjectAsList(attribute.getKey(), elementClass);
    }

    /**
     * @param attribute
     * @return the value of the attribute wrapped around an Optional, or Optional.empty() if there is no value for the
     * given attribute
     */
    default Optional<Object> getOptionalObject(String attribute) {
        if (!hasAttribute(attribute)) {
            return Optional.empty();
        }

        return Optional.ofNullable(getObject(attribute));
    }

    /**
     * @param attribute
     * @return the value of the attribute wrapped around an Optional, or Optional.empty() if there is no value for the
     * given attribute
     */
    default Optional<Object> getOptionalObject(NodeAttribute attribute) {
        return getOptionalObject(attribute.getKey());
    }

    /**
     * @param attribute
     * @returns the value of an attribute as a String. To see all the attributes iterate the list provided by
     * {@link JmmNode#getAttributes()}
     */
    default String get(String attribute) {
        return getObject(attribute).toString();
    }

    /**
     * @param attribute
     * @returns the value of an attribute as a String.
     */
    default String get(NodeAttribute attribute) {
        return get(attribute.getKey());
    }



    /**
     * @param attribute
     * @return the value of the attribute as a String, wrapped around an Optional, or Optional.empty() if there is no
     * value for the given attribute
     */
    default Optional<String> getOptional(String attribute) {
        return getOptionalObject(attribute).map(value -> value.toString());
    }

    /**
     * @param attribute
     * @return the value of the attribute as a String, wrapped around an Optional, or Optional.empty() if there is no
     * value for the given attribute
     */
    default Optional<String> getOptional(NodeAttribute attribute) {
        return getOptional(attribute.getKey());
    }

    default int getInteger(String attribute, int defaultVal) {
        return getOptional(attribute).map(Integer::parseInt).orElse(defaultVal);
    }

    default int getInteger(NodeAttribute attribute, int defaultVal) {
        return getInteger(attribute.getKey(), defaultVal);
    }

    default boolean getBoolean(String attribute, boolean defaultVal) {
        return getOptional(attribute).map(Boolean::parseBoolean).orElse(defaultVal);
    }

    default boolean getBoolean(NodeAttribute attribute, boolean defaultVal) {
        return getBoolean(attribute.getKey(), defaultVal);
    }

    /**
     * Sets the value of an attribute that is a String.
     *
     * @param attribute
     * @param value
     */
    default String put(String attribute, String value) {
        return (String) putObject(attribute, value);
    }

    default String put(NodeAttribute attribute, String value) {
        return put(attribute.getKey(), value);
    }
}
