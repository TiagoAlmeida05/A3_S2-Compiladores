package pt.up.fe.comp.test.env.providers;

import java.io.*;

/**
 * A ConcentProvider that allows continuous stream input.
 */
public class StreamProvider extends TextProvider {

    private final OutputStream out;

    public StreamProvider(String description, OutputStream out, String extension) {
        super(description, "", extension);
        this.out = out;
    }

    public void write(String data) {
        try {
            out.write(data.getBytes());
        } catch (IOException e) {
            throw new RuntimeException("Could not write to output stream", e);
        }
    }

    public void writeln(String data) {

        this.write(data);
        this.write(System.lineSeparator());

    }

    @Override
    public boolean isTempFile() {
        return true;
    }

    @Override
    public String getContent() {
        try {
            out.flush();
        } catch (IOException e) {
            throw new RuntimeException("Could not flush output stream", e);
        }
        return out.toString();
    }
}
