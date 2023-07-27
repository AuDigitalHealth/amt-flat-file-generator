package au.gov.digitalhealth.terminology.amtflatfile;

import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

//import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
//import org.openmbee.junit.JUnitMarshalling;
//import org.openmbee.junit.model.JUnitTestSuite;
import org.testng.Assert;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.Test;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;
import org.testng.xml.internal.Parser;
import au.gov.digitalhealth.JUnitFileParser;
import au.gov.digitalhealth.terminology.amtflatfile.Junit.JUnitFailure;
import au.gov.digitalhealth.terminology.amtflatfile.Junit.JUnitTestCase;
import au.gov.digitalhealth.terminology.amtflatfile.Junit.JUnitTestSuite;

public class Amt2FlatFileTest {

	private String testResDirectory = "src/test/resources/";
	private String inFile = testResDirectory + "NCTS_SCT_RF2_DISTRIBUTION_32506021000036107-20180430-SNAPSHOT.zip";
    private String outFile = "target/test-out/out.csv";
    private String replacementFile = "target/test-out/replacement.csv";
    private String expectedFile = testResDirectory + "expected.csv";
    private String expectedReplacementFile = testResDirectory + "expectedReplacement.csv";
	
	//Clean up files between tests
	@AfterMethod()
	public void clearOutputFile() {
        File of = new File(outFile);
        if (of.exists()) {
            of.delete();
        }
	}
	
	@Test(groups="files", priority = 1, description = "Tests that the JUnit file gets generated")
	public void JUnitGenerated() throws MojoExecutionException, MojoFailureException, IOException {
        Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
        amt2FlatFile.setInputZipFilePath("target/test-classes/rf2-fails-flat-file-generation-1.0.zip");
        amt2FlatFile.setOutputFilePath(outFile);
		amt2FlatFile.execute();
		File validXml = new File("target/ValidationErrors.xml");
		Assert.assertTrue(validXml.exists());
	}

	@Test(groups="files", priority = 1, description = "Tests that the the correct errors are reported by the JUnit xml")
	public void JUnitContainsCorrectErrors() throws MojoExecutionException, MojoFailureException, IOException, XMLStreamException {
        Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
        amt2FlatFile.setInputZipFilePath("target/test-classes/rf2-fails-flat-file-generation-1.0.zip");
        amt2FlatFile.setOutputFilePath(outFile);
        amt2FlatFile.setJunitFilePath("target/JUnitContainsCorrectErrors.xml");
        amt2FlatFile.execute();
        List<JUnitTestCase> testCases = JUnitFileParser.parse(new File("target/JUnitContainsCorrectErrors.xml"));
        List<String> fails = new ArrayList<>();
        testCases.forEach(testCase -> {
            List<JUnitFailure> failures = testCase.getFailures();
            fails.addAll(
                failures.stream()
                    .map(fail -> fail.getValue())
                    .collect(Collectors.toList()));
        });
        // List<String> failures = testCases.stream().flatMap(aCase -> aCase.getFailures().stream().map(fail -> fail.getValue())).collect(Collectors.toList());
        Assert.assertTrue(fails.stream().anyMatch(fail -> fail.contains("1212261000168108")));
        Assert.assertTrue(fails.stream().anyMatch(fail -> fail.contains("1209811000168100")));
        Assert.assertEquals(fails.size(), 10);
	}
	
	
    @Test(groups = "files", priority = 1, description = "An exception is thrown when the provided input zip file doesn't exist", expectedExceptions = IllegalArgumentException.class)
	public void fileDoesNotExistThrowException() throws MojoExecutionException, MojoFailureException, IOException {

		Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
		amt2FlatFile.setInputZipFilePath(testResDirectory + "nonExistent.zip");
		amt2FlatFile.setOutputFilePath(outFile);
		amt2FlatFile.execute();

	}
	
	@Test(groups="files", priority = 1, description = "An exception is thrown when the provided input zip file is missing critical files", expectedExceptions=RuntimeException.class)
	public void fileMissingFromReleaseExceptionThrown() throws MojoExecutionException, MojoFailureException, IOException {

		Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
		amt2FlatFile.setInputZipFilePath(testResDirectory + "incomplete.zip");
		amt2FlatFile.setOutputFilePath(outFile);
		amt2FlatFile.execute();
	}

	@Test(groups="parse", priority = 2, description = "The output file should match the expected result. Test line by line, regardless fo order")
	public void outputMatchesExpectedImproved() throws MojoExecutionException, MojoFailureException, IOException, NoSuchAlgorithmException {

        Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
		amt2FlatFile.setInputZipFilePath(inFile);
		amt2FlatFile.setOutputFilePath(outFile);
        amt2FlatFile.setReplacementsFilePath(replacementFile);
		amt2FlatFile.execute();

        List<String> outFileLines = FileUtils.readLines(new File(outFile), "UTF-8");
        List<String> expectedLines = FileUtils.readLines(new File(expectedFile), "UTF-8");
        Collections.sort(outFileLines);
        Collections.sort(expectedLines);
        assertTrue(outFileLines.equals(expectedLines), "AMT flat file content as expected after sorting");

        List<String> replacemenLines = FileUtils.readLines(new File(replacementFile), "UTF-8");
        List<String> expectedReplacementFileLines = FileUtils.readLines(new File(expectedReplacementFile), "UTF-8");
        Collections.sort(replacemenLines);
        Collections.sort(expectedReplacementFileLines);
        assertTrue(replacemenLines.equals(expectedReplacementFileLines), "Replacements file as expected");
	}
}
