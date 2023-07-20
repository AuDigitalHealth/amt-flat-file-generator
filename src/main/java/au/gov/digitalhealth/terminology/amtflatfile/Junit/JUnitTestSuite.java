package au.gov.digitalhealth.terminology.amtflatfile.Junit;

import java.io.BufferedWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.TransformerFactoryConfigurationError;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import jakarta.xml.bind.annotation.XmlAttribute;
import jakarta.xml.bind.annotation.XmlElement;

@Getter
@Setter
@Accessors(chain = true)
 public class JUnitTestSuite {

    @XmlAttribute
    private int errors;

    @XmlAttribute
    private int failures;

    @XmlAttribute(required = true)
    private int tests;

    @XmlAttribute(required = true)
    private String name;

    @XmlElement(name = "testcase")
    private List<JUnitTestCase> testCases;

	public void writeToFile(BufferedWriter stream) throws IOException {

		Document doc = null;
		try {
			doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();
		} catch (ParserConfigurationException e1) {
			throw new RuntimeException("Error writing out xml: " + e1.getMessage());
		}

		Element testSuitesEl = doc.createElement("testsuites");
		Element testSuiteEl = doc.createElement("testsuite");
		testSuiteEl.setAttribute("failures", "0");
		testSuiteEl.setAttribute("tests", "0");
		testSuiteEl.setAttribute("errors", "0");
		testSuiteEl.setAttribute("name", "validation.errors");

		if(this.getTestCases() != null) {
			this.setFailures((int) this.getTestCases().stream().flatMap(x -> x.getFailures().stream()).count());
			this.setTests(this.getTestCases() != null ? this.getTestCases().size() : 0);
			testSuiteEl.setAttribute("failures", Integer.toString(this.getFailures()));
			testSuiteEl.setAttribute("tests", Integer.toString(this.getFailures()));
			testSuiteEl.setAttribute("errors", "0");
			testSuiteEl.setAttribute("name", "validation.errors");

			for (JUnitTestCase testCase : this.getTestCases()) {
				Element testCaseEl = doc.createElement("testcase");
				testCaseEl.setAttribute("name", testCase.getName());
				testCaseEl.setAttribute("classname", "flatfile." + testCase.getClassName());

				for (JUnitFailure failure : testCase.getFailures()) {
					Element failEl = doc.createElement("failure");
					failEl.setAttribute("message", failure.getMessage());
					failEl.setAttribute("type", failure.getType());
					failEl.setTextContent(failure.getValue());
					testCaseEl.appendChild(failEl);
				}
				testSuiteEl.appendChild(testCaseEl);
			}
		}

		testSuitesEl.appendChild(testSuiteEl);
		doc.appendChild(testSuiteEl);

		Transformer transformer;
		try {
			transformer = TransformerFactory.newInstance().newTransformer();
	        transformer.setOutputProperty(OutputKeys.INDENT, "yes"); 
	        DOMSource source = new DOMSource(doc);
	        StreamResult writeOut = new StreamResult(stream);
	        transformer.transform(source, writeOut);
		} catch (TransformerFactoryConfigurationError | TransformerException e) {
			throw new RuntimeException("Error writing out xml: " + e.getMessage());
		}
	}


	/*
	 * Adds a new test case. If a test case with the same name already exists within the
	 * testSuite, the add all the test cases failures to the existing test case
	 */
	public void addTestCase(JUnitTestCase testCase){
		if(this.getTestCases() == null)
			this.setTestCases(new ArrayList<JUnitTestCase>());

		List<JUnitTestCase> existingTestCase = this.getTestCases()
				.stream()
				.filter(aCase -> aCase.getName().equals(testCase.getName()))
				.collect(Collectors.toList());

		if(existingTestCase.size() == 1) {
			for(JUnitFailure fail : testCase.getFailures())
				((JUnitTestCase) existingTestCase.get(0)).addFailure(fail);
		}else if(existingTestCase.size() == 0){
			this.getTestCases().add(testCase);
		}

	}

    public void addTestCase(String message, String detail, String className, String testCaseName, String failType) {
        JUnitFailure fail = new JUnitFailure();
        fail.setMessage(message);
        fail.setValue(detail);
        fail.setType(failType);
        JUnitTestCase testCase = new JUnitTestCase().setName(testCaseName).setClassName(className);
        testCase.addFailure(fail);
        this.addTestCase(testCase);
    }

}
	