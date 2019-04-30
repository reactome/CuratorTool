package org.gk.qualityCheck;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

/**
 * This QA check reports human subpathways which do not have a
 * diagram but are not embedded in the nearest ancestor pathway
 * which has a diagram. The report lists the reactions which
 * are not contained in the embedding diagram.
 *
 * @author Fred Loney <loneyf@ohsu.edu>
 */
public class DiagramSubpathwayCheck extends PathwayELVCheck {

    private static class Detail {
        GKInstance embedder;
        Collection<GKInstance> missing;
        
        Detail(GKInstance diagram, Collection<GKInstance> missing) {
            this.embedder = diagram;
            this.missing = missing;
        }
    }
    
    private static final String HUMAN = "Homo sapiens";

    private static final String[] LOAD_ATTS = { 
            ReactomeJavaConstants.species,
            ReactomeJavaConstants.hasEvent
    };

    private static final String[] LOAD_REVERSE_ATTS = { 
            ReactomeJavaConstants.hasEvent,
            ReactomeJavaConstants.representedPathway
    };
    
    private static final String[] HEADERS = {
            "Diagram_DBID", "Pathway_DisplayName", "Pathway_DBID",
            "Subpathway_DBID", "Subpathway_DisplayName",
            "Missing_Reaction_DBIDs", "Created", "Modified"
    };
    
    private Map<GKInstance, Detail> details = new HashMap<GKInstance, Detail>();

    @Override
    protected String[] getHeaders() {
        return HEADERS;
    }

    @Override
    public String getDisplayName() {
        return "Diagram_Subpathway_Undiagrammed_Reactions";
    }
    
    /**
     * Overrides the superclass
     * {@link PathwayELVCheck#checkEventUsageInELV(MySQLAdaptor)}
     * method to collect the subpathways to report. The report issue
     * is retained in a {subpathway: detail} map for subsequent
     * reporting in {@link #addEventToDiagramMapToReport(QAReport, Map)}.
     *  
     * The return value is a map whose keys are the subpathways to
     * report. The map values are unused; thus the map is effectively
     * a key set.
     * 
     * @param dba
     * @return the {subpathway: empty set} map
     */
    @SuppressWarnings("unchecked")
    @Override
    public Map<GKInstance, Set<GKInstance>> checkEventUsageInELV(MySQLAdaptor dba)
            throws Exception {
        // Clear the details map.
        details.clear();
        // The {pathway: diagrams} map of all subpathway diagrams contained in the
        // event hierarchy of the key.
        Map<GKInstance, Set<GKInstance>> subpathwayUsage = super.checkEventUsageInELV(dba);
        // Load all Pathways in the database.
        Collection<GKInstance> pathways =
                dba.fetchInstancesByClass(ReactomeJavaConstants.Pathway);
        // Remove pathways represented in another diagram.
        pathways.removeIf(pathway -> subpathwayUsage.containsKey(pathway));
        dba.loadInstanceAttributeValues(pathways, LOAD_ATTS);
        dba.loadInstanceReverseAttributeValues(pathways, LOAD_REVERSE_ATTS);
        for (GKInstance pathway: pathways) {
            // Skip non-human pathways.
            GKInstance species =
                    (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.species);
            if (!HUMAN.equals(species.getDisplayName())) {
                continue;
            }
            // Skip top-level pathways.
            Collection<GKInstance> parents =
                    pathway.getReferers(ReactomeJavaConstants.hasEvent);
            if (parents == null || parents.isEmpty()) {
                continue;
            }
            // If the pathway does not have a diagram, then its reactions
            // should be contained in the diagram of the nearest ancestor
            // pathway which has a diagram.
            Collection<GKInstance> referers =
                    pathway.getReferers(ReactomeJavaConstants.representedPathway);
            if (referers == null || referers.isEmpty()) {
                Set<GKInstance> reactions = new HashSet<GKInstance>();
                // Collect the undiagrammed reactions.
                collectOwnReactions(pathway, reactions);
                // Find the nearest ancestor pathway which has a diagram.
                GKInstance embedder = getEmbeddingPathway(pathway);
                if (embedder == null) {
                    // This situation is detected in the OrphanedEvents QA check.
                    continue;
                }
                // Get the diagram component db ids.
                Collection<GKInstance> diagrams =
                        embedder.getReferers(ReactomeJavaConstants.representedPathway);
                GKInstance diagram = diagrams.iterator().next();
                Set<Long> diagramDbIds = getComponentDbIds(diagram);
                // The missing reactions are those which are undiagrammed
                // but are not in the embedding pathway's diagram.
                Set<GKInstance> missing = reactions.stream()
                        .filter(rxn-> !diagramDbIds.contains(rxn.getDBID()))
                        .collect(Collectors.toSet());
                // If there are undiagrammed reactions, then add a new issue detail.
                if (!missing.isEmpty()) {
                    List<GKInstance> diseases = pathway.getAttributeValuesList(ReactomeJavaConstants.disease);
                    if (diseases != null && !diseases.isEmpty()) {
                        Set<GKInstance> diseaseMissing = new HashSet<GKInstance>();
                        for (GKInstance rxn: missing) {
                            GKInstance normal =
                                    (GKInstance) rxn.getAttributeValue(ReactomeJavaConstants.normalReaction);
                            if (normal != null) {
                                diseaseMissing.add(rxn);
                            }
                        }
                        missing.removeAll(diseaseMissing);
                        // Recheck without the disease reactions.
                        if (missing.isEmpty()) {
                            continue;
                        }
                    }
                    details.put(pathway, new Detail(embedder, missing));
                }
            }
        }
        
        // Transform the pathways into a trivial map to match the
        // superclass method contract.
        Set<GKInstance> dummy = Collections.emptySet();
        return details.keySet().stream()
                .collect(Collectors.toMap(Function.identity(), pw -> dummy));
    }
    
    /**
     * Adds the pathway's reactions, recursively including undiagrammed
     * subpathway reactions, to the given set.
     * 
     * @param pathway the pathway to check
     * @param reactions the set to add the pathway's reactions
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private void collectOwnReactions(GKInstance pathway, Set<GKInstance> reactions)
            throws Exception {
        Collection<GKInstance> events =
                pathway.getAttributeValuesList(ReactomeJavaConstants.hasEvent);
        for (GKInstance event: events) {
            if (event.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                Collection<GKInstance> diagrams =
                        event.getReferers(ReactomeJavaConstants.representedPathway);
                if (diagrams == null || diagrams.isEmpty()) {
                    collectOwnReactions(event, reactions);
                }
            } else {
                reactions.add(event);
            }
        }
    }

    /**
     * @param event
     * @return the nearest ancestor pathway which has a diagram 
     * @throws Exception
     */
    @SuppressWarnings("unchecked")
    private GKInstance getEmbeddingPathway(GKInstance event) throws Exception {
        Collection<GKInstance> parents = event.getReferers(ReactomeJavaConstants.hasEvent);
        if (parents == null) {
            return null;
        }
        for (GKInstance parent: parents) {
            // If the parent has a diagram, then that is the return value.
            // Otherwise, recurse on the parent.
            Collection<GKInstance> referers =
                    parent.getReferers(ReactomeJavaConstants.representedPathway);
            if (referers == null || referers.isEmpty()) {
                GKInstance embedder = getEmbeddingPathway(parent);
                if (embedder != null) {
                    return embedder;
                }
            }
            else {
                return parent;
            }
        }
        
        return null;
    }

    @Override
    protected void addEventToDiagramMapToReport(QAReport report,
            Map<GKInstance, Set<GKInstance>> eventToDiagrams) throws Exception {
        if (eventToDiagrams.size() == 0)
            return;
        report.setColumnHeaders(HEADERS);
        
        // eventToDiagrams is the {pathway: empty set} map. We only use the
        // keys sorted based on the pathway db id within embedder db id.
        // We can't sort directly with a comparator that calls
        // getPathwayDiagram() since that method throws a checked exception.
        // Work around this encumberance by making a {embedder: diagram} map
        // first and using that in the sort comparator instead.
        Map<GKInstance, GKInstance> pwToPdMap = new HashMap<GKInstance, GKInstance>();
        for (GKInstance pathway: eventToDiagrams.keySet()) {
            GKInstance embedder = details.get(pathway).embedder;
            if (!pwToPdMap.containsKey(embedder)) {
                pwToPdMap.put(embedder, getPathwayDiagram(embedder));
            }
        }
        List<GKInstance> subpathways = eventToDiagrams.keySet().stream()
                .sorted((p1, p2) -> comparePathways(p1, p2, pwToPdMap))
                .collect(Collectors.toList());
        for (GKInstance pathway: subpathways) {
            addToReport(pathway, report);
        }
    }
    
    /**
     * Compare pathway db ids within detail embedder db ids.
     * 
     * @param p1 the first pathway to compare
     * @param p2 the second pathway to compare
     * @param pwToPdMap the {pathway: diagram} map
     * @return the sort comparison value
     * @throws Exception 
     */
    private int comparePathways(GKInstance p1, GKInstance p2, Map<GKInstance, GKInstance> pwToPdMap) {
        GKInstance e1 = details.get(p1).embedder;
        GKInstance e2 = details.get(p2).embedder;
        if (e1.equals(e2)) {
            return p1.getDBID().compareTo(p2.getDBID());
        } else {
            GKInstance pd1 = pwToPdMap.get(e1);
            GKInstance pd2 = pwToPdMap.get(e2);
            return pd1.getDBID().compareTo(pd2.getDBID());
        }
    }

    private void addToReport(GKInstance pathway, QAReport report) throws Exception {
        Detail detail = details.get(pathway);
        String missing = detail.missing.stream()
                .map(GKInstance::getDBID)
                .map(Object::toString)
                .sorted()
                .collect(Collectors.joining(", "));
        GKInstance diagram = getPathwayDiagram(detail.embedder);
        GKInstance created =
                (GKInstance) pathway.getAttributeValue(ReactomeJavaConstants.created);
        GKInstance modified = QACheckUtilities.getLatestCuratorIEFromInstance(pathway);
        report.addLine(diagram.getDBID().toString(),
                detail.embedder.getDisplayName(),
                detail.embedder.getDBID().toString(),
                pathway.getDBID().toString(),
                pathway.getDisplayName(),
                missing,
                created.getDisplayName(),
                modified.getDisplayName());
    }

    private GKInstance getPathwayDiagram(GKInstance pathway) throws Exception {
        @SuppressWarnings("unchecked")
        Collection<GKInstance> diagrams =
                pathway.getReferers(ReactomeJavaConstants.representedPathway);
        if (diagrams == null || diagrams.isEmpty()) {
            return null;
        } else {
            return diagrams.iterator().next();
        }
    }

}
