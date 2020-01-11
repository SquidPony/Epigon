/*******************************************************************************
 * Copyright 2011 See AUTHORS file.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *   http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ******************************************************************************/

package squidpony.epigon.playground.tests;

import java.io.Serializable;

/** A point in a 2D grid, with integer x and y coordinates
 * 
 * @author badlogic */
public class GridPoint2 implements Serializable {
	private static final long serialVersionUID = -4019969926331717380L;

	public int x;
	public int y;

	/** Constructs a new 2D grid point. */
	public GridPoint2 () {
	}

	/** Constructs a new 2D grid point.
	 * 
	 * @param x X coordinate
	 * @param y Y coordinate */
	public GridPoint2 (int x, int y) {
		this.x = x;
		this.y = y;
	}

	/** Copy constructor
	 * 
	 * @param point The 2D grid point to make a copy of. */
	public GridPoint2 (GridPoint2 point) {
		this.x = point.x;
		this.y = point.y;
	}

	/** Sets the coordinates of this 2D grid point to that of another.
	 * 
	 * @param point The 2D grid point to copy the coordinates of.
	 * 
	 * @return this 2D grid point for chaining. */
	public GridPoint2 set (GridPoint2 point) {
		this.x = point.x;
		this.y = point.y;
		return this;
	}

	/** Sets the coordinates of this 2D grid point.
	 * 
	 * @param x X coordinate
	 * @param y Y coordinate
	 * 
	 * @return this 2D grid point for chaining. */
	public GridPoint2 set (int x, int y) {
		this.x = x;
		this.y = y;
		return this;
	}

	/**
	 * @param other The other point
	 * @return the squared distance between this point and the other point.
	 */
	public float dst2 (GridPoint2 other) {
		int xd = other.x - x;
		int yd = other.y - y;

		return xd * xd + yd * yd;
	}

	/**
	 * @param x The x-coordinate of the other point
	 * @param y The y-coordinate of the other point
	 * @return the squared distance between this point and the other point.
	 */
	public float dst2 (int x, int y) {
		int xd = x - this.x;
		int yd = y - this.y;

		return xd * xd + yd * yd;
	}

	/**
	 * @param other The other point
	 * @return the distance between this point and the other vector.
	 */
	public float dst (GridPoint2 other) {
		int xd = other.x - x;
		int yd = other.y - y;

		return (float)Math.sqrt(xd * xd + yd * yd);
	}

	/**
	 * @param x The x-coordinate of the other point
	 * @param y The y-coordinate of the other point
	 * @return the distance between this point and the other point.
	 */
	public float dst (int x, int y) {
		int xd = x - this.x;
		int yd = y - this.y;

		return (float)Math.sqrt(xd * xd + yd * yd);
	}

	/**
	 * Adds another 2D grid point to this point.
	 *
	 * @param other The other point
	 * @return this 2d grid point for chaining.
	 */
	public GridPoint2 add (GridPoint2 other) {
		x += other.x;
		y += other.y;
		return this;
	}

	/**
	 * Adds another 2D grid point to this point.
	 *
	 * @param x The x-coordinate of the other point
	 * @param y The y-coordinate of the other point
	 * @return this 2d grid point for chaining.
	 */
	public GridPoint2 add (int x, int y) {
		this.x += x;
		this.y += y;
		return this;
	}

	/**
	 * Subtracts another 2D grid point from this point.
	 *
	 * @param other The other point
	 * @return this 2d grid point for chaining.
	 */
	public GridPoint2 sub (GridPoint2 other) {
		x -= other.x;
		y -= other.y;
		return this;
	}

	/**
	 * Subtracts another 2D grid point from this point.
	 *
	 * @param x The x-coordinate of the other point
	 * @param y The y-coordinate of the other point
	 * @return this 2d grid point for chaining.
	 */
	public GridPoint2 sub (int x, int y) {
		this.x -= x;
		this.y -= y;
		return this;
	}

	/**
	 * @return a copy of this grid point
	 */
	public GridPoint2 cpy () {
		return new GridPoint2(this);
	}

	@Override
	public boolean equals (Object o) {
		if (this == o) return true;
		if (o == null || o.getClass() != this.getClass()) return false;
		GridPoint2 g = (GridPoint2)o;
		return this.x == g.x && this.y == g.y;
	}

	@Override
	public int hashCode () {
		//5670185991ns taken, about 10 to the 9.753597304667192 power.
//		return (int)((x * 0xC13FA9A902A6328FL + y * 0x91E10DA5C79E7B1DL) >>> 32);
		//4828246273ns taken, about 10 to the 9.6837894139268 power.
//		final long xx = (x << 1 ^ x >> 31) + 0x80000000L;
//		final long yy = (y << 1 ^ y >> 31) + 0x80000000L;
		final long xx = (x << 1 ^ x >> 31) + 0x80000000L;
		final long yy = (y << 1 ^ y >> 31) + 0x80000000L;
		return (int) (xx > yy ? xx * xx + xx + xx - yy : yy * yy + xx);
//		return (int) (xx > yy 
//			? ((xx & 1) == 0 ? xx * xx + xx + xx - yy : yy * yy + yy + yy - xx) 
//			: ((yy & 1) == 0 ? yy * yy + xx : xx * xx + yy));

		//// handy way of making all but the largest x or y into a positive number, with negative originals odd.
//		final int xx = x << 1 ^ x >> 31;
//		final int yy = y << 1 ^ y >> 31;
//		////Rosenberg-Strong Pairing Function
//		////assigns numbers to (x,y) pairs, assigning bigger numbers to bigger shells (the shell is max(x,y)).
//		return xx + (xx >= yy ? xx * xx + xx - yy : yy * yy);
		////Cantor Pairing Function
		////also assigns numbers to (x,y) pairs, but shells are triangular stripes instead of right angles.
//		return yy + ((xx + yy) * (xx + yy + 1) >> 1);

		////this one's... OK.
//		return x * 0xC13F + y * 0x91E1;
	}

	@Override
	public String toString () {
		return "(" + x + ", " + y + ")";
	}
}
