package org.gk.qualityCheck;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.*;

public class HumanReactionWithNonHumanComplexWithHumanComponentCheck extends NonHumanEventsNotManuallyInferredCheck {

    private static GKInstance humanSpeciesInst = new GKInstance();

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        humanSpeciesInst = dba.fetchInstance(48887L);
        Collection<GKInstance> reactions = dba.fetchInstancesByClass(ReactomeJavaConstants.ReactionlikeEvent);
        Set<String> reportLines = new HashSet<>();
        for (GKInstance reaction : reactions) {
            GKInstance stableIdentifierInst = (GKInstance) reaction.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
            if (reaction.getReferers(ReactomeJavaConstants.inferredFrom) == null && stableIdentifierInst.getDisplayName().contains("R-HSA-")) {
                Map<GKInstance, Set<GKInstance>> nonHumanComplexesWithHumanComponentsMap = findNonHumanComplexesWithHumanComponentInReaction(reaction);
                for (GKInstance complexWithHumanComponent : nonHumanComplexesWithHumanComponentsMap.keySet()) {
                    for (GKInstance componentWithHumanSpecies : nonHumanComplexesWithHumanComponentsMap.get(complexWithHumanComponent)) {
                        reportLines.add(getReportLine(reaction, complexWithHumanComponent, componentWithHumanSpecies));
                    }
                }
            }
        }
        for (String reportLine : reportLines) {
            report.addLine(reportLine);
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    private Map<GKInstance, Set<GKInstance>> findNonHumanComplexesWithHumanComponentInReaction(GKInstance reaction) throws Exception {
        Map<GKInstance, Set<GKInstance>> nonHumanComplexesWithHumanComponentsMap = new HashMap<>();
        for (GKInstance physicalEntity : findAllPhysicalEntitiesInReaction(reaction)) {
            GKInstance speciesInst = (GKInstance) physicalEntity.getAttributeValue(ReactomeJavaConstants.species);
            if (!humanSpeciesInst.equals(speciesInst) && physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex)) {
                Set<GKInstance> humanComponents = findHumanComponentsInComplex(physicalEntity);
                if (humanComponents.size() > 0) {
                    nonHumanComplexesWithHumanComponentsMap.put(physicalEntity, humanComponents);
                }
            }
        }
        return nonHumanComplexesWithHumanComponentsMap;
    }

    private Set<GKInstance> findHumanComponentsInComplex(GKInstance complex) throws Exception {
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
