package org.gk.qualityCheck;

import org.gk.database.InstanceListPane;
import org.gk.model.GKInstance;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.schema.GKSchemaClass;

import java.util.*;

/**
 * QA check that finds non-human Complexes that contain human Components, that are members of human, non-manually inferred ReactionlikeEvents.
 */

public class HumanReactionsWithNonHumanComplexesWithHumanComponentsCheck extends AbstractQualityCheck {
    
    @Override
    public QAReport checkInCommand() throws Exception {
        QAReport report = new QAReport();
        MySQLAdaptor dba = (MySQLAdaptor) dataSource;
        QACheckUtilities.setHumanSpeciesInst(dba);
        QACheckUtilities.setSkipList(null);

        for (GKInstance reaction : QACheckUtilities.findHumanReactionsNotUsedForManualInference(dba)) {
            // QA Check is only on PhysicalEntities that are participants of human ReactionlikeEvents that are not manually inferred
            Map<GKInstance, Set<GKInstance>> nonHumanComplexesWithHumanComponentsMap = findAllNonHumanComplexesWithHumanComponentInReaction(reaction);
            for (GKInstance complexWithHumanComponent : nonHumanComplexesWithHumanComponentsMap.keySet()) {
                for (GKInstance componentWithHumanSpecies : nonHumanComplexesWithHumanComponentsMap.get(complexWithHumanComponent)) {
                    report.addLine(getReportLine(reaction, complexWithHumanComponent, componentWithHumanSpecies));
                }
            }
        }
        report.setColumnHeaders(getColumnHeaders());
        return report;
    }

    /**
     * Finds any non-human Complexes that have human Components.
     * @param reaction GKInstance -- ReactionlikeEvent with Human species.
     * @return Map<GKInstance, Set<GKInstance> -- Key are non-human Complexes, Values are any human components found in that Complex.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    private Map<GKInstance, Set<GKInstance>> findAllNonHumanComplexesWithHumanComponentInReaction(GKInstance reaction) throws Exception {
        Map<GKInstance, Set<GKInstance>> nonHumanComplexesWithHumanComponentsMap = new HashMap<>();
        // First find all PhysicalEntities in the Reaction, and then filter that list for any non-human or non-species Complexes.
        for (GKInstance physicalEntity : QACheckUtilities.findAllPhysicalEntitiesInReaction(reaction)) {
            if (!QACheckUtilities.isHumanDatabaseObject(physicalEntity)
                    && physicalEntity.getSchemClass().isa(ReactomeJavaConstants.Complex)) {

                // Find any human Components in the non-human Complex.
                nonHumanComplexesWithHumanComponentsMap.put(physicalEntity, findAllHumanComponentsInComplex(physicalEntity));
            }
        }
        return nonHumanComplexesWithHumanComponentsMap;
    }

    /**
     * Finds any human Components in the incoming Complex.
     * @param complex GKInstance -- Complex with non-human species or no species.
     * @return Set<GKInstance> -- Any Components in incoming Complex with human species.
     * @throws Exception -- Thrown by MySQLAdaptor.
     */
    private Set<GKInstance> findAllHumanComponentsInComplex(GKInstance complex) throws Exception {
        // Only find GKInstances within incoming Complex. It is recursive, so if Complex-within-Complex, it will return ALL components.
        Set<GKInstance> humanPEs = new HashSet<>();
        for (GKInstance physicalEntity : QACheckUtilities.findAllConstituentPEs(complex)) {
            if (QACheckUtilities.isHumanDatabaseObject(physicalEntity)) {
                humanPEs.add(physicalEntity);
            }
        }
        return humanPEs;
    }

    private String getReportLine(GKInstance event, GKInstance complex, GKInstance component) throws Exception {
        String complexCreatedName = QACheckUtilities.getInstanceAttributeName(complex, ReactomeJavaConstants.created);
        String componentCreatedName = QACheckUtilities.getInstanceAttributeName(component, ReactomeJavaConstants.created);
        return String.join("\t",
                event.getDBID().toString(),
                event.getDisplayName(),
                complex.getDBID().toString(),
                complex.getDisplayName(),
                component.getDBID().toString(),
                component.getDisplayName(),
                component.getSchemClass().getName(),
                complexCreatedName,
                componentCreatedName);
    }

    @Override
    public String getDisplayName() {
        return "Complexes_That_Should_Have_Homo_sapiens_Species";
    }

    private String[] getColumnHeaders() {
        return new String[]{"DB_ID_RlE", "DisplayName_RlE", "DB_ID_Complex", "DisplayName_Complex", "DB_ID_Component", "DisplayName_Component", "Class_Component", "Created_Complex", "Created_Component"};
    }


    // Unused, but required, AbstractQualityCheck methods.
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
    protected InstanceListPane getDisplayedList() { return null; }
}
