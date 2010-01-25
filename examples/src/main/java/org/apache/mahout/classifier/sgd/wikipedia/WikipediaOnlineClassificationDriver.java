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

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;

import org.apache.commons.cli2.CommandLine;
import org.apache.commons.cli2.Group;
import org.apache.commons.cli2.Option;
import org.apache.commons.cli2.builder.ArgumentBuilder;
import org.apache.commons.cli2.builder.DefaultOptionBuilder;
import org.apache.commons.cli2.builder.GroupBuilder;
import org.apache.commons.cli2.commandline.Parser;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.conf.Configured;
import org.apache.hadoop.fs.FileStatus;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.io.IOUtils;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;
import org.apache.hadoop.io.Writable;
import org.apache.hadoop.mapred.FileInputFormat;
import org.apache.hadoop.mapred.FileOutputFormat;
import org.apache.hadoop.mapred.JobClient;
import org.apache.hadoop.mapred.JobConf;
import org.apache.hadoop.mapred.SequenceFileOutputFormat;
import org.apache.hadoop.mapred.lib.IdentityReducer;
import org.apache.hadoop.util.ReflectionUtils;
import org.apache.hadoop.util.Tool;
import org.apache.hadoop.util.ToolRunner;
import org.apache.mahout.classifier.bayes.XmlInputFormat;
import org.apache.mahout.classifier.sgd.MultiLabelScores;
import org.apache.mahout.classifier.sgd.ThresholdClassifier;
import org.apache.mahout.common.CommandLineUtil;
import org.apache.mahout.common.FileLineIterable;
import org.apache.mahout.math.MultiLabelVectorWritable;
import org.apache.mahout.math.Vector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extract a vectorized dataset from a chunked Wikipedia XML dump. The generated
 * dataset is suitable for the training of a Logistic Regression model using
 * Stochastic Gradient Descent so as to perform document classification.
 *
 * For each document the list of labels are extracted the integer indexes of the
 * document categories if the belong to the list of categories given as input.
 *
 * The features are extracted from the tokenized content of each wiki article
 * deterministically mapped to a fixed dimension array of occurrence and
 * co-occurrence of terms counts through a random function (a.k.a
 * TermRandomizer).
 */
public final class WikipediaOnlineClassificationDriver extends Configured
    implements Tool {
  private static final Logger log = LoggerFactory
      .getLogger(WikipediaOnlineClassificationDriver.class);

  public static void main(String[] args) throws Exception {
    int res = ToolRunner.run(new Configuration(),
        new WikipediaOnlineClassificationDriver(), args);
    System.exit(res);
  }

  public int run(String[] args) {
    DefaultOptionBuilder obuilder = new DefaultOptionBuilder();
    ArgumentBuilder abuilder = new ArgumentBuilder();
    GroupBuilder gbuilder = new GroupBuilder();

    Option extractFeaturesOpt = obuilder.withLongName("extract").withRequired(
        false).withShortName("e").withDescription(
        "Launch a parallel vector features extraction job from"
            + " a Wikipedia XML dump.").create();

    Option trainModelOpt = obuilder.withLongName("train").withRequired(false)
        .withShortName("t").withDescription(
            "Launch a sequential training procedure from previously"
                + " extracted vector features.").create();

    Option xmlDumpPathOpt = obuilder.withLongName("xml-dump").withRequired(
        false).withArgument(
        abuilder.withName("xml-dump").withMinimum(1).withMaximum(1).create())
        .withDescription("Path to the chunked wikipedia XML dump")
        .withShortName("x").create();

    Option featuresPathOpt = obuilder.withLongName("features").withRequired(
        true).withArgument(
        abuilder.withName("features").withMinimum(1).withMaximum(1).create())
        .withDescription("Path to the extracted hashed features")
        .withShortName("f").create();

    Option modelPathOpt = obuilder.withLongName("model").withRequired(false)
        .withArgument(
            abuilder.withName("model").withMinimum(1).withMaximum(1).create())
        .withDescription("Path to host the trained model parameters")
        .withShortName("m").create();

    Option categoriesOpt = obuilder
        .withLongName("categories")
        .withRequired(true)
        .withArgument(
            abuilder.withName("categories").withMinimum(1).withMaximum(1)
                .create())
        .withDescription(
            "Location of the categories file.  One entry per line. "
                + "Will be used to make a string match in Wikipedia Category field")
        .withShortName("c").create();

    Option helpOpt = obuilder.withLongName("help").withDescription(
        "Print out help").withShortName("h").create();

    Group group = gbuilder.withName("Options").withOption(extractFeaturesOpt)
        .withOption(trainModelOpt).withOption(categoriesOpt).withOption(
            xmlDumpPathOpt).withOption(featuresPathOpt)
        .withOption(modelPathOpt).withOption(helpOpt).create();

    Parser parser = new Parser();
    parser.setGroup(group);
    try {
      CommandLine cmdLine = parser.parse(args);
      if (cmdLine.hasOption(helpOpt)) {
        CommandLineUtil.printHelp(group);
        return 0;
      }
      if (!cmdLine.hasOption(extractFeaturesOpt)
          && !cmdLine.hasOption(trainModelOpt)) {
        System.err.println("Either extract or train option should be used.");
        return 1;
      }
      String featuresPath = (String) cmdLine.getValue(featuresPathOpt);
      String catFile = (String) cmdLine.getValue(categoriesOpt);
      List<String> categories = readCategories(catFile);
      getConf().set("wikipedia.categories", StringUtils.join(categories, ','));

      if (cmdLine.hasOption(extractFeaturesOpt)) {
        String xmlDumpPath = (String) cmdLine.getValue(xmlDumpPathOpt);
        if (xmlDumpPath == null) {
          System.err.println("The path to the XML dump file(s) is missing:");
          CommandLineUtil.printHelp(group);
          return 1;
        }
        runFeatureExtractionJob(xmlDumpPath, featuresPath, catFile);
      }
      if (cmdLine.hasOption(trainModelOpt)) {
        String modelPath = (String) cmdLine.getValue(modelPathOpt);
        if (modelPath == null) {
          modelPath = String.format("model-%1$tF--%1$tH-%1$tM.dat", Calendar
              .getInstance());
        }
        trainModel(featuresPath, modelPath);
      }
      return 0;
    } catch (Exception e) {
      log.error(e.getMessage(), e);
      CommandLineUtil.printHelp(group);
      return 1;
    }
  }

  /**
   * Run the job
   *
   * @param input
   *          the input pathname String
   * @param output
   *          the output pathname String
   * @param catFile
   *          the file containing the Wikipedia categories, one entry per line
   */
  public void runFeatureExtractionJob(String input, String output,
      String catFile) throws IOException {
    JobConf job = new JobConf(getConf(),
        WikipediaOnlineClassificationDriver.class);
    if (log.isInfoEnabled()) {
      log.info("Input: " + input + " Out: " + output + " Categories: "
          + catFile);
    }
    job.set("key.value.separator.in.input.line", " ");
    FileInputFormat.setInputPaths(job, new Path(input));
    Path outPath = new Path(output);
    FileOutputFormat.setOutputPath(job, outPath);
    job.setMapperClass(WikipediaRandomHasherMapper.class);
    job.setReducerClass(IdentityReducer.class);
    job.setInputFormat(XmlInputFormat.class);
    job.setOutputFormat(SequenceFileOutputFormat.class);
    job.setOutputKeyClass(LongWritable.class);
    job.setOutputValueClass(MultiLabelVectorWritable.class);

    // we need to extract the title, not just the markup content
    job.set(XmlInputFormat.START_TAG_KEY, "<page>");
    job.set(XmlInputFormat.END_TAG_KEY, "</page>");

    FileSystem dfs = FileSystem.get(outPath.toUri(), job);
    if (dfs.exists(outPath)) {
      dfs.delete(outPath, true);
    }
    JobClient.runJob(job);
  }

  public List<String> readCategories(String catFile) throws IOException {
    List<String> categories = new ArrayList<String>();
    for (String line : new FileLineIterable(new File(catFile))) {
      categories.add(line.trim().toLowerCase());
    }
    return categories;
  }

  public void trainModel(String featuresPathStr, String modelPathStr)
      throws ClassNotFoundException, InstantiationException,
      IllegalAccessException, IOException {
    Configuration conf = getConf();
    ThresholdClassifier classifier = ThresholdClassifier.getInstance(conf);
    long updateScoreInterval = conf.getLong("online.updateScoreInterval", 5000);
    int epochs = conf.getInt("online.epochs", 1);
    int epoch = 0;
    long steps = 0;
    final FileSystem modelFs = FileSystem.get(URI.create(modelPathStr), conf);
    Path modelPath = new Path(modelPathStr);
    if (modelFs.exists(modelPath)) {
      throw new IOException(modelPath.getName() + " already exists");
    }

    // read the extracted feature
    final FileSystem featuresFs = FileSystem.get(URI.create(featuresPathStr),
        conf);

    Path path = new Path(featuresPathStr);
    while (epoch < epochs) {
      for (FileStatus fileStatus : featuresFs.listStatus(path)) {
        if (fileStatus.isDir()) {
          continue;
        }
        log.info(String.format("Training with file: '%s':", fileStatus
            .getPath()));
        SequenceFile.Reader reader = null;
        try {
          reader = new SequenceFile.Reader(featuresFs, fileStatus.getPath(),
              conf);
          Writable key = (Writable) ReflectionUtils.newInstance(reader
              .getKeyClass(), conf);
          Writable value = (Writable) ReflectionUtils.newInstance(reader
              .getValueClass(), conf);
          while (reader.next(key, value)) {
            if (!(value instanceof MultiLabelVectorWritable)) {
              log.warn(String.format("ignoring file: '%s'", fileStatus
                  .getPath()));
              break;
            }
            MultiLabelVectorWritable instance = (MultiLabelVectorWritable) value;
            Vector vector = instance.get();
            int[] labels = instance.getLabels();

            // Progressive Validation
            classifier.evaluateAndTrain(vector, labels);

            if (steps % updateScoreInterval == 0) {
              MultiLabelScores scores = classifier.getCurrentEvaluation();
              log.info(String.format("At instance #%d '%s': %s, density %s", steps, vector
                  .getName(), scores, classifier.density()));
              log.info("Precision by categories: "
                  + ArrayUtils.toString(scores.precision));
              log.info("Recall by categories:    "
                  + ArrayUtils.toString(scores.recall));
              log.info("F1 score by categories:  "
                  + ArrayUtils.toString(scores.f1Score));
              classifier.resetEvaluation();
            }
            steps++;
          }
          log.info(String.format("Completed epoch %d/%d", epoch + 1, epochs));
        } finally {
          IOUtils.closeStream(reader);
        }
      }
      epoch++;
    }
    log.info(String.format("Saving trained model to '%s'", modelPathStr));
    // TODO: save the result
  }
}