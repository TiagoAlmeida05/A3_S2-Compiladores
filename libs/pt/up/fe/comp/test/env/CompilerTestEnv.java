// language: java
package pt.up.fe.comp.test.env;

import org.junit.Before;
import org.junit.Rule;
import pt.up.fe.comp.test.env.utils.TestInfo;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Base test environment that provides a preconfigured {@link CompilerAssertion} instance for
 * running compilation stage and apply predefined assertions and logging.
 * Sets the current test name before each test run and uses the {@link CompilerAssertion} instance
 * for convenience delegation methods for assertion and logging.
 */
public abstract class CompilerTestEnv extends CompilerAssertion {
//    protected final CompilerAssertion assertion = new CompilerAssertion(Collections.emptyMap());

    public CompilerTestEnv() {
        this(Collections.emptyMap());
    }

    public CompilerTestEnv(Map<String, String> config) {
        super(config);
    }


    /**
     * JUnit hook executed before each test. Sets a human-readable test name in the underlying
     * {@link CompilerAssertion} and prints a short message to standard output.
     */
    @Before
    public void before() {
        System.out.println("--- Running test: " + testInfo.getMethodName() + " ---");
    }

}