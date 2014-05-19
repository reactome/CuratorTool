package org.gk.qualityCheck;


/**
 * This QA checks the drawn reactions in a diagram are the same as annotated reactions
 * contained in the database. A reaction's inputs, outputs, catalysts and regulators will
 * be checked to make sure they are the same. 
 * @author Gwu
 *
 */
public class DiagramReactionSyncCheck extends ServletBasedQACheck {
	
	public DiagramReactionSyncCheck() {
	    actionName = "ReactionSynELVCheck";
        resultTitle = "Reaction ELV Synchronization QA Results";
	}

}
