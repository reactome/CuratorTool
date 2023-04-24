/*
 * Created on Jan 13, 2004
 */
package org.gk.model;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.gk.schema.GKSchemaClass;
import org.gk.schema.InvalidAttributeException;
import org.gk.schema.SchemaAttribute;
import org.gk.schema.SchemaClass;
import org.gk.util.StringUtils;

/**
 * A utility class to generate the display name for the GKInstance object. This class 
 * is base on Imre's perl module GKB::NamedInstance.
 * @author wugm
 */
@SuppressWarnings({"unchecked", "rawtypes"})
public class InstanceDisplayNameGenerator {
    
    /**
     * Set the display name for GKInstance object.
     * @param instance Instance for which to set the display name
     */
    public static void setDisplayName(GKInstance instance) {
        instance.setDisplayName(generateDisplayName(instance));
    }
    
	/**
	 * Generate a display name for a specified GKInstance object.
	 * @param instance Instance for which to generate a display name
	 * @return display name
	 */
    public static String generateDisplayName(GKInstance instance) {
        GKSchemaClass schemaClass = (GKSchemaClass) instance.getSchemClass();
        try {
            String clsName = schemaClass.getName();
            if (clsName.equals("ModifiedResidue"))	
                return generateModifiedResidueName(instance);
            if (clsName.equals(ReactomeJavaConstants.NonsenseMutation))
                return generateNonsenseMutationName(instance);
            if (clsName.equals("ReplacedResidue"))
                return generateReplacedResidueName(instance);
            if (clsName.equals(ReactomeJavaConstants.ModifiedNucleotide))
                return generateModifiedNucleotideName(instance);
            if (clsName.equals(ReactomeJavaConstants.GroupModifiedResidue))
                return generateGroupModifiedResidueName(instance);
            if (clsName.equals(ReactomeJavaConstants.FragmentDeletionModification))
                return generateFragmentModificationName(instance, "Deletion");
            if (clsName.equals(ReactomeJavaConstants.FragmentInsertionModification))
                return generateFragmentModificationName(instance, "Insertion");
            if (clsName.equals(ReactomeJavaConstants.FragmentReplacedModification))
                return generateFragmentModificationName(instance, "Replacement");
            if (schemaClass.isa(ReactomeJavaConstants.CrosslinkedResidue))
                return generateCrosslinkedResidueName(instance);
            if (clsName.equals("DatabaseIdentifier"))
                return generateDatabaseIdentifierName(instance);
            if (clsName.equals("ReferenceGroup"))
                return generateReferenceGroupName(instance);
            if (clsName.equals("ReferenceMolecule") || 
                clsName.equals(ReactomeJavaConstants.ReferenceTherapeutic))
                return generateReferenceMoleculeName(instance);
            if (schemaClass.isa("ReferenceSequence")) {
                return generateReferenceSequenceName(instance);
            }
            if (clsName.equals("CatalystActivity"))
                return generateCatalystActivityName(instance);
            if (clsName.equals("LiteratureReference"))
                return generateLiteratureReferenceName(instance);
            
            // The following is for Regulation
            if (schemaClass.isa(ReactomeJavaConstants.Regulation))
                return generateRegulationName(instance);
            
            if (clsName.equals("Figure"))
                return generateFigureName(instance);
            if (clsName.equals("Summation"))
                return generateSummationName(instance);
            if (clsName.equals("Person"))
                return generatePersonName(instance);
            if (clsName.equals("InstanceEdit"))
                return generateInstanceEditName(instance);
            if (schemaClass.isa("PhysicalEntity"))
                return generateEntityName(instance);
            if (schemaClass.isa("Reaction"))
                return generateReactionName(instance);
            if (schemaClass.isa(ReactomeJavaConstants.PathwayDiagram))
                return generatePathwayDiagramName(instance);
            if (schemaClass.isa(ReactomeJavaConstants.TargettedInteraction))
                return generateTargettedInteractionName(instance);
            if (schemaClass.isa(ReactomeJavaConstants.FunctionalStatus)) {
                return generateFunctionalStatusName(instance);
            }
            if (schemaClass.isa(ReactomeJavaConstants.StableIdentifier)) {
                return generateStableIdentifierName(instance);
            }
            if (schemaClass.isa(ReactomeJavaConstants.EntityFunctionalStatus)){
                return generateEntityFunctionalStatusName(instance);
            }
            if (schemaClass.isa(ReactomeJavaConstants.ControlReference)) {
                return generateControlReferenceName(instance);
            }
            if (schemaClass.isa(ReactomeJavaConstants._UpdateTracker)) {
                return generateUpdateTrackerName(instance);
            }
            if (schemaClass.isa(ReactomeJavaConstants._Release)) {
                return generateReleaseName(instance);
            }
            if (schemaClass.isa(ReactomeJavaConstants.NegativePrecedingEvent))
                return generateNegativePrecedingEventName(instance);
            if (schemaClass.isa(ReactomeJavaConstants._Deleted))
                return generateDeletedName(instance);
            if (schemaClass.isa(ReactomeJavaConstants._DeletedInstance))
                return generateDeletedInstanceName(instance);
            if (schemaClass.isValidAttribute("name")) {
                java.util.List list = instance.getAttributeValuesList("name");
                if (list != null && list.size() > 0) 
                    return (String)list.get(0);
            }
            Collection definingAttributes = schemaClass.getDefiningAttributes();
            if (definingAttributes != null && definingAttributes.size() > 0) {
                StringBuffer buffer = new StringBuffer();
                // Need to make sure the output is the same always. Therefore, we need an order
                // of these attributes
                List<SchemaAttribute> attributes = new ArrayList<>(definingAttributes);
                attributes.sort((att1, att2) -> att1.getName().compareTo(att2.getName()));
                for (Iterator it = attributes.iterator(); it.hasNext();) {
                    SchemaAttribute att = (SchemaAttribute) it.next();
                    java.util.List list = instance.getAttributeValuesList(att);
                    if (list != null && list.size() > 0) {
                        if (att.isInstanceTypeAttribute()) {
                            GKInstance tmp = (GKInstance) list.get(0);
                            if (tmp.getDisplayName() != null)
                                buffer.append(tmp.getDisplayName());
                        }
                        else {
                            buffer.append(list.get(0));
                        }
                    }
                    if (it.hasNext())
                        buffer.append(" ");
                }
                if (buffer.length() > 0)
                    return buffer.toString();
            }
        }
        catch (Exception e) {
            System.err.println("InstanceDisplayNameGenerator.generateDisplayName(): " + e);
            e.printStackTrace();
        }
        // Return an empty String for the name
        return "";
    }
    
    private static String generateUpdateTrackerName(GKInstance instance) throws InvalidAttributeException, Exception {
        GKInstance updatedEvent = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.updatedEvent);
        Long updatedEventDBID = updatedEvent.getDBID();
        return "Revision of instance: " + updatedEventDBID;
    }

    private static String generateReleaseName(GKInstance instance) throws InvalidAttributeException, Exception {
        Object releaseNumber = instance.getAttributeValue(ReactomeJavaConstants.releaseNumber);
        Object releaseDate = instance.getAttributeValue(ReactomeJavaConstants.releaseDate);
        return releaseNumber + " on " + releaseDate;
    }
    
    private static String generateDeletedName(GKInstance instance) throws InvalidAttributeException, Exception {
        List<?> deletedIds = instance.getAttributeValuesList(ReactomeJavaConstants.deletedInstanceDB_ID);
        String displayName = null;
        if (deletedIds == null || deletedIds.size() == 0)
            displayName = "Deletion of instance: unknown";
        else if (deletedIds.size() == 1)
            displayName = "Deletion of instance: " + StringUtils.join(", ", deletedIds);
        else
            displayName = "Deletion of instances: " + StringUtils.join(", ", deletedIds);
        return displayName;
    }
    
    private static String generateDeletedInstanceName(GKInstance instance) throws InvalidAttributeException, Exception {
        StringBuilder displayName = new StringBuilder();
        displayName.append("Deleted Instance - [");
        String clsName = (String) instance.getAttributeValue("class");
        displayName.append(clsName).append(": ");
        String name = (String) instance.getAttributeValue("name");
        displayName.append(name).append(" (");
        Object dbId = instance.getAttributeValue(ReactomeJavaConstants.deletedInstanceDB_ID);
        displayName.append(dbId).append(") - ");
        GKInstance species = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.species);
        if (species == null)
            displayName.append("]");
        else
            displayName.append(species.getDisplayName()).append("]");
        return displayName.toString();
    }

    private static String generateControlReferenceName(GKInstance instance) throws Exception {
        StringBuilder builder = new StringBuilder();
        if (instance.getSchemClass().isa(ReactomeJavaConstants.RegulationReference)) {
            GKInstance regulation = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.regulation);
            builder.append(regulation.getDisplayName());
        }
        else if (instance.getSchemClass().isa(ReactomeJavaConstants.CatalystActivityReference)) {
            GKInstance ca = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.catalystActivity);
            builder.append(ca.getDisplayName());
        }
        else if (instance.getSchemClass().isa(ReactomeJavaConstants.MarkerReference)) {
            List<GKInstance> markers = instance.getAttributeValuesList(ReactomeJavaConstants.marker);
            if (markers.size() == 0)
                builder.append("Marker unknown:");
            else if (markers.size() == 1) {
                String markerName = markers.get(0).getDisplayName();
                builder.append(markerName);
            }
            else {
                String markerName = markers.get(0).getDisplayName();
                builder.append(markerName + "...");
            }
        }
        GKInstance reference = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.literatureReference);
        if (reference != null)
            builder.append(" ").append(reference.getDisplayName());
        return builder.toString();
    }
    
    private static String generateStableIdentifierName(GKInstance instance) throws Exception {
        String identifier = (String) instance.getAttributeValue(ReactomeJavaConstants.identifier);
        String version = (String) instance.getAttributeValue(ReactomeJavaConstants.identifierVersion);
        return identifier + "." + version;
    }
	
	private static String generateTargettedInteractionName(GKInstance instance) throws Exception {
	    GKInstance factor = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.factor);
	    GKInstance target = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.target);
	    return factor.getDisplayName() + " " + target.getDisplayName();
	}
	
	private static String generateFunctionalStatusName(GKInstance instance) throws Exception {
	    StringBuilder builder = new StringBuilder();
	    GKInstance type = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.functionalStatusType);
	    if (type == null)
	        builder.append("unknown");
	    else
	        builder.append(type.getDisplayName());
	    builder.append(" via ");
	    GKInstance variant = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.structuralVariant);
	    if (variant == null)
	        builder.append("unknown");
	    else
	        builder.append(variant.getDisplayName());
	    return builder.toString();
	}
	
	private static String generateEntityFunctionalStatusName(GKInstance instance) throws Exception {
	    StringBuilder builder = new StringBuilder();
	    List<GKInstance> values = instance.getAttributeValuesList(ReactomeJavaConstants.functionalStatus);
	    if (values == null || values.size() == 0)
	        builder.append("unknown");
	    else {
	        // Since multiple types may be used via origin, need some parsing to get the common functional type
	        // via their _displayName
	        Set<String> types = new HashSet<String>();
	        for (GKInstance inst : values) {
	            String name = inst.getDisplayName();
	            int index = name.indexOf(" ");
	            name = name.substring(0, index);
	            types.add(name);
	        }
	        List<String> typeList = new ArrayList<String>(types);
	        Collections.sort(typeList);
	        for (Iterator<String> it = typeList.iterator(); it.hasNext();) {
	            builder.append(it.next());
	            if (it.hasNext())
	                builder.append(" and ");
	        }
	    }
	    builder.append(" of ");
	    GKInstance pe = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.diseaseEntity);
	    if (pe == null)
	        builder.append("unknown");
	    else
	        builder.append(pe.getDisplayName());
	    return builder.toString();
	}
	
	private static String generatePathwayDiagramName(GKInstance instance) throws Exception {
	    List<?> pathways = instance.getAttributeValuesList(ReactomeJavaConstants.representedPathway);
	    if (pathways != null && pathways.size() > 0) {
	        StringBuilder builder = new StringBuilder();
	        builder.append("Diagram of ");
	        for (int i = 0; i < pathways.size(); i++) {
	            GKInstance pathway = (GKInstance) pathways.get(i);
	            builder.append(pathway.getDisplayName());
	            if (i < pathways.size() - 2)
	                builder.append(", ");
	            else if (i == pathways.size() - 2) {
	                if (pathways.size() > 2)
	                    builder.append(", ");
	                builder.append(" and ");
	            }
	        }
	        return builder.toString();
	    }
	    return null;
	}
	
	private static String generateEntityName(GKInstance instance) throws Exception {
	    StringBuffer buffer = new StringBuffer();
	    java.util.List values = instance.getAttributeValuesList("name");
	    if (values != null && values.size() > 0) {
	        buffer.append(values.get(0).toString());
	    }
	    else
	        buffer.append("unknown");
	    // Check compartment
	    if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.compartment)) {
	        values = instance.getAttributeValuesList(ReactomeJavaConstants.compartment);
	        if (values != null && values.size() > 0) {
	            GKInstance compartment = (GKInstance) values.get(0);
	            buffer.append(" [");
	            buffer.append(compartment.getDisplayName());
	            buffer.append("]");
	        }
	    }
	    return buffer.toString();
	}
	
	private static String generateInstanceEditName(GKInstance instance) throws Exception {
		StringBuffer buffer = new StringBuffer();
		java.util.List values = instance.getAttributeValuesList("author");
		if (values != null && values.size() > 0) {
			GKInstance author = null;
			for (Iterator it = values.iterator(); it.hasNext();) {
				author = (GKInstance) it.next();
				buffer.append(author.getDisplayName());
				if (it.hasNext())
					buffer.append(", ");
			}
		}
		values = instance.getAttributeValuesList("dateTime");
		if (values != null && values.size() > 0) {
			buffer.append(", ");
			String dateTime = values.get(0).toString();
			// There are two types of values that have been used: 2009-06-02 06:18:22 or 20140719002733
			// Two ways to process dateTime information
			Pattern pattern = Pattern.compile("^(\\d){4}-(\\d){2}-(\\d){2}");
	        Matcher matcher = pattern.matcher(dateTime);
	        if (matcher.find()) {
	            buffer.append(matcher.group());
	        }
	        else {
	            // Convert the format
	            // Year
	            buffer.append(dateTime.substring(0, 4));
	            buffer.append("-");
	            // Month
	            buffer.append(dateTime.substring(4, 6));
	            buffer.append("-");
	            // Day
	            buffer.append(dateTime.substring(6, 8));
	        }
		}
		return buffer.toString();
	}
	
	private static String generatePersonName(GKInstance instance) throws Exception {
		StringBuilder buffer = new StringBuilder();
		String value = (String) instance.getAttributeValue("surname");
		if (value == null || value.length() == 0)
			buffer.append("Unkown");
		else
			buffer.append(value);
		// Use the first name first
        value = (String) instance.getAttributeValue("firstname");
        if (value != null && value.length() > 0) {
            buffer.append(", ");
            buffer.append(value);
        }
        else {
            value = (String) instance.getAttributeValue("initial");
            if (value != null && value.length() > 0) {
                buffer.append(", ");
                buffer.append(value);
            }
        }
		return buffer.toString();
	}
	
	private static String generateSummationName(GKInstance instance) throws Exception {
		java.util.List values = instance.getAttributeValuesList("text");
		if (values == null || values.size() == 0)
			return "";
		String text = values.get(0).toString();
		if (text.length() > 60) // The first 60 characters
			return text.substring(0, 60) + "...";
		else
			return text;
	} 
	
	private static String generateFigureName(GKInstance instance) throws Exception {
		java.util.List values = instance.getAttributeValuesList("url");
		if (values == null || values.size() == 0)
			return "";
		return values.get(0).toString();
	} 
	
	/**
	 * As of April 27, 2018, use this new way to generate _displayName for Regulation,
	 * which encourage to reuse Regulation for different ReactionlikeEvent if possible.
	 * @param regulation
	 * @return
	 * @throws Exception
	 */
	private static String generateRegulationName(GKInstance regulation) throws Exception {
	    StringBuilder builder = new StringBuilder();
	    SchemaClass cls = regulation.getSchemClass();
	    if (cls.isa(ReactomeJavaConstants.NegativeGeneExpressionRegulation))
	        builder.append("Negative gene expression regulation");
	    else if (cls.isa(ReactomeJavaConstants.NegativeRegulation))
	        builder.append("Negative regulation");
	    else if (cls.isa(ReactomeJavaConstants.PositiveGeneExpressionRegulation))
	        builder.append("Positive gene expression regulation");
	    else if (cls.isa(ReactomeJavaConstants.Requirement))
	        builder.append("Requirement");
	    else if (cls.isa(ReactomeJavaConstants.PositiveRegulation))
	        builder.append("Positive regulation");
	    else
	        builder.append("Regulation");
	    builder.append(" by ");
	    GKInstance regulator = (GKInstance) regulation.getAttributeValue(ReactomeJavaConstants.regulator);
	    if (regulator == null)
	        builder.append("\'UNKNOWN\'");
	    else
	        builder.append("\'" + regulator.getDisplayName() + "\'");
	    return builder.toString();
	}
	
	// Leave this method for the time being for debugging though it is not used any more.
	private static String generateRegulationName(GKInstance instance, String action) throws Exception {
		StringBuffer buffer = new StringBuffer();
		java.util.List values = instance.getAttributeValuesList(ReactomeJavaConstants.regulator);
		if (values == null || values.size() == 0) {
			buffer.append("\'UNKNOWN ENITTY\'");
		}
		else {
			GKInstance regulator = (GKInstance) values.get(0);
			buffer.append("\'" + regulator.getDisplayName() + "\'");
		}
		buffer.append(" " + action + " ");
		GKInstance regulatedEntity = InstanceUtilities.getRegulatedInstance(instance);
		if (regulatedEntity == null) {
			buffer.append("\'UNKNOWN ENTITY\'");
		}
		else {
			buffer.append("\'" + regulatedEntity.getDisplayName() + "\'");
		}
        if (buffer.length() > 0)
            return buffer.toString();
        // Check the old displayName
        String oldName = instance.getDisplayName();
        if (oldName != null && oldName.length() > 0)
            return oldName;
        return "";
	}
	
	private static String generateLiteratureReferenceName(GKInstance instance) throws Exception {
		java.util.List values = instance.getAttributeValuesList("title");
		if (values != null && values.size() > 0) {
			return values.get(0).toString();
		}
		// Try pubmed
		values = instance.getAttributeValuesList("pubMedIdentifier");
		if (values != null && values.size() > 0)
			return values.get(0).toString();
        // Try journal
        String journal = (String) instance.getAttributeValue("journal");
        if (journal != null)
            return journal;
		return "";
	} 
	
	private static String generateCatalystActivityName(GKInstance instance) throws Exception {
 		StringBuffer buffer = new StringBuffer();
 		java.util.List values = instance.getAttributeValuesList("activity");
 		String actName = null;
 		if (values == null || values.size() == 0) {
 			actName = "unknown";
 		}
 		else {
 			GKInstance activity = (GKInstance) values.get(0);
 			actName = activity.getDisplayName();
 		}
 		buffer.append(actName);
 		if (actName.toLowerCase().indexOf("activity") == -1) { // need activity
 			buffer.append(" activity ");
 		}
 		else
 			buffer.append(" ");
 		buffer.append("of");
 		values = instance.getAttributeValuesList("physicalEntity");
 		if (values == null || values.size() == 0)
 			buffer.append(" unknown entity");
 		else {
 			GKInstance entity = (GKInstance) values.get(0);
 			buffer.append(" " + entity.getDisplayName());
 		}
 		return buffer.toString();
 	}
 		
	private static String generateDatabaseIdentifierName(GKInstance instance) throws Exception {
		StringBuffer buffer = new StringBuffer();
		java.util.List values = instance.getAttributeValuesList("referenceDatabase");
		if (values != null && values.size() > 0) {
			GKInstance refDB = (GKInstance) values.get(0);
			buffer.append(refDB.getDisplayName());
		}
		values = instance.getAttributeValuesList("identifier");
		if (values != null && values.size() > 0) {
			buffer.append(":");
			buffer.append(values.get(0).toString());
		}
		return buffer.toString();
	}
	
	private static String generateReferenceSequenceName(GKInstance instance) throws Exception {
	    String dbName = null;
	    GKInstance refDB = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.referenceDatabase);
	    if (refDB != null) 
	        dbName = refDB.getDisplayName();
	    if (dbName == null)
	        dbName = "Unknown";
	    String identifier = null;
	    if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.variantIdentifier)) {
	        // Use variantIdentifier first
	        identifier = (String) instance.getAttributeValue(ReactomeJavaConstants.variantIdentifier);
	    }
	    else if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.identifier))
	        identifier = (String) instance.getAttributeValue(ReactomeJavaConstants.identifier);
	    if (identifier == null)
	        identifier = "Unknown";
	    String name = null;
	    if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.geneName)) {
	        name = (String) instance.getAttributeValue(ReactomeJavaConstants.geneName);
	    }
	    else if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.name)) {
	        name = (String) instance.getAttributeValue(ReactomeJavaConstants.name);
	    }
	    if (name == null)
	        name = "Unknown";
	    return dbName + ":" + identifier + " " + name;
	}
	
	private static String generateReferenceGroupName(GKInstance instance) throws Exception {
		java.util.List names = instance.getAttributeValuesList("name");
	    if (names != null && names.size() > 0) {
	        String name = (String) names.get(0);
	        return name;
	    }
	    StringBuffer buffer = new StringBuffer();
	    GKInstance refDB = (GKInstance) instance.getAttributeValue("referenceDatabase");
	    if (refDB != null) {
	        buffer.append(refDB.getDisplayName());
	    }
	    String identifier = (String) instance.getAttributeValue("identifier");
	    if (identifier != null) {
	        if (refDB != null)
	            buffer.append(":");
	        else
	            buffer.append("unknown:");
	        buffer.append(identifier);
	    }
		return buffer.toString();
	}
	
	private static String generateReferenceMoleculeName(GKInstance instance) throws Exception {
	    StringBuilder buffer = new StringBuilder();
        java.util.List names = instance.getAttributeValuesList("name");
        if (names != null && names.size() > 0) {
            String name = (String) names.get(0);
            buffer.append(name);
        }
	    GKInstance refDB = (GKInstance) instance.getAttributeValue("referenceDatabase");
	    if (refDB != null) {
	        buffer.append(" [").append(refDB.getDisplayName());
	    }
	    else
	        buffer.append(" [unknown");
	    buffer.append(":");
	    String identifier = (String) instance.getAttributeValue("identifier");
	    if (identifier != null) {
	        buffer.append(identifier);
	    }
	    else
	        buffer.append("unknown");
	    buffer.append("]");
	    return buffer.toString();
	}
	
	private static String generateNonsenseMutationName(GKInstance instance) throws Exception {
	    List psiMods = instance.getAttributeValuesList(ReactomeJavaConstants.psiMod);
	    Integer coordinate = (Integer) instance.getAttributeValue(ReactomeJavaConstants.coordinate);
	    String pos = (coordinate == null ? "unknown position" : coordinate.toString());
	    String aa = null;
	    if (psiMods == null || psiMods.size() == 0) {
	        aa = "unknown";
	    }
	    else if (psiMods.size() == 1) {
	        GKInstance psi = (GKInstance) psiMods.get(0);
	        String name = getPsiModName(psi);
	        int index = name.indexOf("removal");
	        if (index > 0)
	            name = name.substring(0, index).trim();
	        aa = name;
	    }
	    return "Nonsense mutation at " + aa + " " + pos;
	}
	
	private static String generateReplacedResidueName(GKInstance instance) throws Exception {
	    List psiMods = instance.getAttributeValuesList(ReactomeJavaConstants.psiMod);
	    Integer coordinate = (Integer) instance.getAttributeValue(ReactomeJavaConstants.coordinate);
	    String pos = (coordinate == null ? "at unknown position" : coordinate.toString());
	    String replaced = null;
	    String replacement = null;
	    if (psiMods == null || psiMods.size() == 0) {
	        replaced = "unknown ";
	        replacement = "unknown";
	    }
	    else if (psiMods.size() == 1) {
	        GKInstance psi = (GKInstance) psiMods.get(0);
	        String name = getPsiModName(psi);
	        int index = name.indexOf("removal");
	        if (index > 0)
	            name = name.substring(0, index).trim();
	        replaced = name;
	        replacement = "unknown";
	    }
	    else if (psiMods.size() == 2) {
	        GKInstance psi = (GKInstance) psiMods.get(0);
            String name = getPsiModName(psi);
            int index = name.indexOf("removal");
            if (index > 0)
                name = name.substring(0, index).trim();
            replaced = name;
            psi = (GKInstance) psiMods.get(1);
            name = getPsiModName(psi);
            index = name.indexOf("residue");
            if (index > 0)
                name = name.substring(0, index).trim();
            replacement = name;
	    }
		return replaced + " " + pos + " replaced with " + replacement;
	}
	
	private static String generateNegativePrecedingEventName(GKInstance instance) throws Exception {
	    GKInstance precedingEvent = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.precedingEvent);
	    GKInstance reason = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.reason);
	    StringBuilder builder = new StringBuilder();
	    if (reason != null)
            builder.append(reason.getDisplayName());
	    if (precedingEvent != null) {
	        if (builder.length() > 0)
	            builder.append(": ");
	        builder.append(precedingEvent.getDisplayName());
	    }
	    if (builder.length() == 0) // Show something
	        builder.append(instance.getSchemClass().getName() + ": " + instance.getDBID());
	    return builder.toString();
	}
	
	private static String getPsiModName(GKInstance psiMod) {
        String displayName = psiMod.getDisplayName();
        if (displayName != null && displayName.length() > 0) {
            // Remove MOD id in the display name
            int index = displayName.indexOf("[MOD:");
            if (index > 0)
                displayName = displayName.substring(0, index).trim();
            return displayName;
        }
        return "";
	}
	
	private static String generateCrosslinkedResidueName(GKInstance instance) throws Exception {
	    StringBuilder builder = new StringBuilder();
	    if (instance.getSchemClass().isa(ReactomeJavaConstants.InterChainCrosslinkedResidue)) {
	        builder.append("Inter-chain Crosslink via ");
	    }
	    else
	        builder.append("Intra-chain Crosslink via ");
	    GKInstance psiMod = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.psiMod);
        if (psiMod != null) {
            String displayName = getPsiModName(psiMod);
            builder.append(displayName);
        }
        else
            builder.append("unknown");
        builder.append(" at ");
        Integer coordinate = (Integer) instance.getAttributeValue(ReactomeJavaConstants.coordinate);
        if (coordinate == null)
            builder.append("unknown");
        else
            builder.append(coordinate);
        builder.append(" and ");
        coordinate = (Integer) instance.getAttributeValue(ReactomeJavaConstants.secondCoordinate);
        if (coordinate == null)
            builder.append("unknown");
        else
            builder.append(coordinate);
	    return builder.toString();
	}

	private static String generateModifiedResidueName(GKInstance instance) throws Exception {
	    StringBuilder buffer = new StringBuilder();
	    // Check PsiMod first. If there is a PsiMod, use it
	    GKInstance psiMod = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.psiMod);
	    if (psiMod != null) {
	        String displayName = getPsiModName(psiMod);
	        buffer.append(displayName);
	    }
	    buffer.append(" at ");
	    Integer coordinates = (Integer) instance.getAttributeValue(ReactomeJavaConstants.coordinate);
	    if (coordinates == null) {
	        buffer.append("unknown position");
	    }
	    else
	        buffer.append(coordinates);
	    return buffer.toString();
	}
	
	private static String generateGroupModifiedResidueName(GKInstance instance) throws Exception {
	    Integer coordinate = (Integer) instance.getAttributeValue(ReactomeJavaConstants.coordinate);
	    String pos = (coordinate == null ? "unknown position" : coordinate.toString());
	    GKInstance psiMod = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.psiMod);
	    String psiModName = psiMod == null ? "unknown" : getPsiModName(psiMod);
	    GKInstance modification = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.modification);
	    String modificationName = modification == null ? "unknown" : modification.getDisplayName();
	    return psiModName + " (" + modificationName + ") at " + pos;
	}

	private static String generateModifiedNucleotideName(GKInstance instance) throws Exception {
	    Integer coordinate = (Integer) instance.getAttributeValue(ReactomeJavaConstants.coordinate);
	    String pos = (coordinate == null ? "unknown position" : coordinate.toString());
	    GKInstance modification = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.modification);
	    String modificationName = null;
	    if (modification == null) {
	        modificationName = "unknown";
	    }
	    else {
	        // Since we may get a shell instance, we cannot directly query the name slot. Here it a way to parse
	        // the name from _displayName
	        modificationName = modification.getDisplayName();
	        int index = modificationName.indexOf("[ChEBI:");
	        if (index > 0)
	            modificationName = modificationName.substring(0, index).trim();
	    }
	    return modificationName + " at " + pos;
	}

	private static String generateReactionName(GKInstance reaction) throws Exception {
        String name = (String) reaction.getAttributeValue("name");
        if (name != null)
            return name; // Use the first name as default
        // However, if there is no name defined, use input/output
        GKInstance input = (GKInstance) reaction.getAttributeValue("input");
        GKInstance output = (GKInstance) reaction.getAttributeValue("output");
        StringBuffer buffer = new StringBuffer();
        if (input != null)
            buffer.append(input.getDisplayName());
        else
            buffer.append("null");
        buffer.append("->");
        if (output != null)
            buffer.append(output.getDisplayName());
        else
            buffer.append("null");
        return buffer.toString();
    }
    
    private static String generateFragmentModificationName(GKInstance instance,
                                                           String type) throws Exception {
        Integer start = (Integer) instance.getAttributeValue(ReactomeJavaConstants.startPositionInReferenceSequence);
        String startText = null;
        if (start == null)
            startText = "unknown";
        else
            startText = start.toString();
        Integer end = (Integer) instance.getAttributeValue(ReactomeJavaConstants.endPositionInReferenceSequence);
        String endText = null;
        if (end == null)
            endText = "unknown";
        else
            endText = end.toString();
        StringBuilder builder = new StringBuilder();
        builder.append(type).append(" of residues ");
        builder.append(startText).append(" to ");
        builder.append(endText);
        if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.coordinate)) {
            Integer coordinate = (Integer) instance.getAttributeValue(ReactomeJavaConstants.coordinate);
            builder.append(" at ");
            if (coordinate == null)
                builder.append("unknown");
            else
                builder.append(coordinate);
            builder.append(" from ");
            // Need to use ReferenceSequence
            GKInstance refSeq = (GKInstance) instance.getAttributeValue(ReactomeJavaConstants.referenceSequence);
            if (refSeq != null)
                builder.append(refSeq.getDisplayName());
            else
                builder.append("unknown");
        }
        if (instance.getSchemClass().isValidAttribute(ReactomeJavaConstants.alteredAminoAcidFragment)) {
            String aas = (String) instance.getAttributeValue(ReactomeJavaConstants.alteredAminoAcidFragment);
            if (aas != null && aas.trim().length() > 0) {
                builder.append(" by ").append(aas);
            }
        }
        return builder.toString();
    }
    
}
