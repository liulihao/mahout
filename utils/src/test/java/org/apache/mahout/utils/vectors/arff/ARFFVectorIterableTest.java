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

package org.apache.mahout.utils.vectors.arff;

import java.text.DateFormat;
import java.util.Iterator;
import java.util.Map;

import junit.framework.Assert;

import org.apache.mahout.common.MahoutTestCase;
import org.apache.mahout.math.DenseVector;
import org.apache.mahout.math.RandomAccessSparseVector;
import org.apache.mahout.math.Vector;

public class ARFFVectorIterableTest extends MahoutTestCase {
  
  public void testValues() throws Exception {
    StringBuilder builder = new StringBuilder();
    builder.append("%comments").append('\n').append("@RELATION Mahout").append('\n')
    .append("@ATTRIBUTE foo numeric").append('\n')
    .append("@ATTRIBUTE bar numeric").append('\n')
    .append("@ATTRIBUTE timestamp DATE \"yyyy-MM-dd HH:mm:ss\"").append('\n')
    .append("@ATTRIBUTE junk string").append('\n')
    .append("@ATTRIBUTE theNominal {c,b,a}").append('\n')
    .append("@DATA").append('\n')
    .append("1,2, \"2009-01-01 5:55:55\", foo, c").append('\n')
    .append("2,3").append('\n')
    .append("{0 5,1 23}").append('\n');
    ARFFModel model = new MapBackedARFFModel();
    ARFFVectorIterable iterable = new ARFFVectorIterable(builder.toString(), model);
    Assert.assertEquals("Mahout", iterable.getModel().getRelation());
    Map<String, Integer> bindings = iterable.getModel().getLabelBindings();
    Assert.assertNotNull(bindings);
    Assert.assertEquals(5, bindings.size());
    Iterator<Vector> iter = iterable.iterator();
    Assert.assertTrue(iter.hasNext());
    Vector next = iter.next();
    Assert.assertNotNull(next);
    Assert.assertTrue("Wrong instanceof", next instanceof DenseVector);
    Assert.assertEquals(1.0, next.get(0));
    Assert.assertEquals(2.0, next.get(1));
    Assert.assertTrue(iter.hasNext());
    next = iter.next();
    Assert.assertNotNull(next);
    Assert.assertTrue("Wrong instanceof", next instanceof DenseVector);
    Assert.assertEquals(2.0, next.get(0));
    Assert.assertEquals(3.0, next.get(1));
    
    Assert.assertTrue(iter.hasNext());
    next = iter.next();
    Assert.assertNotNull(next);
    Assert.assertTrue("Wrong instanceof", next instanceof RandomAccessSparseVector);
    Assert.assertEquals(5.0, next.get(0));
    Assert.assertEquals(23.0, next.get(1));
    
    Assert.assertFalse(iter.hasNext());
  }
  
  public void testDense() throws Exception {
    ARFFModel model = new MapBackedARFFModel();
    ARFFVectorIterable iterable = new ARFFVectorIterable(SAMPLE_DENSE_ARFF, model);
    int count = 0;
    for (Vector vector : iterable) {
      Assert.assertTrue("Vector is not dense", vector instanceof DenseVector);
      count++;
    }
    Assert.assertEquals(10, count);
  }
  
  public void testSparse() throws Exception {
    ARFFModel model = new MapBackedARFFModel();
    ARFFVectorIterable iterable = new ARFFVectorIterable(SAMPLE_SPARSE_ARFF, model);
    int count = 0;
    for (Vector vector : iterable) {
      Assert.assertTrue("Vector is not dense", vector instanceof RandomAccessSparseVector);
      count++;
    }
    Assert.assertEquals(10, count);
  }
  
  public void testNonNumeric() throws Exception {
    
    MapBackedARFFModel model = new MapBackedARFFModel();
    ARFFVectorIterable iterable = new ARFFVectorIterable(NON_NUMERIC_ARFF, model);
    int count = 0;
    for (Vector vector : iterable) {
      Assert.assertTrue("Vector is not dense", vector instanceof RandomAccessSparseVector);
      count++;
    }
    Assert.assertEquals(10, count);
    Map<String, Map<String, Integer>> nominalMap = iterable.getModel().getNominalMap();
    Assert.assertNotNull(nominalMap);
    Assert.assertEquals(1, nominalMap.size());
    Map<String, Integer> noms = nominalMap.get("bar");
    Assert.assertNotNull("nominals for bar are null", noms);
    Assert.assertEquals(2, noms.size());
    Map<Integer, ARFFType> integerARFFTypeMap = model.getTypeMap();
    Assert.assertNotNull("Type map null", integerARFFTypeMap);
    Assert.assertEquals(5, integerARFFTypeMap.size());
    Map<String, Long> words = model.getWords();
    Assert.assertNotNull("words null", words);
    Assert.assertEquals(10, words.size());
    //System.out.println("Words: " + words);
    Map<Integer, DateFormat> integerDateFormatMap = model.getDateMap();
    Assert.assertNotNull("date format null", integerDateFormatMap);
    Assert.assertEquals(1, integerDateFormatMap.size());
    
  }
  
  public void testMultipleNoms() throws Exception {
    MapBackedARFFModel model = new MapBackedARFFModel();
    ARFFVectorIterable iterable = new ARFFVectorIterable(NON_NUMERIC_ARFF, model);
    int count = 0;
    for (Vector vector : iterable) {
      Assert.assertTrue("Vector is not dense", vector instanceof RandomAccessSparseVector);
      count++;
    }
    Assert.assertEquals(10, count);
    Map<String, Map<String, Integer>> nominalMap = iterable.getModel().getNominalMap();
    Assert.assertNotNull(nominalMap);
    Assert.assertEquals(1, nominalMap.size());
    Map<String, Integer> noms = nominalMap.get("bar");
    Assert.assertNotNull("nominals for bar are null", noms);
    Assert.assertEquals(2, noms.size());
    Map<Integer, ARFFType> integerARFFTypeMap = model.getTypeMap();
    Assert.assertNotNull("Type map null", integerARFFTypeMap);
    Assert.assertEquals(5, integerARFFTypeMap.size());
    Map<String, Long> words = model.getWords();
    Assert.assertNotNull("words null", words);
    Assert.assertEquals(10, words.size());
    //System.out.println("Words: " + words);
    Map<Integer, DateFormat> integerDateFormatMap = model.getDateMap();
    Assert.assertNotNull("date format null", integerDateFormatMap);
    Assert.assertEquals(1, integerDateFormatMap.size());
    model = new MapBackedARFFModel(model.getWords(), model.getWordCount(),
      model.getNominalMap());
    iterable = new ARFFVectorIterable(NON_NUMERIC_ARFF2, model);
    count = 0;
    for (Vector vector : iterable) {
      Assert.assertTrue("Vector is not dense", vector instanceof RandomAccessSparseVector);
      count++;
    }
    nominalMap = model.getNominalMap();
    Assert.assertNotNull(nominalMap);
    Assert.assertEquals(2, nominalMap.size());
    noms = nominalMap.get("test");
    Assert.assertNotNull("nominals for bar are null", noms);
    Assert.assertEquals(2, noms.size());
  }
  
  
  private static final String SAMPLE_DENSE_ARFF = "   % Comments\n" +
  "   % \n" +
  "   % Comments go here" +
  "   % \n" +
  "   @RELATION Mahout\n" +
  '\n' +
  "   @ATTRIBUTE foo  NUMERIC\n" +
  "   @ATTRIBUTE bar   NUMERIC\n" +
  "   @ATTRIBUTE hockey  NUMERIC\n" +
  "   @ATTRIBUTE football   NUMERIC\n" +
  "  \n" +
  '\n' +
  '\n' +
  "   @DATA\n" +
  "   23.1,3.23,1.2,0.2\n" +
  "   2.9,3.0,1.2,0.2\n" +
  "   2.7,3.2,1.3,0.2\n" +
  "   2.6,3.1,1.23,0.2\n" +
  "   23.0,3.6,1.2,0.2\n" +
  "   23.2,3.9,1.7,0.2\n" +
  "   2.6,3.2,1.2,0.3\n" +
  "   23.0,3.2,1.23,0.2\n" +
  "   2.2,2.9,1.2,0.2\n" +
  "   2.9,3.1,1.23,0.1\n";
  
  
  private static final String SAMPLE_SPARSE_ARFF = "   % Comments\n" +
  "   % \n" +
  "   % Comments go here" +
  "   % \n" +
  "   @RELATION Mahout\n" +
  '\n' +
  "   @ATTRIBUTE foo  NUMERIC\n" +
  "   @ATTRIBUTE bar   NUMERIC\n" +
  "   @ATTRIBUTE hockey  NUMERIC\n" +
  "   @ATTRIBUTE football   NUMERIC\n" +
  "   @ATTRIBUTE tennis   NUMERIC\n" +
  "  \n" +
  '\n' +
  '\n' +
  "   @DATA\n" +
  "   {1 23.1,2 3.23,3 1.2,4 0.2}\n" +
  "   {0 2.9}\n" +
  "   {0 2.7,2 3.2,3 1.3,4 0.2}\n" +
  "   {1 2.6,2 3.1,3 1.23,4 0.2}\n" +
  "   {1 23.0,2 3.6,3 1.2,4 0.2}\n" +
  "   {0 23.2,1 3.9,3 1.7,4 0.2}\n" +
  "   {0 2.6,1 3.2,2 1.2,4 0.3}\n" +
  "   {1 23.0,2 3.2,3 1.23}\n" +
  "   {1 2.2,2 2.94 0.2}\n" +
  "   {1 2.9,2 3.1}\n";
  
  private static final String NON_NUMERIC_ARFF = "   % Comments\n" +
  "   % \n" +
  "   % Comments go here" +
  "   % \n" +
  "   @RELATION Mahout\n" +
  '\n' +
  "   @ATTRIBUTE junk  NUMERIC\n" +
  "   @ATTRIBUTE foo  NUMERIC\n" +
  "   @ATTRIBUTE bar   {c,d}\n" +
  "   @ATTRIBUTE hockey  string\n" +
  "   @ATTRIBUTE football   date \"yyyy-MM-dd\"\n" +
  "  \n" +
  '\n' +
  '\n' +
  "   @DATA\n" +
  "   {2 c,3 gretzky,4 1973-10-23}\n" +
  "   {1 2.9,2 d,3 orr,4 1973-11-23}\n" +
  "   {2 c,3 bossy,4 1981-10-23}\n" +
  "   {1 2.6,2 c,3 lefleur,4 1989-10-23}\n" +
  "   {3 esposito,4 1973-04-23}\n" +
  "   {1 23.2,2 d,3 chelios,4 1999-2-23}\n" +
  "   {3 richard,4 1973-10-12}\n" +
  "   {3 howe,4 1983-06-23}\n" +
  "   {0 2.2,2 d,3 messier,4 2008-11-23}\n" +
  "   {2 c,3 roy,4 1973-10-13}\n";
  
  private static final String NON_NUMERIC_ARFF2 = "   % Comments\n" +
  "   % \n" +
  "   % Comments go here" +
  "   % \n" +
  "   @RELATION Mahout\n" +
  '\n' +
  "   @ATTRIBUTE junk  NUMERIC\n" +
  "   @ATTRIBUTE foo  NUMERIC\n" +
  "   @ATTRIBUTE test   {f,z}\n" +
  "   @ATTRIBUTE hockey  string\n" +
  "   @ATTRIBUTE football   date \"yyyy-MM-dd\"\n" +
  "  \n" +
  '\n' +
  '\n' +
  "   @DATA\n" +
  "   {2 f,3 gretzky,4 1973-10-23}\n" +
  "   {1 2.9,2 z,3 orr,4 1973-11-23}\n" +
  "   {2 f,3 bossy,4 1981-10-23}\n" +
  "   {1 2.6,2 f,3 lefleur,4 1989-10-23}\n" +
  "   {3 esposito,4 1973-04-23}\n" +
  "   {1 23.2,2 z,3 chelios,4 1999-2-23}\n" +
  "   {3 richard,4 1973-10-12}\n" +
  "   {3 howe,4 1983-06-23}\n" +
  "   {0 2.2,2 f,3 messier,4 2008-11-23}\n" +
  "   {2 f,3 roy,4 1973-10-13}\n";
}
