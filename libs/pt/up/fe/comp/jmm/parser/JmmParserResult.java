package pt.up.fe.comp.jmm.parser;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.JmmSerializer;
import pt.up.fe.comp.jmm.report.Report;
import pt.up.fe.comp.jmm.report.StageResult;

import java.util.*;

public record JmmParserResult(JmmNode rootNode, List<Report> reports,
                              Map<String, String> config) implements StageResult {

    // this.rootNode = rootNode != null ? rootNode.sanitize() : null;

    /**
     * Utility constructor when an error occurs.
     *
     * @param errorReport
     */
    // public JmmParserResult(Report errorReport) {
    // this(null, Arrays.asList(errorReport), Collections.emptyMap());
    // }
    public static JmmParserResult newError(Report errorReport) {
        return newError(errorReport, new HashMap<>());
    }

    public static JmmParserResult newError(Report errorReport, Map<String, String> config) {
        return new JmmParserResult(null, new ArrayList<>(Collections.singletonList(errorReport)), config);
    }

    public String toJson() {
        Gson gson = new GsonBuilder()
                .setPrettyPrinting()
                // .excludeFieldsWithoutExposeAnnotation()
                .registerTypeAdapter(JmmNode.class, new JmmSerializer())
                .create();
        return gson.toJson(this, JmmParserResult.class);
    }
}
