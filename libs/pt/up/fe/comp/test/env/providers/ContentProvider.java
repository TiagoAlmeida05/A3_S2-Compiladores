package pt.up.fe.comp.test.env.providers;

import pt.up.fe.specs.lang.SpecsPlatforms;

import java.io.File;
import java.io.IOException;


/**
 * An instance of this interface provides text content (code or normal text) and a file reference (either a link to an existing file or a temporary file).
 * The current implementations are:
 * - {@link FileProvider}: provides a link to an existing file.
 * - {@link ResourceProvider}: provides content from a resource file, and either a link to the resource file or a temporary file with the resource content.
 * - {@link TextProvider}: provides text content, and a temporary file with the content.
 */
public abstract class ContentProvider {

    private String description;

    //get file, if resource then create a link to the resource with the resource base dir
    //otherwise return the file as is, if null return a temporary file
    public ContentProvider(String description) {
        this.description = description;
    }

    public abstract File getFile();

    public abstract String getContent();

    public abstract boolean isTempFile();


    protected File createTempFile(String extension) {
        var prefix = description != null ? description : "temp";
        return createTempFile(prefix, extension);
    }

    protected File createTempFile(String prefix, String extension) {
        try {
            prefix = prefix.replaceAll("[^a-zA-Z0-9]", "_") + "_";
            extension = extension != null ? "." + extension : ".txt";
            return File.createTempFile(prefix, extension);
        } catch (IOException e) {
            throw new RuntimeException("Could not create temporary file with prefix " + prefix + " and extension " + extension, e);
        }
    }

    public String getFileLink() {
        var prefix = SpecsPlatforms.isWindows() ? "file://" : "file:///";
        return prefix + getFile().toURI().getPath();
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

