package org.openmbee.junit.model;

import java.util.List;

public class JUnitTestSuite {

    private int failures;
    private int tests;
    private List<JUnitTestCase> testCases;

    public int getFailures() {
        return failures;
    }

    public void setFailures(int failures) {
        this.failures = failures;
    }

    public int getTests() {
        return tests;
    }

    public void setTests(int tests) {
        this.tests = tests;
    }

    public List<JUnitTestCase> getTestCases() {
        return testCases;
    }

    public void setTestCases(List<JUnitTestCase> testCases) {
        this.testCases = testCases;
    }
}
