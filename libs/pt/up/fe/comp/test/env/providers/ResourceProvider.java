package pt.up.fe.comp.test.env.providers;

import pt.up.fe.specs.util.SpecsIo;

import java.io.File;

/**
 * Provides content from a resource file, and either a link to the resource file or a temporary file with the resource content if the <code>resourceBaseDir</code> parameter is null.
 */
public class ResourceProvider extends ContentProvider {

    private final String resourcePath;
    private final String resourceBaseDir;
    private File fileRef;
    private String content;
    private boolean tempFile;

    public ResourceProvider(String description, String resourcePath, String resourceBaseDir) {
        super(description);
        this.resourcePath = resourcePath;
        this.resourceBaseDir = resourceBaseDir;
        this.tempFile = true;
    }

    @Override
    public File getFile() {
        if (fileRef == null) {
            //create link to resource with resource base dir if not null, otherwise generate temp file
            if (resourceBaseDir != null) {
                File baseDir = new File(resourceBaseDir);
                fileRef = new File(baseDir, resourcePath);
                tempFile = false;
            } else {
                var fileName = SpecsIo.getResourceName(resourcePath);
                var extension = SpecsIo.getExtension(fileName);
                fileName = fileName.replace("." + extension, "");
                fileRef = createTempFile(fileName, extension);
                tempFile = true;
            }
        }
        return fileRef;
    }

    @Override
    public boolean isTempFile() {
        if (fileRef == null) {
            //force creation of fileRef to know if temp file or not
            getFile();
        }
        return tempFile;
    }

    @Override
    public String getContent() {
        if (content == null) {
            content = SpecsIo.getResource(resourcePath);
            if (content == null) {
                throw new RuntimeException("Resource not found: " + resourcePath);
            }
        }
        return content;
    }
}
