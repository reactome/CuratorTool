package org.gk.qualityCheck;

import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;

import java.util.*;

public class NonHumanComplexContainingHumanComponentCheck extends NonHumanEventsNotInferredCheck {

    private static GKInstance humanSpeciesInst = new GKInstance();

    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        if (report == null) {
            return null;
        }
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        humanSpeciesInst = dba.fetchInstance(48887L);
        List<GKInstance> nonHumanEventsNotUsedForInference = findNonHumanEventsNotUsedForInference(dba);
        Set<String> reportLines = new HashSet<>();
        for (GKInstance event : nonHumanEventsNotUsedForInference) {
            if (!event.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                Map<GKInstance, Set<GKInstance>> complexesWithHumanComponentsMap = findNonHumanComplexesWithHumanComponentInReaction(event);
                for (GKInstance complexWithHumanComponent : complexesWithHumanComponentsMap.keySet()) {
                    for (GKInstance componentWithHumanSpecies : complexesWithHumanComponentsMap.get(complexWithHumanComponent)) {
                        reportLines.add(getReportLine(event, complexWithHumanComponent, componentWithHumanSpecies));
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

    private String getReportLine(GKInstance event, GKInstance complexWithHumanComponent, GKInstance componentWithHumanSpecies) throws Exception {
        GKInstance complexCreatedInst = (GKInstance) complexWithHumanComponent.getAttributeValue(ReactomeJavaConstants.created);
        String complexCreatedName = complexCreatedInst != null ? complexCreatedInst.getDisplayName() : "null";
        GKInstance componentCreatedInst = (GKInstance) componentWithHumanSpecies.getAttributeValue(ReactomeJavaConstants.created);
        String componentCreatedName = componentCreatedInst != null ? componentCreatedInst.getDisplayName() : "null";
        String reportLine = String.join("\t", event.getDBID().toString(), event.getDisplayName(), complexWithHumanComponent.getDBID().toString(), complexWithHumanComponent.getDisplayName(), componentWithHumanSpecies.getDBID().toString(), componentWithHumanSpecies.getDisplayName(), componentWithHumanSpecies.getSchemClass().getName(), complexCreatedName, componentCreatedName);
        return reportLine;
    }

    private Map<GKInstance, Set<GKInstance>> findNonHumanComplexesWithHumanComponentInReaction(GKInstance reaction) throws Exception {
        Map<GKInstance, Set<GKInstance>> complexesWithHumanComponentsMap = new HashMap<>();
        for (GKInstance physicalEntity : findAllPhysicalEntitiesInReaction(reaction)) {
            if (physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex)) {
                complexesWithHumanComponentsMap.put(physicalEntity, findHumanComponentsInComplex(physicalEntity));
            }
        }
        return complexesWithHumanComponentsMap;
    }

    private Set<GKInstance> findHumanComponentsInComplex(GKInstance complex) throws Exception {
        Set<GKInstance> physicalEntitiesInComplex = findAllConstituentPEs(complex);
        Set<GKInstance> physicalEntitiesWithHumanSpecies = new HashSet<>();
        for (GKInstance physicalEntity : physicalEntitiesInComplex) {
            Collection<GKInstance> speciesInstances = physicalEntity.getAttributeValuesList(ReactomeJavaConstants.species);
            if (speciesInstances.contains(humanSpeciesInst)) {
                physicalEntitiesWithHumanSpecies.add(physicalEntity);
            }
        }
        return physicalEntitiesWithHumanSpecies;
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

    @Override
    protected String[] getColumnHeaders() {
        return new String[]{"DB_ID_RlE", "DisplayName_RlE", "DB_ID_Complex", "DisplayName_Complex", "DB_ID_Component", "DisplayName_Component", "Class_Component", "Created_Complex", "Created_Component"};
    }

    @Override
    public String getDisplayName() {
        return "NonHuman_Complexes_With_Human_Components";
    }
}
