package au.gov.digitalhealth.terminology.amtflatfile;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Triple;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.openmbee.junit.model.JUnitFailure;

public class AmtCache {

    private static final String AU_METADATA_MODULE = "161771000036108";

    private static final String INTERNATIONAL_METADATA_MODULE = "900000000000012004";

    private static final String PREFERRED = "900000000000548007";

    private static final String FSN = "900000000000003001";

    private static final String AMT_MODULE_ID = "900062011000036108";

    private static final Logger logger = Logger.getLogger(AmtCache.class.getCanonicalName());

    private SimpleDirectedGraph<Long, Edge> graph = new SimpleDirectedGraph<>(Edge.class);

    private Map<Long, Concept> conceptCache = new HashMap<>();

    private Set<Long> preferredDescriptionIdCache = new HashSet<>();

    private Map<Long, Concept> ctpps = new HashMap<>();

    private Set<Triple<Concept, Concept, Concept>> replacements = new HashSet<>();

    private boolean exitOnError;

    private JUnitTestSuite_EXT testSuite;
    private JUnitTestCase_EXT graphCase;

    public AmtCache(FileSystem amtZip, JUnitTestSuite_EXT testSuite, boolean exitOnError_param) throws IOException {
        this.testSuite = testSuite;
        processAmtFiles(amtZip);
        exitOnError = exitOnError_param;
    }

    private void processAmtFiles(FileSystem amtZip) throws IOException {

        graphCase = new JUnitTestCase_EXT().setName("Graph errors");

        TerminologyFileVisitor visitor = new TerminologyFileVisitor();

        Files.walkFileTree(amtZip.getPath("/"), visitor);

        readFile(visitor.getConceptFile(), s -> handleConceptRow(s), true, "\t");
        readFile(visitor.getRelationshipFile(), s -> handleRelationshipRow(s), true, "\t");
        readFile(visitor.getLanguageRefsetFile(), s -> handleLanguageRefsetRow(s), true, "\t");
        readFile(visitor.getDescriptionFile(), s -> handleDescriptionRow(s), true, "\t");
        readFile(visitor.getArtgIdRefsetFile(), s -> handleArtgIdRefsetRow(s), true, "\t");
        for (Path historicalFile : visitor.getHistoricalAssociationRefsetFiles()) {
            readFile(historicalFile, s -> handleHistoricalAssociationRefsetRow(s), true, "\t");
        }

        try {
            calculateTransitiveClosure();
        } catch (Exception e) {
            String message = "Could not close graph. Elements missing";
            JUnitFailure fail = new JUnitFailure();
            fail.setMessage(message);
            graphCase.addFailure(fail);
            if (exitOnError)
                throw new RuntimeException(message);
        }

        graph.incomingEdgesOf(AmtConcept.CTPP.getId())
            .stream()
            .map(e -> e.getSource())
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .forEach(id -> ctpps.put(id, conceptCache.get(id)));

        conceptCache.values().stream().forEach(c -> {
            c.addAncestors(
                graph.outgoingEdgesOf(c.getId())
                    .stream()
                    .map(e -> e.getTarget())
                    .collect(Collectors.<Long, Long, Concept> toMap(id -> id, id -> conceptCache.get(id))));
        });

        logger.info("Loaded " + ctpps.size() + " CTPPs " + conceptCache.size() + " concepts ");

        validateUnits();

        logger.info("Validated cached concepts ");
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

            JUnitFailure fail = new JUnitFailure();
            fail.setMessage("Detected pack concepts with no units and/or MPPs with TPUU units and/or TPP/CTPPs with MPUU units");
            fail.setValue(detail);
            JUnitTestCase_EXT testCase = new JUnitTestCase_EXT().setName("heirarchy_error");
            testCase.addFailure(fail);
            testSuite.addTestCase(testCase);

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
            if (isAmtOrMetadataModule(row)) {
                long conceptId = Long.parseLong(row[0]);
                graph.addVertex(conceptId);
                conceptCache.put(conceptId, new Concept(conceptId));
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

            if (isActive(row) && isAmtModule(row) && AttributeType.isEnumValue(type) && graph.containsVertex(source)
                    && graph.containsVertex(destination)) {
                Concept sourceConcept = conceptCache.get(source);

                switch (AttributeType.fromIdString(type)) {
                    case IS_A:
                        graph.addEdge(source, destination);
                        sourceConcept.addParent(conceptCache.get(destination));
                        break;

                    case HAS_MPUU:
                    case HAS_TPUU:
                        sourceConcept.addUnit(conceptCache.get(destination));
                        break;

                    case HAS_TP:
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

            if (isActive(row) && isAmtOrMetadataModule(row) && conceptCache.containsKey(conceptId)) {
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
            if (isActive(row) && isAmtOrMetadataModule(row) && row[6].equals(PREFERRED)) {
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

    private void handleHistoricalAssociationRefsetRow(String[] row) {
        try {
            if (isActive(row) && isAmtModule(row) && !isDescriptionId(row[5])) {
                Concept replacementType = conceptCache.get(Long.parseLong(row[4]));
                Concept inactiveConcept = conceptCache.get(Long.parseLong(row[5]));
                Concept replacementConcept = conceptCache.get(Long.parseLong(row[6]));

                replacements.add(Triple.of(inactiveConcept, replacementType, replacementConcept));
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

    public Collection<Long> getDescendantOf(Long... id) {
        Set<Long> result = graph.incomingEdgesOf(id[0])
            .stream()
            .map(e -> e.getSource())
            .collect(Collectors.toSet());

        for (int i = 1; i < id.length; i++) {
            result.retainAll(graph.incomingEdgesOf(id[i])
                .stream()
                .map(e -> e.getSource())
                .collect(Collectors.toSet()));
        }

        return result;
    }

    public Concept getConcept(Long id) {
        return conceptCache.get(id);
    }

    public Set<Triple<Concept, Concept, Concept>> getReplacementConcepts() {
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
