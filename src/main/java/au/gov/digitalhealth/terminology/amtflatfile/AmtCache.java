package au.gov.digitalhealth.terminology.amtflatfile;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang3.tuple.Triple;
import org.jgrapht.alg.TransitiveClosure;
import org.jgrapht.graph.SimpleDirectedGraph;
import org.openmbee.junit.model.JUnitFailure;

public class AmtCache {

    private static final String AU_METADATA_MODULE_V4 = "32570601000036100";
    private static final String INTERNATIONAL_METADATA_MODULE = "900000000000012004";

    private static final String PREFERRED = "900000000000548007";

    private static final String AMT_LANGUAGE_REFSET_ID = "32570271000036106";

    private static final String FSN = "900000000000003001";

    private static final String AMT_MODULE_ID_V4 = "32506021000036107";

    private static final String ARTG_MAP_REFSET_ID = "11000168105";

    private static final String TPP_REFSET_ID = "929360041000036105";

    private static final String CTPP_REFSET_ID = "929360051000036108";

    private static final String TP_REFSET_ID = "929360021000036102";

    private static final String TPUU_REFSET_ID = "929360031000036100";

    private static final String MPP_REFSET_ID = "929360081000036101";

    private static final String MPUU_REFSET_ID = "929360071000036103";

    private static final String MP_REFSET_ID = "929360061000036106";

    private static final Logger logger = Logger.getLogger(AmtCache.class.getCanonicalName());

    private SimpleDirectedGraph<Long, Edge> graph = new SimpleDirectedGraph<>(Edge.class);

    private Map<Long, Concept> conceptCache = new LinkedHashMap<>();

    private Set<Long> preferredDescriptionIdCache = new HashSet<>();

    private Map<Long, Concept> ctpps = new LinkedHashMap<>();

    private Set<Triple<Concept, Concept, Concept>> replacements = new LinkedHashSet<>();

    private Map<String, Set<Long>> refsetMembers = new LinkedHashMap<>();

    private boolean exitOnError;

    private JUnitTestSuite_EXT testSuite;
    private JUnitTestCase_EXT graphCase;

    public AmtCache(FileSystem amtZip, JUnitTestSuite_EXT testSuite, boolean exitOnError) throws IOException {
        this.testSuite = testSuite;
        this.exitOnError = exitOnError;
        processAmtFiles(amtZip);
    }

    private void processAmtFiles(FileSystem amtZip) throws IOException {

        graphCase = new JUnitTestCase_EXT().setName("Graph errors");

        TerminologyFileVisitor visitor = new TerminologyFileVisitor();

        Files.walkFileTree(amtZip.getPath("/"), visitor);
        
        visitor.ensureAllFilesExist();

        readFile(visitor.getConceptFile(), s -> handleConceptRow(s), true, "\t");
        readFile(visitor.getRelationshipFile(), s -> handleRelationshipRow(s), true, "\t");
        readFile(visitor.getLanguageRefsetFile(), s -> handleLanguageRefsetRow(s), true, "\t");
        readFile(visitor.getDescriptionFile(), s -> handleDescriptionRow(s), true, "\t");
        if (visitor.getSimpleRefsetFile() != null) {
            readFile(visitor.getSimpleRefsetFile(), s -> handleSimpleRefsetRow(s), true, "\t");
        }
        for (Path historicalFile : visitor.getHistoricalAssociationRefsetFiles()) {
            readFile(historicalFile, s -> handleHistoricalAssociationRefsetRow(s), true, "\t");
        }
        for (Path artgMapFile : visitor.getArtgMapFiles()) {
            readFile(artgMapFile, s -> handleArtgMapRow(s), true, "\t");
        }

        try {
            calculateTransitiveClosure();
        } catch (Exception e) {
            String message = "Could not close graph. Elements missing";
            JUnitFailure fail = new JUnitFailure();
            fail.setMessage(message);
            graphCase.addFailure(fail);
            if (exitOnError) {
                throw new RuntimeException(message);
            }
        }

        getCtppIdsFromRefsetLinkages()
            .stream()
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .forEach(id -> ctpps.put(id, conceptCache.get(id)));

        Iterator<Entry<Long, Concept>> it = ctpps.entrySet().iterator();
        while (it.hasNext()) {
            Entry<Long, Concept> entry = it.next();
            if (!entry.getValue().isActive()) {
                String message = "Found inactive CTPP! " + entry.getValue();
                logger.warning(message);
                testSuite.addTestCase("Inactive CTPP found", entry.getValue().toString(), "Inactive_CTPP", "ERROR");
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
            testSuite.addTestCase(message, errors.toString(), testCaseName, "ERROR");

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
        Set<Concept> packConceptsWithNoUnits = getRefsetMemberIds(MPP_REFSET_ID)
            .stream()
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .map(id -> conceptCache.get(id))
            .filter(concept -> concept != null)
            .filter(concept -> concept.getUnits() == null || concept.getUnits().size() == 0)
            .collect(Collectors.toSet());

        Set<Concept> mppsWithTpuus = getRefsetMemberIds(MPP_REFSET_ID)
            .stream()
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .map(id -> conceptCache.get(id))
            .filter(concept -> concept != null)
            .filter(concept -> concept.getUnits()
                .stream()
                .anyMatch(unit -> isRefsetMember(unit.getId(), TPUU_REFSET_ID)))
            .collect(Collectors.toSet());

        Set<Concept> tppsWithMpuus = getRefsetMemberIds(TPP_REFSET_ID)
            .stream()
            .filter(id -> !AmtConcept.isEnumValue(Long.toString(id)))
            .map(id -> conceptCache.get(id))
            .filter(concept -> concept != null)
            .filter(concept -> concept.getUnits()
                .stream()
                .anyMatch(unit -> isRefsetMember(unit.getId(), MPUU_REFSET_ID)))
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

            testSuite.addTestCase("Detected pack concepts with no units and/or MPPs with TPUU units and/or TPP/CTPPs with MPUU units",
                detail, "heirarchy_error", "ERROR");

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
            if (isActive(row)) {
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

                if (isActive(row) && AttributeType.isEnumValue(type) && graph.containsVertex(source)
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
                    case CONTAINS_DEVICE:
                    case CONTAINS_PACKAGED_CLINICAL_DRUG:
                        sourceConcept.addUnit(conceptCache.get(destination));
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

            if (isActive(row) && conceptCache.containsKey(conceptId)) {
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
            if (isActive(row) && AMT_LANGUAGE_REFSET_ID.equals(row[4]) && row[6].equals(PREFERRED)) {
                preferredDescriptionIdCache.add(Long.parseLong(row[5]));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + row + " of Language file", e);
        }

    }

    private void handleHistoricalAssociationRefsetRow(String[] row) {
        try {
            if (isActive(row) && !isDescriptionId(row[5])) {
                Concept replacementType = conceptCache.get(Long.parseLong(row[4]));
                Concept inactiveConcept = conceptCache.get(Long.parseLong(row[5]));
                Concept replacementConcept = conceptCache.get(Long.parseLong(row[6]));

                if (inactiveConcept == null || replacementType == null || replacementConcept == null) {
                    return;
                }

                replacements.add(Triple.of(inactiveConcept, replacementType, replacementConcept));
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + row + " of History file", e);
        }
    }

    private void handleSimpleRefsetRow(String[] row) {
        try {
            if (!isActive(row)) {
                return;
            }

            String refsetId = row[4];
            long referencedComponentId = Long.parseLong(row[5]);

            if (!conceptCache.containsKey(referencedComponentId)) {
                return;
            }

            refsetMembers.computeIfAbsent(refsetId, ignored -> new LinkedHashSet<>()).add(referencedComponentId);
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + row + " of Simple refset file", e);
        }
    }

    private void handleArtgMapRow(String[] row) {
        try {
            if (isActive(row) && ARTG_MAP_REFSET_ID.equals(row[4])) {
                long referencedComponentId = Long.parseLong(row[5]);

                if (!conceptCache.containsKey(referencedComponentId)) {
                    return;
                }

                String artgValue = row[6];
                if (artgValue != null && !artgValue.trim().isEmpty()) {
                    conceptCache.get(referencedComponentId).addArtgId(Long.parseLong(artgValue));
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed processing row: " + row + " of ARTG map file", e);
        }
    }

    private boolean isDescriptionId(String id) {
        return id.substring(id.length() - 2, id.length() - 1).equals("1");
    }

    public boolean isRefsetMember(long conceptId, String refsetId) {
        return getRefsetMemberIds(refsetId).contains(conceptId);
    }

    public Set<Long> getRefsetMemberIds(String refsetId) {
        return refsetMembers.getOrDefault(refsetId, Collections.emptySet());
    }

    public Set<Concept> getLeafAncestorsInRefset(long conceptId, String refsetId) {
        if (!graph.containsVertex(conceptId)) {
            return Collections.emptySet();
        }

        Set<Long> candidates = graph.outgoingEdgesOf(conceptId)
                .stream()
                .map(Edge::getTarget)
                .filter(id -> isRefsetMember(id, refsetId))
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (candidates.isEmpty()) {
            return Collections.emptySet();
        }

        Set<Long> nonLeafCandidates = new HashSet<>();
        for (long candidate : candidates) {
            for (long other : candidates) {
                if (candidate != other && graph.containsEdge(other, candidate)) {
                    nonLeafCandidates.add(candidate);
                    break;
                }
            }
        }

        candidates.removeAll(nonLeafCandidates);

        return candidates.stream()
                .map(conceptCache::get)
                .filter(c -> c != null)
                .collect(Collectors.toSet());
    }

    public Set<Concept> getContainedTpuus(Concept concept) {
        return getContainedMembersInRefset(concept, TPUU_REFSET_ID);
    }

    public Set<Concept> getContainedMpuus(Concept concept) {
        return getContainedMembersInRefset(concept, MPUU_REFSET_ID);
    }

    private Set<Concept> getContainedMembersInRefset(Concept concept, String targetRefsetId) {
        Set<Long> visited = new HashSet<>();
        Set<Concept> resolvedMembers = new LinkedHashSet<>();
        collectContainedMembers(concept, targetRefsetId, visited, resolvedMembers);
        return resolvedMembers;
    }

    private void collectContainedMembers(Concept concept, String targetRefsetId, Set<Long> visited,
            Set<Concept> resolvedMembers) {
        if (concept == null || !visited.add(concept.getId())) {
            return;
        }

        if (isRefsetMember(concept.getId(), targetRefsetId)) {
            resolvedMembers.add(concept);
            return;
        }

        for (Concept child : concept.getUnits()) {
            collectContainedMembers(child, targetRefsetId, visited, resolvedMembers);
        }
    }

    private Set<Long> getCtppIdsFromRefsetLinkages() {
        Set<Long> tppIds = getRefsetMemberIds(TPP_REFSET_ID);
        if (tppIds.isEmpty()) {
            logger.warning("No TPP refset members found; cannot derive CTPP concepts from v4 linkages.");
            return Collections.emptySet();
        }

        return conceptCache.values().stream()
                .filter(Concept::isActive)
                .map(Concept::getId)
                .filter(id -> !tppIds.contains(id))
                .filter(id -> graph.outgoingEdgesOf(id).stream().map(Edge::getTarget).anyMatch(tppIds::contains))
            .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public String getTpRefsetId() {
        return TP_REFSET_ID;
    }

    private Set<Long> getIncomingSourceIds(AmtConcept targetConcept, String targetLabel) {
        long targetId = targetConcept.getId();

        if (!graph.containsVertex(targetId)) {
            String message = "Missing expected " + targetLabel + " root concept in input RF2: " + targetId
                    + ". Continuing with empty set for this root.";
            logger.warning(message);
            testSuite.addTestCase("Missing root concept", message, "Missing_" + targetLabel + "_root", "ERROR");
            return Collections.emptySet();
        }

        return graph.incomingEdgesOf(targetId)
                .stream()
                .map(e -> e.getSource())
                .collect(Collectors.toSet());
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
        return row[3].equals(AMT_MODULE_ID_V4);
    }

    private boolean isAmtOrMetadataModule(String[] row) {
        return isAmtModule(row)
                || row[3].equals(INTERNATIONAL_METADATA_MODULE)
                || row[3].equals(AU_METADATA_MODULE_V4);
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
