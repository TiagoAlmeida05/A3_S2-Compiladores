package pt.up.fe.comp.jmm.ast;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.SpecsEnums;
import pt.up.fe.specs.util.SpecsSystem;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JmmNodeImpl extends AJmmNode {

    private static final Set<String> ATTR_IGNORE = new HashSet<>(SpecsEnums.getKeys(NodePosition.class));

    protected List<JmmNode> children;
    private JmmNode parent;
    private final Kind kind;


    public JmmNodeImpl(Kind kind) {
        SpecsCheck.checkNotNull(kind, () -> "Node kind must be not null");
        this.kind = kind;
        this.children = new ArrayList<>();
    }

    @Override
    public JmmNode copy(Kind kind) {
        var copy = new JmmNodeImpl(kind);
        for (var attr : this.getAttributes()) {
            copy.put(attr, this.get(attr));
        }

        return copy;
    }

    @Override
    public Kind getKind() {
        return this.kind;//this.getClass().getSimpleName();
    }

    @Override
    public JmmNode getParent() {
        return this.parent;
    }

    @Override
    public List<JmmNode> getChildren() {
        return new ArrayList<>(this.children);
    }


    @Override
    public int getNumChildren() {
        return this.children.size();
    }

    @Override
    public void add(JmmNode child) {
        children.add(child);
        child.setParent(this);
    }

    @Override
    public void add(JmmNode child, int index) {
        this.children.add(index, child);
        child.setParent(this);
    }

    /**
     * Convert the string into a JmmNode instance
     *
     * @param source
     * @return
     */
    public static JmmNodeImpl fromJson(String source, Function<String, ? extends Kind> stringToKind) {

        Gson gson = new GsonBuilder()
                .registerTypeAdapter(JmmNode.class, new JmmDeserializer(stringToKind))
                .registerTypeAdapter(JmmNodeImpl.class, new JmmDeserializer(stringToKind))
                // .excludeFieldsWithoutExposeAnnotation()
                .create();
        return gson.fromJson(source, JmmNodeImpl.class);
    }

    @Override
    public void setParent(JmmNode parent) {
        this.parent = parent;
    }

    @Override
    public String toString() {
        return toString(SpecsSystem.isDebug());
    }

    public String toString(boolean debug) {
        var string = new StringBuilder();

        string.append(getKind());

        var attrs = getAttributes();

        // Remove attributes that should be ignored
        attrs = attrs.stream()
                .filter(attr -> !ATTR_IGNORE.contains(attr))
                .collect(Collectors.toList());

        var attrsString = attrs.isEmpty() ? ""
                : attrs.stream()
                .map(attr -> attr + ": " + get(attr))
                .collect(Collectors.joining(", ", " (", ")"));

        string.append(attrsString);

        if (debug) {
            var locString = getLocationString();
            if (!locString.isEmpty()) {
                locString = " " + locString;
            }

            string.append(locString);
        }

        return string.toString();
    }

    public String getLocationString() {
        // Assume that if it has one, it has all
        var location = new StringBuilder();

        if (hasAttribute(NodePosition.LINE_START.getKey())) {
            location.append(get(NodePosition.LINE_START.getKey()));
        }

        if (hasAttribute(NodePosition.COL_START.getKey())) {
            location.append(":").append(get(NodePosition.COL_START.getKey()));
        }

        if (hasAttribute(NodePosition.LINE_END.getKey())) {
            location.append("->" + get(NodePosition.LINE_END.getKey()));
        }

        if (hasAttribute(NodePosition.COL_END.getKey())) {
            location.append(":").append(get(NodePosition.COL_END.getKey()));
        }

        return location.toString();
    }

    @Override
    public JmmNode removeChild(int index) {
        int numChildren = children.size();
        if (index >= numChildren) {
            System.out.println(
                    "[WARNING] Tried to remove child at index " + index + ", but node only has " + numChildren
                            + " children");
            return null;
        }

        var removedChild = (JmmNodeImpl) children.remove(index);
        removedChild.parent = null;
        return removedChild;
    }

    @Override
    public int removeChild(JmmNode node) {
        // Find index of node
        for (int i = 0; i < children.size(); i++) {
            var child = children.get(i);

            // Test if same node
            if (node != child) {
                continue;
            }

            // Found node
            removeChild(i);
            return i;
        }

        System.out
                .println("[WARNING] Tried to remove child from node, but could not find it.\nChild:" + node
                        + "\nParent:" + this);
        return -1;
    }

    @Override
    public void delete() {
        var parent = getParent();
        if (parent == null) {
            System.out.println("[WARNING] Tried to remove itself from the tree, but node has no parent");
            return;
        }

        parent.removeChild(this);
    }

    @Override
    public void setChild(JmmNode newNode, int index) {
        var currentChild = getChild(index);

        // Remove parent before setting
        JmmNode newNodeParent = newNode.getParent();
        int newNodeCurrentIndex = -1;

        if (newNodeParent != null) {
            newNodeCurrentIndex = newNode.getIndexOfSelf();
            newNode.removeParent();
        }

        children.set(index, newNode);
        newNode.setParent(this);

        // Remove parent from current child
        currentChild.removeParent();

        // If new node had a parent, set this node at the old position of the new node
        if (newNodeParent != null) {
            ((JmmNodeImpl) newNodeParent).children.set(newNodeCurrentIndex, currentChild);
            currentChild.setParent(newNodeParent);
        }
    }

    @Override
    public void removeParent() {
        this.parent = null;
    }

    /**
     * Swaps two nodes. Both the parents and the children of the two nodes are swapped.
     *
     * @param newNode
     */
    public void swap(JmmNode newNode) {

        // swap the children
        List<JmmNode> thisChildren = new ArrayList<>();
        List<JmmNode> newNodeChildren = new ArrayList<>();

        while (!getChildren().isEmpty()) {

            thisChildren.add(removeChild(0));
        }

        while (!newNode.getChildren().isEmpty()) {

            newNodeChildren.add(newNode.removeChild(0));
        }

        thisChildren.forEach(newNode::add);
        newNodeChildren.forEach(this::add);

        // swap the parents
        super.replace(newNode);
    }
}
