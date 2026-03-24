package pt.up.fe.comp.test.env.providers;

import pt.up.fe.specs.util.SpecsIo;

import java.io.File;
import java.util.function.Function;

public class ObjectProvider<T> extends ContentProvider {
    private final T object;
    private final Function<T, String> toContent;
    private File fileRef;

    public ObjectProvider(String description, T object, Function<T, String> toContent) {
        super(description);
        this.object = object;
        this.toContent = toContent;
    }

    public ObjectProvider(String description, T object) {
        this(description, object, Object::toString);
    }

    public T get() {
        return object;
    }

    @Override
    public File getFile() {
        if (this.fileRef == null) {
            this.fileRef = createTempFile("txt");
            SpecsIo.write(this.fileRef, getContent());
        }
        return this.fileRef;
    }

    @Override
    public String getContent() {
        return toContent.apply(object);
    }

    @Override
    public boolean isTempFile() {
        return true;
    }
}
