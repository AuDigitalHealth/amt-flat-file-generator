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

    private Path conceptFile, relationshipFile, descriptionFile, languageRefsetFile, artgIdRefsetFile, medicinalProductRefsetFile;
    private List<Path> historicalAssociationRefsetFiles = new ArrayList<>();

    private Tika tika = new Tika();

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) throws IOException {
        if (attr.isRegularFile()) {
            String fileName = file.getFileName().toString();
            if (fileName.matches("sct2_Concept_Snapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    conceptFile = file;
                }
            } else if (fileName.matches("sct2_Relationship_Snapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    relationshipFile = file;
                }
            } else if (fileName.matches("sct2_Description_Snapshot-en-AU_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    descriptionFile = file;
                }
            } else if (fileName.matches("der2_cRefset_LanguageSnapshot-en-AU_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    languageRefsetFile = file;
                }
            } else if (fileName.matches("der2_iRefset_ARTGIdSnapshot_AU1000036_\\d{8}\\.txt") || fileName.matches("der2_iRefset_SimpleMapSnapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    artgIdRefsetFile = file;
                }
            } else if (fileName.matches("der2_cRefset_AssociationReferenceSnapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_AlternativeAssociationSnapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_MovedFromAssociationReferenceSnapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_MovedToAssociationReferenceSnapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_PossiblyEquivalentToAssociationSnapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_ReplacedByAssociationSnapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_SameAsAssociationSnapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_cRefset_WasAAssociationSnapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    historicalAssociationRefsetFiles.add(file);
                }
            } else if (fileName.matches("der2_Refset_MedicinalProductSnapshot_AU1000036_\\d{8}\\.txt") || fileName.matches("der2_Refset_SimpleSnapshot_AU1000036_\\d{8}\\.txt")) {
                if (verifyFile(file)) {
                    medicinalProductRefsetFile = file;
                }
            }
        }
        return FileVisitResult.CONTINUE;
    }

    private boolean verifyFile(Path file) throws IOException {
        if (Files.size(file) > MAX_FILE_SIZE) {
            logger.warning("File " + file + " was detected for reading but skipped because it is over the maximum file size theshold "
                    + MAX_FILE_SIZE);
            return false;
        } else if (!tika.detect(file).equals("text/plain")) {
            logger.warning(
                "File " + file + " was detected for reading but skipped because it is not a plain text file as expected, detected type was "
                        + tika.detect(file));
            return false;
        }
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

    public Path getArtgIdRefsetFile() {
        return artgIdRefsetFile;
    }

    public Path getMedicinalProductRefsetFile() {
        return medicinalProductRefsetFile;
    }

    public List<Path> getHistoricalAssociationRefsetFiles() {
        return historicalAssociationRefsetFiles;
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
    	} else if (this.getArtgIdRefsetFile() == null) {
    		throw new RuntimeException("Could not find artgid refset file in rf2");
    	}
    }

}