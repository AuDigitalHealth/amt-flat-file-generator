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
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
import org.apache.tika.Tika;
import au.gov.digitalhealth.terminology.amtflatfile.Junit.JUnitTestSuite;

/**
 * This is both a Java CLI class compiled into a runnable JAR, and a Maven Mojo to transform a ZIP file of SNOMED CT-AU
 * release files into an AMT flat file. Note it only really needs the snapshot files, and expects file names to match
 * the release filename convention.
 */
@Mojo(name = "amt-to-flat-file")
public class Amt2FlatFile extends AbstractMojo {

    private static final int MAX_ZIP_FILE_SIZE = 900000000;

    private static final String INPUT_FILE_OPTION = "i";

	private static final String OUTPUT_FILE_OPTION = "o";

	private static final String EXIT_ON_ERROR_OPTION = "e";

    private static final String REPLACEMENT_FILE_PATH = "r";

	private static final String JUNIT_FILE_PATH = "j";

	private static final Logger logger = Logger.getLogger(Amt2FlatFile.class.getCanonicalName());

	private JUnitTestSuite testSuite;

	@Parameter(property = "inputZipFilePath", required = true)
	private String inputZipFilePath;

	@Parameter(property = "outputFilePath", required = true)
	private String outputFilePath;

    @Parameter(property = "replacementsOutputFilePath", required = false)
    private String replacementsOutputFilePath;

	@Parameter(property = "junitFilePath", required = false, defaultValue = "target/ValidationErrors.xml")
	private String junitFilePath;

	@Parameter(property = "exitOnError", required = false, defaultValue = "false")
    private boolean exitOnError;

	private AmtCache conceptCache;

    private Tika tika = new Tika();

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
            .hasArg()
            .desc("Output file path to write out the junit result file")
            .build());
        options.addOption(Option.builder(REPLACEMENT_FILE_PATH)
            .longOpt("replacementsOutputFile")
            .argName("REPLACEMENTS_FILE_PATH")
            .hasArg()
            .desc("Output file path to write out the file listing inactive AMT concepts and their replacement active concepts")
            .build());

		CommandLineParser parser = new DefaultParser();
		try {
			CommandLine line = parser.parse(options, args);

			Amt2FlatFile amt2FlatFile = new Amt2FlatFile();
			amt2FlatFile.setInputZipFilePath(line.getOptionValue(INPUT_FILE_OPTION));
			amt2FlatFile.setOutputFilePath(line.getOptionValue(OUTPUT_FILE_OPTION));
			amt2FlatFile.setExitOnError(line.hasOption(EXIT_ON_ERROR_OPTION));
            amt2FlatFile.setJunitFilePath(line.getOptionValue(JUNIT_FILE_PATH));
            amt2FlatFile.setReplacementsFilePath(line.getOptionValue(REPLACEMENT_FILE_PATH));
			amt2FlatFile.execute();

		} catch (ParseException exp) {
            logger.severe("Parsing failed.  Reason: " + exp.getMessage());
			HelpFormatter formatter = new HelpFormatter();
			formatter.printHelp("Amt2FlatFile", options);
		} catch (MojoExecutionException | MojoFailureException e) {
            logger.severe("Failed due to execution exception");
			throw new RuntimeException(e);
		}
		logger.info("Done in " + (System.currentTimeMillis() - start) + " milliseconds");
	}

    @Override
	public void execute() throws MojoExecutionException, MojoFailureException {
        logger.info("Input file is " + inputZipFilePath);
        logger.info("Output will be written to " + outputFilePath);

        validateInputZipFile(inputZipFilePath);

        validateOutputPath(outputFilePath, "text/csv");
        
        if (replacementsOutputFilePath == null || replacementsOutputFilePath.isEmpty()) {
            logger.info("Replacement file was not requested and will not be written");
        } else {
            validateOutputPath(replacementsOutputFilePath, "text/csv");
            logger.info("Replacement file will be written to " + replacementsOutputFilePath);
        }

        if (junitFilePath == null || junitFilePath.isEmpty()) {
            logger.info("JUnit file was not requested and will not be written");
        } else {
            validateOutputPath(junitFilePath, "application/xml");
            logger.info("JUnit file will be written to " + junitFilePath);
        }

        if (exitOnError) {
            logger.info("AMT flat file generation will be aborted if any errors are detected");
        } else {
            logger.warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            logger.warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            logger.warning(
                "Configured to continue regardless of detected errors. This is useful for testing pre-release AMT content, "
                        + "but is NOT recommended for any other use! Resultant AMT flat file may be unreliable. "
                        + "Consider rerunning with the -e or --exit-on-error flag set!!!");
            logger.warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
            logger.warning("!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");

        }

		//initialise test suite	
		this.testSuite = new JUnitTestSuite();
        try (FileSystem zipFileSystem = FileSystems.newFileSystem(URI.create(
            "jar:file:" + FileSystems.getDefault().getPath(inputZipFilePath).toAbsolutePath().toString()),
                    new HashMap<>());) {

            conceptCache = new AmtCache(zipFileSystem, exitOnError, testSuite);
            writeFlatFile(FileSystems.getDefault().getPath(outputFilePath));
            if (replacementsOutputFilePath != null && !replacementsOutputFilePath.isEmpty()) {
                writeReplacementsFile(FileSystems.getDefault().getPath(replacementsOutputFilePath));
            }
			if (junitFilePath == null || junitFilePath.trim().isEmpty()) {
				junitFilePath = "target/ValidationErrors.xml";
			}
            File junitFile = new File(junitFilePath);
            File parentDir = junitFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
              parentDir.mkdirs();
            }
			BufferedWriter outputJunitXml = new BufferedWriter(new FileWriter(junitFilePath));
			testSuite.writeToFile(outputJunitXml);
			logger.info("Output junit results to: " + new File(junitFilePath).getAbsolutePath());
		} catch (IOException e) {
			throw new MojoExecutionException("Failed due to IO error executing transformation", e);
		}
	}

    private void validateOutputPath(String outputPath, String expectedMimeType) {
        try {
            Path path = Paths.get(outputPath);

            if (Files.isSymbolicLink(path)) {
                throw new SecurityException("The output " + outputPath + " file must not be a symlink for security reasons");
            }

            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                BasicFileAttributes attr = Files.readAttributes(
                    path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

                if (!attr.isRegularFile()) {
                    throw new SecurityException(
                        "The specified output file " + outputPath + " exists, but is not a regular file. Cannot be overwritten.");
                } else if (!tika.detect(path).equals(expectedMimeType)) {
                    throw new SecurityException(
                        "The specified output file " + outputPath + " exists, but is not a " + expectedMimeType
                                + " file as expected, detected type was " + tika.detect(path) + ". Cannot be overwritten");
                }
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not validate output file path " + outputPath, e);
        }
    }

    private void validateInputZipFile(String inputZipFilePath) {
        try {
            Path path = Paths.get(inputZipFilePath);

            if (Files.isSymbolicLink(path)) {
                throw new SecurityException("The input ZIP file must not be a symlink for security reasons");
            } else if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("Specified input ZIP file " + inputZipFilePath + " does not exist");
            }

            BasicFileAttributes attr = Files.readAttributes(
                path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);

            if (!attr.isRegularFile()) {
                throw new SecurityException("The input ZIP file must be a regular file");
            } else if (attr.size() > MAX_ZIP_FILE_SIZE) {
                throw new SecurityException("For security, input ZIP files over 900M are not accepted. "
                        + "This should permit RF2 ALL or SNAPSHOT bundles requiring the required files - file size was "
                        + attr.size());
            } else if (!tika.detect(path).equals("application/zip") && !tika.detect(path).equals("application/java-archive")) {
                throw new SecurityException(
                    "The input ZIP file " + inputZipFilePath + " is not a zip file as expected, detected type was "
                            + tika.detect(path));
            }
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not validate input ZIP file path " + inputZipFilePath, e);
        }
    }

    private void writeFlatFile(Path path) throws IOException {
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectory(path.getParent());
        }
        try (
                BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            writer.write(
                String.join(",", "CTPP SCTID", "CTPP PT", "ARTG_ID", "TPP SCTID", "TPP PT", "TPUU SCTID", "TPUU PT",
                    "TPP TP SCTID", "TPP TP PT", "TPUU TP SCTID", "TPUU TP PT", "MPP SCTID", "MPP PT", "MPUU SCTID",
                    "MPUU PT", "MP SCTID", "MP PT"));
            writer.newLine();

            int count = 1;
            int ctppCount = conceptCache.getCtppsFromRefset().values().size();
            long startTime = System.currentTimeMillis();
            for (Concept ctpp : conceptCache.getCtppsFromRefset().values()) {
                // TPPs
                Concept tpp = getParent(AmtRefset.TPP, ctpp);
                if (tpp == null) {
                    tpp = ctpp;
                }
                Concept tppTp = null;
                if (tpp.getTps().size() == 1) {
                    tppTp = tpp.getTps().iterator().next();
                } else {
                	String message = "TPP " + tpp + " has too many TPs " + tpp.getTps();
                    testSuite.addTestCase("TPP error", message, this.getClass().getName(), "TPP has too many TPs (" + tpp + ")", "ERROR");
                    if (exitOnError) {
                		throw new RuntimeException(message);
                    }
                    logger.severe(message);
                	continue;
                }
                
                Concept mpp = getParent(AmtRefset.MPP, tpp);
                if (mpp == null) {
                    logger.severe("No MPP found for TPP " + tpp);
                }

                // TPUUS
                Set<Concept> tpuus = tpp.getUnits();

                Set<Concept> addedMpuus = new HashSet<>();
                for (Concept tpuu : tpuus) {
                    Concept tpuuTp = conceptCache.isAmtV3() ? getParent(AmtRefset.TP, tpuu) : null;
                    if (tpuuTp == null) {
                        if (tpuu.getTps().size() > 1) {
                            throw new RuntimeException("TPUU " + tpuu + " has too many TPs " + tpuu.getTps());
                        } else if (tpuu.getTps().size() == 1) {
                            tpuuTp = tpuu.getTps().iterator().next();
                        } else {
                            logger.severe("TPUU " + tpuu + " has no TPs");
                            testSuite.addTestCase("TPUU error", "TPUU has no TPs " + tpuu,
                                this.getClass().getName(), "TPUU has no TPs (" + tpuu.getId() + ")", "ERROR");
                        }
                    }
                    Concept mpuu = getParent(AmtRefset.MPUU, tpuu);
                    if (mpuu == null) {
                        throw new RuntimeException("TPUU " + tpuu + " has no MPUU");
                    }
                    addedMpuus.add(mpuu);

                    Set<Concept> mps = getParents(AmtRefset.MP, mpuu);

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

                    testSuite.addTestCase("Mismatch", message, this.getClass().getName(), "MPP mismatch (" + mpp.getId() + ")", "ERROR");
                }

                if ( count % 10000 == 0) {
                    long endTime = System.currentTimeMillis();
                    logger.info("Processed CTPP [" + count + " of " + ctppCount + "] Time spent processing 10000 CTPPs is " + (endTime - startTime) + "ms");
                    startTime = System.currentTimeMillis();
                }
                count++;
            }
        }
	}

    private Set<Concept> memberOf(Set<Concept> concepts, AmtRefset refset) {
        Set<Concept> refsetMembers = new HashSet<>();
        Map<Long, Concept> refsetConcepts = conceptCache.getAmtRefsets().get(refset.getIdString());
        for (Concept concept : concepts) {
            if (refsetConcepts.containsKey(concept.getId())) {
                refsetMembers.add(concept);
            }
        }
        return refsetMembers;
    }

    private void writeReplacementsFile(Path path) throws IOException {
        if (path.getParent() != null && !Files.exists(path.getParent())) {
            Files.createDirectory(path.getParent());
        }
        try (
                BufferedWriter writer = Files.newBufferedWriter(path, StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE)) {

            writer.write(
                String.join(",", "INACTIVE SCTID", "INACTIVE PT", "REPLACEMENT TYPE SCTID", "REPLACEMENT TYPE PT", "REPLACEMENT SCTID",
                    "REPLACEMENT PT", "DATE"));
            writer.newLine();
            for (Replacement entry : conceptCache.getReplacementConcepts()) {
                if (entry.getInactiveConcept() == null || entry.getReplacementType() == null || entry.getActiveConcept() == null) {
                    throw new RuntimeException("Null replacement concept: " + entry);
                }
                writer.write(
                    String.join(",",
                        entry.getInactiveConcept().getId() + "", "\"" + entry.getInactiveConcept().getPreferredTerm() + "\"",
                        entry.getReplacementType().getId() + "", "\"" + entry.getReplacementType().getPreferredTerm() + "\"",
                        entry.getActiveConcept().getId() + "", "\"" + entry.getActiveConcept().getPreferredTerm() + "\"", entry.getVersion()));
                writer.newLine();
            }
        }
    }

    private Concept getParent(AmtRefset parentType, Concept concept) {
		Set<Concept> parents = getParents(parentType, concept);
		
		if (parents.size() != 1) {
			String message = "Expected 1 parent of type " + parentType + " for concept " + concept + " but got " + parents;
            testSuite.addTestCase("multiple parents", message, this.getClass().getName(), "Multiple parents (" + concept.getId() + ")", "ERROR");
			
            if (exitOnError) {
				throw new RuntimeException(message);
            }
			return null;
		}
		return parents.iterator().next();
    }

    private Set<Concept> getParents(AmtRefset parentType, Concept concept) {
        Set<Concept> leafParents = new HashSet<>();

        leafParents.addAll(concept.getAncestors().stream().collect(Collectors.toSet()));
        leafParents = memberOf(leafParents, parentType);

        Set<Concept> redunantAncestors =
                leafParents.stream().flatMap(p -> p.getAncestors().stream()).collect(Collectors.toSet());

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

    public void setExitOnError(boolean exitOnError) {
        this.exitOnError = exitOnError;
	}

    public void setJunitFilePath(String path) {
        this.junitFilePath = path;
	}

    public void setReplacementsFilePath(String path) {
        this.replacementsOutputFilePath = path;
    }
}
