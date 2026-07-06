package au.gov.digitalhealth.terminology.amtflatfile;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import org.apache.tika.Tika;

class TerminologyFileVisitor extends SimpleFileVisitor<Path> {

    private static final Logger logger = Logger.getLogger(TerminologyFileVisitor.class.getCanonicalName());

    private static final int MAX_FILE_SIZE = 1000000000;

    private Path conceptFile, relationshipFile, descriptionFile, languageRefsetFile, simpleRefsetFile;
    private List<Path> artgMapFiles = new ArrayList<>();
    private List<Path> historicalAssociationRefsetFiles = new ArrayList<>();

    private Tika tika = new Tika();

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
        if (attr.isRegularFile()) {
            String fileName = file.getFileName().toString();
            if (fileName.matches("sct2_Concept_Snapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    conceptFile = file;
                }
            } else if (fileName.matches("sct2_Relationship_Snapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    relationshipFile = file;
                }
            } else if (fileName.matches("sct2_Description_Snapshot.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    descriptionFile = file;
                }
            } else if (fileName.matches("der2_cRefset_LanguageSnapshot.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    languageRefsetFile = file;
                }
            } else if (fileName.matches("der2_Refset_SimpleSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    simpleRefsetFile = file;
                }
            } else if (fileName.matches("der2_iRefset_SimpleMapSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    artgMapFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_AssociationReferenceSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_AssociationSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_ccRefset_ExtendedAssociationSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_AlternativeAssociationSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_MovedFromAssociationReferenceSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_MovedToAssociationReferenceSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_PossiblyEquivalentToAssociationSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_ReplacedByAssociationSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_SameAsAssociationSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_WasAAssociationSnapshot_.*_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            }
        }
        return FileVisitResult.CONTINUE;
    }

    private boolean verifyFile(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_SIZE) {
            logger.warning("File " + file + " was detected for reading but skipped because it is over the maximum file size threshold "
                    + MAX_FILE_SIZE);
            return false;
        }

        String mimeType = tika.detect(file);
        if (mimeType != null && mimeType.startsWith("text/")) {
            return true;
        }

        // Some RF2 .txt files can be detected as generic binary depending on environment.
        logger.warning("File " + file + " has non-text MIME type '" + mimeType
                + "' but will still be processed based on RF2 filename match.");
        return true;
    }

    public Path getConceptFile() {
        return conceptFile;
    }

    public Path getRelationshipFile() {
        return relationshipFile;
    }

    public Path getDescriptionFile() {
        return descriptionFile;
    }

    public Path getLanguageRefsetFile() {
        return languageRefsetFile;
    }

    public Path getSimpleRefsetFile() {
        return simpleRefsetFile;
    }

    public List<Path> getHistoricalAssociationRefsetFiles() {
        return historicalAssociationRefsetFiles;
    }

    public List<Path> getArtgMapFiles() {
        return artgMapFiles;
    }

    public void ensureAllFilesExist() {
    	if(this.getConceptFile() == null) {
    		throw new RuntimeException("Could not find concept file in rf2");
    	} else if (this.getRelationshipFile() == null) {
    		throw new RuntimeException("Could not find relationship file in rf2");
    	} else if (this.getDescriptionFile() == null) {
    		throw new RuntimeException("Could not find description file in rf2");
    	} else if (this.getLanguageRefsetFile() == null) {
    		throw new RuntimeException("Could not find language refset file in rf2");
        }

        if (this.getHistoricalAssociationRefsetFiles().isEmpty()) {
        	logger.warning("Could not find historical association refset files in rf2. Continuing without replacement concepts.");
    	}

        if (this.getArtgMapFiles().isEmpty()) {
            logger.warning("Could not find ARTG mapping file in rf2. Continuing without ARTG IDs.");
        }

        if (this.getSimpleRefsetFile() == null) {
            logger.warning("Could not find simple refset membership file in rf2. Class-based filtering may be incomplete.");
        }
    }

}