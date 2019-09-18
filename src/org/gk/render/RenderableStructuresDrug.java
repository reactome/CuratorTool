package org.gk.render;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Drug renderable class for structures (currently Complex and EntitySet) that contains one or more drug in their:
 * 
 * <ul>
 *  <li><code>hasComponent</code> slot or</li>
 *  <li><code>hasMember</code> slot or</li>
 *  <li><code>hasCandidate</code> slot</li>
 * </ul>
 * Follows same logic as implemented in <a href="https://git.io/Je3IP">DiagramGraphFactory.java:199</a>.
 *
 */
public class RenderableStructuresDrug {
	
	/**
	 * Query database.
	 * 
	 * @param dbId
	 * @return Collection composed of database query result, empty if database query failed.
	 */
	private Collection<Long> getStructuresDrug(Long dbId) {
		// TODO create query of structures that have one more drugs in hasComponent, hasMember, or hasCandidate slots.
		String query = "" +
				"MATCH path=(p:Pathway{dbId:{dbId}})-[:hasEvent*]->(rle:ReactionLikeEvent) " +
				"WHERE SINGLE(x IN NODES(path) WHERE NOT x.hasDiagram IS NULL AND x.hasDiagram) " +
				"WITH DISTINCT rle " +
				"MATCH (rle)-[:input|output|catalystActivity|physicalEntity|regulatedBy|regulator*]->(pe:PhysicalEntity)-[:hasComponent|hasMember|hasCandidate*]->(p:Drug) " +
				"RETURN DISTINCT pe.dbId";

		// TODO set up database info.
		Map<String, Object> params = new HashMap<>();
		params.put("dbId", dbId);
		
		try {
			// TODO query database.
			return advancedDatabaseObjectService.getCustomQueryResults(Long.class, query, params);
		} catch (Exception e) {
			return Collections.emptyList();
		}
	}
}
