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

package org.apache.mahout.classifier.sgd;

/**
 * Created by IntelliJ IDEA. User: tdunning Date: Jun 2, 2010 Time: 8:25:37 AM To change this
 * template use File | Settings | File Templates.
 */
public class ElasticBandPrior extends PriorFunction {
  private double alphaByLambda;
  private L1 l1;
  private L2 l2;

  public ElasticBandPrior(double alphaByLambda) {
    this.alphaByLambda = alphaByLambda;
    l1 = new L1();
    l2 = new L2(1);
  }

  @Override
  public double age(double oldValue, double generations, double learningRate) {
    oldValue = oldValue * Math.pow(1 - alphaByLambda * learningRate , generations);
    double newValue = oldValue - Math.signum(oldValue) * learningRate * generations;
    if (newValue * oldValue < 0) {
      // don't allow the value to change sign
      return 0;
    } else {
      return newValue;
    }
  }

  @Override
  public double logP(double beta_ij) {
    return l1.logP(beta_ij) + alphaByLambda * l2.logP(beta_ij);
  }
}
