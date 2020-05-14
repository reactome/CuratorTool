package org.gk.qualityCheck;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class ReactionsWithNonHumanEntityWithoutDiseaseCheck extends NonHumanEventsNotInferredCheck {

    List<String> skipListDbIds = new ArrayList<>();
    GKInstance humanSpeciesInst = new GKInstance();

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        skipListDbIds = Files.readAllLines(Paths.get("QA_SkipList/Manually_Curated_NonHuman_Pathways.txt"));
        skipListDbIds.add("168249"); // Innate Immune System
        humanSpeciesInst = dba.fetchInstance(48887L);
        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        for (GKInstance reaction : reactions) {
            if (!isMemberSkippedEvent(reaction)) {
                Set<GKInstance> nonHumanPhysicalEntitiesWithoutDiseaseAttribute = findNonHumanEntitiesWithoutDiseaseAttribute(reaction);
                for (GKInstance nonHumanPhysicalEntityWithoutDiseaseAttribute : nonHumanPhysicalEntitiesWithoutDiseaseAttribute) {
                    report.addLine(getReportLine(nonHumanPhysicalEntityWithoutDiseaseAttribute, reaction));
                }
            }
        }
        report.setColumnHeaders();
        return report;
    }

    private String getReportLine(GKInstance physicalEntity, GKInstance reaction) throws Exception {
        GKInstance speciesInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.species);
        String speciesName = speciesInst != null ? speciesInst.getDisplayName() : "null";
        GKInstance createdInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst != null ? createdInst.getDisplayName() : "null";
        String reportLine = String.join("\t", reaction.getDBID().toString(), reaction.getDisplayName(), physicalEntity.getDBID().toString(), physicalEntity.getDisplayName(), physicalEntity.getSchemClass().getName(), speciesName, createdName);
        return reportLine;
    }

    private Set<GKInstance> findNonHumanEntitiesWithoutDiseaseAttribute(GKInstance reaction) throws Exception {
        Set<GKInstance> nonHumanPhysicalEntitiesWithoutDiseaseAttribute = new HashSet<>();
        for (GKInstance physicalEntity : findAllPhysicalEntitiesInReaction(reaction)) {
            GKInstance stableIdentifierInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            if (physicalEntity.getAttributeValue(ReactomeJavaConstants.species) != humanSpeciesInst && !stableIdentifierInst.getDisplayName().contains("R-ALL") && physicalEntity.getAttributeValue(ReactomeJavaConstants.disease) == null) {
                nonHumanPhysicalEntitiesWithoutDiseaseAttribute.add(physicalEntity);
            }
        }
        return nonHumanPhysicalEntitiesWithoutDiseaseAttribute;
    }

    private Set<GKInstance> findAllPhysicalEntitiesInReaction(GKInstance reaction) throws Exception {
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

    private Set<GKInstance> findAllConstituentPEs(GKInstance physicalEntity) throws Exception {
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

    private boolean isMemberSkippedEvent(GKInstance event) throws Exception {
        if (skipListDbIds.contains(event.getDBID().toString())) {
            return true;
        }
        Collection<GKInstance> hasEventReferrals = event.getReferers(ReactomeJavaConstants.hasEvent);
        if (hasEventReferrals != null) {
            for (GKInstance hasEventReferral : hasEventReferrals) {
                return isMemberSkippedEvent(hasEventReferral);
            }
        }
        return false;
    }

    private boolean hasSpeciesAttribute(GKInstance physicalEntity) {
        if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.OtherEntity) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Drug) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity)) {
            return false;
        }
        return true;
    }

    private boolean containsMultiplePEs(GKInstance physicalEntity) {
        if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Polymer) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
            return true;
        }
        return false;
    }

    @Override
    protected String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species_PE", "Created_PE"};
    }

    @Override
    public String getDisplayName() {
        return "Reactions_With_NonHuman_PhysicalEntities_Without_Disease";
    }
}
