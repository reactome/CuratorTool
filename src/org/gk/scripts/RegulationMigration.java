package org.gk.scripts;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.gk.model.GKInstance;
import org.gk.model.InstanceDisplayNameGenerator;
import org.gk.model.InstanceUtilities;
import org.gk.model.ReactomeJavaConstants;
import org.gk.persistence.MySQLAdaptor;
import org.gk.qualityCheck.QACheckUtilities;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaClass;
import org.gk.util.FileUtilities;
import org.junit.Test;

/**
 * This class is used to migrate Regulation instances directly under ReactionlikeEvent.
 * @author wug
 *
 */
@SuppressWarnings("unchecked")
public class RegulationMigration {
    
    public RegulationMigration() {
    }
    
    @Test
    public void testHandleCAReferences() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "test_gk_central_schema_update_gw",
                                            "",
                                            "");
        handleCatalystActivityRefereces(dba);
    }
    
    public void handleCatalystActivityRefereces(MySQLAdaptor dba) throws Exception {
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.CatalystActivity);
        dba.loadInstanceAttributeValues(regulations, new String[]{ReactomeJavaConstants.literatureReference});
        dba.loadInstanceReverseAttributeValues(regulations,  new String[] {ReactomeJavaConstants.catalystActivity});
        Set<GKInstance> toBeHandled = handleControlReferences(regulations, ReactomeJavaConstants.catalystActivity);
        System.out.println("\nTotal to be handled CatalystActivities: " + toBeHandled.size());
        handleCatalystActivityReferences(toBeHandled, dba);
    }
    
    private void handleCatalystActivityReferences(Set<GKInstance> cas,
                                                  MySQLAdaptor dba) throws Exception {
        if (dba.supportsTransactions())
            dba.startTransaction();
        GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
        System.out.println("Total to be handled: " + cas.size());
        Set<GKInstance> toBeUpdatedRLEs = new HashSet<>();
        System.out.println("\nStarting to handling CatalystActivities: ");
        for (GKInstance ca : cas) {
            System.out.println("Handling " + ca + "...");
            // Create a RegulationReference
            GKInstance catalystActivityReference = createInstance(ReactomeJavaConstants.CatalystActivityReference,
                                                            defaultIE,
                                                            dba);
            catalystActivityReference.setAttributeValue(ReactomeJavaConstants.catalystActivity, ca);
            List<GKInstance> references = ca.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
            catalystActivityReference.setAttributeValue(ReactomeJavaConstants.literatureReference,
                                                  new ArrayList<>(references));
            Collection<GKInstance> rles = ca.getReferers(ReactomeJavaConstants.catalystActivity);
            for (GKInstance rle : rles) {
                rle.addAttributeValue(ReactomeJavaConstants.catalystActivityReference, catalystActivityReference);
                toBeUpdatedRLEs.add(rle);
            }
            InstanceDisplayNameGenerator.setDisplayName(catalystActivityReference);
            dba.storeInstance(catalystActivityReference);
        }
        System.out.println("\nStarting to updating ReactionlikeEvents: ");
        for (GKInstance rle : toBeUpdatedRLEs) {
            System.out.println("Updating " + rle + "...");
            dba.updateInstanceAttribute(rle, ReactomeJavaConstants.catalystActivityReference);
            ScriptUtilities.addIEToModified(rle, defaultIE, dba);
        }
        if (dba.supportsTransactions())
            dba.commit();
    }
    
    @Test
    public void testHandleRegulationReferences() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                                            "test_gk_central_schema_update_gw",
                                            "root",
                                            "macmysql01");
        handleRegulationReferences(dba);
    }
    
    public void handleRegulationReferences(MySQLAdaptor dba) throws Exception {
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.Regulation);
        dba.loadInstanceAttributeValues(regulations, new String[]{ReactomeJavaConstants.literatureReference});
        dba.loadInstanceReverseAttributeValues(regulations,  new String[] {ReactomeJavaConstants.regulatedBy});
        Set<GKInstance> toBeHandled = handleControlReferences(regulations, ReactomeJavaConstants.regulatedBy);
        handleRegulationReferences(toBeHandled, dba);
    }

    private Set<GKInstance> handleControlReferences(Collection<GKInstance> controls,
                                                    String rleAttributeName) throws Exception {
        Set<GKInstance> notToBeHandled = new HashSet<>();
        Set<GKInstance> toBeHandled = new HashSet<>();
        for (GKInstance control : controls) {
            List<GKInstance> references = control.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
            if (references == null || references.size() == 0)
                continue;
            // Check if this regulation is used in more than one RLE
            Collection<GKInstance> rles = control.getReferers(rleAttributeName);
            if (rles == null || rles.size() == 0)
                continue;
            if (rles.size() > 1 && references.size() > 1) {
                System.out.println("Referred by more than 1 RLE and having more than 1 reference: " + control);
                // In this case, we also want to check if RLE instances have covered all Regulation's references
                boolean notBeHandled = false;
                for (GKInstance rle : rles) {
                    List<GKInstance> rleReferences = rle.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
                    if (rleReferences.containsAll(references))
                        continue;
                    notBeHandled = true;
                    break;
                }
                if (notBeHandled)
                    notToBeHandled.add(control);
                else
                    toBeHandled.add(control);
            }
            else
                toBeHandled.add(control);
        }
        System.out.println("\nTotal not to be handled: " + notToBeHandled.size());
        // Get a nice output
        StringBuilder builder = new StringBuilder();
        System.out.println("DB_ID\tDisplayName\tClassName\tTotalReferences\tTotalReferringRLEs\tLastIE");
        for (GKInstance control : notToBeHandled) {
            List<GKInstance> references = control.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
            Collection<GKInstance> rles = control.getReferers(rleAttributeName);
            GKInstance lastIE = QACheckUtilities.getLatestCuratorIEFromInstance(control);
            builder.append(control.getDBID() + "\t" +
                           control.getDisplayName() + "\t" + 
                           control.getSchemClass().getName() + "\t" +
                           references.size() + "\t" + 
                           rles.size() + "\t" + 
                           lastIE.getDisplayName());
            System.out.println(builder.toString());
            builder.setLength(0);
        }
        return toBeHandled;
    }
    
    private void handleRegulationReferences(Set<GKInstance> regulations,
                                            MySQLAdaptor dba) throws Exception {
        if (dba.supportsTransactions())
            dba.startTransaction();
        GKInstance defaultIE = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
        System.out.println("Total to be handled: " + regulations.size());
        Set<GKInstance> toBeUpdatedRLEs = new HashSet<>();
        System.out.println("\nStarting to handling Regulations: ");
        for (GKInstance regulation : regulations) {
            System.out.println("Handling " + regulation + "...");
            // Create a RegulationReference
            GKInstance regulationReference = createInstance(ReactomeJavaConstants.RegulationReference,
                                                            defaultIE,
                                                            dba);
            regulationReference.setAttributeValue(ReactomeJavaConstants.regulation, regulation);
            List<GKInstance> references = regulation.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
            regulationReference.setAttributeValue(ReactomeJavaConstants.literatureReference,
                                                  new ArrayList<>(references));
            Collection<GKInstance> rles = regulation.getReferers(ReactomeJavaConstants.regulatedBy);
            for (GKInstance rle : rles) {
                rle.addAttributeValue(ReactomeJavaConstants.regulationReference, regulationReference);
                toBeUpdatedRLEs.add(rle);
            }
            InstanceDisplayNameGenerator.setDisplayName(regulationReference);
            dba.storeInstance(regulationReference);
        }
        System.out.println("\nStarting to updating ReactionlikeEvents: ");
        for (GKInstance rle : toBeUpdatedRLEs) {
            System.out.println("Updating " + rle + "...");
            dba.updateInstanceAttribute(rle, ReactomeJavaConstants.regulationReference);
            ScriptUtilities.addIEToModified(rle, defaultIE, dba);
        }
        if (dba.supportsTransactions())
            dba.commit();
    }
    
    private GKInstance createInstance(String clsName,
                                      GKInstance defaultIE,
                                      MySQLAdaptor dba) throws Exception {
        GKInstance instance = new GKInstance();
        instance.setDbAdaptor(dba);
        // Should not call fetchSchema(). Otherwise, a new schema will be created.
        SchemaClass cls = dba.getSchema().getClassByName(clsName);
        instance.setSchemaClass(cls);
        instance.setAttributeValue(ReactomeJavaConstants.created, defaultIE);
        return instance;
    }
    
    @Test
    public void checkRegulationSummtationTextForMerge() throws Exception {
        MySQLAdaptor dba = getDBA();
        // Fetch all human RLEs
        Collection<GKInstance> humanRLEs = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                                                                        ReactomeJavaConstants.species,
                                                                        "=",
                                                                        ScriptUtilities.getHomoSapiens(dba));
        dba.loadInstanceAttributeValues(humanRLEs, new String[] {
                ReactomeJavaConstants.regulatedBy,
                ReactomeJavaConstants.inferredFrom,
                ReactomeJavaConstants.summation
        });
        // Want to get inferredFrom too
        Set<GKInstance> totalRLEs = new HashSet<>();
        for (GKInstance humanRLE : humanRLEs) {
            GKInstance inferredFrom = (GKInstance) humanRLE.getAttributeValue(ReactomeJavaConstants.inferredFrom);
            if (inferredFrom != null)
                totalRLEs.add(inferredFrom);
        }
        System.out.println("Total RLEs for inference: " + totalRLEs.size());
        Set<GKInstance> inferredFromRLEs = new HashSet<>(totalRLEs);
        totalRLEs.addAll(humanRLEs);
        
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.Regulation);
        dba.loadInstanceAttributeValues(regulations, new String[] {
                ReactomeJavaConstants.summation
        });
        
        Set<GKInstance> selectedRegulations = new HashSet<>();
        for (GKInstance rle : humanRLEs) {
            List<GKInstance> rleRegulations = rle.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
            for (GKInstance rleRegulation : rleRegulations) {
                GKInstance summation = (GKInstance) rleRegulation.getAttributeValue(ReactomeJavaConstants.summation);
                if (summation == null)
                    continue;
                String text = (String) summation.getAttributeValue(ReactomeJavaConstants.text);
                if (text == null)
                    continue;
                selectedRegulations.add(rleRegulation);
            }
        }
        System.out.println("Total regulations to be exported: " + selectedRegulations.size());
        
        FileUtilities fu = new FileUtilities();
        String out = "RLERegulationSummationText_040721.txt";
        fu.setOutput(out);
        fu.printLine("RLE_DB_ID\t" + 
                     "LastIE\t" + 
                     "IEWithCurrentCurator\t" + 
                     "RLE_DisplayName\t" + 
                     "UsedForInferrence\t" +  // This is a new column
                     "Regulation_DB_ID\t" + 
                     "Regulation_DisplayName\t" + 
                     "RLE_Summation\t" + 
                     "RLE_Summation_Number\t" + 
                     "RLE_Summation_Text\t" + 
                     "Regulation_Summation\t" + 
                     "Regulation_Summation_Number\t" + 
                     "Regulation_Summation_Text");
        
        StringBuilder builder = new StringBuilder();
        for (GKInstance regulation : selectedRegulations) {
            Collection<GKInstance> rles = regulation.getReferers(ReactomeJavaConstants.regulatedBy);
            for (GKInstance rle : rles) {
                builder.append(rle.getDBID());
                GKInstance lastIE = ScriptUtilities.getAuthor(rle);
                builder.append("\t").append(lastIE.getDisplayName());
                lastIE = getCurrentAuthor(rle);
                builder.append("\t").append(lastIE == null ? "" : lastIE.getDisplayName());
                builder.append("\t").append(rle.getDisplayName());
                builder.append("\t").append(inferredFromRLEs.contains(rle));
                builder.append("\t").append(regulation.getDBID());
                builder.append("\t").append(regulation.getDisplayName());
                appendSummations(builder, rle);
                appendSummations(builder, regulation);
                fu.printLine(builder.toString());
                builder.setLength(0);
            }
        }
        fu.close();
    }
    
    private GKInstance getCurrentAuthor(GKInstance inst) throws Exception {
        String[] alumni = {
            "de Bono",
            "Duenas",
            "Garapati",
            "Gopinathrao",
            "Guerreiro",
            "Joshi-Tope",
            "Jupe",
            "Mahajan",
            "Murillo",
            "Schmidt",
            "Vastrik",
            "Wu" // Add myself
        };
        Set<String> escaped = Stream.of(alumni).collect(Collectors.toSet());
        List<GKInstance> list = inst.getAttributeValuesList(ReactomeJavaConstants.modified);
        if (list != null) {
            for (GKInstance ie : list) {
                String name = ie.getDisplayName().split(",")[0]; // Get the last name
                if (escaped.contains(name))
                    continue;
                return ie;
            }
        }
        GKInstance ie = (GKInstance) inst.getAttributeValue(ReactomeJavaConstants.created);
        String name = ie.getDisplayName().split(",")[0];
        if (escaped.contains(name))
            return null;
        return ie;
    }

    private void appendSummations(StringBuilder builder, GKInstance rle) throws InvalidAttributeException, Exception {
        List<GKInstance> rleSummations = rle.getAttributeValuesList(ReactomeJavaConstants.summation);
        String summationIds = rleSummations.stream().map(i -> i.getDBID() + "").collect(Collectors.joining("|"));
        builder.append("\t").append(summationIds);
        builder.append("\t").append(rleSummations.size());
        String summationText = "";
        for (GKInstance summation : rleSummations) {
            String text = (String) summation.getAttributeValue(ReactomeJavaConstants.text);
            if (summationText.length() > 0)
                summationText += "|";
            summationText += text;
        }
        summationText = summationText.replaceAll("\n", "<br>");
        summationText = summationText.replaceAll("\t", " "); // In case
        builder.append("\t").append(summationText);
    }
    
    
    /**
     * This method is different from checkRegulationSummation() in that it focues on Regulation instances.
     * @throws Exception
     */
    @Test
    public void dumpRegulationWithSummation() throws Exception {
        MySQLAdaptor dba = getDBA();
        Collection<GKInstance> regulations = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Regulation,
                                                                          ReactomeJavaConstants.summation,
                                                                          "!=",
                                                                          "null");
        System.out.println("Total regulations: " + regulations.size());
        String fileName = "RegulationWithSummation_061121.txt";
        FileUtilities fu = new FileUtilities();
        fu.setOutput(fileName);
        fu.printLine(
                    "Regulation_ID\t" + 
                    "Regulation_DisplayName\t" + 
                    "Regulation_LastIE\t" + 
                    "Regulation_Summation\t" + 
                    "Regulation_Summation_Number\t" + 
                    "Regulation_Summation_Text\t" + 
                    "RLE_ID\t" + 
                    "RLE_DisplayName\t" + 
                    "RLE_Species\t" +
                    "RLE_Summation\t" + 
                    "RLE_Summation_Number\t" + 
                    "RLE_Summation_Text\t" + 
                    "Inferred_RLE_DB_ID\t" + 
                    "Inferred_RLE_DisplayName\t" + 
                    "Inferred_RLE_Species\t" + 
                    "Inferred_RLE_Summation\t" +
                    "Inferred_RLE_Number\t" + 
                    "Inferred_RLE_Summation_Text"
                    );
        StringBuilder builder = new StringBuilder();
        for (GKInstance regulation: regulations) {
            GKInstance lastIE = ScriptUtilities.getAuthor(regulation);
            Collection<GKInstance> rles = regulation.getReferers(ReactomeJavaConstants.regulatedBy);
            if (rles == null || rles.size() == 0) {
                builder.append(regulation.getDBID()).append("\t");
                builder.append(regulation.getDisplayName()).append("\t");
                builder.append(lastIE.getDisplayName());
                appendSummations(builder, regulation);
                fu.printLine(builder.toString());
                builder.setLength(0);
                continue;
            }
            for (GKInstance rle : rles) {
                Collection<GKInstance> inferredRLEs = rle.getReferers(ReactomeJavaConstants.inferredFrom);
                if (inferredRLEs == null || inferredRLEs.size() == 0) {
                    builder.append(regulation.getDBID()).append("\t");
                    builder.append(regulation.getDisplayName()).append("\t");
                    builder.append(lastIE.getDisplayName());
                    appendSummations(builder, regulation);
                    List<GKInstance> species = rle.getAttributeValuesList(ReactomeJavaConstants.species);
                    String speciesText = species.stream().map(s -> s.getDisplayName()).collect(Collectors.joining(","));
                    builder.append("\t");
                    builder.append(rle.getDBID()).append("\t");
                    builder.append(rle.getDisplayName()).append("\t");
                    builder.append(speciesText);
                    appendSummations(builder, rle);
                    fu.printLine(builder.toString());
                    builder.setLength(0);
                    continue;
                }
                for (GKInstance infRLE : inferredRLEs) {
                    builder.append(regulation.getDBID()).append("\t");
                    builder.append(regulation.getDisplayName()).append("\t");
                    builder.append(lastIE.getDisplayName());
                    appendSummations(builder, regulation);
                    List<GKInstance> species = rle.getAttributeValuesList(ReactomeJavaConstants.species);
                    String speciesText = species.stream().map(s -> s.getDisplayName()).collect(Collectors.joining(","));
                    builder.append("\t");
                    builder.append(rle.getDBID()).append("\t");
                    builder.append(rle.getDisplayName()).append("\t");
                    builder.append(speciesText);
                    appendSummations(builder, rle);
                    builder.append("\t");
                    builder.append(infRLE.getDBID()).append("\t");
                    builder.append(infRLE.getDisplayName());
                    species = infRLE.getAttributeValuesList(ReactomeJavaConstants.species);
                    speciesText = species.stream().map(s -> s.getDisplayName()).collect(Collectors.joining(","));
                    builder.append("\t").append(speciesText);
                    appendSummations(builder, infRLE);
                    fu.printLine(builder.toString());
                    builder.setLength(0);
                }
            }
        }
        fu.close();
    }
    
    /**
     * This should be the last step to remove summation from Regulation.
     * @throws Exception
     */
    @Test
    public void deleteRegulationSummationForAll() throws Exception {
    	MySQLAdaptor dba = getDBA();
    	// Get Regulations having summations
    	Collection<GKInstance> regulations = dba.fetchInstanceByAttribute(ReactomeJavaConstants.Regulation,
    			ReactomeJavaConstants.summation,
    			"is not null",
    			null);
    	System.out.println("Total Regulations having summation: " + regulations.size());
    	try {
    		// Check if the summation is used elseSystem.out.println("Starting updating Regulations...");
    		System.out.println("Starting to remove summations from regulations and then delete summations that are not used else...");
    		dba.startTransaction();
    		GKInstance ie = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
    		for (GKInstance regulation : regulations) {
    			System.out.println("Handling " + regulation + "...");
    			List<GKInstance> summations = regulation.getAttributeValuesList(ReactomeJavaConstants.summation);
    			if (summations.size() > 1) {
    				System.out.println("More than one summation: " + summations);
    			}
    			// In case we have more than one summation.
    			Map<GKInstance, Boolean> summation2deletion = new HashMap<>();
    			for (GKInstance summation : summations) {
    				// Can be used in the summation attribute in other classes
    				Collection<GKInstance> referrers = summation.getReferers(ReactomeJavaConstants.summation);
    				referrers.remove(regulation);
    				if (referrers.size() > 0) {
    					System.out.println("More than one referrer: " + regulation + " -> " + summation);
    					summation2deletion.put(summation, Boolean.FALSE);
    				}
    				else {
    					summation2deletion.put(summation, Boolean.TRUE);
    				}
    			}
    			// Null summation
    			regulation.setAttributeValue(ReactomeJavaConstants.summation, null);
    			dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.summation);
    			// Update modified
                regulation.getAttributeValuesList(ReactomeJavaConstants.modified);
                regulation.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.modified);
    			for (GKInstance summation : summation2deletion.keySet()) {
    				Boolean deletion = summation2deletion.get(summation);
    				if (deletion) {
    					System.out.println("Deleting " + summation + "...");
    					dba.deleteInstance(summation); 
    				}
    			}
    		}
    		dba.commit();
    	}
    	catch(Exception e) {
    		dba.rollback();
    		e.printStackTrace();
    	}
    }
    
    @Test
    public void checkRegulationSummation() throws Exception {
        MySQLAdaptor dba = getDBA();
        // Fetch all human RLEs
        Collection<GKInstance> humanRLEs = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                ReactomeJavaConstants.species,
                "=",
                ScriptUtilities.getHomoSapiens(dba));
        dba.loadInstanceAttributeValues(humanRLEs, new String[] {
                ReactomeJavaConstants.regulatedBy,
                ReactomeJavaConstants.inferredFrom,
                ReactomeJavaConstants.summation
        });
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.Regulation);
        dba.loadInstanceAttributeValues(regulations, new String[] {
                ReactomeJavaConstants.summation
        });
        
        String output = "RLERegulationSummation.txt";
        FileUtilities fu = new FileUtilities();
        fu.setOutput(output);
        fu.printLine("RLE_DB_ID\tRLE_DisplayName\tRegulation_DB_ID\tRegulation_DisplayName\t" + 
                     "RLE_Summation\tRLE_Summation_Text\t" +
                     "Regulation_Summation\tRegulation_Summation_Text\t" +
                     "IsSummationSame\t" + 
                     "RLE_Summation_Literature\tRegulation_Summation_Literature\t" + 
                     "IsReferenceSame");
        StringBuilder builder = new StringBuilder();
        for (GKInstance humanRLE : humanRLEs) {
            List<GKInstance> summations = collectRLEValues(humanRLE, ReactomeJavaConstants.summation);
            List<GKInstance> rleRegulations = humanRLE.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
            for (GKInstance regulation : rleRegulations) {
                List<GKInstance> rSummations = regulation.getAttributeValuesList(ReactomeJavaConstants.summation);
                if (rSummations.size() == 0)
                    continue;
                if (summations.size() == 0) {
                    for (GKInstance rSummation : rSummations) {
                        builder.append(humanRLE.getDBID()).append("\t").append(humanRLE.getDisplayName());
                        builder.append("\t").append(regulation.getDBID()).append("\t").append(regulation.getDisplayName());
                        builder.append("\t").append("\t");
                        String text = (String) rSummation.getAttributeValue(ReactomeJavaConstants.text);
                        text = text.replaceAll("\n|\t", " ");
                        builder.append("\t").append(rSummation.getDBID()).append("\t").append(text);
                        builder.append("\t").append(Boolean.FALSE);
                        builder.append("\t");
                        builder.append("\t");
                        List<GKInstance> rReferences = rSummation.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
                        for (GKInstance lit : rReferences) {
                            builder.append(lit.getDBID()).append(";");
                        }
                        if (rReferences.size() > 0)
                            builder.deleteCharAt(builder.length() - 1);
                        builder.append("\t").append(rReferences.size() == 0);
                        fu.printLine(builder.toString());
                        builder.setLength(0);
                    }
                }
                else {
                    // For easy checking, we will permute all pair-wise
                    for (GKInstance rleSummation : summations) {
                        for (GKInstance rSummation : rSummations) {
                            builder.append(humanRLE.getDBID()).append("\t").append(humanRLE.getDisplayName());
                            builder.append("\t").append(regulation.getDBID()).append("\t").append(regulation.getDisplayName());
                            String text = (String) rleSummation.getAttributeValue(ReactomeJavaConstants.text);
                            text = text.replaceAll("\n|\t", " ");
                            builder.append("\t").append(rleSummation.getDBID()).append("\t").append(text);
                            text = (String) rSummation.getAttributeValue(ReactomeJavaConstants.text);
                            text = text.replaceAll("\n|\t", " ");
                            builder.append("\t").append(rSummation.getDBID()).append("\t").append(text);
                            builder.append("\t").append(rleSummation == rSummation);
                            List<GKInstance> literatures = rleSummation.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
                            builder.append("\t");
                            for (GKInstance lit : literatures)
                                builder.append(lit.getDBID()).append(";");
                            if (literatures.size() > 0) // Need to delete the last extra character
                                builder.deleteCharAt(builder.length() - 1);
                            builder.append("\t");
                            List<GKInstance> rReferences = rSummation.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
                            for (GKInstance lit : rReferences) {
                                builder.append(lit.getDBID()).append(";");
                            }
                            if (rReferences.size() > 0)
                                builder.deleteCharAt(builder.length() - 1);
                            InstanceUtilities.sortInstances(literatures);
                            InstanceUtilities.sortInstances(rReferences);
                            builder.append("\t").append(literatures.equals(rReferences));
                            fu.printLine(builder.toString());
                            builder.setLength(0);
                        }
                    }
                }
            }
        }
        fu.close();
    }
    
    private List<GKInstance> collectRLEValues(GKInstance rle, String attribute) throws Exception {
        List<GKInstance> summations = rle.getAttributeValuesList(attribute);
        Set<GKInstance> set = new HashSet<>(summations);
        List<GKInstance> inferredFrom = rle.getAttributeValuesList(ReactomeJavaConstants.inferredFrom);
        for (GKInstance inst : inferredFrom) {
            summations = inst.getAttributeValuesList(attribute);
            set.addAll(summations);
        }
        List<GKInstance> list = new ArrayList<>(set);
        InstanceUtilities.sortInstances(list);
        return list;
    }
    
    @Test
    public void checkRegulationLiteratureReference() throws Exception {
        MySQLAdaptor dba = getDBA();
        // Fetch all human RLEs
        Collection<GKInstance> humanRLEs = dba.fetchInstanceByAttribute(ReactomeJavaConstants.ReactionlikeEvent,
                ReactomeJavaConstants.species,
                "=",
                ScriptUtilities.getHomoSapiens(dba));
        dba.loadInstanceAttributeValues(humanRLEs, new String[] {
                ReactomeJavaConstants.regulatedBy,
                ReactomeJavaConstants.inferredFrom,
                ReactomeJavaConstants.literatureReference
        });
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.Regulation);
        dba.loadInstanceAttributeValues(regulations, new String[] {
                ReactomeJavaConstants.literatureReference
        });
        
        String output = "RLERegulationReference.txt";
        FileUtilities fu = new FileUtilities();
        fu.setOutput(output);
        fu.printLine("RLE_DB_ID\tRLE_DisplayName\tRegulation_DB_ID\tRegulation_DisplayName\t" + 
                     "RLE_Literatures\tRegulation_Literatures\t" + 
                     "Shared\tRLE_NotShared\tRegulation_NotShared");
        StringBuilder builder = new StringBuilder();
        for (GKInstance humanRLE : humanRLEs) {
            List<GKInstance> literatures = collectRLEValues(humanRLE, ReactomeJavaConstants.literatureReference);
            List<GKInstance> rleRegulations = humanRLE.getAttributeValuesList(ReactomeJavaConstants.regulatedBy);
            for (GKInstance regulation : rleRegulations) {
                List<GKInstance> rReferences = regulation.getAttributeValuesList(ReactomeJavaConstants.literatureReference);
                if (rReferences == null || rReferences.size() == 0)
                    continue;
                builder.append(humanRLE.getDBID()).append("\t").append(humanRLE.getDisplayName());
                builder.append("\t").append(regulation.getDBID()).append("\t").append(regulation.getDisplayName());
                builder.append("\t");
                // Need to consider if there is no references in an RLE.
                for (GKInstance lit : literatures)
                    builder.append(lit.getDBID()).append(";");
                if (literatures.size() > 0) // Need to delete the last extra character
                    builder.deleteCharAt(builder.length() - 1);
                builder.append("\t");
                for (GKInstance lit : rReferences) {
                    builder.append(lit.getDBID()).append(";");
                }
                builder.deleteCharAt(builder.length() - 1);
                List<GKInstance> shared = getShared(literatures, rReferences);
                builder.append("\t").append(shared.size());
                List<GKInstance> copy = new ArrayList<>(literatures);
                copy.removeAll(shared);
                builder.append("\t").append(copy.size());
                copy = new ArrayList<>(rReferences);
                copy.removeAll(shared);
                builder.append("\t").append(copy.size());
                fu.printLine(builder.toString());
                builder.setLength(0);
            }
        }
        fu.close();
    }
    
    private List<GKInstance> getShared(List<GKInstance> list1, List<GKInstance> list2) {
        List<GKInstance> shared = new ArrayList<>(list1);
        shared.retainAll(list2);
        return shared;
    }
    
    private MySQLAdaptor getDBA() throws Exception {
        MySQLAdaptor dba = new MySQLAdaptor("localhost",
                "gk_central_061121",
                "root",
                "macmysql01");
        return dba;
    }
    
    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("Provide four parameters: dbHost, dbName, dbUser, dbPwd, and operation (one of CheckErrors, CheckWarnings, Migration, MigrateSummation, UpdateDisplayNames, and DeleteNotReleasedStableIds)");
            System.exit(1);
        }
        MySQLAdaptor dba = new MySQLAdaptor(args[0], args[1], args[2], args[3]);
        RegulationMigration regulationMigration = new RegulationMigration();
        Collection<GKInstance> regulations = dba.fetchInstancesByClass(ReactomeJavaConstants.Regulation);
        if (args[4].equals("CheckErrors")) {
            regulationMigration.checkErrors(regulations);
        }
        else if (args[4].equals("CheckWarnings")) {
            regulationMigration.checkWarnings(regulations);
        }
        else if (args[4].equals("Migration")) {
            if (regulationMigration.checkErrors(regulations))
                return;
            // Since they are warnings, the migration can still go ahead
            regulationMigration.checkWarnings(regulations);
            regulationMigration.migrate(dba, regulations);    
        }
        else if (args[4].equals("MigrateSummation")) {
            regulationMigration.migrateSummation(dba);
        }
        else if (args[4].equals("UpdateDisplayNames")) {
            regulationMigration.updateDisplayNames(regulations, dba);
        }
        else if (args[4].equals("DeleteNotReleasedStableIds")) {
            regulationMigration.deleteNotReleasedStableIds(regulations, dba);
        }
    }
    
    /**
     * This method is used to migrate regulation Summation text to RLEs.
     * @throws Exception
     * TODO: There is a bug in this method: need to update display names of
     * Summation instances with text reset.
     */
    public void migrateSummation(MySQLAdaptor dba) throws Exception {
        // Make sure the list file exists
        String name = "RLE_Regulation_Summation_Text_Review.tsv";
        name = "Regulations_with_summations_on_non-human_RLEs_Plant_instances.tsv";
        File file = new File(name);
        if (!file.exists())
            throw new IllegalStateException("File doesn't exist: " + name);
        FileUtilities fu = new FileUtilities();
        fu.setInput(name);
        String line = fu.readLine(); // Skip the header line
        Set<GKInstance> toBeDeletedSummations = new HashSet<>();
        Set<GKInstance> toBeUpdateRegulations = new HashSet<>();
        Set<GKInstance> toBeUpdateRLEs = new HashSet<>();
        Set<GKInstance> toBeUpdateSummations = new HashSet<>();
        // A set of column indices to be used
        // These are for RLE_Regulation_Summation_Text_Review.tsv
//        int actionCol = 13;
//        int rleDBIdCol = 0;
//        int regulationDBIDCol = 5;
//        int regSummationDBIdCol = 10;
//        int newRegSumTextCol = 12;
//        int originalRegSumTextCol = 11;
//        int rleSummationCol = 7;
        // These are for rice instances
        int actionCol = 14;
        int rleDBIdCol = 6;
        int regulationDBIDCol = 0;
        int regSummationDBIdCol = 3;
        int newRegSumTextCol = 13;
        int originalRegSumTextCol = 5;
        int rleSummationCol = 9;
        
        while ((line = fu.readLine()) != null) {
            String[] tokens = line.split("\t");
            if (tokens[regSummationDBIdCol].contains("|"))
            	continue; // Escape if there is more than one DB_ID for handling
            String action = tokens[actionCol];
            GKInstance rle = dba.fetchInstance(new Long(tokens[rleDBIdCol]));
            GKInstance regulation = dba.fetchInstance(new Long(tokens[regulationDBIDCol]));
            GKInstance regSummation = dba.fetchInstance(new Long(tokens[regSummationDBIdCol]));
            // See if there is any new text
            String newRegSumText = tokens[newRegSumTextCol].trim().length() == 0 ? null : tokens[newRegSumTextCol].trim();
            String originalRegSumText = tokens[originalRegSumTextCol].trim();
            GKInstance rleSummation = tokens[rleSummationCol].trim().length() == 0 ? null : dba.fetchInstance(new Long(tokens[rleSummationCol]));
            if (action.equals("MigrateSummationInstance")) {
                // Don't forget to load all
                rle.getAttributeValuesList(ReactomeJavaConstants.summation);
                rle.addAttributeValue(ReactomeJavaConstants.summation, regSummation);
                toBeUpdateRLEs.add(rle);
                // See if the text should be updated
                if (newRegSumText != null) {
                    regSummation.setAttributeValue(ReactomeJavaConstants.text, newRegSumText);
                    toBeUpdateSummations.add(regSummation);
                }
            }
            else if (action.equals("MergeSummationText") || action.equals("MergeSummation")) {
                if (rleSummation == null)
                    throw new IllegalStateException(rle + " doesn't have any Summation. No way to merge summation text!");
                String rleSumText = (String) rleSummation.getAttributeValue(ReactomeJavaConstants.text);
                if (rleSumText == null)
                    throw new IllegalStateException(rleSummation + " doesn't have any text!");
                String newRleSumText = rleSumText.trim() + " " + 
                                       (newRegSumText == null ? originalRegSumText.trim() : newRegSumText.trim());
                rleSummation.setAttributeValue(ReactomeJavaConstants.text, newRleSumText);
                toBeUpdateSummations.add(rleSummation);
                // Delete the original Summation
                toBeDeletedSummations.add(regSummation);
            }
            if (action.equals("noAction") || action.equals("NoAction")) {
                // Delete the Summation instance for regulation
                toBeDeletedSummations.add(regSummation);
            }
            // Regardless this should be common
            List<GKInstance> summations = regulation.getAttributeValuesList(ReactomeJavaConstants.summation);
            summations.remove(regSummation);
            regulation.setAttributeValue(ReactomeJavaConstants.summation, summations);
            toBeUpdateRegulations.add(regulation);
        }
        fu.close();
        System.out.println("Summations to be deleted: " + toBeDeletedSummations.size());
        System.out.println("Regulations to be updated for summation: " + toBeUpdateRegulations.size());
        System.out.println("RLEs to be updated for summation: " + toBeUpdateRLEs.size());
        System.out.println("Summations to be updated for text: " + toBeUpdateSummations.size());
//        if (true)
//        	return;
        // Start to push to the database
        try {
            dba.startTransaction();
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
            // Don't forget to add IE
            System.out.println("Starting updating Summations for text...");
            int counter = 0;
            for (GKInstance summation : toBeUpdateSummations) {
                System.out.println("Updating " + summation + "...");
                dba.updateInstanceAttribute(summation, ReactomeJavaConstants.text);
                summation.getAttributeValuesList(ReactomeJavaConstants.modified);
                summation.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(summation, ReactomeJavaConstants.modified);
                // Check if _displayName needs to be updated
                String displayName = summation.getDisplayName();
                String newDisplayName = InstanceDisplayNameGenerator.generateDisplayName(summation);
                if (!displayName.equals(newDisplayName)) {
                	summation.setDisplayName(newDisplayName);
                	System.out.println("\tUpdate display name to " + newDisplayName);
                	dba.updateInstanceAttribute(summation, ReactomeJavaConstants._displayName);
                }
                counter ++;
            }
            System.out.println("Total: " + counter);
            counter = 0;
            System.out.println("Starting updating RLEs for summation...");
            for (GKInstance rle : toBeUpdateRLEs) {
                System.out.println("Updating " + rle + "...");
                dba.updateInstanceAttribute(rle, ReactomeJavaConstants.summation);
                rle.getAttributeValuesList(ReactomeJavaConstants.modified);
                rle.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(rle, ReactomeJavaConstants.modified);
                counter ++;
            }
            System.out.println("Total: " + counter);
            counter = 0;
            System.out.println("Starting updating Regulations for summation...");
            for (GKInstance regulation : toBeUpdateRegulations) {
                System.out.println("Updating " + regulation + "...");
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.summation);
                regulation.getAttributeValuesList(ReactomeJavaConstants.modified);
                regulation.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.modified);
                counter ++;
            }
            System.out.println("Total: " + counter);
            counter = 0;
            dba.commit();
            // If a Summation is not used any more, we will just delete it
            System.out.println("Starting deleting Summations that are not used...");
            for (GKInstance summation : toBeDeletedSummations) {
                Collection<GKInstance> referrers = summation.getReferers(ReactomeJavaConstants.summation);
                if (referrers!= null && referrers.size() > 0) {
                    System.out.println(summation + " is used.");
                    continue;
                }
                System.out.println("Deleting " + summation + "...");
                dba.deleteInstance(summation);
                counter ++;
            }
            System.out.println("Total: " + counter);
            dba.commit();
        }
        catch(Exception e) {
            System.err.println(e);
            e.printStackTrace();
            dba.rollback();
        }
    }
    
    /**
     * Delete StableIdentifiers that are used for Regulations but not released.
     * @param regulations
     * @param dba
     * @throws Exception
     */
    public void deleteNotReleasedStableIds(Collection<GKInstance> regulations,
                                           MySQLAdaptor dba) throws Exception {
        try {
            dba.startTransaction();
            int c = 0;
            // Don't forget to add IE
            System.out.println("Starting updating Regulations...");
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
            for (GKInstance regulation : regulations) {
                System.out.println("Checking " + regulation + "...");
                GKInstance stableId = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.stableIdentifier);
                if (stableId == null)
                    continue;
                Boolean released = (Boolean) stableId.getAttributeValue(ReactomeJavaConstants.released);
                if (released != null && released)
                    continue;
                System.out.println("Deleting " + stableId);
                // Delete stable id
                regulation.setAttributeValue(ReactomeJavaConstants.stableIdentifier, null);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.stableIdentifier);
                dba.deleteInstance(stableId);
                // Update modified
                regulation.getAttributeValuesList(ReactomeJavaConstants.modified);
                regulation.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.modified);
                c ++;
            }
            dba.commit();
            System.out.println("Total deletions: " + c);
        }
        catch(Exception e) {
            System.err.println(e);
            dba.rollback();
        }
    }
    
    /**
     * Update the display names for all Regulations based on regulators only.
     * @throws Exception
     */
    public void updateDisplayNames(Collection<GKInstance> regulations,
                                   MySQLAdaptor dba) throws Exception {
        try {
            dba.startTransaction();
            // Don't forget to add IE
            System.out.println("Starting updating Regulations...");
            int c = 0;
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
            for (GKInstance regulation : regulations) {
                String displayName = regulation.getDisplayName();
                String newName = InstanceDisplayNameGenerator.generateDisplayName(regulation);
                if (displayName.equals(newName))
                    continue;
                System.out.println("Updating " + regulation + "...");
                InstanceDisplayNameGenerator.setDisplayName(regulation);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants._displayName);
                regulation.getAttributeValuesList(ReactomeJavaConstants.modified);
                regulation.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.modified);
                c ++;
            }
            dba.commit();
            System.out.println("Total updated: " + c);
        }
        catch(Exception e) {
            System.err.println(e);
            dba.rollback();
        }
    }
    
    // As of April 9, the following check should pass for gk_central@reactomecurator.
    public void checkRegulationFromCasToRle(Collection<GKInstance> regulations) throws Exception {
        for (GKInstance regulation : regulations) {
            GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
            if (regulatedEntity == null || !regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.CatalystActivity)) {
//                System.out.println(regulation + " doesn't have regulatedEntity!");
                continue;
            }
//            System.out.println("Checking " + regulation);
            // Get the target RLE
            Collection<GKInstance> c = regulatedEntity.getReferers(ReactomeJavaConstants.catalystActivity);
            if (c == null || c.size() == 0) {
                throw new IllegalStateException(regulatedEntity + " is not used!");
            }
            if (c.size() > 1) {
                System.out.println(regulation + ": " + regulatedEntity + " is used in more than one RLE!");
            }
            for (GKInstance rle : c) {
                if (!rle.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                    throw new IllegalStateException(regulatedEntity + " is used for non-RLE event: " + rle);
                }
            }
        }
    }
    
    public void migrate(MySQLAdaptor dba,
                        Collection<GKInstance> regulations) throws Exception {
        try {
            // Sort Regulations based on DB_IDs so that they can be added based on DB_IDs
            List<GKInstance> regulationList = new ArrayList<>(regulations);
            regulationList.sort((reg1, reg2) -> reg1.getDBID().compareTo(reg2.getDBID()));
            Set<GKInstance> reactions = new HashSet<>(); // To be updated
            // These regulations need to update their _displayName since their targets are updated
            Set<GKInstance> regulationsToBeUpdated = new HashSet<>();
            for (GKInstance regulation : regulationList) {
                GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
                if (regulatedEntity == null)
                    continue; // Do nothing
                if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.Pathway)) {
                    System.err.println(regulation + " has a pathway as its regulatedEntity: " + regulatedEntity);
                    continue;
                }
                boolean needRegulationUpdate = false;
                Set<GKInstance> targets = new HashSet<>();
                if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent)) {
                    targets.add(regulatedEntity);
                }
                else if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.CatalystActivity)) {
                    Collection<GKInstance> referrers = regulatedEntity.getReferers(ReactomeJavaConstants.catalystActivity);
                    // Just in case this CAS is reused. Usually it should not be true
                    if (referrers != null && referrers.size() > 0)
                        targets.addAll(referrers);
                    needRegulationUpdate = true;
                }
                if (targets.size() == 0) {
                    System.err.println("Cannot find a target for " + regulation);
                    continue; 
                }
                if (targets.size() > 1) {
                    System.out.println(regulation + " will be assigned to multiple ReactionlikeEvent instances!");
                }
                // Another check
                for (GKInstance target : targets) {
                    if (target.getSchemClass().isValidAttribute(ReactomeJavaConstants.regulatedBy)) {
                        target.addAttributeValue(ReactomeJavaConstants.regulatedBy, regulation);
                        reactions.add(target);
                    }
                }
                if (needRegulationUpdate) {
                    regulationsToBeUpdated.add(regulation);
                }
            }
            dba.startTransaction();
            // Don't forget to add IE
            System.out.println("Starting updating ReactionlikeEvent...");
            GKInstance ie = ScriptUtilities.createDefaultIE(dba, ScriptUtilities.GUANMING_WU_DB_ID, true);
            for (GKInstance reaction : reactions) {
                System.out.println("Updating " + reaction + "...");
                dba.updateInstanceAttribute(reaction, ReactomeJavaConstants.regulatedBy);
                reaction.getAttributeValuesList(ReactomeJavaConstants.modified);
                reaction.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(reaction, ReactomeJavaConstants.modified);
            }
            dba.commit();
            dba.startTransaction();
            System.out.println("Starting updating Regulation...");
            // To get the referrers, the changes for regulatedBy should be in the database first
            for (GKInstance regulation : regulationsToBeUpdated) {
                System.out.println("Updating " + regulation + "...");
                InstanceDisplayNameGenerator.setDisplayName(regulation);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants._displayName);
                regulation.getAttributeValuesList(ReactomeJavaConstants.modified);
                regulation.addAttributeValue(ReactomeJavaConstants.modified, ie);
                dba.updateInstanceAttribute(regulation, ReactomeJavaConstants.modified);
            }
            dba.commit();
        }
        catch(Exception e) {
            System.err.println(e);
            dba.rollback();
        }
    }
    
    public boolean checkWarnings(Collection<GKInstance> regulations) throws Exception {
        List<GKInstance> reactions = checkContainedInPathway(regulations);
        if (reactions.size() > 0) {
            System.out.println("ReactionlikeEvent instances are listed in more than one Pathways' hasEvent slot and regulated by Regulations with containedInPathway != null: " + reactions.size());
            System.out.println("DB_ID\tDisplayName\tCurator\tPathways");
            StringBuilder builder = new StringBuilder();
            for (GKInstance reaction : reactions) {
                Collection<GKInstance> pathways = reaction.getReferers(ReactomeJavaConstants.hasEvent);
                builder.append(reaction.getDBID()).append("\t");
                builder.append(reaction.getDisplayName()).append("\t");
                GKInstance ie = InstanceUtilities.getLatestIEFromInstance(reaction);
                GKInstance author = (GKInstance) ie.getAttributeValue(ReactomeJavaConstants.author);
                builder.append(author.getDisplayName()).append("\t");
                for (GKInstance pathway : pathways)
                    builder.append(pathway).append("\t");
                builder.delete(builder.length() - 1, builder.length());
                System.out.println(builder.toString());
                builder.setLength(0);
            }
            return true;
        }
        return false;
    }
    
    /**
     * If a ReactionlikeEvent has been annotated for several pathways, it may be regulated in some of them,
     * but not others. This check picks up ReactionlikeEvents, which have been annotated in regulatedEntity
     * for Regulation instances whose containedInPathway are not null, for curators to make manual edits.
     * @param regulations
     * @throws Exception
     */
    public List<GKInstance> checkContainedInPathway(Collection<GKInstance> regulations) throws Exception {
        Set<GKInstance> reactions = new HashSet<>();
        for (GKInstance regulation : regulations) {
            GKInstance pathway = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.containedInPathway);
            if (pathway == null)
                continue;
            GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
            if (regulatedEntity == null || !regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent))
                continue;
            Collection<GKInstance> pathways = regulatedEntity.getReferers(ReactomeJavaConstants.hasEvent);
            if (pathways == null || pathways.size() == 0)
                continue;
            if (pathways.size() > 1) {
                reactions.add(regulatedEntity);
            }
        }
        return new ArrayList<>(reactions);
    }
    
    /**
     * Make sure regulatedType is ReactionlikeEvent since we will migrate Regulation
     * to ReactionlikeEvent only.
     * @return a list of Regulation having Pathways as regulatedEntity.
     * @throws Exception
     */
    public List<GKInstance> checkRegulatedType(Collection<GKInstance> regulations) throws Exception {
        List<GKInstance> rtn = new ArrayList<>();
        for (GKInstance regulation : regulations) {
            GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
            if (regulatedEntity == null) {
                continue;
                // This should be fine since we will keep Regulation instances as they are
//                throw new IllegalStateException(regulation + " has null regulatedEntity!");
            }
            if (!regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.ReactionlikeEvent) &&
                !regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.CatalystActivity)) {
                rtn.add(regulation);
            }
        }
        return rtn;
    }
    
    public boolean checkErrors(Collection<GKInstance> regulations) throws Exception {
        List<GKInstance> wrongRegulatedEntity = checkRegulatedType(regulations);
        boolean hasPathwayAsRegulatedEntity = false;
        if (wrongRegulatedEntity.size() > 0) {
            System.err.println("Regulation instances have regulatedEntity values are not ReactionlikeEvent or CatalystActivity: " + wrongRegulatedEntity.size());
            InstanceUtilities.sortInstances(wrongRegulatedEntity);
            System.out.println("DBID\tDisplayName\tRegulatedEntity_DBID\tRegulatedEntity_Class");
            for (GKInstance regulation : wrongRegulatedEntity) {
                GKInstance regulatedEntity = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulatedEntity);
                System.out.println(regulation.getDBID() + "\t" + 
                                   regulation.getDisplayName() + "\t" + 
                                   regulatedEntity.getDBID() + "\t" + 
                                   regulatedEntity.getSchemClass().getName());
                if (regulatedEntity.getSchemClass().isa(ReactomeJavaConstants.Pathway))
                    hasPathwayAsRegulatedEntity = true;
            }
        }
        checkRegulationFromCasToRle(regulations);
        if (hasPathwayAsRegulatedEntity)
            return true;
        return false;
    }

}
