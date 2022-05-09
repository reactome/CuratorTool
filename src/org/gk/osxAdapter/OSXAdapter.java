/*
 * Created on Oct 29, 2003
 *
 * This class is modified from the sample code OSXAdapter.java from applet.
 */

package org.gk.osxAdapter;

import org.springframework.context.ApplicationEvent;

public class OSXAdapter {
	private static OSXAdapter theAdapter;

	// reference to the app where the existing quit, about, prefs code is
	private OSXApplication mainApp;
	
	private OSXAdapter (OSXApplication inApp) {
		mainApp = inApp;
	}
	
	// implemented handler methods.  These are basically hooks into existing 
	// functionality from the main app, as if it came over from another platform.
	public void handleAbout(ApplicationEvent ae) {
		if (mainApp != null) {
			mainApp.about();
		} else {
			throw new IllegalStateException("handleAbout: OSXApplication instance detached from listener");
		}
	}
	
	public void handlePreferences(ApplicationEvent ae) {
		if (mainApp != null) {
			mainApp.preferences();
		} else {
			throw new IllegalStateException("handlePreferences: OSXApplication instance detached from listener");
		}
	}
	
	public void handleQuit(ApplicationEvent ae) {
		if (mainApp != null) {
			mainApp.quit();
		} else {
			throw new IllegalStateException("handleQuit: OSXApplication instance detached from listener");
		}
	}
	
	
	// The main entry-point for this functionality.  This is the only method
	// that needs to be called at runtime, and it can easily be done using
	// reflection (see MyApp.java) 
	public static void registerMacOSXApplication(OSXApplication inApp) {
		if (theAdapter == null) {
			theAdapter = new OSXAdapter(inApp);
		}
	}
	
	// Another static entry point for EAWT functionality.  Enables the 
	// "Preferences..." menu item in the application menu. 
	public static void enablePrefs(boolean enabled) {
		// TODO
	}
}
