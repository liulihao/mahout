package org.apache.mahout.classifier.sgd;

import com.google.common.collect.Sets;
import org.apache.mahout.classifier.MurmurHash;
import org.apache.mahout.math.Vector;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Record values are what is notionally stored in fields of Records for learning.  In fact, however,
 * all we ever do is encode the values directly from their original string-like representation
 * into a vectorized format.
 *
 * By convention, sub-classes should provide a constructor that accepts just a field name as
 * well as setters to customize properties of the conversion such as adding tokenizers or a
 * weight dictionary.
 */
public abstract class RecordValueEncoder {
  protected static final int CONTINUOUS_VALUE_HASH_SEED = 1;
  protected static final int WORD_LIKE_VALUE_HASH_SEED = 100;

  protected Random random;
  protected String name;
  protected int probes = 1;

  private Map<String, Set<Integer>> traceDictionary = null;

  public RecordValueEncoder(String name) {
    this.name = name;
  }

  /**
   * Adds a value to a vector.
   * @param originalForm  The original form of the value as a string.
   * @param data The vector to which the value should be added.
   */
  public abstract void addToVector(String originalForm, Vector data);

  // ******* Utility functions used by most implementations

   /**
   * Hash a string and an integer into the range [0..numFeatures-1].
   *
   * @param term   The string.
   * @param probe  An integer that modifies the resulting hash.
   * @param numFeatures  The range into which the resulting hash must fit.
   * @return An integer in the range [0..numFeatures-1] that has good spread for small changes in term and probe.
   */
  protected int hash(String term, int probe, int numFeatures) {
    long r = MurmurHash.hash64A(term.getBytes(Charset.forName("UTF-8")), probe) % numFeatures;
    if (r < 0) {
      r += numFeatures;
    }
    return (int) r;
  }

  /**
   * Hash two strings and an integer into the range [0..numFeatures-1].
   *
   * @param term1   The first string.
   * @param term2   The second string.
   * @param probe  An integer that modifies the resulting hash.
   * @param numFeatures  The range into which the resulting hash must fit.
   * @return An integer in the range [0..numFeatures-1] that has good spread for small changes in term and probe.
   */
  protected int hash(String term1, String term2, int probe, int numFeatures) {
    long r = MurmurHash.hash64A(term1.getBytes(Charset.forName("UTF-8")), probe);
    r = MurmurHash.hash64A(term2.getBytes(Charset.forName("UTF-8")), (int) r) % numFeatures;
    if (r < 0) {
      r += numFeatures;
    }
    return (int) r;
  }

  /**
   * Converts a value into a form that would help a human understand the internals of
   * how the value is being interpreted.  For text-like things, this is likely to be
   * a list of the terms found with associated weights (if any).
   * @param originalForm  The original form of the value as a string.
   * @return A string that a human can read.
   */
  public abstract String asString(String originalForm);

  /**
   * Sets the number of locations in the feature vector that a value should be in.
   * @param probes  Number of locations to increment.
   */
  public void setProbes(int probes) {
    this.probes = probes;
  }

  public String getName() {
    return name;
  }

  protected void trace(String name, String subName, int n) {
    if (traceDictionary != null) {
      String key = name;
      if (subName != null) {
        key = name + "=" + subName;
      }
      Set<Integer> trace = traceDictionary.get(key);
      if (trace == null) {
        trace = Sets.newHashSet(n);
        traceDictionary.put(key, trace);
      } else {
        trace.add(n);
      }
    }
  }

  public void setTraceDictionary(Map<String, Set<Integer>> traceDictionary) {
    this.traceDictionary = traceDictionary;
  }
}
