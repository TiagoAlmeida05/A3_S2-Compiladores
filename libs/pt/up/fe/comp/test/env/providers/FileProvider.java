package pt.up.fe.comp.test.env.providers;

import pt.up.fe.specs.util.SpecsCheck;
import pt.up.fe.specs.util.SpecsIo;

import java.io.File;

/**
 * Provides a link to an existing file and its content.
 */
public class FileProvider extends ContentProvider {
    private final File file;
    private String content;
    private final boolean tempFile;

    public FileProvider(String description, File file) {
        this(description, file, false);
    }

    public FileProvider(String description, File file, boolean tempFile) {
        super(description);
        SpecsCheck.checkNotNull(file, () -> "File of FileProvider cannot be null");
        this.file = file;
        this.tempFile = tempFile;
    }

    @Override
    public File getFile() {
        return file;
    }

    @Override
    public boolean isTempFile() {
        return this.tempFile;
    }

    @Override
    public String getContent() {
        if (content == null) {
            content = SpecsIo.read(file);
        }
        return content;
    }
}
