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

package org.apache.mahout.cf.taste.hadoop.similarity;

/**
 * Distributed version of {@link org.apache.mahout.cf.taste.impl.similarity.EuclideanDistanceSimilarity}
 */
public class DistributedEuclideanDistanceSimilarity extends AbstractDistributedItemSimilarity {

  @Override
  protected double doComputeResult(Iterable<CoRating> coratings,
                                   double weightOfItemVectorX,
                                   double weightOfItemVectorY,
                                   int numberOfUsers) {

    double n = 0.0;
    double sumXYdiff2 = 0.0;

    for (CoRating coRating : coratings) {
      double diff = coRating.getPrefValueX() - coRating.getPrefValueY();
      sumXYdiff2 += diff * diff;
      n++;
    }

    return n / (1.0 + Math.sqrt(sumXYdiff2));
  }
}
