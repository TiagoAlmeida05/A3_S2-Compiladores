package pt.up.fe.comp.test.env.utils;

import pt.up.fe.comp.test.env.providers.ContentProvider;
import pt.up.fe.specs.util.SpecsIo;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Helper that constructs an HTML report page from a {@link CompilerHistory} instance and writes it
 * to a temporary file. The generated page contains code snippets, execution reports and logs and
 * is intended to be shown to users when tests fail.
 *
 * <p>Public API:</p>
 * <ul>
 *     <li>{@link #show(String, CompilerHistory, String, String, String)} - create the HTML page and
 *     return a file:// URL pointing to it.</li>
 * </ul>
 */
public class InfoHtml {

    public File baseOutputDir;

    public InfoHtml(File baseOutputDir) {
        this.baseOutputDir = baseOutputDir;
    }

    /**
     * Create an HTML report page for the provided test/context information and return a file://
     * URL referencing the generated temporary file.
     *
     * @param testInfo Information about the current test, used to generate html file, set the page title and description
     * @param history  compiler history containing code snippets, execution results and logs
     * @param message  descriptive message to include in the page (may contain newlines)
     * @param expected expected value displayed in the report (may be {@code null})
     * @param given    actual/given value displayed in the report (may be {@code null})
     * @return a {@code file://} URL pointing to the created temporary HTML file
     * @throws IOException if writing the temporary file or reading the HTML template fails
     */
    public String show(TestInfo testInfo, CompilerHistory history, String message, String expected, String given) throws IOException {
        String html = buildHTML(testInfo.getMethodName(), history, message, expected, given);
        var fileName = testInfo.getTestClass().getName() + "." + testInfo.getMethodName().replaceAll("[^a-zA-Z0-9]", "_") + ".html";
//        Path f = Files.createTempFile("view-", "-" + fileName + ".html");
        var htmlFile = new File(baseOutputDir, fileName);
//        Files.writeString(f, html, StandardCharsets.UTF_8);
        SpecsIo.write(htmlFile, html);
        return "file://" + SpecsIo.normalizePath(htmlFile.getAbsolutePath());
    }


    /**
     * Produce an HTML fragment containing the provided code wrapped in a preformatted block.
     * The method performs HTML escaping on the title and code content.
     *
     * @param title    title shown above the code block
     * @param code     raw code text to include in the block (may be {@code null} or empty)
     * @param language language identifier used for CSS/class hints (e.g. "java", "txt")
     * @return HTML snippet containing an H2 title and a PRE element with escaped code
     */
    private static String codeHtml(String title, String code, String language) {
        String escapedCode = escapeHtml(code);
        String escapedTitle = escapeHtml(title);
        return "<h2>" + escapedTitle + "</h2>"
                + "<pre class=\"prettyprint lang-" + language + " linenums\">"
                + escapedCode
                + "</pre>\n";
    }

    private static String codeHtml(ContentProvider provider, String language) {
        if (provider == null)
            return "";
        return codeHtml(provider.getDescription(), provider.getContent(), language);
    }


    /**
     * Build the full HTML contents for the provided information, write it to a temporary file and
     * return the {@link Path} to that file.
     *
     * <p>The method reads an HTML template resource named {@code templates/feedback/report.html}
     * and replaces placeholders with the escaped title, message, expected/given values and the
     * assembled compiler information.</p>
     *
     * @param title    visible title for the page (used to create a filename and page title)
     * @param history  compiler history instance containing snippets and execution information
     * @param message  descriptive message to include in the page
     * @param expected expected value shown in the report
     * @param given    actual/given value shown in the report
     * @return path to the temporary HTML file created
     * @throws IOException if creating or writing the temporary file or reading the template fails
     */
    private static String buildHTML(String title, CompilerHistory history, String message, String expected, String given) throws IOException {
        String escapedTitle = escapeHtml(title);
        String description = escapeHtml(history.getTestDescription());
        StringBuilder compilerInfo = new StringBuilder();
        compilerInfo.append(codeHtml(history.getJmm(), "java"));

        if (history.hasComparingCode()) {
            compilerInfo.append(codeHtml(history.getComparingOllir(), "java"));
        }
        compilerInfo.append(codeHtml(history.getOllir(), "java"));
        compilerInfo.append(codeHtml(history.getJasmin(), "java"));

        var execResult = history.getExecutionResult();

        if (execResult != null) {

            compilerInfo.append(codeHtml(history.getExecutionReport(), "log"));
            compilerInfo.append(codeHtml("Standard Output", execResult.getStdout(), "txt"));
            compilerInfo.append(
                    execResult.getStderr().isEmpty() ? "" : codeHtml("Standard Error", execResult.getStderr(), "txt"));
        }
        ContentProvider provider = history.debugMidSteps();
        compilerInfo.append(codeHtml(provider, "log"));
        compilerInfo.append(codeHtml(history.getReportLog(), "log"));
        compilerInfo.append(codeHtml(history.getLogger(), "log"));


        var escapedMessage = escapeHtml(message).replace("\n", "<br>");
        String html = SpecsIo.getResource("templates/feedback/report.html")
                .replace("${title}", escapedTitle)
                .replace("${description}", description.isEmpty() ? "" : "<h2>Description</h2><p>" + description + "</p>")
                .replace("${message}", escapedMessage)
                .replace("${expected}", escapeHtml(expected))
                .replace("${given}", escapeHtml(given))
                .replace("${info}", compilerInfo.toString());
        return html;
    }

    /**
     * Escape special HTML characters in a string to prevent injection and preserve formatting.
     *
     * @param s input string (may be {@code null})
     * @return escaped string safe for inclusion in HTML; returns an empty string if {@code s} is {@code null}
     */
    private static String escapeHtml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;").replace("'", "&#39;");
    }
}