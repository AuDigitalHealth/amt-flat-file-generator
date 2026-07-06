package org.openmbee.junit.model;

import java.util.List;

public class JUnitTestCase {

    private String name;
    private List<JUnitFailure> failures;

    public String getName() {
        return name;
    }

    public JUnitTestCase setName(String name) {
        this.name = name;
        return this;
    }

    public List<JUnitFailure> getFailures() {
        return failures;
    }

    public void setFailures(List<JUnitFailure> failures) {
        this.failures = failures;
    }
}
