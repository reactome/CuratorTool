package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.util.*;

public class NonHumanReactionsWithHumanPhysicalEntitiesCheck extends AbstractQualityCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;

        GKInstance humanInst = dba.fetchInstance(48887L);
        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        for (GKInstance reaction : reactions) {
            Collection<GKInstance> reactionSpecies = reaction.getAttributeValuesList(ReactomeJavaConstants.species);
            if (!reactionSpecies.contains(humanInst)) {
                Set<GKInstance> humanPEs = findHumanPhysicalEntitiesInReaction(reaction, humanInst);
                for (GKInstance humanPE : humanPEs) {
                    String reportLine = getReportLine(humanPE, reaction);
                    report.addLine(reportLine);
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private Set<GKInstance> findHumanPhysicalEntitiesInReaction(GKInstance humanReaction, GKInstance humanInst) throws Exception {
        Set<GKInstance> reactionPEs = findAllPhysicalEntitiesInReaction(humanReaction);
        Set<GKInstance> humanPEs = new HashSet<>();
        for (GKInstance physicalEntity: reactionPEs) {
            if (physicalEntity.getAttributeValue(ReactomeJavaConstants.species) == humanInst) {
                humanPEs.add(physicalEntity);
            }
        }
        return humanPEs;
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

    private boolean hasSpeciesAttribute(GKInstance physicalEntity) {
        if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.OtherEntity) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Drug) ||
                physicalEntity.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity)) {
            return false;
        }
        return true;
    }

    private String getReportLine(GKInstance physicalEntity, GKInstance humanReaction) throws Exception {

        Collection<GKInstance> speciesInstances = physicalEntity.getAttributeValuesList(ReactomeJavaConstants.species);
        String speciesNames = "null";
        if (speciesInstances != null) {
            List<String> speciesNamesList = new ArrayList<>();
            for (GKInstance speciesInst : speciesInstances) {
                speciesNamesList.add(speciesInst.getDisplayName());
            }
            speciesNames = String.join(",", speciesNamesList);
            speciesNames = speciesNames.isEmpty() ? "null" : speciesNames;
        }
        GKInstance createdInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.created);
        String createdName = createdInst != null ? createdInst.getDisplayName() : "null";
        String reportLine = String.join("\t", humanReaction.getDBID().toString(), humanReaction.getDisplayName(), physicalEntity.getDBID().toString(), physicalEntity.getDisplayName(), physicalEntity.getSchemClass().getName(), speciesNames, createdName);
        return reportLine;
    }

    private String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species_PE", "Created_PE"};
    }

    @Override
    public String getDisplayName() {
        return "NonHuman_Reactions_Containing_Human_PhysicalEntities";
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
