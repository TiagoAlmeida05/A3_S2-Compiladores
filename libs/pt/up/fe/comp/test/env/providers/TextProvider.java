package pt.up.fe.comp.test.env.providers;

import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.SpecsIo;

import java.io.File;

/**
 * Provides text content and a temporary file if a file reference is needed.
 */
public class TextProvider extends ContentProvider {

    private File fileRef;
    private final String content;
    private final String extension;

    public TextProvider(String description, String content, String extension) {
        super(description);
        SpecsCheck.checkNotNull(content, () -> "Content of TextProvider cannot be null");
        this.content = content;
        this.extension = extension != null ? extension : "txt";
    }

    @Override
    public File getFile() {
        if (fileRef == null) {
            fileRef = createTempFile(extension);
            SpecsIo.write(fileRef, getContent());
        }
        return fileRef;
    }

    @Override
    public boolean isTempFile() {
        return true;
    }

    @Override
    public String getContent() {
        return content;
    }
}
