package au.gov.digitalhealth.terminology.amtflatfile;

import static org.testng.Assert.assertTrue;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import javax.xml.stream.XMLStreamException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.openmbee.junit.JUnitMarshalling;
import org.openmbee.junit.model.JUnitTestSuite;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

public class Amt2FlatFileTest {

	private static final String INPUT_DIR = "src/test/resources/";
	
	// sample v4 files from June 2026 release; generation succeeds but validation summary includes expected failures
	private static final String SAMPLE_V4_INPUT_ZIP = INPUT_DIR + "sample-v4-release-june-2026.zip";
	
	// missing a required file. will fail
	private static final String INVALID_INPUT_ZIP = INPUT_DIR + "error-v4.zip";
		
	// has all files but missing an optional file. will pass but with validation errors
	private static final String VALIDATION_INPUT_ZIP = INPUT_DIR + "validation-v4.zip";
	
	// just doesn't exist at all
	private static final String NON_EXISTENT_INPUT_ZIP = INPUT_DIR + "nonexistent.zip";

	private static final String EXPECTED_V4_FILE = INPUT_DIR + "expected-v4.csv";
	private static final String EXPECTED_REPLACEMENTS_V4_FILE = INPUT_DIR + "expected-replacements-v4.csv";
	private static final String EXPECTED_VALIDATION_V4_FILE = INPUT_DIR + "expected-validation-v4.txt";
	
	
	private static final String OUTPUT_DIR = "target/test-out/";
	private static final String ACTUAL_V4_OUTPUT_FILE = OUTPUT_DIR + "actual-v4.csv";
	private static final String ACTUAL_REPLACEMENTS_V4_OUTPUT_FILE = OUTPUT_DIR + "actual-replacements-v4.csv";
	private static final String ACTUAL_VALIDATION_V4_XML_FILE = OUTPUT_DIR + "actual-validation-v4.xml";
	private static final String VALIDATION_OUTPUT_FILE = OUTPUT_DIR + "validation.csv";
	private static final String VALIDATION_XML_FILE = OUTPUT_DIR + "validation-errors.xml";
	private static final String REGENERATE_EXPECTED_PROPERTY = "regenerateExpected";

	@AfterClass(alwaysRun = true)
	public void clearOutputFiles() {
		for (String filePath : Arrays.asList(ACTUAL_V4_OUTPUT_FILE, VALIDATION_OUTPUT_FILE, VALIDATION_XML_FILE,
			OUTPUT_DIR + "validation-fixture.xml", ACTUAL_REPLACEMENTS_V4_OUTPUT_FILE, ACTUAL_VALIDATION_V4_XML_FILE)) {
			File output = new File(filePath);
			if (output.exists()) {
				output.delete();
			}
		}
	}

	@Test(groups = "validation", priority = 1, description = "Writes a parseable JUnit XML report for the validation fixture")
	public void writesValidationReportForValidationFixtureRun()
			throws MojoExecutionException, MojoFailureException, IOException, XMLStreamException {
		Amt2FlatFile amt2FlatFile = createGenerator(VALIDATION_INPUT_ZIP, VALIDATION_OUTPUT_FILE);
		amt2FlatFile.setJunitFilePath(VALIDATION_XML_FILE);
		amt2FlatFile.execute();

		File junitXml = new File(VALIDATION_XML_FILE);
		assertTrue(junitXml.exists(), "Validation JUnit XML should be generated");

		JUnitTestSuite suite;
		try (FileInputStream inputStream = new FileInputStream(junitXml)) {
			suite = JUnitMarshalling.unmarshalTestSuite(inputStream);
		}
		Assert.assertNotNull(suite, "Generated JUnit XML should be parseable");
		Assert.assertNotNull(suite.getTestCases(), "Validation fixture report should contain a test case collection");
	}

	@Test(groups = "input-validation", priority = 1, description = "Throws when the input zip path does not exist", expectedExceptions = IllegalArgumentException.class)
	public void throwsWhenInputZipDoesNotExist() throws MojoExecutionException, MojoFailureException, IOException {
		Amt2FlatFile amt2FlatFile = createGenerator(NON_EXISTENT_INPUT_ZIP, VALIDATION_OUTPUT_FILE);
		amt2FlatFile.execute();
	}

	@Test(groups = "input-validation", priority = 1, description = "Throws when the input zip is missing mandatory RF2 snapshot files", expectedExceptions = RuntimeException.class)
	public void throwsWhenInputZipIsMissingRequiredRf2Files()
			throws MojoExecutionException, MojoFailureException, IOException {
		Amt2FlatFile amt2FlatFile = createGenerator(INVALID_INPUT_ZIP, VALIDATION_OUTPUT_FILE);
		amt2FlatFile.execute();
	}

	@Test(groups = "output", priority = 2, description = "Sample-v4 generated flat file matches expected-v4.csv")
	public void generatesExpectedFlatFileForSampleV4()
			throws MojoExecutionException, MojoFailureException, IOException, XMLStreamException {
		Amt2FlatFile amt2FlatFile = createGenerator(SAMPLE_V4_INPUT_ZIP, ACTUAL_V4_OUTPUT_FILE);
		amt2FlatFile.setReplacementsFilePath(ACTUAL_REPLACEMENTS_V4_OUTPUT_FILE);
		amt2FlatFile.setJunitFilePath(ACTUAL_VALIDATION_V4_XML_FILE);
		amt2FlatFile.execute();

		Assert.assertTrue(new File(ACTUAL_REPLACEMENTS_V4_OUTPUT_FILE).exists(), "Replacements CSV should be generated");
		Assert.assertTrue(new File(ACTUAL_VALIDATION_V4_XML_FILE).exists(), "Validation XML should be generated");

		copyActualToExpectedIfRequested(ACTUAL_V4_OUTPUT_FILE, EXPECTED_V4_FILE);
		copyActualToExpectedIfRequested(ACTUAL_REPLACEMENTS_V4_OUTPUT_FILE, EXPECTED_REPLACEMENTS_V4_FILE);
		writeExpectedValidationSummaryIfRequested(ACTUAL_VALIDATION_V4_XML_FILE, EXPECTED_VALIDATION_V4_FILE);

		assertCsvDataEquivalent(EXPECTED_V4_FILE, ACTUAL_V4_OUTPUT_FILE);
		assertCsvDataEquivalent(EXPECTED_REPLACEMENTS_V4_FILE, ACTUAL_REPLACEMENTS_V4_OUTPUT_FILE);
		assertValidationSummaryEquivalent(EXPECTED_VALIDATION_V4_FILE, ACTUAL_VALIDATION_V4_XML_FILE);
	}

	private void assertValidationSummaryEquivalent(String expectedSummaryPath, String actualValidationXmlPath)
			throws IOException, XMLStreamException {
		Path expectedPath = Paths.get(expectedSummaryPath);
		Assert.assertTrue(Files.exists(expectedPath),
			"Expected validation summary is missing: " + expectedSummaryPath
				+ ". Run tests with -D" + REGENERATE_EXPECTED_PROPERTY + "=true to generate it.");

		List<String> expectedLines = Files.readAllLines(expectedPath, StandardCharsets.UTF_8);
		List<String> actualLines = buildValidationSummary(actualValidationXmlPath);
		Assert.assertEquals(actualLines, expectedLines, "Validation summary should match expected v4 output");
	}

	private List<String> buildValidationSummary(String validationXmlPath) throws IOException, XMLStreamException {
		JUnitTestSuite suite;
		try (FileInputStream inputStream = new FileInputStream(new File(validationXmlPath))) {
			suite = JUnitMarshalling.unmarshalTestSuite(inputStream);
		}
		List<String> lines = new ArrayList<>();
		int tests = suite.getTestCases() == null ? 0 : suite.getTestCases().size();
		int failures = suite.getTestCases() == null ? 0
				: suite.getTestCases().stream().mapToInt(t -> t.getFailures() == null ? 0 : t.getFailures().size()).sum();

		lines.add("tests=" + tests);
		lines.add("failures=" + failures);

		if (suite.getTestCases() != null) {
			suite.getTestCases().stream()
				.sorted(Comparator.comparing(t -> t.getName() == null ? "" : t.getName()))
				.forEach(testCase -> lines.add((testCase.getName() == null ? "" : testCase.getName()) + "="
					+ (testCase.getFailures() == null ? 0 : testCase.getFailures().size())));
		}

		return lines;
	}

	private void writeExpectedValidationSummaryIfRequested(String actualValidationXmlPath, String expectedSummaryPath)
			throws IOException, XMLStreamException {
		if (!shouldRegenerateExpected()) {
			return;
		}

		Path expectedPath = Paths.get(expectedSummaryPath);
		if (expectedPath.getParent() != null) {
			Files.createDirectories(expectedPath.getParent());
		}
		Files.write(expectedPath, buildValidationSummary(actualValidationXmlPath), StandardCharsets.UTF_8);
	}

	private void copyActualToExpectedIfRequested(String actualPath, String expectedPath) throws IOException {
		if (!shouldRegenerateExpected()) {
			return;
		}

		Path expected = Paths.get(expectedPath);
		if (expected.getParent() != null) {
			Files.createDirectories(expected.getParent());
		}
		Files.copy(Paths.get(actualPath), expected, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
	}

	private boolean shouldRegenerateExpected() {
		return Boolean.parseBoolean(System.getProperty(REGENERATE_EXPECTED_PROPERTY, "false"));
	}

	private void assertCsvDataEquivalent(String expectedPath, String actualPath) throws IOException {
		CsvDataSummary expectedSummary = summarizeCsv(expectedPath, "Expected CSV should not be empty");
		CsvDataSummary actualSummary = summarizeCsv(actualPath, "Actual CSV should not be empty");

		Assert.assertEquals(actualSummary.header, expectedSummary.header, "CSV header should match");
		Assert.assertEquals(actualSummary.rows, expectedSummary.rows,
				"CSV data rows should match regardless of row order");
	}

	private CsvDataSummary summarizeCsv(String csvPath, String emptyFileMessage) throws IOException {
		try (Stream<String> lines = Files.lines(Paths.get(csvPath), StandardCharsets.UTF_8)) {
			Iterator<String> iterator = lines.iterator();
			Assert.assertTrue(iterator.hasNext(), emptyFileMessage);
			String header = iterator.next();
			Map<String, Long> rows = StreamSupport
					.stream(Spliterators.spliteratorUnknownSize(iterator, Spliterator.ORDERED), false)
					.collect(Collectors.groupingBy(line -> line, Collectors.counting()));
			return new CsvDataSummary(header, rows);
		}
	}

	private static class CsvDataSummary {
		private final String header;
		private final Map<String, Long> rows;

		private CsvDataSummary(String header, Map<String, Long> rows) {
			this.header = header;
			this.rows = rows;
		}
	}

	private Amt2FlatFile createGenerator(String inputZipPath, String outputFilePath) {
		Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
		amt2FlatFile.setInputZipFilePath(inputZipPath);
		amt2FlatFile.setOutputFilePath(outputFilePath);
		return amt2FlatFile;
	}
}
