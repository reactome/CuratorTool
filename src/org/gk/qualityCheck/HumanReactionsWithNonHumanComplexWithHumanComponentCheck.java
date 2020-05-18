package org.gk.qualityCheck;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.*;

public class HumanReactionsWithNonHumanComplexWithHumanComponentCheck extends NonHumanEventsNotManuallyInferredCheck {
    
    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        GKInstance humanSpeciesInst = dba.fetchInstance(48887L);
        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        for (GKInstance reaction : reactions) {
            if (!manuallyInferred(reaction) && isHumanDatabaseObject(reaction, humanSpeciesInst)) {
                Map<GKInstance, Set<GKInstance>> nonHumanComplexesWithHumanComponentsMap = findNonHumanComplexesWithHumanComponentInReaction(reaction, humanSpeciesInst);
                for (GKInstance complexWithHumanComponent : nonHumanComplexesWithHumanComponentsMap.keySet()) {
                    for (GKInstance componentWithHumanSpecies : nonHumanComplexesWithHumanComponentsMap.get(complexWithHumanComponent)) {
                        report.addLine(getReportLine(reaction, complexWithHumanComponent, componentWithHumanSpecies));
                    }
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private Map<GKInstance, Set<GKInstance>> findNonHumanComplexesWithHumanComponentInReaction(GKInstance reaction, GKInstance humanSpeciesInst) throws Exception {
        Map<GKInstance, Set<GKInstance>> nonHumanComplexesWithHumanComponentsMap = new HashMap<>();
        for (GKInstance physicalEntity : findAllPhysicalEntitiesInReaction(reaction)) {
            if (!isHumanDatabaseObject(physicalEntity, humanSpeciesInst) && physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex)) {
                Set<GKInstance> humanComponents = findHumanComponentsInComplex(physicalEntity, humanSpeciesInst);
                if (humanComponents.size() > 0) {
                    nonHumanComplexesWithHumanComponentsMap.put(physicalEntity, humanComponents);
                }
            }
        }
        return nonHumanComplexesWithHumanComponentsMap;
    }

    private Set<GKInstance> findHumanComponentsInComplex(GKInstance complex, GKInstance humanSpeciesInst) throws Exception {
        Set<GKInstance> physicalEntitiesInComplex = findAllConstituentPEs(complex);
        Set<GKInstance> humanPEs = new HashSet<>();
        for (GKInstance physicalEntity : physicalEntitiesInComplex) {
            GKInstance speciesInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.species);
            if (humanSpeciesInst.equals(speciesInst)) {
                humanPEs.add(physicalEntity);
            }
        }
        return humanPEs;
    }

    private String getReportLine(GKInstance event, GKInstance complexWithHumanComponent, GKInstance componentWithHumanSpecies) throws Exception {
        GKInstance complexCreatedInst = (GKInstance) complexWithHumanComponent.getAttributeValue(ReactomeJavaConstants.created);
        String complexCreatedName = complexCreatedInst != null ? complexCreatedInst.getDisplayName() : "null";
        GKInstance componentCreatedInst = (GKInstance) componentWithHumanSpecies.getAttributeValue(ReactomeJavaConstants.created);
        String componentCreatedName = componentCreatedInst != null ? componentCreatedInst.getDisplayName() : "null";
        String reportLine = String.join("\t", event.getDBID().toString(), event.getDisplayName(), complexWithHumanComponent.getDBID().toString(), complexWithHumanComponent.getDisplayName(), componentWithHumanSpecies.getDBID().toString(), componentWithHumanSpecies.getDisplayName(), componentWithHumanSpecies.getSchemClass().getName(), complexCreatedName, componentCreatedName);
        return reportLine;
    }

    @Override
    protected String[] getColumnHeaders() {
        return new String[]{"DB_ID_RlE", "DisplayName_RlE", "DB_ID_Complex", "DisplayName_Complex", "DB_ID_Component", "DisplayName_Component", "Class_Component", "Created_Complex", "Created_Component"};
    }

    @Override
    public String getDisplayName() {
        return "Human_Reactions_With_NonHuman_Complexes_With_Human_Components";
    }
}
