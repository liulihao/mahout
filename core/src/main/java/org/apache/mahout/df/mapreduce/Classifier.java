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

package org.apache.mahout.df.mapreduce;

import org.apache.mahout.common.HadoopUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.mahout.df.DecisionForest;
import org.apache.mahout.df.DFUtils;
import org.apache.mahout.df.data.DataConverter;
import org.apache.mahout.df.data.Dataset;
import org.apache.mahout.df.data.Instance;
import org.apache.mahout.common.RandomUtils;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.FSDataOutputStream;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.filecache.DistributedCache;
import org.apache.hadoop.mapreduce.Job;
import org.apache.hadoop.mapreduce.Mapper;
import org.apache.hadoop.mapreduce.JobContext;
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat;
import org.apache.hadoop.mapreduce.lib.input.TextInputFormat;
import org.apache.hadoop.mapreduce.lib.input.FileSplit;
import org.apache.hadoop.mapreduce.lib.output.FileOutputFormat;
import org.apache.hadoop.mapreduce.lib.output.SequenceFileOutputFormat;
import org.apache.hadoop.io.Text;
import org.apache.hadoop.io.LongWritable;
import org.apache.hadoop.io.SequenceFile;

import java.io.IOException;
import java.util.Random;
import java.net.URI;

/**
 * Mapreduce implementation that classifies the Input data using a previousely built decision forest
 */
public class Classifier {

  private static final Logger log = LoggerFactory.getLogger(Classifier.class);

  private final Path forestPath;

  private final Path inputPath;

  private final Path datasetPath;

  private final Configuration conf;

  private final Path outputPath; // path that will containt the final output of the classifier
  private final Path mappersOutputPath; // mappers will output here

  public Classifier(Path forestPath, Path inputPath, Path datasetPath, Path outputPath, Configuration conf) {
    this.forestPath = forestPath;
    this.inputPath = inputPath;
    this.datasetPath = datasetPath;
    this.outputPath = outputPath;
    this.conf = conf;
    mappersOutputPath = new Path(outputPath, "mappers");
  }

  private void configureJob(Job job) throws IOException {
    Configuration conf = job.getConfiguration();

    job.setJarByClass(Classifier.class);

    FileInputFormat.setInputPaths(job, inputPath);
    FileOutputFormat.setOutputPath(job, mappersOutputPath);

    job.setOutputKeyClass(LongWritable.class);
    job.setOutputValueClass(Text.class);

    job.setMapperClass(CMapper.class);
    job.setNumReduceTasks(0); // no reducers

    job.setInputFormatClass(CTextInputFormat.class);
    job.setOutputFormatClass(SequenceFileOutputFormat.class);

  }

  public void run() throws IOException, ClassNotFoundException, InterruptedException {
    FileSystem fs = FileSystem.get(conf);

    // check the output
    if (fs.exists(outputPath)) {
      throw new IOException("Output path already exists : " + outputPath);
    }

    log.info("Adding the dataset to the DistributedCache");
    // put the dataset into the DistributedCache
    DistributedCache.addCacheFile(datasetPath.toUri(), conf);

    log.info("Adding the decision forest to the DistributedCache");
    DistributedCache.addCacheFile(forestPath.toUri(), conf);

    Job job = new Job(conf, "decision forest classifier");

    log.info("Configuring the job...");
    configureJob(job);

    log.info("Running the job...");
    if (!job.waitForCompletion(true)) {
      log.error("Job failed!");
      log.error("Job failed!");
      return;
    }

    parseOutput(job);

    HadoopUtil.overwriteOutput(mappersOutputPath);
  }

  /**
   * Extract the prediction for each mapper and write them in the corresponding output file. The name of the output file
   * is based on the name of the corresponding input file
   * @param job
   */
  private void parseOutput(Job job) throws IOException {
    Configuration conf = job.getConfiguration();
    FileSystem fs = mappersOutputPath.getFileSystem(conf);

    Path[] outfiles = DFUtils.listOutputFiles(fs, mappersOutputPath);
    FSDataOutputStream ofile = null;

    // read all the output
    LongWritable key = new LongWritable();
    Text value = new Text();
    for (Path path : outfiles) {
      SequenceFile.Reader reader = new SequenceFile.Reader(fs, path, conf);

      try {
        while (reader.next(key, value)) {
          if (ofile == null) {
            // this is the first value, it contains the name of the input file
            ofile = fs.create(new Path(outputPath, value.toString()).suffix(".out"));
          } else {
            // the value contains a prediction
            ofile.writeChars(value.toString()); // write the prediction
            ofile.writeChar('\n');
          }
        }
      } finally {
        reader.close();
        ofile.close();
        ofile = null;
      }
    }

  }

  /**
   * TextInputFormat that does not split the input files. This ensures that each input file is processed by one single
   * mapper.
   */
  public static class CTextInputFormat extends TextInputFormat {
    @Override
    protected boolean isSplitable(JobContext jobContext, Path path) {
      return false;
    }
  }
  
  public static class CMapper extends Mapper<LongWritable, Text, LongWritable, Text> {

    /** used to convert input values to data instances */
    private DataConverter converter;

    private DecisionForest forest;

    private final Random rng = RandomUtils.getRandom();

    private boolean first = true;

    private final Text lvalue = new Text();


    @Override
    protected void setup(Context context) throws IOException, InterruptedException {
      super.setup(context);    //To change body of overridden methods use File | Settings | File Templates.

      Configuration conf = context.getConfiguration();

      URI[] files = DistributedCache.getCacheFiles(conf);

      if ((files == null) || (files.length < 2)) {
        throw new IOException("not enough paths in the DistributedCache");
      }

      Dataset dataset = Dataset.load(conf, new Path(files[0].getPath()));

      converter = new DataConverter(dataset);

      forest = DecisionForest.load(conf, new Path(files[1].getPath()));
      if (forest == null) {
        throw new InterruptedException("DecisionForest not found!");
      }
    }

    @Override
    protected void map(LongWritable key, Text value, Context context) throws IOException, InterruptedException {
      if (first) {
        FileSplit split = (FileSplit) context.getInputSplit();
        Path path = split.getPath(); // current split path
        lvalue.set(path.getName());
        context.write(key, new Text(path.getName()));

        first = false;
      }

      String line = value.toString();
      if (!line.isEmpty()) {
        Instance instance = converter.convert(0, line);
        int prediction = forest.classify(rng, instance);
        lvalue.set(Integer.toString(prediction));
        context.write(key, lvalue);
      }
    }
  }
}
