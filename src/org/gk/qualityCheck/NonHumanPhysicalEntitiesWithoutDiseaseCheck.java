package org.gk.qualityCheck;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.*;

public class NonHumanPhysicalEntitiesWithoutDiseaseCheck extends NonHumanEventsNotManuallyInferredCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        List<GKInstance> nonHumanEventsNotUsedForInference = findNonHumanEventsNotUsedForInference(dba);
        for (GKInstance nonHumanEvent : nonHumanEventsNotUsedForInference) {
            if (nonHumanEvent.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                Set<GKInstance> reactionPEs = findAllPhysicalEntitiesInReaction(nonHumanEvent);
                for (GKInstance reactionPE : reactionPEs) {
                    if (hasSpecies(reactionPE) && reactionPE.getAttributeValue(ReactomeJavaConstants.disease) == null) {
                        String reportLine = getReportLine(reactionPE, nonHumanEvent);
                        report.addLine(reportLine);
                    }
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private boolean hasSpecies(GKInstance reactionPE) throws Exception {
        if (reactionPE.getSchemClass().isa(ReactomeJavaConstants.Drug)) {
            return false;
        }
        if (reactionPE.getAttributeValue(ReactomeJavaConstants.species) == null) {
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

    private boolean containsMultiplePEs(GKInstance physicalEntity) {
        if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Polymer) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.EntitySet)) {
            return true;
        }
        return false;
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

    private String getReportLine(GKInstance reactionPE, GKInstance reaction) throws Exception {
        GKInstance speciesInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.species);
        GKInstance createdInst = (GKInstance) reactionPE.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst == null ? "null" : createdInst.getDisplayName();
        String reportLine = String.join("\t", reaction.getDBID().toString(), reaction.getDisplayName(), reactionPE.getDBID().toString(), reactionPE.getDisplayName(), reactionPE.getSchemClass().getName(), speciesInst.getDisplayName(), createdName);
        return reportLine;
    }

    @Override
    protected String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species", "Created"};
    }

    @Override
    public String getDisplayName() {
        return "NonHuman_PhysicalEntities_Without_Disease";
    }

}
