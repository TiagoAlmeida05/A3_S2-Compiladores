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

package pt.up.fe.comp.jmm.ast;

import pt.up.fe.specs.util.SpecsCheck;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

public abstract class AJmmNode implements JmmNode {

    private final Map<String, Object> attributes = new HashMap<>();

    protected Map<String, Object> getAttributesMap() {
        return attributes;
    }


    @Override
    public Collection<String> getAttributes() {
        return attributes.keySet();
    }

    @Override
    public Object putObject(String attribute, Object value) {
        return attributes.put(attribute, value);
    }

    @Override
    public Object getObject(String attribute) {
        var value = this.attributes.get(attribute);

        SpecsCheck.checkNotNull(value, () -> "Node " + getKind() + " does not contain attribute '" + attribute + "'");

        return value;
    }

}
