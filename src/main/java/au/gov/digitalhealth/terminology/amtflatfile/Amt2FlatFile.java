package au.gov.digitalhealth.terminology.amtflatfile;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.openmbee.junit.model.JUnitFailure;

/**
 * This is both a Java CLI class compiled into a runnable JAR, and a Maven Mojo to transform a ZIP file of SNOMED CT-AU
 * release files into an AMT flat file. Note it only really needs the snapshot files, and expects file names to match
 * the release filename convention.
 */
@Mojo(name = "amt-to-flat-file")
public class Amt2FlatFile extends AbstractMojo {

	private static final String INPUT_FILE_OPTION = "i";

	private static final String OUTPUT_FILE_OPTION = "o";

	private static final String EXIT_ON_ERROR_OPTION = "e";

	private static final String JUNIT_FILE_PATH = "j";

	private static final Logger logger = Logger.getLogger(Amt2FlatFile.class.getCanonicalName());
	
	private JUnitTestSuite_EXT testSuite;

	@Parameter(property = "inputZipFilePath", required = true)
	private String inputZipFilePath;

	@Parameter(property = "outputFilePath", required = true)
	private String outputFilePath;

	@Parameter(property = "junitFilePath", required = false, defaultValue = "target/ValidationErrors.xml")
	private String junitFilePath;

	@Parameter(property = "exitOnError", required = false, defaultValue = "false")
	private static boolean exitOnError;

	private AmtCache conceptCache;

	public static void main(String args[]) throws IOException, URISyntaxException {
		long start = System.currentTimeMillis();
		Options options = new Options();

		options.addOption(Option.builder(INPUT_FILE_OPTION)
				.longOpt("inputFile")
				.argName("AMT_ZIP_FILE_PATH")
				.hasArg()
				.desc("Input AMT release ZIP file")
				.required(true)
				.build());
		options.addOption(Option.builder(OUTPUT_FILE_OPTION)
				.longOpt("outputFile")
				.argName("OUTPUT_FILE")
				.hasArg()
				.desc("Output file path to write out the flat file")
				.required()
				.build());
		options.addOption(Option.builder(EXIT_ON_ERROR_OPTION)
				.longOpt("exit-on-error")
				.argName("EXIT_ON_ERROR")
				.desc("Flag dictating whether the program will exit on an error or keep processing")
				.build());
		options.addOption(Option.builder(JUNIT_FILE_PATH)
				.longOpt("junitFile")
				.argName("JUNIT_FILE")
				.desc("Output file path to write out the junit result file")
				.build());

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine line = parser.parse(options, args);

			Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
			amt2FlatFile.setInputZipFilePath(line.getOptionValue(INPUT_FILE_OPTION));
			amt2FlatFile.setOutputFilePath(line.getOptionValue(OUTPUT_FILE_OPTION));
			amt2FlatFile.setExitOnError(line.hasOption(EXIT_ON_ERROR_OPTION));
			amt2FlatFile.setJunitFilePath(line.getOptionValue(JUNIT_FILE_PATH));
			amt2FlatFile.execute();

		} catch (ParseException exp) {
			System.err.println("Parsing failed.  Reason: " + exp.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("Amt2FlatFile", options);
		} catch (MojoExecutionException | MojoFailureException e) {
			System.err.println("Failed due to execution exception");
			throw new RuntimeException(e);
		}
		logger.info("Done in " + (System.currentTimeMillis() - start) + " milliseconds");
	}

	@Override
	public void execute() throws MojoExecutionException, MojoFailureException {
		
		//initialise test suite	
		this.testSuite = new JUnitTestSuite_EXT();
		FileSystem zipFileSystem = null;
		try {
			zipFileSystem = FileSystems.newFileSystem(URI.create(
							"jar:file:" + FileSystems.getDefault().getPath(inputZipFilePath).toAbsolutePath().toString()),
					new HashMap<>());
			conceptCache = new AmtCache(zipFileSystem, this.testSuite, exitOnError);
			writeFlatFile(FileSystems.getDefault().getPath(outputFilePath));
			if (junitFilePath == null || junitFilePath.trim().isEmpty()) {
				junitFilePath = "target/ValidationErrors.xml";
			}
			BufferedWriter outputJunitXml = new BufferedWriter(new FileWriter(junitFilePath));
			testSuite.writeToFile(outputJunitXml);
			logger.info("Output junit results to: " + new File(junitFilePath).getAbsolutePath());
		} catch (IOException e) {
			throw new MojoExecutionException("Failed due to IO error executing transformation", e);
		} finally {
			if(zipFileSystem != null)
				try {
					zipFileSystem.close();
				} catch (IOException e) {
					throw new RuntimeException(e.getMessage());
				}
		}
	}

	private void writeFlatFile(Path path) throws IOException   {
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectory(path.getParent());
        }
        try (
                BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            writeCsvHeader(writer);

            for (Concept ctpp : conceptCache.getCtpps().values()) {
                Concept tpp = getParent(AmtConcept.TPP, AmtConcept.CTPP, ctpp);
                Concept tppTp = null;
                if (tpp.getTps().size() == 1) {
                    tppTp = tpp.getTps().iterator().next();
                } else {
                	String message = "TPUU " + tpp + " has too many TPs " + tpp.getTps();
        			JUnitFailure fail = new JUnitFailure().setValue(message).setMessage("TPUU error").setType("ERROR");
        			JUnitTestCase_EXT concept = new JUnitTestCase_EXT().setName("TPUU has too many TPs (" + tpp + ")").addFailure(fail);
        			testSuite.addTestCase(concept);
                	if(exitOnError)
                		throw new RuntimeException(message);
                	continue;
                }
                
                Concept mpp = getParent(AmtConcept.MPP, AmtConcept.TPP, tpp);
                Set<Concept> tpuus = tpp.getUnits();

                Set<Concept> addedMpuus = new HashSet<>();
                for (Concept tpuu : tpuus) {
                    Concept tpuuTp = getParent(AmtConcept.TP, AmtConcept.TPUU, tpuu);
                    Concept mpuu = getParent(AmtConcept.MPUU, AmtConcept.TPUU, tpuu);
                    addedMpuus.add(mpuu);

                    Set<Concept> mps = getParents(AmtConcept.MP, AmtConcept.MPUU, mpuu);

                    Set<String> artgids = ctpp.getArtgIds();
                    if (artgids == null || artgids.size() == 0) {
                        artgids = Collections.singleton("");
                    }

                    artgids = artgids.stream().map(String::trim).collect(Collectors.toSet());;

                    if(tpuuTp == null || mpuu == null) {
                    	continue;
                    }
                    
                    for (Concept mp : mps) {
                        for (String artgid : artgids) {
                            writer.write(
                                String.join(",",
                                    ctpp.getId() + "", "\"" + ctpp.getPreferredTerm() + "\"",
                                    artgid,
                                    tpp.getId() + "", "\"" + tpp.getPreferredTerm() + "\"",
                                    tpuu.getId() + "", "\"" + tpuu.getPreferredTerm() + "\"",
                                    tppTp.getId() + "", "\"" + tppTp.getPreferredTerm() + "\"",
                                    tpuuTp.getId() + "", "\"" + tpuuTp.getPreferredTerm() + "\"",
                                    mpp.getId() + "", "\"" + mpp.getPreferredTerm() + "\"",
                                    mpuu.getId() + "", "\"" + mpuu.getPreferredTerm() + "\"",
                                    mp.getId() + "", "\"" + mp.getPreferredTerm() + "\""));
                            writer.newLine();
                        }
                    }
                }

                if (!mpp.getUnits().containsAll(addedMpuus) || !addedMpuus.containsAll(mpp.getUnits())) {
                    
                	String message = "Mismatch between MPUUs from MPP "
                            + mpp.getUnits().stream().map(c -> c.getId()).collect(Collectors.toList())
                            + " and MPUUs added from TPUUs "
                            + addedMpuus.stream().map(c -> c.getId()).collect(Collectors.toList())
                            + " for MPP " + mpp;
                	logger.warning(message);

                	JUnitFailure fail = new JUnitFailure();
                	fail.setValue(message).setMessage("Mismatch").setType("ERROR");
                	JUnitTestCase_EXT testCase = new JUnitTestCase_EXT();
                	testCase.setName("MPP mismatch (" + mpp.getId() + ")");
                	testCase.addFailure(fail);
                	testSuite.addTestCase(testCase);
                }
            }
        }
		
		
	}


	private void writeCsvHeader(BufferedWriter writer) throws IOException {
		writer.write(
				String.join(",", "CTPP SCTID", "CTPP PT", "ARTG_ID", "TPP SCTID", "TPP PT", "TPUU SCTID", "TPUU PT",
						"TPP TP SCTID", "TPP TP PT", "TPUU TP SCTID", "TPUU TP PT", "MPP SCTID", "MPP PT", "MPUU SCTID",
						"MPUU PT", "MP SCTID", "MP PT"));
		writer.newLine();
	}

	private Concept getParent(AmtConcept parentType, AmtConcept current, Concept concept) {
		Set<Concept> parents = getParents(parentType, current, concept).stream()
				.collect(Collectors.toSet());
		
		if (parents.size() != 1) {
			String message = "Expected 1 parent of type " + parentType + " for concept " + concept + " but got " + parents;
			
			JUnitFailure fail = new JUnitFailure().setMessage("multiple parents").setValue(message).setType("ERROR");
			JUnitTestCase_EXT conceptCase = new JUnitTestCase_EXT().setName("Multiple parents (" + concept.getId() + ")").addFailure(fail);
			testSuite.addTestCase(conceptCase);
			
			if(exitOnError)
				throw new RuntimeException(message);
			
			return null;
		}
		return parents.iterator().next();
	}

	private Set<Concept> getParents(AmtConcept parentType, AmtConcept current, Concept concept) {
		return getParents(parentType, current, Collections.singleton(concept));
	}

	private Set<Concept> getParents(AmtConcept parentType, AmtConcept current, Set<Concept> concepts) {
		Set<Concept> leafParents = new HashSet<>();

		leafParents.addAll(concepts.stream()
				.flatMap(c -> c.getAncestors(parentType).stream())
				.filter(p -> !AmtConcept.isEnumValue(p.getId()))
				.filter(p -> p.hasAtLeastOneMatchingAncestor(parentType))
				.filter(p -> !p.hasAtLeastOneMatchingAncestor(current))
				.collect(Collectors.toSet()));

		Set<Concept> redunantAncestors =
				leafParents.stream().flatMap(p -> p.getAncestors(parentType).stream()).collect(Collectors.toSet());

		leafParents.removeAll(redunantAncestors);

		return leafParents;
	}

	public String getInputZipFilePath() {
		return inputZipFilePath;
	}

	public void setInputZipFilePath(String inputZipFilePath) {
		this.inputZipFilePath = inputZipFilePath;
	}

	public String getOutputFilePath() {
		return outputFilePath;
	}

	public void setOutputFilePath(String outputFilePath) {
		this.outputFilePath = outputFilePath;
	}

	public void setExitOnError(boolean exitOnError_param) {
		exitOnError = exitOnError_param;
	}

	private void setJunitFilePath(String path) {
		junitFilePath = path;
	}
}
