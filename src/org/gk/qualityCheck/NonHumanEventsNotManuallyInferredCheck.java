package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class NonHumanEventsNotManuallyInferredCheck extends AbstractQualityCheck {

    private static List<String> skiplistDbIDs = new ArrayList<>();

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        GKInstance humanSpeciesInst = dba.fetchInstance(48887L);
        List<GKInstance> eventsNotUsedForInference = findEventsNotUsedForManualInference(dba);
        for (GKInstance event : eventsNotUsedForInference) {
            List<GKInstance> eventSpecies = event.getAttributeValuesList(ReactomeJavaConstants.species);
            if (!eventSpecies.contains(humanSpeciesInst)) {
                report.addLine(getReportLine(event));
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    protected List<GKInstance> findEventsNotUsedForManualInference(MySQLAdaptor dba) throws Exception {
        setSkipList();
        List<GKInstance> eventsNotUsedForInference = new ArrayList<>();
        Collection<GKInstance> events = dba.fetchInstancesByClass(ReactomeJavaConstants.Event);
        for (GKInstance event : events) {
            if (!manuallyInferred(event) && !isMemberSkipListPathway(event)) {
                eventsNotUsedForInference.add(event);
            }
        }
        return eventsNotUsedForInference;
    }

    boolean manuallyInferred(GKInstance event) throws Exception {
        return event.getReferers(ReactomeJavaConstants.inferredFrom) != null ? true : false;
    }

    boolean isHumanDatabaseObject(GKInstance databaseObject, GKInstance humanSpeciesInst) throws Exception {
        Collection<GKInstance> objectSpecies = databaseObject.getAttributeValuesList(ReactomeJavaConstants.species);
        return objectSpecies.size() == 1 && objectSpecies.contains(humanSpeciesInst);
    }

    protected boolean isMemberSkipListPathway(GKInstance nonHumanEvent) throws Exception {

        if (inSkipList(nonHumanEvent)) {
            return true;
        }

        Collection<GKInstance> hasEventReferrals = nonHumanEvent.getReferers(ReactomeJavaConstants.hasEvent);
        if (hasEventReferrals != null) {
            for (GKInstance hasEventReferral : hasEventReferrals) {
                return isMemberSkipListPathway(hasEventReferral);
            }
        }

        return false;
    }

    boolean hasNonHumanSpecies(GKInstance physicalEntity, GKInstance humanSpeciesInst) throws Exception {
        if (!hasSpeciesAttribute(physicalEntity)) {
            return false;
        }
        if (physicalEntity.getAttributeValue(ReactomeJavaConstants.species) == null) {
            return false;
        }
        if (physicalEntity.getAttributeValue(ReactomeJavaConstants.species) == humanSpeciesInst) {
            return false;
        }
        return true;
    }

    private boolean hasSpeciesAttribute(GKInstance physicalEntity) {
        if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.OtherEntity) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Drug) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity)) {
            return false;
        }
        return true;
    }

    private void setSkipList() throws IOException {
        skiplistDbIDs = Files.readAllLines(Paths.get("QA_SkipList/Manually_Curated_NonHuman_Pathways.txt"));
    }

    protected void addToSkipList(String dbId) {
        skiplistDbIDs.add(dbId);
    }

    private boolean inSkipList(GKInstance nonHumanEvent) {
        return skiplistDbIDs.contains(nonHumanEvent.getDBID().toString());
    }

    Set<GKInstance> findAllPhysicalEntitiesInReaction(GKInstance reaction) throws Exception {
        Set<GKInstance> reactionPEs = new HashSet<>();
        reactionPEs.addAll(findAllInputAndOutputPEs(reaction));
        reactionPEs.addAll(findAllCatalystPEs(reaction));
        reactionPEs.addAll(findAllRegulationPEs(reaction));
        return reactionPEs;
    }

    private Set<GKInstance> findAllInputAndOutputPEs(GKInstance reaction) throws Exception {
        Set<GKInstance> inputOutputPEs = new HashSet<>();
        for (String attribute : Arrays.asList(ReactomeJavaConstants.input, ReactomeJavaConstants.output)) {
            for (GKInstance attributePE : (Collection<GKInstance>) reaction.getAttributeValuesList(attribute)) {
                if (hasSpeciesAttribute(attributePE)) {
                    inputOutputPEs.add(attributePE);
                    inputOutputPEs.addAll(findAllPhysicalEntities(attributePE));
                }
            }
        }
        return inputOutputPEs;
    }

    private Set<GKInstance> findAllCatalystPEs(GKInstance reaction) throws Exception {
        Set<GKInstance> catalystPEs = new HashSet<>();
        List<GKInstance> catalysts = reaction.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        for (GKInstance catalyst : catalysts) {
            for (String attribute : Arrays.asList(ReactomeJavaConstants.activeUnit, ReactomeJavaConstants.physicalEntity)) {
                for (GKInstance attributePE : (Collection<GKInstance>) catalyst.getAttributeValuesList(attribute)) {
                    if (hasSpeciesAttribute(attributePE)) {
                        catalystPEs.add(attributePE);
                        catalystPEs.addAll(findAllPhysicalEntities(attributePE));
                    }
                }
            }
        }
        return catalystPEs;
    }

    private Set<GKInstance> findAllRegulationPEs(GKInstance reaction) throws Exception {
        Set<GKInstance> regulationPEs = new HashSet<>();
        List<GKInstance> regulations = reaction.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
        for (GKInstance regulation : regulations) {
            for (String attribute : Arrays.asList(ReactomeJavaConstants.activeUnit, ReactomeJavaConstants.regulator)) {
                for (GKInstance attributePE : (Collection<GKInstance>) regulation.getAttributeValuesList(attribute)) {
                    if (hasSpeciesAttribute(attributePE)) {
                        regulationPEs.add(attributePE);
                        regulationPEs.addAll(findAllPhysicalEntities(attributePE));
                    }
                }
            }
        }
        return regulationPEs;
    }

    private Set<GKInstance> findAllPhysicalEntities(GKInstance physicalEntity) throws Exception {
        Set<GKInstance> physicalEntities = new HashSet<>();
        if (hasSpeciesAttribute(physicalEntity)) {
            if (containsMultiplePEs(physicalEntity)) {
                physicalEntities.add(physicalEntity);
                physicalEntities.addAll(findAllConstituentPEs(physicalEntity));
            } else if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.EntityWithAccessionedSequence)) {
                physicalEntities.add(physicalEntity);
            }
        }
        return physicalEntities;
    }

    private boolean containsMultiplePEs(GKInstance physicalEntity) {
        if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Polymer) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
            return true;
        }
        return false;
    }

    Set<GKInstance> findAllConstituentPEs(GKInstance physicalEntity) throws Exception {
        Set<GKInstance> physicalEntities = new HashSet<>();
        if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex)) {
            for (GKInstance complexComponent : (Collection<GKInstance>) physicalEntity.getAttributeValuesList(ReactomeJavaConstants.hasComponent)) {
                physicalEntities.addAll(findAllPhysicalEntities(complexComponent));
            }
        } else if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Polymer)) {
            for (GKInstance polymerUnit : (Collection<GKInstance>) physicalEntity.getAttributeValuesList(ReactomeJavaConstants.repeatedUnit)) {
                physicalEntities.addAll(findAllPhysicalEntities(polymerUnit));
            }
        } else if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
            physicalEntities.addAll(findEntitySetPhysicalEntities(physicalEntity));
        }
        return physicalEntities;
    }

    private Set<GKInstance> findEntitySetPhysicalEntities(GKInstance physicalEntity) throws Exception {
        Set<GKInstance> physicalEntities = new HashSet<>();
        Set<String> entitySetAttributes = new HashSet<>(Arrays.asList(ReactomeJavaConstants.hasMember, ReactomeJavaConstants.hasCandidate));
        if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.DefinedSet)) {
            entitySetAttributes.remove(ReactomeJavaConstants.hasCandidate);
        }
        for (String entitySetAttribute : entitySetAttributes) {
            for (GKInstance setInstance : (Collection<GKInstance>) physicalEntity.getAttributeValuesList(entitySetAttribute)) {
                physicalEntities.addAll(findAllPhysicalEntities((setInstance)));
            }
        }
        return physicalEntities;
    }

    private String getReportLine(GKInstance instance) throws Exception {

        GKInstance speciesInst = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.species);
        GKInstance createdInst = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.created);
        String reportLine = String.join("\t", instance.getDBID().toString(), instance.getDisplayName(), speciesInst.getDisplayName(), createdInst.getDisplayName());
        return reportLine;
    }


    @Override
    public String getDisplayName() {
        return "NonHuman_Events_Not_Manually_Inferred";
    }

    protected String[] getColumnHeaders() {
        return new String[] {"DB_ID", "DisplayName", "Species", "Created"};
    }

    @Override
    public void check() {
    }

    @Override
    public void check(GKInstance instance) {
    }

    @Override
    public void check(List<GKInstance> instances) {
    }

    @Override
    public void check(GKSchemaClass cls) {
    }

    @Override
    public void checkProject(GKInstance event) {
    }

    @Override
    protected InstanceListPane getDisplayedList() {
        return null;
    }

}
