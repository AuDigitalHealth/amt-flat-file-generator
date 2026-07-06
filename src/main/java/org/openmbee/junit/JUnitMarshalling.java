package org.openmbee.junit;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilderFactory;

import org.openmbee.junit.model.JUnitFailure;
import org.openmbee.junit.model.JUnitTestCase;
import org.openmbee.junit.model.JUnitTestSuite;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

public final class JUnitMarshalling {

    private JUnitMarshalling() {
    }

    public static JUnitTestSuite unmarshalTestSuite(InputStream inputStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            Document document = factory.newDocumentBuilder().parse(inputStream);

            Element testSuiteEl = findTestSuiteElement(document);
            JUnitTestSuite suite = new JUnitTestSuite();
            suite.setFailures(parseIntAttribute(testSuiteEl, "failures"));
            suite.setTests(parseIntAttribute(testSuiteEl, "tests"));

            List<JUnitTestCase> testCases = new ArrayList<>();
            NodeList testCaseNodes = testSuiteEl.getElementsByTagName("testcase");
            for (int i = 0; i < testCaseNodes.getLength(); i++) {
                Element testCaseEl = (Element) testCaseNodes.item(i);
                JUnitTestCase testCase = new JUnitTestCase().setName(testCaseEl.getAttribute("name"));

                List<JUnitFailure> failures = new ArrayList<>();
                NodeList failureNodes = testCaseEl.getElementsByTagName("failure");
                for (int j = 0; j < failureNodes.getLength(); j++) {
                    Element failureEl = (Element) failureNodes.item(j);
                    JUnitFailure failure = new JUnitFailure();
                    failure.setMessage(failureEl.getAttribute("message"));
                    failure.setType(failureEl.getAttribute("type"));
                    failure.setValue(failureEl.getTextContent());
                    failures.add(failure);
                }

                testCase.setFailures(failures);
                testCases.add(testCase);
            }

            suite.setTestCases(testCases);
            return suite;
        } catch (Exception e) {
            throw new RuntimeException("Could not parse JUnit XML", e);
        }
    }

    private static Element findTestSuiteElement(Document document) {
        Element root = document.getDocumentElement();
        if ("testsuite".equals(root.getTagName())) {
            return root;
        }

        if ("testsuites".equals(root.getTagName())) {
            NodeList suites = root.getElementsByTagName("testsuite");
            if (suites.getLength() > 0) {
                return (Element) suites.item(0);
            }
        }

        throw new IllegalArgumentException("No testsuite element found");
    }

    private static int parseIntAttribute(Element element, String name) {
        String value = element.getAttribute(name);
        if (value == null || value.isEmpty()) {
            return 0;
        }
        return Integer.parseInt(value);
    }
}
