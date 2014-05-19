/*
 * Created on Jun 27, 2003
 */
package org.gk.model;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 
 * @author wgm
 */
public class InstanceCache {
//	private Map cache = new HashMap();
	// There may be a multiple threading problem in a servlet environment.
	// e.g. two threads may put some instances together into the cache, which
	// basically block get and put!
	// Use this version of map may help to avoid such a problem.
	private Map<Long, Instance> cache = new ConcurrentHashMap<Long, Instance>();
	
	/**
	 * 
	 */
	public void clear() {
		cache.clear();
	}

	/**
	 * @param arg0
	 * @return
	 */
	public boolean containsKey(Object arg0) {
		return cache.containsKey(arg0);
	}

	/**
	 * @param arg0
	 * @return
	 */
	public boolean containsValue(GKInstance arg0) {
		return cache.containsValue(arg0);
	}

	/**
	 * @return
	 */
	public Set<Map.Entry<Long, Instance>> entrySet() {
		return cache.entrySet();
	}

	/**
	 * @param arg0
	 * @return
	 */
	public GKInstance get(Object arg0) {
		return (GKInstance) cache.get(arg0);
	}

	public GKInstance get(long dbID) {
		return (GKInstance) cache.get(new Long(dbID));
	}

	/**
	 * @return
	 */
	public boolean isEmpty() {
		return cache.isEmpty();
	}

	/**
	 * @return
	 */
	public Set<Long> keySet() {
		return cache.keySet();
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 */
	public GKInstance put(Object arg0, Instance arg1) {
	    if (!(arg0 instanceof Long))
	        throw new IllegalArgumentException("The first parameter must be a Long object!");
		return (GKInstance) cache.put((Long)arg0, arg1);
	}

	/**
	 * @param arg0
	 * @return
	 */
	public Instance put(Instance arg0) {
		return put(arg0.getDBID(), arg0);
	}

	/**
	 * @param arg0
	 * @param arg1
	 * @return
	 */
	public GKInstance put(long dbId, GKInstance arg1) {
		return put(new Long(dbId), arg1);
	}

	public GKInstance put(Long dbId, GKInstance arg1) {
		return put(dbId, arg1);
	}

	/**
	 * @param arg0
	 */
	public void putAll(Map<Long, Instance> arg0) {
		cache.putAll(arg0);
	}

	/**
	 * @param arg0
	 * @return
	 */
	public Object remove(Object arg0) {
		return cache.remove(arg0);
	}

	/**
	 * @return
	 */
	public int size() {
		return cache.size();
	}

	/**
	 * @return
	 */
	public Collection<Instance> values() {
		return cache.values();
	}

}
