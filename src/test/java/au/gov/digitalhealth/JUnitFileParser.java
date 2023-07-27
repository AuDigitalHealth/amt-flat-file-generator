package au.gov.digitalhealth;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;
import au.gov.digitalhealth.terminology.amtflatfile.Junit.JUnitFailure;
import au.gov.digitalhealth.terminology.amtflatfile.Junit.JUnitTestCase;
import org.w3c.dom.Node;
import org.w3c.dom.Element;

public class JUnitFileParser {

  public static List<JUnitTestCase> parse(File junitXmlFile) throws IOException {

    DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
    DocumentBuilder dBuilder;
    Document doc = null;
    try {
        dBuilder = dbFactory.newDocumentBuilder();
        doc = dBuilder.parse(junitXmlFile);
        doc.getDocumentElement().normalize(); 
    } catch (ParserConfigurationException | SAXException e) {
        throw new IOException("Failed to parse JUnit XML", e);
    }
    //Get the root element
    NodeList nList = doc.getElementsByTagName("testcase");

    List<JUnitTestCase> testCases = new ArrayList<>();

    for(int i=0; i<nList.getLength(); i++) {
      Node node = nList.item(i);
      if (node.getNodeType() == Node.ELEMENT_NODE) {   
        Element element = (Element) node;
        String testCaseName = element.getAttribute("name");
        String testClassName = element.getAttribute("classname");
        List<JUnitFailure> failures = new ArrayList<>();
        NodeList failureNodes = element.getElementsByTagName("failure");
        for (int j=0; j<failureNodes.getLength(); j++) {
            Element failureElement = (Element) failureNodes.item(j);
            String failureMessage = failureElement.getAttribute("message");
            String failureType = failureElement.getAttribute("type");
            String failureValue = failureElement.getTextContent();
            JUnitFailure failure = new JUnitFailure().setMessage(failureMessage).setType(failureType).setValue(failureValue);
            failures.add(failure);
        }
        testCases.add(new JUnitTestCase()
            .setFailures(failures)
            .setName(testCaseName)
            .setClassName(testClassName));
      }
    }
    return testCases;
  }
  
}
