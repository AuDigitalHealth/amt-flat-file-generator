package au.gov.digitalhealth.terminology.amtflatfile;

import org.testng.annotations.Test;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.AfterMethod;
import org.testng.annotations.BeforeClass;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.bind.JAXBException;
import javax.xml.stream.XMLStreamException;

import org.apache.commons.io.FileUtils;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.openmbee.junit.JUnitMarshalling;
import org.openmbee.junit.model.JUnitFailure;
import org.openmbee.junit.model.JUnitTestSuite;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeTest;
import org.testng.annotations.AfterTest;
import org.testng.annotations.BeforeSuite;
import org.testng.annotations.AfterSuite;

public class Amt2FlatFileTest {
	
	private String testResDirectory = "src/test/resources/";
	private String inFile = testResDirectory + "NCTS_SCT_RF2_DISTRIBUTION_32506021000036107-20180430-SNAPSHOT.zip";
	private String outFile = "target/test-out/out.csv";
	private String expectedFile = testResDirectory + "expected.csv";
	
	//Clean up files between tests
	@AfterMethod()
	public void clearOutputFile() {
		File of = new File(outFile);
		if(of.exists()) of.delete();
		//File val = new File("target/ValidationErrors.xml");
		//if(val.exists()) val.delete();
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
	public void JUnitContainsCorrectErrors() throws MojoExecutionException, MojoFailureException, IOException, JAXBException, XMLStreamException {
		Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
		amt2FlatFile.setInputZipFilePath("target/test-classes/rf2-fails-flat-file-generation-1.0.zip");
		amt2FlatFile.setOutputFilePath(outFile);
		amt2FlatFile.execute();
		File validXml = new File("target/ValidationErrors.xml");
		JUnitTestSuite info = JUnitMarshalling.unmarshalTestSuite(new FileInputStream(validXml));
		List<String> failures = info.getTestCases().stream().flatMap(aCase -> aCase.getFailures().stream().map(fail -> fail.getValue())).collect(Collectors.toList());
		Assert.assertTrue(failures.stream().anyMatch(fail -> fail.contains("1212261000168108")));
		Assert.assertTrue(failures.stream().anyMatch(fail -> fail.contains("1209811000168100")));
		Assert.assertTrue(failures.size() == 8);
	}
	
	
	@Test(groups="files", priority = 1, description = "An exception is thrown when the provided input zip file doesn't exist", expectedExceptions=FileSystemNotFoundException.class)
	public void fileDoesNotExistThrowException() throws MojoExecutionException, MojoFailureException, IOException {

		Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
		amt2FlatFile.setInputZipFilePath(testResDirectory + "nonExistent.zip");
		amt2FlatFile.setOutputFilePath(outFile);
		amt2FlatFile.execute();


	}
	
	@Test(groups="files", priority = 1, description = "An exception is thrown when the provided input zip file is missing critical files", expectedExceptions=NullPointerException.class)
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
		amt2FlatFile.execute();
		
		BufferedReader outFileReader = Files.newBufferedReader(
				Paths.get(outFile), Charset.forName("UTF-8")
				);
		
		BufferedReader expectedFileReader = Files.newBufferedReader(
				Paths.get(expectedFile), Charset.forName("UTF-8")
				);
		
		
		//Hash each of the lines out of the output file, and expected test file
		String outFileLine = new String();
		String expectedFileLine = new String();
		ArrayList<Integer> outHashes = new ArrayList<Integer>();
		ArrayList<Integer> expectedHashes = new ArrayList<Integer>();
		
		while((outFileLine = outFileReader.readLine()) != null && 
				(expectedFileLine = expectedFileReader.readLine()) != null){
			outHashes.add(outFileLine.hashCode());
			expectedHashes.add(expectedFileLine.hashCode());
		}
		
		
		
		//Deep copy outhashes to save the original permutation and identify problem line.
		ArrayList<Integer> originalPerm = new ArrayList<Integer>();
		originalPerm.addAll(outHashes);
		
		//Sort the hashes
		Collections.sort(outHashes);
		Collections.sort(expectedHashes);
		
		String errorLine = new String();
		
		
		//Compare the hash lists until a mismatch is found
		for(int ii = 0; ii < outHashes.size();ii++)
		{
			if(outHashes.get(ii).intValue() != expectedHashes.get(ii).intValue()) {
				//Return problem line from outfile
				int errorLineNumber = originalPerm.indexOf(outHashes.get(ii));
				outFileReader = Files.newBufferedReader(
						Paths.get(outFile), Charset.forName("UTF-8")
						);
				for(int x = 0; x < errorLineNumber; x++)
					outFileReader.readLine();
				errorLine = outFileReader.readLine();
				break;
			}
		}
		
		assertEquals(errorLine, "");
	}
  


}
