/*
 * Created on Oct 29, 2003
 */
package org.gk.osxAdapter;

/**
 * @author guanming
 *
 *This interface is used to hook about, prefrences and quit actions to MacOSX's
 *application menu.
 */
public interface OSXApplication {
	
	/**
	 * About the application.
	 */
	public void about();
	
	/**
	 * Quit the application.
	 */
	public void quit();
	
	/**
	 * Set the preferences for the application.
	 */
	public void preferences();

}
