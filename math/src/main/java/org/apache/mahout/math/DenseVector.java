/**
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.mahout.math;

import java.util.Arrays;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** Implements vector as an array of doubles */
public class DenseVector extends AbstractVector {

  protected double[] values;
  protected double lengthSquared = -1.0;

  /** For serialization purposes only */
  public DenseVector() {
  }

  public DenseVector(String name) {
    super(name);
  }

  /** Construct a new instance using provided values */
  public DenseVector(double[] values) {
    this(values, false);
  }

  public DenseVector(double[] values, boolean shallowCopy) {
    this.values = shallowCopy ? values : values.clone();
  }

  public DenseVector(String name, double[] values) {
    super(name);
    this.values = values.clone();
  }

  /** Construct a new instance of the given cardinality */
  public DenseVector(int cardinality) {
    this(null, cardinality);
  }

  public DenseVector(String name, int cardinality) {
    super(name);
    this.values = new double[cardinality];
  }

  /**
   * Copy-constructor (for use in turning a sparse vector into a dense one, for example)
   * @param vector
   */
  public DenseVector(Vector vector) {
    super(vector.getName());
    values = new double[vector.size()];
    Iterator<Vector.Element> it = vector.iterateNonZero();
    while(it.hasNext()) {
      Vector.Element e = it.next();
      values[e.index()] = e.get();
    }
  }

  @Override
  protected Matrix matrixLike(int rows, int columns) {
    return new DenseMatrix(rows, columns);
  }

  @Override
  public int size() {
    return values.length;
  }

  @Override
  public DenseVector clone() {
    DenseVector clone = (DenseVector) super.clone();
    clone.values = values.clone();
    return clone;
  }

  @Override
  public double getQuick(int index) {
    return values[index];
  }

  @Override
  public DenseVector like() {
    DenseVector denseVector = new DenseVector(size());
    denseVector.setLabelBindings(getLabelBindings());
    return denseVector;
  }

  @Override
  public Vector like(int cardinality) {
    Vector denseVector = new DenseVector(cardinality);
    denseVector.setLabelBindings(getLabelBindings());
    return denseVector;
  }

  @Override
  public void setQuick(int index, double value) {
    lengthSquared = -1.0;
    values[index] = value;
  }

  @Override
  public Vector assign(Vector other, BinaryFunction function) {
    if (other.size() != size()) {
      throw new CardinalityException();
    }
    // is there some other way to know if function.apply(0, x) = x for all x?
    if(function instanceof PlusFunction || function instanceof PlusWithScaleFunction) {
      Iterator<Vector.Element> it = other.iterateNonZero();
      Vector.Element e;
      while(it.hasNext() && (e = it.next()) != null) {
        values[e.index()] = function.apply(values[e.index()], e.get());
      }
    } else {
      for (int i = 0; i < size(); i++) {
        values[i] = function.apply(values[i], other.getQuick(i));
      }
    }
    return this;
  }

  @Override
  public int getNumNondefaultElements() {
    return values.length;
  }

  @Override
  public Vector viewPart(int offset, int length) {
    if (length > values.length) {
      throw new CardinalityException();
    }
    if (offset < 0 || offset + length > values.length) {
      throw new IndexException();
    }
    return new VectorView(this, offset, length);
  }

  /**
   * Returns an iterator that traverses this Vector from 0 to cardinality-1, in that order.
   *
   * @see java.lang.Iterable#iterator
   */
  @Override
  public Iterator<Vector.Element> iterateNonZero() {
    return new NonZeroIterator();
  }

  @Override
  public Iterator<Vector.Element> iterateAll() {
    return new AllIterator();
  }

  private class NonZeroIterator implements Iterator<Vector.Element> {

    private final Element element = new Element(0);
    private int offset;

    private NonZeroIterator() {
      goToNext();
    }

    private void goToNext() {
      while (offset < values.length && values[offset] == 0) {
        offset++;
      }
    }

    @Override
    public boolean hasNext() {
      return offset < values.length;
    }

    @Override
    public Vector.Element next() {
      if (offset >= values.length) {
        throw new NoSuchElementException();
      }
      element.ind = offset;
      offset++;
      goToNext();
      return element;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  private class AllIterator implements Iterator<Vector.Element> {

    private final Element element = new Element(-1);

    @Override
    public boolean hasNext() {
      return element.ind + 1 < values.length;
    }

    @Override
    public Vector.Element next() {
      if (!hasNext()) {
        throw new NoSuchElementException();
      }
      element.ind++;
      return element;
    }

    @Override
    public void remove() {
      throw new UnsupportedOperationException();
    }
  }

  public class Element implements Vector.Element {

    private int ind;

    public Element(int ind) {
      this.ind = ind;
    }

    @Override
    public double get() {
      return values[ind];
    }

    @Override
    public int index() {
      return ind;
    }

    @Override
    public void set(double value) {
      lengthSquared = -1.0;
      values[ind] = value;
    }
  }

  @Override
  public Vector.Element getElement(int index) {
    return new Element(index);
  }

  /**
   * Indicate whether the two objects are the same or not. Two {@link org.apache.mahout.math.Vector}s can be equal
   * even if the underlying implementation is not equal.
   *
   * @param o The object to compare
   * @return true if the objects have the same cell values and same name, false otherwise.
   * @see AbstractVector#strictEquivalence(Vector, Vector)
   * @see AbstractVector#equivalent(Vector, Vector)
   */
  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof Vector)) {
      return false;
    }

    Vector that = (Vector) o;
    String thisName = getName();
    String thatName = that.getName();
    if (this.size() != that.size()) {
      return false;
    }
    if (thisName != null && thatName != null && !thisName.equals(thatName)) {
      return false;
    } else if ((thisName != null && thatName == null)
        || (thatName != null && thisName == null)) {
      return false;
    }

    if (that instanceof DenseVector) {
      if (!Arrays.equals(values, ((DenseVector) that).values)) {
        return false;
      }
    } else {
      return equivalent(this, that);
    }

    return true;
  }


  @Override
  public double getLengthSquared() {
    if (lengthSquared >= 0.0) {
      return lengthSquared;
    }

    double result = 0.0;
    for (double value : values) {
      result += value * value;

    }
    lengthSquared = result;
    return result;
  }

  @Override
  public double getDistanceSquared(Vector v) {
    double result = 0.0;
    for (int i = 0; i < values.length; i++) {
      double delta = values[i] - v.getQuick(i);
      result += delta * delta;
    }
    return result;
  }

  @Override
  public void addTo(Vector v) {
    if (v.size() != size()) {
      throw new CardinalityException();
    }
    for (int i = 0; i < values.length; i++) {
      v.setQuick(i, values[i] + v.getQuick(i));
    }
  }
}
