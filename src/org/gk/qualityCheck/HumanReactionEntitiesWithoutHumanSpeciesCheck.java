package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.util.*;

public class HumanReactionEntitiesWithoutHumanSpeciesCheck extends AbstractQualityCheck {

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;

        GKInstance humanInst = dba.fetchInstance(48887L);
        Collection<GKInstance> humanReactions = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent, ReactomeJavaConstants.species, "=", humanInst);
        for (GKInstance humanReaction : humanReactions) {
            Set<GKInstance> humanReactionPEsWithoutHumanSpecies = findEntitiesWithoutHumanSpecies(humanReaction, humanInst);
            for (GKInstance physicalEntity : humanReactionPEsWithoutHumanSpecies) {
                String reportLine = getReportLine(physicalEntity, humanReaction);
                report.addLine(reportLine);
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
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

    private Set<GKInstance> findEntitiesWithoutHumanSpecies(GKInstance humanReaction, GKInstance humanInst) throws Exception {
        Set<GKInstance> humanReactionPEs = findPhysicalEntitiesInReaction(humanReaction);
        Set<GKInstance> humanReactionPEsWithoutHumanSpecies = new HashSet<>();
        for (GKInstance humanPE : humanReactionPEs) {
            GKInstance stableIdentifierInst = (GKInstance) humanPE.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            if (stableIdentifierInst.getDisplayName().contains("R-HSA-")) {
                if (hasSpeciesAttribute(humanPE) && !humanPE.getSchemClass().isa(ReactomeJavaConstants.SimpleEntity)) {
                    Collection<GKInstance> speciesInstances = humanPE.getAttributeValuesList(ReactomeJavaConstants.species);
                    if (!speciesInstances.contains(humanInst)) {
                        humanReactionPEsWithoutHumanSpecies.add(humanPE);
                    }
                }
            }
        }
        return humanReactionPEsWithoutHumanSpecies;
    }

    private boolean hasSpeciesAttribute(GKInstance reactionPE) {
        if (reactionPE.getSchemClass().isa(ReactomeJavaConstants.Drug) || reactionPE.getSchemClass().isa(ReactomeJavaConstants.OtherEntity)) {
            return false;
        }
        return true;
    }

    private Set<GKInstance> findPhysicalEntitiesInReaction(GKInstance reaction) throws Exception {
        Set<GKInstance> reactionPEs = new HashSet<>();
        reactionPEs.addAll(reaction.getAttributeValuesList(ReactomeJavaConstants.input));
        reactionPEs.addAll(reaction.getAttributeValuesList(ReactomeJavaConstants.output));
        reactionPEs.addAll(findCatalystPEs(reaction));
        reactionPEs.addAll(findRegulationPEs(reaction));
        return reactionPEs;
    }

    private Set<GKInstance> findRegulationPEs(GKInstance reaction) throws Exception {
        Set<GKInstance> regulationPEs = new HashSet<>();
        List<GKInstance> regulations = reaction.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
        for (GKInstance regulation : regulations) {
            regulationPEs.addAll(regulation.getAttributeValuesList(ReactomeJavaConstants.activeUnit));
            regulationPEs.add((GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulator));
        }
        return regulationPEs;
    }

    private Set<GKInstance> findCatalystPEs(GKInstance reaction) throws Exception {
        Set<GKInstance> catalystPEs = new HashSet<>();
        List<GKInstance> catalysts = reaction.getAttributeValuesList(ReactomeJavaConstants.catalystActivity);
        for (GKInstance catalyst : catalysts) {
            catalystPEs.addAll(catalyst.getAttributeValuesList(ReactomeJavaConstants.activeUnit));
            catalystPEs.add((GKInstance) catalyst.getAttributeValue(ReactomeJavaConstants.physicalEntity));
        }
        return catalystPEs;
    }

    private String[] getColumnHeaders() {
        return new String[] {"DB_ID_RlE", "DisplayName_RlE", "DB_ID_PE", "DisplayName_PE", "Class_PE", "Species_PE", "Created_PE"};
    }

    @Override
    public String getDisplayName() {
        return "Human_Reactions_Containing_NonHuman_PhysicalEntities";
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
