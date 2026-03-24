/**
 * Copyright 2021 SPeCS.
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except in compliance with
 * the License. You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software distributed under the License is distributed on
 * an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations under the License. under the License.
 */

package pt.up.fe.comp.jmm.ollir;

import org.specs.comp.ollir.ClassUnit;
import org.specs.comp.ollir.Ollir;
import org.specs.comp.ollir.OllirParseResult;
import org.specs.comp.ollir.antlr.ErrorReport;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.List;

public class OllirUtils {

    /**
     * Parses OLLIR code.
     *
     * @param code
     * @return a ClassUnit representing all the information from the OLLIR code
     *
     */
    public static OllirParseResult parse(String code) {
        try {
            var result = Ollir.parse(code);
            if (!result.hasErrors())

                result.classUnit().buildVarTables();
            return result;
        } catch (Exception e) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            pw.flush();

            return OllirParseResult.of(List.of(ErrorReport.of("Exception while parsing OLLIR code: " + e.getMessage() + "\n" + sw)));
//            throw new RuntimeException("Exception while parsing OLLIR code:\n" + code, e);
        }
    }
//        OllirParser parser = new OllirParser(SpecsIo.toInputStream(code));
//
//        try {
//            // parse the input OLLIR and represent it by the class structure used
//            parser.ClassUnit();
//
//            // get Class instance representing the OLLIR file loaded
//            var classUnit = parser.getMyClass();
//
//            // Automatically build var tables
//            classUnit.buildVarTables();
//
//            return classUnit;
//            // TODO: Falta análise semântica no ollir, como forçar literais booleans a true/false,
//            // toda semântica que lhes pedimos mais verificações de tipos e imports nas declaracoes
//        } catch (Exception e) {
//            // // Get cause
//            // // Throwable currentException = e;
//            // String causeMessage = e.getMessage();
//            // if (e.getCause() != null) {
//            // causeMessage = e.getCause().getMessage();
//            // }
//            // // while (currentException != null) {
//            // // causeMessage = currentException.getMessage();
//            // // currentException = currentException.getCause();
//            // // }
//            //
//            // throw new RuntimeException(
//            // "Error on line " + e.currentToken.beginLine + ": " + causeMessage, e);
//
//            throw new RuntimeException("Exception while parsing OLLIR code:\n" + code, e);
//        }

//    }

}
