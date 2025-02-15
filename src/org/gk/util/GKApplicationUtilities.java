/*
 * Created on Nov 11, 2003
 */
package org.gk.util;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.border.Border;
import javax.swing.filechooser.FileFilter;
import javax.swing.plaf.metal.MetalLookAndFeel;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.w3c.dom.Document;
/**
 * A list of utilities that can be used in the gk applications.
 * @author wugm
 */
public class GKApplicationUtilities {
    // DB_ID for homo sapiens: this will never be changed
    public static final Long HOMO_SAPIENS_DB_ID = 48887L;
    // Reactome URL
    public static String REACTOME_INSTANCE_URL = "http://www.reactome.org/cgi-bin/eventbrowser?DB=gk_current&ID=";
    private static final String REACTOME_DIR = System.getProperty("user.home") + File.separator + ".reactome";
	// Default LookAndFeel name
	private static final String DEFAULT_SKIN_LF = "whistler";
	public static final boolean isDeployed = true;
	private static Icon isAIcon;
	private static Icon isPartOfIcon;
	private static Icon isMemberIcon;
	private static Icon isSpecializedFormIcon;
	private static Border titleBorder = BorderFactory.createEmptyBorder(2, 2, 2, 2);
	private static Dimension btnSize = new Dimension(20, 20);
    // Check if the platform is Mac
    private static boolean isMac = false;
    // Application-wise properties
    private static Properties applicationProp;

	// Create a user folder
	static {
	    File file = new File(REACTOME_DIR);
	    if (!file.exists())
	        file.mkdir();
	    String osName = System.getProperty("os.name").toLowerCase();
        if (osName != null && osName.indexOf("mac") > -1)
            isMac = true;
        else
            isMac = false;
    }

	/**
	 * Convert a value in pixel to one in centimeter.
	 *
	 * @param pixels Pixel value to convert
	 * @return Centimeter value of the pixel value passed
	 */
	public static double convertPixelToCentiMeter(double pixels) {
	    Toolkit tk = Toolkit.getDefaultToolkit();
	    int dpi = tk.getScreenResolution();
	    double pixelsPerCm = dpi / 2.54d;
	    return pixels / pixelsPerCm;
	}

	/**
	 * Get the hex
	 *
	 * @param color Color object to convert to hexadecimal value
	 * @return Hexadecimal value (e.g. #FFFFFF) of a the color object passed
	 */
	public static String getHexForColor(Color color) {
	    int r = color.getRed();
	    int g = color.getGreen();
	    int b = color.getBlue();
	    return String.format("%02x%02x%02x",
	                         r,
	                         g,
	                         b);
	}

	/**
	 * Insert a value into a key to value list map.
	 *
	 * @param <T> Type of the map key
	 * @param <K> Type of the map's value list
	 * @param keyToSet Map of key to value list for which to insert new value for a key
	 * @param key Key for which to insert a new value
	 * @param value New value to insert
	 */
	public static <T, K> void insertValueToMap(Map<T, List<K>> keyToSet,
	                                           T key,
	                                           K value) {
	    List<K> values = keyToSet.get(key);
	    if (values == null) {
	        values = new ArrayList<K>();
	        keyToSet.put(key, values);
	    }
	    values.add(value);
	}

	/**
	 * Use this method to generate a String for the bounds for the passed window in a pre-determined format.
	 *
	 * @param window Window object for which to get bounds
	 * @return Bounds of window object as String in format "x y width height"
	 */
	public static String generateWindowBoundsString(Window window) {
	    Rectangle r = window.getBounds();
	    return r.x + " " + r.y + " " + r.width + " " + r.height;
	}

    public static void storeCurrentDir(JFileChooser fileChooser,
                                       Properties prop) {
        File selectedFile = fileChooser.getSelectedFile();
        if (selectedFile != null) {
            File dir = selectedFile.getParentFile();
            prop.setProperty("currentDir", dir.getAbsolutePath());
        }
    }

	/**
	 * Set the window bounds for the passed Window object based on the value from a String, which
	 * should be generated by another utility method generateWindowBoundsString.
	 *
	 * @param target Window object for which to set new bounds
	 * @param str Bounds string in format "x y width height" from which to parse new bounds
	 * @see #generateWindowBoundsString(Window)
	 */
	public static void setWindowBoundsFromString(Window target,
	                                             String str) {
	    String[] tokens = str.split(" ");
        Rectangle rect = new Rectangle();
        rect.x = Integer.parseInt(tokens[0]);
        rect.y = Integer.parseInt(tokens[1]);
        rect.width = Integer.parseInt(tokens[2]);
        rect.height = Integer.parseInt(tokens[3]);
        target.setBounds(rect);
	}

	/**
	 * Set the application-wide properties. There should be only one application-wide properties. This value
	 * should be set just after application is launched to avoid any null exception for method
	 * getApplicationProperties().
	 *
	 * @param prop Properties object to set
	 */
	public static void setApplicationProperties(Properties prop) {
	    applicationProp = prop;
	}

	/**
	 * Get the application-wide properties. This Properties object should be set when the application
	 * is just launched so that the returned value should not be null.
	 *
	 * @return Properties object set for the application
	 */
	public static Properties getApplicationProperties() {
	    return applicationProp;
	}

	public static Border getTitleBorder() {
		return titleBorder;
	}

    public static boolean isMac() {
        return isMac;
    }

	public static JLabel createTitleLabel(String title) {
	    JLabel label = new JLabel(title);
	    label.setFont(label.getFont().deriveFont(Font.BOLD));
	    label.setBorder(getTitleBorder());
	    return label;
	}

	public static String getDefaultLF() {
		String osName = System.getProperty("os.name").toLowerCase();
		if (osName.indexOf("win") > -1)
			return "windows";
		else if (osName.indexOf("mac") > -1)
			return "aqua";
		else if (osName.indexOf("linux") > -1)
			return "whistler";
		return DEFAULT_SKIN_LF;
	}

	public static Icon getIsAIcon() {
	    if (isAIcon == null)
	        isAIcon = createImageIcon(new GKApplicationUtilities().getClass(), "IsA.gif");
	    return isAIcon;
	}

	public static Icon getIsPartOfIcon() {
	    if (isPartOfIcon == null)
	        isPartOfIcon = createImageIcon(new GKApplicationUtilities().getClass(), "PartOf.gif");
		return isPartOfIcon;
	}

	public static Icon getIsMemberIcon() {
	    if (isMemberIcon == null)
	        isMemberIcon = createImageIcon(new GKApplicationUtilities().getClass(), "isMember.gif");
	    return isMemberIcon;
	}

	public static Icon getIsSpecializedFormIcon() {
	    if (isSpecializedFormIcon == null)
	        isSpecializedFormIcon = createImageIcon(new GKApplicationUtilities().getClass(), "isSpecializedForm.gif");
	    return isSpecializedFormIcon;
	}

	public static void setLookAndFeel(String name) {
		if (name == null || name.length() == 0) {
			name = getDefaultLF();
		}
		String osName = System.getProperty("os.name").toLowerCase();
		// Use MacOS aqua
		if (osName.indexOf("mac") > -1 && name.equals("aqua")) {
			try {
				UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                // The default white grid color cannot be displayed in MacOS X LF. Use LIGHT_GRAY
                // to force it display.
                UIManager.put("Table.gridColor", Color.LIGHT_GRAY);
			}
			catch(Exception e) {
				System.err.println("GKEditorFrame.setLookAndFeel() 1: " + e);
				e.printStackTrace();
			}
			return ;
		}
		else if (name.equals("metal")) {
			try {
				UIManager.setLookAndFeel(new MetalLookAndFeel());
			}
			catch(Exception e) {
				System.err.println("GKEditorFrame.setLookAndFeel(): " + e);
				e.printStackTrace();
			}
		}
		else if (name.equals("windows")) {
			try {
				// Use a class name instead of actual class to avoid compilation error
				// at other non-win32 platforms
				UIManager.setLookAndFeel("com.sun.java.swing.plaf.windows.WindowsLookAndFeel");
			}
			catch(Exception e) {
				System.err.println("GKEditorFrame.setLookAndFeel() 1: " + e);
				e.printStackTrace();
			}
		}
	}

//	private static void skinIt(String themeName) {
//		String fullName = "lib" + File.separator + "skin" + File.separator + themeName + "themepack.zip";
//		try {
//		    ClassLoader loader = new GKApplicationUtilities().getClass().getClassLoader();
//		    URL in = getResource(loader, fullName);
//		    Skin skin = SkinLookAndFeel.loadThemePack(in);
//			if (skin == null) {
//				skin = SkinLookAndFeel.getSkin();
//			}
//			if (skin != null) {
//				SkinLookAndFeel.setSkin(skin);
//				SkinLookAndFeel lnf = new SkinLookAndFeel();
//				UIManager.setLookAndFeel(lnf);
//			}
//		}
//		catch(Exception e) {
//			System.err.println("GKApplicationUtilities.skinIt(): " + e);
//		}
//	}

	public static void center(Component comp) {
		Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
		Dimension size = comp.getSize();
		int x = (screenSize.width - size.width) / 2;
		int y = (screenSize.height - size.height) / 2;
		comp.setLocation(x, y);
	}
	
	/**
	 * Place the comp component at the center of the target component.
	 * @param comp
	 * @param target
	 */
	public static void center(Component comp, Component target) {
	    int targetWidth = target.getWidth();
	    int targetHeight = target.getHeight();
	    Point targetPos = target.getLocationOnScreen();
	    int targetX = targetPos.x;
	    int targetY = targetPos.y;
	    // Make sure it has at least 10 pixel
	    int compW = Math.max(10, comp.getWidth());
	    int compH = Math.max(10, comp.getHeight());
	    int compX = targetX + (targetWidth - compW) / 2;
//	    if (compX < 0) // This may be negative in a multiple monitors setting.
//	        compX = 0;
	    int compY = targetY + (targetHeight - compH) / 2;
//	    if (compY < 0) // Don't do this correction. The y coordinate may be negative with multiple monitors.
//	        compY = 0;
	    comp.setLocation(compX, compY);
	}

	/**
	 * Generate a JDialog with the specified title. The owner container will
	 * be comp if comp is a container (either JFrame or JDialog), or the container
	 * in the hierarchy of comp.
	 *
	 * @param comp Component for which to generate a JDialog object
	 * @param title Title for the JDialog object
	 * @return JDialog object with the title and, potentially, the comp passed as its owner
	 */
	public static JDialog createDialog(Component comp, String title) {
	    if (comp instanceof JFrame)
	        return new JDialog((JFrame)comp, title);
	    if (comp instanceof JDialog)
	        return new JDialog((JDialog)comp, title);
	    JDialog dialog = (JDialog) SwingUtilities.getAncestorOfClass(JDialog.class, comp);
	    if (dialog != null)
	        return new JDialog(dialog, title);
	    JFrame frame = (JFrame) SwingUtilities.getAncestorOfClass(JFrame.class, comp);
	    if (frame != null)
	        return new JDialog(frame, title);
	    dialog = new JDialog();
	    dialog.setTitle(title);
	    return dialog;
	}

	/**
	 * Generates a Calendar version of the current date/time.
	 *
	 * @return Calendar object for the current date and time
	 */
	public static Calendar getCalendar() {
		// Use GMT time zone
		TimeZone timeZone = TimeZone.getTimeZone("GMT");
		return Calendar.getInstance(timeZone);
	}

	/**
	 * Note added by G.W. on Jan 26, 2023: This method should be used for process
	 * the dateTime string kept in the Reactome's InstanceEdit's dataTime slot string,
	 * which has added one to the Month. Prior to January 26, 2023, the month is not
	 * reduced to create Calendar. This has been fixed.
	 * This method is marked Deprecated. To parse the dateTime string in InstanceEdit,
	 * use org.gk.model.InstanceUtilities.getDateTimeInIE(GKInstance).
	 * Generates a Calendar version of the given date/time:
	 *
	 * YYYYMMDDhhmmss
	 *
	 * N.B. this won't work properly after 9999 AD.
	 *
	 * Returns null if any problems encountered.
	 *
	 * @param dateTime String of datetime in the format "YYYMMDDhhmmss"
	 * @return Calendar object for the datetime passed
	 */
	@Deprecated
	public static Calendar getCalendar(String dateTime) {
		if (dateTime==null || dateTime.equals(""))
			return null;
		
		// There are

		String year = dateTime.substring(0, 4);
		if (year==null || year.length()<4)
			return null;

		String month = dateTime.substring(4, 6);
		if (month==null || month.length()<2)
			return null;

		String day = dateTime.substring(6, 8);
		if (day==null || day.length()<2)
			return null;

		String hour = dateTime.substring(8, 10);
		if (hour==null || hour.length()<2)
			return null;

		String minute = dateTime.substring(10, 12);
		if (minute==null || minute.length()<2)
			return null;

		String second = dateTime.substring(12, 14);
		if (second==null || second.length()<2)
			return null;

		Calendar calendar = getCalendar();

		calendar.set(Calendar.YEAR, (new Integer(year)).intValue());
	      // This works for the dataTime slot in Reactome's InstanceEdit only
        // See getDateTime()
		int monthValue = Integer.parseInt(month);
		monthValue --;
		if (monthValue < 0) monthValue = 0; // Just in case. Should not happen.
		calendar.set(Calendar.MONTH, monthValue);
		calendar.set(Calendar.DAY_OF_MONTH, (new Integer(day)).intValue());
		calendar.set(Calendar.HOUR_OF_DAY, (new Integer(hour)).intValue());
		calendar.set(Calendar.MINUTE, (new Integer(minute)).intValue());
		calendar.set(Calendar.SECOND, (new Integer(second)).intValue());

		return(calendar);
	}

	/**
	 * Generates a String version of the current date/time:
	 *
	 * YYYYMMDDhhmmss
	 * 
	 * NB (GW on 1/26/23): The generated string is used for the local InstanceEdit generation. However,
	 * after this InstanceEdit is committed into a mysql database, the string will be automatically converted
	 * into the TimeStamp format as YYYY-MM-DD HH:mm:ss.S. In order to make this conversion correct, the month
	 * in Java has been increased by 1. 
	 *
	 * @return String of the current datetime in the format "YYYMMDDhhmmss"
	 */
	public static String getDateTime() {
		Calendar calendar = getCalendar();
		// The following code can be replaced by the following more standard wawy
//        TimeZone timeZone = TimeZone.getTimeZone("GMT");
//        SimpleDateFormat textFormatter = new SimpleDateFormat("yyyyMMddHHmmss");
//        textFormatter.setTimeZone(timeZone);
//		  return textFormatter.format(calendar.getTime());
		StringBuffer buffer = new StringBuffer();
		buffer.append(calendar.get(Calendar.YEAR));
		int month = calendar.get(Calendar.MONTH);
		month ++;
		if (month < 10)
			buffer.append("0" + month);
		else
			buffer.append(month);
		int day = calendar.get(Calendar.DAY_OF_MONTH);
		if (day < 10)
			buffer.append("0" + day);
		else
			buffer.append(day);
		int hour = calendar.get(Calendar.HOUR_OF_DAY);
		if (hour < 10)
			buffer.append("0" + hour);
		else
			buffer.append(hour);
		int minute = calendar.get(Calendar.MINUTE);
		if (minute < 10)
			buffer.append("0" + minute);
		else
			buffer.append(minute);
		int second = calendar.get(Calendar.SECOND);
		if (second < 10)
			buffer.append("0" + second);
		else
			buffer.append(second);
		return buffer.toString();
	}

	/**
	 * This method is used to create a JFileChooser which has a default directory set if any.
	 *
	 * @param gkProperties Properties file from which to get a "currentDir", if it contains that property
	 * @return JFileChooser object (with the current directory set if found in gkProperties)
	 */
	public static JFileChooser createFileChooser(Properties gkProperties) {
	    JFileChooser fileChooser = new JFileChooser();
	    // Set the current directory
	    if (gkProperties != null) {
	        String currentDir = gkProperties.getProperty("currentDir");
	        if (currentDir != null) {
	            File dir = new File(currentDir);
	            if (dir.exists())
	                fileChooser.setCurrentDirectory(dir);
	        }
	    }
	    return fileChooser;
	}

	/**
	 * Choose a file by using the selected fileDialog for saving a file.
	 *
	 * @param fileChooser JFileChooser object from which to choose a file
	 * @param parent Parent container for which to display messages
	 * @return File object selected
	 */
	public static File chooseSaveFile(JFileChooser fileChooser, Component parent) {
		File selectedFile = null;
		// Want to use a window container in case the passed parent is too big to cause
		// the dialog in a corner of the screen
		Window window = (Window) SwingUtilities.getAncestorOfClass(Window.class, parent);
		// For fileName checking
		while (true) {
			int reply = fileChooser.showSaveDialog(window);
			if (reply == JFileChooser.APPROVE_OPTION) {
				File file = fileChooser.getSelectedFile();
				String fileName = file.toString();
				// Make sure fileName is valid
				int index = fileName.lastIndexOf(".");
				if (index == -1) {// If no extension, add one
					FileFilter filter = fileChooser.getFileFilter();
					if (filter instanceof GKFileFilter) {
					    String ext = ((GKFileFilter)filter).getExtName();
						fileName += ext;
					}
					else if (filter instanceof XMLFileFilter) {
						fileName += ".xml";
					}
				}
				// Need a warning for overwriting
				file = new File(fileName);
				if (file.exists()) {
					int rtn = JOptionPane.showConfirmDialog(window,
															"The file \"" + fileName + "\" already exists.\n" +
															"Do you want to replace the existing file?",
															"File Overwriting Warning",
															JOptionPane.YES_NO_CANCEL_OPTION);
					if (rtn == JOptionPane.YES_OPTION) {
						selectedFile = file;
						break;
					}
					else if (rtn == JOptionPane.CANCEL_OPTION) {
						break;
					}
				}
				else {
					selectedFile = file;
					break;
				}
			}
			else
				break;
		}
		return selectedFile;
	}

	/**
	 * Choose a file by using the selected fileDialog for saving a file.
	 *
	 * @param fileChooser JFileChooser object from which to choose a file
	 * @param extName should contain ".", e.g., .xml.
	 * @param parent for popuping dialog.
	 * @return File object selected
	 */
	public static File chooseSaveFile(JFileChooser fileChooser,
	                                  String extName,
	                                  Component parent) {
		File selectedFile = null;
		// For fileName checking
		while (true) {
			int reply = fileChooser.showSaveDialog(parent);
			if (reply == JFileChooser.APPROVE_OPTION) {
				File file = fileChooser.getSelectedFile();
				String fileName = file.toString();
				// Make sure fileName is valid
				int index = fileName.lastIndexOf(".");
				if (index == -1) {// If no extension, add one
					fileName += extName;
				}
				// Need a warning for overwriting
				file = new File(fileName);
				if (file.exists()) {
					int rtn = JOptionPane.showConfirmDialog(parent,
															"The file \"" + fileName + "\" already exists.\n" +
															"Do you want to replace the existing file?",
															"File Overwriting Warning",
															JOptionPane.YES_NO_CANCEL_OPTION);
					if (rtn == JOptionPane.YES_OPTION) {
						selectedFile = file;
						break;
					}
					else if (rtn == JOptionPane.CANCEL_OPTION) {
						break;
					}
				}
				else {
					selectedFile = file;
					break;
				}
			}
			else
				break;
		}
		return selectedFile;
	}

	 /**
	  * Tile a list of Components within the specified Rectangle.
	  *
	  * @param components the Component list to be tiled.
	  * @param bounds the parent bounds all Components reside in.
	  */
	 public static void tileComponents(Component[] components, Rectangle bounds) {
		int size = components.length;
		int colNumber = (int) Math.ceil(Math.sqrt(size));
		int rowNumber = (int) Math.ceil((double)size / colNumber);
		int width = bounds.width / colNumber;
		int height = bounds.height / rowNumber;
		Component comp = null;
		int i = 0;
		for (int row = 0; row < rowNumber; row ++) {
			for (int col = 0; col < colNumber && i < size; col ++) {
				comp = components[i];
				comp.setSize(width, height);
				comp.setLocation(col * width, row * height);
				i ++;
			}
		}
	 }

	 public static ImageIcon createImageIcon(Class cls, String imgFileName) {
         return AuthorToolAppletUtilities.createImageIcon(imgFileName);
	 }

	 public static Dimension getToolBarBtnSize() {
	 	return btnSize;
	 }

	 /**
	  * Output a XML document to a local file.
	  *
	  * @param doc Document object whose contents are to be output to a file
	  * @param file File object for which to write the document contents
	  * @throws Exception Thrown if unable to parse the document or write the contents to the file
	  */
	 public static void outputXML(Document doc, File file) throws Exception {
         FileOutputStream xmlOut = new FileOutputStream(file);
         TransformerFactory tffactory = TransformerFactory.newInstance();
         Transformer transformer = tffactory.newTransformer();
         DOMSource source = new DOMSource(doc);
         StreamResult result = new StreamResult(xmlOut);
         transformer.transform(source, result);
	 }

	 /**
	  * Load an XML source into a Document.
	  *
	  * @param source InputStream from which to read and parse XML document
	  * @return Document generated from the source InputStream object
	  * @throws Exception Thrown if unable to build the document or parse the source InputStream to an XML document
	  */
	 public static Document loadXML(InputStream source) throws Exception {
	     DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
	     DocumentBuilder builder = dbf.newDocumentBuilder();
	     Document document = builder.parse(source);
	     return document;
	 }

	 /**
	  * Get a property file.
	  *
	  * @param fileName Name of the property file to obtain
	  * @return File object
	  * @throws IOException Thrown if the file specified does not exist
	  */
	 public static File getPropertyFile(String fileName) throws IOException {
	     String filePath = REACTOME_DIR + File.separator + fileName;
		 File file = new File(filePath);
	     return file;
	 }

     public static String getReactomeDir() {
         return REACTOME_DIR;
     }

     /**
	  * Get a configuration file. The configuration file can be in two places: in the installation folder from
	  * the installation or in the user's folder. The file in the user's folder has precedence to the one in
	  * the installation folder.
	  *
	  * @param fileName Name of the configuration file
	  * @return InputStream of the configuration file specified
	  * @throws IOException Thrown if unable to get an InputStream for the configuration file name passed
	  */
	 public static InputStream getConfig(String fileName) throws IOException {
	     File file = new File(REACTOME_DIR + File.separator + fileName);
	     if (file.exists())
	         return new FileInputStream(file);
	     return AuthorToolAppletUtilities.getResourceAsStream(fileName);
	 }

     public static File createTempFile(String fileName) throws IOException {
         File file = new File(REACTOME_DIR + File.separator + fileName);
         return file;
     }

     public static File getTempFile(String fileName) {
         File file = new File(REACTOME_DIR + File.separator + fileName);
         return file;
     }

	 /**
      * A utility method to copy a file.
      *
      * @param source File source from which to copy
      * @param dest File destination to which the source content should be copied
      * @throws IOException Thrown if unable to read the source file or write to the destination file
      */
     public static void copy(File source, File dest) throws IOException {
         FileInputStream fis = new FileInputStream(source);
         FileOutputStream fos = new FileOutputStream(dest);
         byte[] buffer = new byte[10 * 1024]; // 10 k
         int read = 0;
         while ((read = fis.read(buffer)) > 0) {
             fos.write(buffer, 0, read);
         }
         fis.close();
         fos.close();
     }

     public static String encrypt(String value) {
         char[] array = value.toCharArray();
         java.util.List list = new ArrayList(array.length);
         for (int i = 0; i < array.length; i++) {
             int tmp = (int) array[i];
             tmp += i * i;
             list.add(new Integer(tmp));
         }
         return list.toString();
     }

     public static String decrypt(String value) {
         return AuthorToolAppletUtilities.decrypt(value);
     }

     public static Object cloneViaIO(Object source) throws Exception {
         // Need a new copy of attributes
         ByteArrayOutputStream bos = new ByteArrayOutputStream(1024);
         ObjectOutputStream oos = new ObjectOutputStream(bos);
         oos.writeObject(source);
         ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
         ObjectInputStream ois = new ObjectInputStream(bis);
         Object clone = ois.readObject();
         return clone;
     }

     /**
      * A Utility method to delete a file or directory. If the provided parameter is a directory,
      * it must be empty. Otherwise, the build-in java deletion cannot work.
      *
      * @param file File or directory to delete
      */
     public static void delete(File file) {
         if (!file.isDirectory()) {
             file.delete();
             return;
         }
         File[] files = file.listFiles();
         if (files == null || files.length == 0) {
             file.delete();
             return;
         }
         for (File tmp : files)
             delete(tmp);
         file.delete();
     }

     public static void zipDir(File dir,
                               File dest,
                               String ext,
                               boolean needDelete) throws IOException {
         FileOutputStream fos = new FileOutputStream(dest);
         ZipOutputStream out = new ZipOutputStream(new BufferedOutputStream(fos));
         int buffer = 1024 * 2; // 2 k for buffer
         byte[] data = new byte[buffer];
         int count;
         for (File file : dir.listFiles()) {
             String name = file.getName();
             if (ext != null && !name.endsWith(ext)) {
                 continue;
             }
             FileInputStream fis = new FileInputStream(file);
             BufferedInputStream bis = new BufferedInputStream(fis, buffer);
             ZipEntry entry = new ZipEntry(file.getName());
             out.putNextEntry(entry);
             while ((count = bis.read(data, 0, buffer)) != -1) {
                 out.write(data, 0, count);
             }
             bis.close();
             fis.close();
         }
         out.close();
         if (needDelete) {
             for (File file : dir.listFiles()) {
                 if (ext != null && file.getName().endsWith(ext))
                     file.delete();
                 else if (!file.getName().endsWith(dest.getName())) // Delete all other files.
                     file.delete();
             }
         }
     }
}

