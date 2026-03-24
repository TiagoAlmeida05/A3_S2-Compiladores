package pt.up.fe.comp.test.env.utils;

import org.junit.rules.TestWatcher;
import org.junit.runner.Description;

public class TestInfo extends TestWatcher {

    private Description description;


    @Override
    protected void starting(org.junit.runner.Description description) {
        this.description = description;
    }

    public String getMethodName() {
        return description.getMethodName();
    }

    public Class<?> getTestClass() {
        return description.getTestClass();
    }

}
