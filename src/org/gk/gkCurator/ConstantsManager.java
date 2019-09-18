package org.gk.gkCurator;

import java.util.HashMap;
import java.util.Map;

public class ConstantsManager {

	private static Map<String, String> map = new HashMap<String, String>();
	
	public ConstantsManager() {
		set();
	}
	
	public void set() {
		map.put("editor", "Peter D’Eustachio");
		map.put("uneditableClassTemplate", "You cannot create an instance for this type of class. Please ask %s to create one for you.");
		map.put("uneditableClassTitle", "Uneditable Class");
	}

	public String get(String key) {
		return map.get(key);
	}
}
