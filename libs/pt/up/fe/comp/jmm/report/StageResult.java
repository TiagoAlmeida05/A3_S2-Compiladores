/**
 * Copyright 2022 SPeCS.
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

package pt.up.fe.comp.jmm.report;

import java.util.List;
import java.util.Map;

public interface StageResult {

    List<Report> reports();

    default List<Report> getReports(ReportType type) {
        return reports().stream()
                .filter(r -> r.getType() == type)
                .toList();
    }

    default List<Report> getErrors() {
        return getReports(ReportType.ERROR);
    }

    default boolean hasErrors() {
        return !noErrors();
    }

    default boolean noErrors() {
        return getErrors().isEmpty();
    }

    public Map<String, String> config();

    default void throwIfErrors() {
        if (hasErrors()) {
            throw new RuntimeException("Compilation has errors:\n" + String.join("\n", getErrors().stream().map(Report::toString).toList()));
        }
    }

}
