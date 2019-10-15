package org.gk.render;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;

class EllipsesUtils {
	/**
	 * Helper function for {@link EllipsesUtils#getLabelCoordinates(String, Dimension, Dimension)}.
	 * 
	 * @param position
	 * @param labelCoordinates
	 * @param bounds
	 * @return Dimension
	 */
	Point getLabelCoordinates(EllipsesPosition position, Dimension labelDimensions, Rectangle bounds) {
		Point oldCoordinates = getEllipsesCoordinates(position, bounds);
		return getLabelCoordinates(position, oldCoordinates, labelDimensions, bounds);	
	}

	/**
	 * "Corrects" the label dimensions by moving it left, right, up, or down to fit within the ellipses' boundary.
	 * 
	 * @param position
	 * @param labelCoordinates
	 * @param oldCoordinates
	 * @return Point
	 */
	private Point getLabelCoordinates(EllipsesPosition position, Point oldCoordinates, Dimension labelDimensions, Rectangle bounds) {
		double x           = oldCoordinates.getX();
		double y           = oldCoordinates.getY();
		double labelWidth  = labelDimensions.getWidth();
		double labelHeight = labelDimensions.getHeight();
		double boundsTop   = bounds.getMinY();
		double boundsLeft  = bounds.getMinX();

		y += 2 * labelHeight * ((boundsTop - y)          / y);
		x -= 2 * labelWidth  * ((x         - boundsLeft) / x);

		Point newCoordinates = new Point();
		newCoordinates.setLocation(x, y);
		return newCoordinates;
	}

	/**
	 * Returns a dimension corresponding to a point on the boundary of an ellipse.
	 * Supported positions (case-insensitive): RIGHT, TOPRIGHT, TOP, TOPLEFT, LEFT, BOTTOMLEFT, BOTTOM, BOTTOMRIGHT.
	 * 
	 * @param position
	 * @param bounds
	 * @return Point
	 */
	private Point getEllipsesCoordinates(EllipsesPosition position, Rectangle bounds) {
		double radians = 0, x, y;

		switch (position) {
			case RIGHT:
				radians = EllipsesPosition.RIGHT.getRadians();
				break;
			case TOPRIGHT:
				radians = EllipsesPosition.TOPRIGHT.getRadians();
				break;
			case TOP:
				radians = EllipsesPosition.TOP.getRadians();
				break;
			case TOPLEFT:
				radians = EllipsesPosition.TOPLEFT.getRadians();
				break;
			case LEFT:
				radians = EllipsesPosition.LEFT.getRadians();
				break;
			case BOTTOMLEFT:
				radians = EllipsesPosition.BOTTOMLEFT.getRadians();
				break;
			case BOTTOM:
				radians = EllipsesPosition.BOTTOM.getRadians();
				break;
			case BOTTOMRIGHT:
				radians = EllipsesPosition.BOTTOMRIGHT.getRadians();
				break;
		    default:
				radians = EllipsesPosition.BOTTOMRIGHT.getRadians();
		}

		double a = bounds.getWidth() / 2.0;
		double b = bounds.getHeight() / 2.0;
		x = bounds.getCenterX() + a * Math.cos(radians);
		y = bounds.getCenterY() - b * Math.sin(radians);

		Point xy = new Point();
		xy.setLocation(x, y);
		return xy;
	}

	/**
	 * Radians for each position along an ellipses.
	 */
	static enum EllipsesPosition {
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