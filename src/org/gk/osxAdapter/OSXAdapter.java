/*
 * Created on Oct 29, 2003
 *
 * This class is modified from the sample code OSXAdapter.java from applet.
 */

package org.gk.osxAdapter;

import com.apple.eawt.*;

public class OSXAdapter extends ApplicationAdapter {

	// pseudo-singleton model; no point in making multiple instances
	// of the EAWT application or our adapter
	private static OSXAdapter						theAdapter;
	private static com.apple.eawt.Application		theApplication;

	// reference to the app where the existing quit, about, prefs code is
	private OSXApplication									mainApp;
	
	private OSXAdapter (OSXApplication inApp) {
		mainApp = inApp;
	}
	
	// implemented handler methods.  These are basically hooks into existing 
	// functionality from the main app, as if it came over from another platform.
	public void handleAbout(ApplicationEvent ae) {
		if (mainApp != null) {
			ae.setHandled(true);
			mainApp.about();
		} else {
			throw new IllegalStateException("handleAbout: OSXApplication instance detached from listener");
		}
	}
	
	public void handlePreferences(ApplicationEvent ae) {
		if (mainApp != null) {
			mainApp.preferences();
			ae.setHandled(true);
		} else {
			throw new IllegalStateException("handlePreferences: OSXApplication instance detached from listener");
		}
	}
	
	public void handleQuit(ApplicationEvent ae) {
		if (mainApp != null) {
			/*	
			/	You MUST setHandled(false) if you want to delay or cancel the quit.
			/	This is important for cross-platform development -- have a universal quit
			/	routine that chooses whether or not to quit, so the functionality is identical
			/	on all platforms.  This example simply cancels the AppleEvent-based quit and
			/	defers to that universal method.
			*/
			ae.setHandled(false);
			mainApp.quit();
		} else {
			throw new IllegalStateException("handleQuit: OSXApplication instance detached from listener");
		}
	}
	
	
	// The main entry-point for this functionality.  This is the only method
	// that needs to be called at runtime, and it can easily be done using
	// reflection (see MyApp.java) 
	public static void registerMacOSXApplication(OSXApplication inApp) {
		if (theApplication == null) {
			theApplication = new com.apple.eawt.Application();
		}			
		
		if (theAdapter == null) {
			theAdapter = new OSXAdapter(inApp);
		}
		theApplication.addApplicationListener(theAdapter);
	}
	
	// Another static entry point for EAWT functionality.  Enables the 
	// "Preferences..." menu item in the application menu. 
	public static void enablePrefs(boolean enabled) {
		if (theApplication == null) {
			theApplication = new com.apple.eawt.Application();
		}
		theApplication.setEnabledPreferencesMenu(enabled);
	}
}
