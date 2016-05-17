/*
 * Created on Jul 8, 2015
 *
 */
package scratch;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import javax.swing.JComponent;

import org.gk.util.SwingImageCreator;
import org.junit.Test;

/**
 * Just a test class to draw some prototype for a network holistic view.
 * @author Gwu
 *
 */
public class NetworkHolisticView {

	public NetworkHolisticView() {
	}
	
	@Test
	public void generateRandomNetwork() {
	    int vCount = 25;
	    int edgeCount = 5;
	    List<String> vertices = new ArrayList<String>();
	    for (int i = 0; i < vCount; i++) {
	        vertices.add("Module" + (i + 1));
	    }
	    Set<String> edges = new HashSet<String>();
	    Random random = new Random();
	    for (int i = 0; i < vertices.size(); i++) {
	        String v1 = vertices.get(i);
	        // This is used to control how many edges in average
	        int count = random.nextInt(edgeCount);
	        for (int j = 0; j < count; j++) {
	            int j1 = random.nextInt(vCount);
	            String v2 = vertices.get(j1);
	            if (i < j1)
	                edges.add(v1 + "\t" + v2);
	            else if (i > j1)
	                edges.add(v2 + "\t" + v1);
	        }
	    }
//	    System.out.println("Total edges: " + edges.size());
	    for (String edge : edges)
	        System.out.println(edge);
	    int[] colors = new DrawPanel().getColors(vCount);
	    System.out.println();
	    // Attributes
	    int size = 100;
	    System.out.println("Name\tSize\tColor");
	    for (int i = 0; i < vCount; i++) {
	        Color color = new Color(colors[i]);
	        // As this format: rgb(255,0,255)
	        String colorText = "rgb(" + color.getRed() + "," + color.getGreen() + "," + color.getBlue() + ")";
	        System.out.println(vertices.get(i) + "\t" + 
	                           random.nextInt(size) + "\t" + 
	                           colorText);
	    }
	}
	
	@Test
	public void check() {
		int[] values = new int[]{16711680, 16580864};
		for (int value : values)
			System.out.println(new Color(value));
	}
	
	@Test
	public void draw() throws IOException {
	    String fileName = "tmp/SortedGrid.pdf";
	    DrawPanel drawPanel = new DrawPanel();
	    drawPanel.setSize(300, 300);
	    SwingImageCreator.exportImageInPDF(drawPanel, new File(fileName));
	    
	    drawPanel = new DrawPanel();
	    drawPanel.setSize(300, 300);
	    drawPanel.setNeedRandom(true);
	    fileName = "tmp/RandomGrid.pdf";
	    SwingImageCreator.exportImageInPDF(drawPanel, new File(fileName));
	    List<Integer> randomIndices = drawPanel.getRandomIndex();
	    
	    drawPanel = new DrawPanel();
	    drawPanel.setSize(300, 300);
	    drawPanel.setNeedRandom(false);
	    drawPanel.setRandomIndex(randomIndices);
	    fileName = "tmp/DiffGrid.pdf";
	    SwingImageCreator.exportImageInPDF(drawPanel, new File(fileName));
	}
	
	private class DrawPanel extends JComponent {
	    private List<Integer> randomIndex;
	    private List<Integer> diffValues;
	    private Integer maxDiff;
	    private Integer minDiff;
	    private boolean needRandom = false;
		
		DrawPanel() {
		    randomIndex = new ArrayList<Integer>();
		}
		
		public List<Integer> getRandomIndex() {
            return randomIndex;
        }

        public void setRandomIndex(List<Integer> randomIndex) {
            this.randomIndex = randomIndex;
            if (randomIndex == null || randomIndex.size() == 0)
                return;
            diffValues = new ArrayList<Integer>();
            maxDiff = Integer.MIN_VALUE;
            minDiff = Integer.MAX_VALUE;
            for (int i = 0; i < randomIndex.size(); i++) {
                int diff = i - randomIndex.get(i);
                if (diff > maxDiff)
                    maxDiff = diff;
                if (diff < minDiff)
                    minDiff = diff;
                diffValues.add(diff);
            }
        }
        
        public boolean isNeedRandom() {
            return needRandom;
        }

        public void setNeedRandom(boolean needRandom) {
            this.needRandom = needRandom;
        }

        private int[] getColors(int w,
		                        int h) {
            int size = w * h;
			return getColors(size);
		}

        private int[] getColors(int size) {
            int color1 = Color.red.getRGB();
			int color2 = Color.green.getRGB();
	        int r1 = (color1 >> 16) & 0xff;
	        int g1 = (color1 >> 8) & 0xff;
	        int b1 = (color1) & 0xff;
	        int dr = ((color2 >> 16) & 0xff) - r1;
	        int dg = ((color2 >> 8) & 0xff) - g1;
	        int db = ((color2) & 0xff) - b1;
	        int[] colors = new int[size]; 
	        for (int i = 0; i < colors.length; i++) {
	            float rel = (float) i / colors.length;
	            int rgb = (((int) (r1 + rel * dr)) << 16) |
	                      (((int) (g1 + rel * dg)) << 8) |
	                      (((int) (b1 + rel * db)));
	            colors[i] = rgb;
	        }
	        return colors;
        }
        
        private int getColorIndex(int index,
                                  int length) {
            if (needRandom) {
                int rtn = (int) (Math.random() * length);
                randomIndex.add(rtn);
                return rtn;
            }
            else if (diffValues != null && diffValues.size() > 0) {
                return (int) ((float)(diffValues.get(index) - minDiff)/(maxDiff - minDiff) * (length - 1));
            }
            return index;
        }
		
		@Override
		public void paint(Graphics g) {
			long time1 = System.currentTimeMillis();
			Graphics2D g2 = (Graphics2D) g;
			int w = getWidth();
			int h = getHeight();
			g2.clearRect(0, 0, w, h);
			g2.setPaint(Color.black);
			g2.setStroke(new BasicStroke(1.0f));
			int count = 150;
			double step = w / (double) count;
			Rectangle2D rect2d = new Rectangle2D.Double(0.0d, 0.0d, step, step);
			int[] colors = getColors(count, count);
			int index = 0;
			for (int i = 0; i < count; i++) {
				for (int j = 0; j < count; j++) {
					rect2d.setRect(j * step, i * step, step, step);
					int colorIndex = getColorIndex(index, colors.length);
					Color color = new Color(colors[colorIndex]);
					g2.setPaint(color);
					g2.fill(rect2d);
					index ++;
				}
			}
			long time2 = System.currentTimeMillis();
//			System.out.println("Time: " + (time2 - time1));
		}
		
	}
	
}
