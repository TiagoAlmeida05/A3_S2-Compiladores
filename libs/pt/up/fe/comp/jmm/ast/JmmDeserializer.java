package pt.up.fe.comp.jmm.ast;

import java.lang.reflect.Type;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.reflect.TypeToken;

class JmmDeserializer implements JsonDeserializer<JmmNodeImpl> {

    private final Function<String, ? extends Kind> stringToKind;

    public JmmDeserializer(Function<String, ? extends Kind> stringToKind) {
        this.stringToKind = stringToKind;
    }

    private Type ListOfJmm = new TypeToken<List<JmmNodeImpl>>() {
    }.getType();

    private Type MapOfAttrs = new TypeToken<Map<String, String>>() {
    }.getType();

    private Type CollectionOfStrings = new TypeToken<Collection<String>>() {
    }.getType();

    @Override
    public JmmNodeImpl deserialize(JsonElement jsonElement, Type type,
            JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {

        JsonObject jsonObject = jsonElement.getAsJsonObject();
        String kind = jsonDeserializationContext.deserialize(jsonObject.get("kind"), String.class);
//        List<String> hierarchy = jsonDeserializationContext.deserialize(jsonObject.get("hierarchy"), this.CollectionOfStrings);

        JmmNodeImpl node = new JmmNodeImpl(stringToKind.apply(kind));
        Map<String, String> attrs = jsonDeserializationContext.deserialize(jsonObject.get("attributes"),
                this.MapOfAttrs);
        attrs.forEach(node::put);
        List<JmmNodeImpl> children = jsonDeserializationContext.deserialize(jsonObject.get("children"), this.ListOfJmm);
        children.forEach(child -> {
            child.setParent(node);
            node.add(child);
        });


        return node;
    }
}