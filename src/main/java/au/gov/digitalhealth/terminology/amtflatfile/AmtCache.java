package au.gov.digitalhealth.terminology.amtflatfile;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Triple;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.graph.SimpleDirectedGraph;
//import org.openmbee.junit.model.JUnitFailure;

public class AmtCache {

    private static final String AU_METADATA_MODULE = "161771000036108";

    private static final String INTERNATIONAL_METADATA_MODULE = "900000000000012004";

    private static final String PREFERRED = "900000000000548007";

    private static final String FSN = "900000000000003001";

    private static final String AMT_MODULE_ID = "900062011000036108";

    private static final Logger logger = Logger.getLogger(AmtCache.class.getCanonicalName());
    private static final String INTERNATIONAL_MODULE = "900000000000207008";
    private static final String AU_MODULE = "32506021000036107";

    private static final List<String> HISTORICAL_ASSOCAITION_IDS = List.of("900000000000523009",
            "900000000000530003",
            "900000000000525002",
            "900000000000524003",
            "1186924009",
            "900000000000523009",
            "1186921001",
            "900000000000531004",
            "900000000000526001",
            "900000000000527005",
            "900000000000529008",
            "900000000000528000");

    private SimpleDirectedGraph<Long, Edge> graph = new SimpleDirectedGraph<>(Edge.class);

    private Map<Long, Concept> conceptCache = new HashMap<>();

    private Set<Long> preferredDescriptionIdCache = new HashSet<>();

    private Map<Long, Concept> ctpps = new HashMap<>();

    private Set<Replacement> replacements = new HashSet<>();

    private boolean exitOnError;

//    private JUnitTestSuite_EXT testSuite;
//    private JUnitTestCase_EXT graphCase;

    public AmtCache(FileSystem amtZip, boolean exitOnError) throws IOException {
//        this.testSuite = testSuite;
        this.exitOnError = exitOnError;
        processAmtFiles(amtZip);
    }

    private void processAmtFiles(FileSystem amtZip) throws IOException {

//        graphCase = new JUnitTestCase_EXT().setName("Graph errors");

        TerminologyFileVisitor visitor = new TerminologyFileVisitor();

        Files.walkFileTree(amtZip.getPath("/"), visitor);
        
        visitor.ensureAllFilesExist();

        readFile(visitor.getConceptFile(), s -> handleConceptRow(s), true, "\t");
        readFile(visitor.getRelationshipFile(), s -> handleRelationshipRow(s), true, "\t");
        readFile(visitor.getLanguageRefsetFile(), s -> handleLanguageRefsetRow(s), true, "\t");
        readFile(visitor.getDescriptionFile(), s -> handleDescriptionRow(s), true, "\t");
        readFile(visitor.getArtgIdRefsetFile(), s -> handleArtgIdRefsetRow(s), true, "\t");
        readFile(visitor.getMedicinalProductRefsetFile(), s -> handleMedicinalProductRefsetRow(s), true, "\t");
        for (Path historicalFile : visitor.getHistoricalAssociationRefsetFiles()) {
            readFile(historicalFile, s -> handleHistoricalAssociationRefsetRow(s), true, "\t");
        }

        try {
            calculateTransitiveClosure();
        } catch (Exception e) {
            String message = "Could not close graph. Elements missing";
//            JUnitFailure fail = new JUnitFailure();
//            fail.setMessage(message);
//            graphCase.addFailure(fail);
            if (exitOnError) {
                throw new RuntimeException(message);
            }
        }

        graph.incomingEdgesOf(AmtConcept.CTPP.getId())
            .stream()
            .map(e -> e.getSource())
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .forEach(id -> ctpps.put(id, conceptCache.get(id)));

        Iterator<Entry<Long, Concept>> it = ctpps.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Long, Concept> entry = it.next();
            if (!entry.getValue().isActive()) {
                String message = "Found inactive CTPP! " + entry.getValue();
                logger.warning(message);
//                testSuite.addTestCase("Inactive CTPP found", entry.getValue().toString(), "Inactive_CTPP", "ERROR");
                if (exitOnError) {
                    throw new RuntimeException(message);
                }
                it.remove();
            }
        }

        conceptCache.values().stream().forEach(c -> {
            c.addAncestors(
                graph.outgoingEdgesOf(c.getId())
                    .stream()
                    .map(e -> e.getTarget())
                    .collect(Collectors.<Long, Long, Concept> toMap(id -> id, id -> conceptCache.get(id))));
        });

        validateConceptCache();

        logger.info("Loaded " + ctpps.size() + " CTPPs " + conceptCache.size() + " concepts ");

        validateUnits();

        logger.info("Validated cached concepts ");
    }

    private void validateConceptCache() {
        // inactive concepts shouldn't have references to other things
        assertConceptCache(c -> !c.isActive() && !c.getParents().isEmpty(), "Inactive concepts with parents", "Inactive_with_parents",
            c -> c.getParents().clear());
        assertConceptCache(c -> !c.isActive() && !c.getTps().isEmpty(), "Inactive concepts with TPs", "Inactive_with_TPs",
            c -> c.getTps().clear());
        assertConceptCache(c -> !c.isActive() && !c.getUnits().isEmpty(), "Inactive concepts with Units", "Inactive_with_Units",
            c -> c.getUnits().clear());
        assertConceptCache(c -> !c.isActive() && !c.getArtgIds().isEmpty(), "Inactive concepts with ARTGIDs", "Inactive_with_ARTGIDs",
            c -> c.getArtgIds().clear());

        // all concepts should have PTs and FSNs
        assertConceptCache(c -> c.getFullSpecifiedName() == null || c.getFullSpecifiedName().isEmpty(), "Concepts with null or empty FSN",
            "Null_or_empty_FSN", c -> c.setFullSpecifiedName("Concept " + c.getId() + " has not FSN!!!")); // fix is a no-op
        assertConceptCache(c -> c.getPreferredTerm() == null || c.getPreferredTerm().isEmpty(), "Concepts with null or empty PT",
            "Null_or_empty_PT", c -> c.setPreferredTerm("Concept " + c.getId() + " has not Preferred Term!!!"));

        // active concepts should only reference active things
        assertConceptCache(c -> c.isActive() && c.getUnits().stream().anyMatch(u -> !u.isActive()),
            "Active concept with inactive linked unit/s", "Active_concept_inactive_units",
            c -> c.getUnits().removeAll(c.getUnits().stream().filter(u -> !u.isActive()).collect(Collectors.toSet())));
        assertConceptCache(c -> c.isActive() && c.getTps().stream().anyMatch(u -> !u.isActive()),
            "Active concept with inactive linked TP/s", "Active_concept_inactive_TP",
            c -> c.getTps().remove(c.getTps()
                .stream()
                .filter(t -> !t.isActive())
                .collect(Collectors.toSet())));
        assertConceptCache(c -> c.isActive() && c.getParents().values().stream().anyMatch(u -> !u.isActive()),
            "Active concept with inactive linked parent/s", "Active_concept_inactive_parents",
            c -> c.getParents().remove(c.getParents()
                .entrySet()
                .stream()
                .filter(e -> !e.getValue().isActive())
                .map(e -> e.getValue())
                .collect(Collectors.toSet())));
    }

    private void assertConceptCache(Predicate<Concept> predicate, String message, String testCaseName, Consumer<Concept> fix) {
        Set<Concept> errors =
                conceptCache.values().stream().filter(predicate).collect(Collectors.toSet());

        if (!errors.isEmpty()) {
            logger.warning(message + " " + errors);
//            testSuite.addTestCase(message, errors.toString(), testCaseName, "ERROR");

            if (exitOnError || fix == null) {
                throw new RuntimeException(message + " " + errors);
            } else {
                logger.warning(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                logger.warning(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                logger.warning(
                    "FIX APPLIED FOR ERRONEOUS INPUT DATA - continuing as requested, however RESULTS MAY BE UNRELIABLE AS A RESULT!!!!");
                logger.warning(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                logger.warning(
                    "!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                errors.stream().forEach(fix);
            }
        }
    }

    private void validateUnits() {
        Set<Concept> packConceptsWithNoUnits = graph.incomingEdgesOf(AmtConcept.MPP.getId())
            .stream()
            .map(e -> e.getSource())
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .map(id -> conceptCache.get(id))
            .filter(concept -> concept.getUnits() == null || concept.getUnits().size() == 0)
            .collect(Collectors.toSet());

        Set<Concept> mppsWithTpuus = graph.incomingEdgesOf(AmtConcept.MPP.getId())
            .stream()
            .map(e -> e.getSource())
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .map(id -> conceptCache.get(id))
            .filter(concept -> !concept.hasAtLeastOneMatchingAncestor(AmtConcept.TPP))
            .filter(concept -> concept.getUnits()
                .stream()
                .anyMatch(unit -> unit.hasAtLeastOneMatchingAncestor(AmtConcept.TPUU)))
            .collect(Collectors.toSet());

        Set<Concept> tppsWithMpuus = graph.incomingEdgesOf(AmtConcept.TPP.getId())
            .stream()
            .map(e -> e.getSource())
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .map(id -> conceptCache.get(id))
            .filter(concept -> concept.getUnits()
                .stream()
                .anyMatch(unit -> !unit.hasAtLeastOneMatchingAncestor(AmtConcept.TPUU)))
            .collect(Collectors.toSet());

        if (!packConceptsWithNoUnits.isEmpty() || !mppsWithTpuus.isEmpty() || !tppsWithMpuus.isEmpty()) {

            String detail = "Detected pack concepts with no units "
                    + packConceptsWithNoUnits.stream().map(c -> c.getId() + " |" + c.getPreferredTerm() + "|\n").collect(Collectors.toSet())
                    + " and/or MPPs with TPUU units "
                    + mppsWithTpuus.stream()
                        .map(c -> c.getId() + " |" + c.getPreferredTerm() + "|\n")
                        .collect(Collectors.toSet())
                    + " and/or TPP/CTPPs with MPUU units "
                    + tppsWithMpuus.stream()
                        .map(c -> c.getId() + " |" + c.getPreferredTerm() + "|\n")
                        .collect(Collectors.toSet());
//
//            testSuite.addTestCase("Detected pack concepts with no units and/or MPPs with TPUU units and/or TPP/CTPPs with MPUU units",
//                detail, "heirarchy_error", "ERROR");

            if (exitOnError) {
                throw new RuntimeException(detail);
            }
        }
    }

    public Map<Long, Concept> getCtpps() {
        return ctpps;
    }

    private void handleConceptRow(String[] row) {
        try {
            if (isAuModule(row) || isAmtOrMetadataModule(row) || isIntModule(row)) {
                long conceptId = Long.parseLong(row[0]);
                graph.addVertex(conceptId);
                conceptCache.put(conceptId, new Concept(conceptId, isActive(row)));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + row + " of Concepts file", e);
        }
    }

    private void handleRelationshipRow(String[] row) {

        try {
            long source = Long.parseLong(row[4]);
            long destination = Long.parseLong(row[5]);
            String type = row[7];

            if (isActive(row) && (isAmtModule(row) || isAuModule(row) || isIntModule(row)) && AttributeType.isEnumValue(type) && graph.containsVertex(source)
                    && graph.containsVertex(destination)) {
                Concept sourceConcept = conceptCache.get(source);

                switch (AttributeType.fromIdString(type)) {
                    case IS_A:
                        graph.addEdge(source, destination);
                        sourceConcept.addParent(conceptCache.get(destination));
                        break;

                    case HAS_MPUU:
                    case HAS_TPUU:
                    case CONTAINS_CLINICAL_DRUG:
                        sourceConcept.addUnit(conceptCache.get(destination));
                        break;

                    case CONTAINS_PACKAGED_CLINICAL_DRUG:
                        sourceConcept.addSubpack(conceptCache.get(destination));
                        break;

                    case HAS_TP:
                    case HAS_PRODUCT_NAME:
                        sourceConcept.addTp(conceptCache.get(destination));

                    default:
                        break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + row + " of Relationships file", e);
        }

    }

    private void handleDescriptionRow(String[] row) {

        try {

            Long conceptId = Long.parseLong(row[4]);

            if (isActive(row) && (isAmtOrMetadataModule(row) || isIntModule(row) || isAuModule(row)) && conceptCache.containsKey(conceptId)) {
                String descriptionId = row[0];
                String term = row[7];
                Concept concept = conceptCache.get(conceptId);
                if (row[6].equals(FSN)) {
                    concept.setFullSpecifiedName(term);
                } else if (preferredDescriptionIdCache.contains(Long.parseLong(descriptionId))) {
                    concept.setPreferredTerm(term);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + row + " of Descriptions file", e);
        }
    }

    private void handleLanguageRefsetRow(String[] row) {

        try {
            if (isActive(row) && (isAmtOrMetadataModule(row) || isAuModule(row)) && row[6].equals(PREFERRED)) {
                preferredDescriptionIdCache.add(Long.parseLong(row[5]));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + row + " of Language file", e);
        }

    }

    private void handleArtgIdRefsetRow(String[] row) {
        try {
            long conceptId = Long.parseLong(row[5]);
            if (isActive(row) && isAmtModule(row)) {
                conceptCache.get(conceptId).addArtgIds(row[6]);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + row + " of ARTG file", e);
        }
    }

    private void handleMedicinalProductRefsetRow(String[] row) {
        try {
            long conceptId = Long.parseLong(row[5]);
            if (isActive(row) && isAmtModule(row) && row[4].equals("929360061000036106")) {
                conceptCache.get(conceptId).setType(AmtConcept.MP);
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + row + " of ARTG file", e);
        }
    }

    private void handleHistoricalAssociationRefsetRow(String[] row) {
        try {
            if (isActive(row) && isAmtModule(row) && !isDescriptionId(row[5]) && HISTORICAL_ASSOCAITION_IDS.contains(row[4])) {
                Concept replacementType = conceptCache.get(Long.parseLong(row[4]));
                Concept inactiveConcept = conceptCache.get(Long.parseLong(row[5]));
                Concept replacementConcept = conceptCache.get(Long.parseLong(row[6]));

                if (replacementType == null || inactiveConcept == null || replacementConcept == null) {
                    throw new RuntimeException("Failed processing row: " + String.join("\t", row) + " of History file.\nOne of the concepts is null. "
                            + "\nreplacementType: " + replacementType + " replacement id is " + Long.parseLong(row[4]) + "\ninactiveConcept: " + inactiveConcept + "\nreplacementConcept: "
                            + replacementConcept);
                }

                replacements.add(new Replacement(inactiveConcept, replacementType, replacementConcept, row[1]));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + row + " of History file", e);
        }
    }

    private boolean isDescriptionId(String id) {
        return id.substring(id.length() - 2, id.length() - 1).equals("1");
    }

    private void calculateTransitiveClosure() {
        logger.info("Calculating transitive closure");
        TransitiveClosure.INSTANCE.closeSimpleDirectedGraph(graph);
        logger.info("Calculated transitive closure");
    }

    private boolean isActive(String[] row) {
        return row[2].equals("1");
    }

    private boolean isAmtModule(String[] row) {
        return row[3].equals(AMT_MODULE_ID);
    }

    private boolean isAmtOrMetadataModule(String[] row) {
        return row[3].equals(AMT_MODULE_ID) || row[3].equals(INTERNATIONAL_METADATA_MODULE) || row[3].equals(AU_METADATA_MODULE);
    }
    private boolean isIntModule(String[] row) {
        return row[3].equals(INTERNATIONAL_MODULE);
    }
    private boolean isAuModule(String[] row) {
        return row[3].equals(AU_MODULE);
    }
    public Set<Replacement> getReplacementConcepts() {
        return replacements;
    }

    @SuppressWarnings("resource")
    public static void readFile(Path path, Consumer<String[]> consumer, boolean hasHeader, String delimiter)
            throws IOException {
        Stream<String> stream = Files.lines(path);
        if (hasHeader) {
            stream = stream.skip(1);
        }
        stream.map(s -> s.split(delimiter, -1)).forEach(consumer);
        stream.close();
        logger.info("Processed " + path);
    }
}
