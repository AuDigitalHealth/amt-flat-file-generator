package au.gov.digitalhealth.terminology.amtflatfile;

import java.nio.file.FileVisitResult;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;

class TerminologyFileVisitor extends SimpleFileVisitor<Path> {

    private Path conceptFile, relationshipFile, descriptionFile, languageRefsetFile, artgIdRefsetFile;

    @Override
    public FileVisitResult visitFile(Path file, BasicFileAttributes attr) {
        if (attr.isRegularFile()) {
            String fileName = file.getFileName().toString();
            if (fileName.startsWith("sct2_Concept_Snapshot_AU1000036")) {
                conceptFile = file;
            } else if (fileName.startsWith("sct2_Relationship_Snapshot_AU1000036")) {
                relationshipFile = file;
            } else if (fileName.startsWith("sct2_Description_Snapshot-en-AU_AU1000036")) {
                descriptionFile = file;
            } else if (fileName.startsWith("der2_cRefset_LanguageSnapshot-en-AU_AU1000036")) {
                languageRefsetFile = file;
            } else if (fileName.startsWith("der2_iRefset_ARTGIdSnapshot_AU1000036")) {
                artgIdRefsetFile = file;
            }
        }
        return FileVisitResult.CONTINUE;
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

}