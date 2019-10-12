package org.gk.render;

import java.awt.Dimension;
import java.awt.Rectangle;

public class EllipsesUtils {
	public EllipsesUtils() {

	}

	/**
	 * Helper function for {@link EllipsesUtils#getLabelDimensions(String, Dimension, Dimension)}.
	 * 
	 * @param position
	 * @param labelWidthHeight
	 * @param bounds
	 * @return Dimension
	 */
	protected static Dimension getLabelDimensions(String position, Dimension labelWidthHeight, Rectangle bounds) {
		Dimension oldDimensions = getEllipsesDimensions(position, bounds);
		return getLabelDimensions(position, oldDimensions, labelWidthHeight);	
	}

	/**
	 * "Corrects" the label dimensions by moving it left, right, up, or down to fit it within the ellipses' boundary.
	 * 
	 * @param position
	 * @param labelWidthHeight
	 * @param oldDimension
	 * @return Dimension
	 */
	private static Dimension getLabelDimensions(String position, Dimension oldDimension, Dimension labelWidthHeight) {
		double x = oldDimension.getWidth();
		double y = oldDimension.getHeight();
		double width = labelWidthHeight.getWidth();
		double height = labelWidthHeight.getHeight();
		Dimension newDimensions = new Dimension();
		switch (position.toUpperCase()) {
			case "RIGHT":
				newDimensions.setSize(x - width * 2, y - height / 2);
				break;
			case "TOPRIGHT":
				newDimensions.setSize(x - width,     y + height);
				break;
			case "TOP":
				newDimensions.setSize(x - width / 2, y + height);
				break;
			case "TOPLEFT":
				newDimensions.setSize(x + width,     y + height);
				break;
			case "LEFT":
				newDimensions.setSize(x + width,     y - height / 2);
				break;
			case "BOTTOMLEFT":
				newDimensions.setSize(x + width,     y - height);	
				break;
			case "BOTTOM":
				newDimensions.setSize(x - width / 2, y - height * 2);
				break;
			case "BOTTOMRIGHT":
				newDimensions.setSize(x - width,     y - height);
				break;
		}

		return newDimensions;
	}

	/**
	 * Returns a dimension corresponding to a point on the boundary of an ellipses.
	 * Supported positions (case-insensitive): RIGHT, TOPRIGHT, TOP, TOPLEFT, LEFT, BOTTOMLEFT, BOTTOM, BOTTOMRIGHT.
	 * 
	 * @param position
	 * @param bounds
	 * @return Dimension
	 */
	private static Dimension getEllipsesDimensions(String position, Rectangle bounds) {
		double radians = 0, x, y;

		switch (position.toUpperCase()) {
			case "RIGHT":
				radians = EllipsesPosition.RIGHT.getRadians();
				break;
			case "TOPRIGHT":
				radians = EllipsesPosition.TOPRIGHT.getRadians();
				break;
			case "TOP":
				radians = EllipsesPosition.TOP.getRadians();
				break;
			case "TOPLEFT":
				radians = EllipsesPosition.TOPLEFT.getRadians();
				break;
			case "LEFT":
				radians = EllipsesPosition.LEFT.getRadians();
				break;
			case "BOTTOMLEFT":
				radians = EllipsesPosition.BOTTOMLEFT.getRadians();
				break;
			case "BOTTOM":
				radians = EllipsesPosition.BOTTOM.getRadians();
				break;
			case "BOTTOMRIGHT":
				radians = EllipsesPosition.BOTTOMRIGHT.getRadians();
				break;
		}

		double a = bounds.getWidth() / 2.0;
		double b = bounds.getHeight() / 2.0;
		x = (int) (bounds.getCenterX() + a * Math.cos(radians));
		y = (int) (bounds.getCenterY() - b * Math.sin(radians));

		Dimension xy = new Dimension();
		xy.setSize(x, y);
		return xy;
	}

	/**
	 * Radians for each position along an ellipses.
	 */
	private enum EllipsesPosition {
		RIGHT       (0.               ),
		TOPRIGHT    (     Math.PI / 4.),
		TOP         (     Math.PI / 2.),
		TOPLEFT     (3. * Math.PI / 4.),
		LEFT        (     Math.PI     ),
		BOTTOMLEFT  (5. * Math.PI / 4.),
		BOTTOM      (3. * Math.PI / 2.),
		BOTTOMRIGHT (7. * Math.PI / 4.);

		EllipsesPosition(double radians) {
			this.radians = radians;
		}

		private double radians;

		public double getRadians() {
			return radians;
		}
	}
}