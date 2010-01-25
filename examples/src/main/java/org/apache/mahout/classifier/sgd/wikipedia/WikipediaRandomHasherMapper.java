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

package org.apache.mahout.classifier.sgd.wikipedia;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringEscapeUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.MapReduceBase;
import org.apache.hadoop.mapred.Mapper;
import org.apache.hadoop.mapred.OutputCollector;
import org.apache.hadoop.mapred.Reporter;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.tokenattributes.TermAttribute;
import org.apache.mahout.analysis.WikipediaAnalyzer;
import org.apache.mahout.classifier.sgd.TermRandomizer;
import org.apache.mahout.classifier.sgd.ThresholdClassifier;
import org.apache.mahout.common.RandomUtils;
import org.apache.mahout.math.MultiLabelVectorWritable;
import org.apache.mahout.math.SequentialAccessSparseVector;
import org.apache.mahout.math.Vector;

/**
 * Vectorize Wikipedia articles and use the categories tags as labels. The key
 * of the emitted intermediate data is voluntarily randomized so to meet the
 * I.I.D. assumption of the stochastic learner living in the reducer.
 *
 */
public class WikipediaRandomHasherMapper extends MapReduceBase implements
    Mapper<LongWritable,Text,LongWritable,MultiLabelVectorWritable> {

  public static enum SKIPED_ARTICLES {
    WITHOUT_CATEGORIES, MISSING_MARKUP, MISSING_TITLE, REDIRECT
  };

  private static final Pattern TEXT_TAG_PATTERN = Pattern.compile(
      "<text xml:space=\"preserve\">(.*)?</text>", Pattern.MULTILINE
          | Pattern.DOTALL);

  private static final Pattern TITLE_TAG_PATTERN = Pattern
      .compile("<title>(.*)?</title>");

  private static final Pattern CATEGORY_PATTERN = Pattern
      .compile("\\[\\[Category:(.*)?\\]\\]");

  private static final String REDIRECT_PREFIX = "#REDIRECT";

  private List<String> allCategories = new ArrayList<String>();
  private double maxUnlabeledInstanceRate;
  private boolean exactMatch = false;
  private long unlabeledOuputCount = 0; // use shared counters instead?
  private long totalOutputCount = 0;
  private int seed = 42;
  private Random rng;
  private TermRandomizer randomizer;
  private boolean allPairs;
  private int window;
  private final LongWritable shufflingKey = new LongWritable();
  private final MultiLabelVectorWritable labeledVectorValue = new MultiLabelVectorWritable();
  private Analyzer analyzer;

  @Override
  public void configure(JobConf job) {
    // seed the RNG used to shuffle the instances (wikipedia articles come in
    // Alphabetical order and that bias could harm the convergence of online
    // learner that assume I.I.D. samples).
    seed = job.getInt("wikipedia.random.seed", seed);
    rng = RandomUtils.getRandom(seed);

    try {
      ThresholdClassifier classifier = ThresholdClassifier
          .getInstance(new Configuration(job));
      randomizer = classifier.getModel().getRandomizer();
      allPairs = classifier.isAllPairs();
      window = classifier.getWindow();
      allCategories = classifier.getCategories();
    } catch (Exception e) {
      // the #configure(JobConf job) does not allow for properly reporting
      // errors
      e.printStackTrace();
    }
    exactMatch = job.getBoolean("wikipedia.categories.exactMatch", false);

    // reasonable default to avoid generating to many unlabeled instances
    maxUnlabeledInstanceRate = 1.0 / allCategories.size();
    analyzer = new WikipediaAnalyzer(null);
  }

  @Override
  public void map(LongWritable key, Text value,
      OutputCollector<LongWritable,MultiLabelVectorWritable> collector,
      Reporter reporter) throws IOException {
    shufflingKey.set(rng.nextLong());

    // extract the raw markup from the XML dump slice
    Matcher textMatcher = TEXT_TAG_PATTERN.matcher(value.toString());
    if (!textMatcher.find()) {
      reporter.incrCounter(SKIPED_ARTICLES.MISSING_MARKUP, 1);
      return;
    }
    String rawMarkup = StringEscapeUtils.unescapeHtml(textMatcher.group(1))
        .trim();
    if (rawMarkup.startsWith(REDIRECT_PREFIX)) {
      reporter.incrCounter(SKIPED_ARTICLES.REDIRECT, 1);
      return;
    }

    // collect the categories as indexes
    int[] categories = findMatchingCategories(rawMarkup);
    labeledVectorValue.setLabels(categories);

    // ensure we are not
    if (categories.length == 0) {
      if (totalOutputCount != 0
          && ((double) unlabeledOuputCount) / totalOutputCount > maxUnlabeledInstanceRate) {
        reporter.incrCounter(SKIPED_ARTICLES.WITHOUT_CATEGORIES, 1);
        return;
      }
      unlabeledOuputCount++;
    }
    totalOutputCount++;

    // extract the title of the article as instance name
    Matcher titleMatcher = TITLE_TAG_PATTERN.matcher(value.toString());
    if (!titleMatcher.find()) {
      reporter.incrCounter(SKIPED_ARTICLES.MISSING_TITLE, 1);
      return;
    }
    String name = titleMatcher.group(1);

    // strip the wikimarkup and hash the terms using the randomizer
    TokenStream stream = analyzer
        .tokenStream(null, new StringReader(rawMarkup));
    List<String> allTerms = new ArrayList<String>();
    TermAttribute termAtt = (TermAttribute) stream
        .addAttribute(TermAttribute.class);
    while (stream.incrementToken()) {
      allTerms.add(termAtt.term());
    }
    // TODO: refactor randomizedInstance to take a token stream as input and
    // avoid all those wasted string allocations (or prove they are harmless
    // using the profiler).
    Vector vector = randomizer.randomizedInstance(allTerms, window, allPairs);
    vector.setName(name);
    labeledVectorValue.set(new SequentialAccessSparseVector(vector));
    if (totalOutputCount % 10 == 0) {
      reporter.setStatus(String.format(
          "Extracted %d instances. Last: '%s' with categories: %s and "
              + "density: %d/%d", totalOutputCount, name, Arrays
              .toString(categories), vector.getNumNondefaultElements(), vector
              .size()));
    }
    collector.collect(shufflingKey, labeledVectorValue);
  }

  private int[] findMatchingCategories(String rawMarkup) {
    Set<Integer> matchingCategories = new HashSet<Integer>();
    Matcher matcher = CATEGORY_PATTERN.matcher(rawMarkup);
    while (matcher.find()) {
      String category = matcher.group(1).toLowerCase().trim();
      if (exactMatch) {
        if (allCategories.contains(category)) {
          matchingCategories.add(allCategories.indexOf(category));
        }
      } else {
        for (int i = 0; i < allCategories.size(); i++) {
          if (category.contains(allCategories.get(i))) {
            matchingCategories.add(i);
          }
        }
      }
    }
    return ArrayUtils.toPrimitive(matchingCategories
        .toArray(new Integer[matchingCategories.size()]));
  }

}