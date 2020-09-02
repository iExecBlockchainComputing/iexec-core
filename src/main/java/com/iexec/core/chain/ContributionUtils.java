/*
 * Copyright 2020 IEXEC BLOCKCHAIN TECH
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.iexec.core.chain;

public class ContributionUtils {

    private ContributionUtils() {
        throw new UnsupportedOperationException();
    }

    public static int scoreToCredibility(int score) {
        int credibility = score;
        /*
         *  should be :   c(s)=1-0.2/max(s,1)
         *  considering   :   c(s)= s
         */
        return credibility;
    }

    public static int trustToCredibility(int trust) {
        int credibility = trust;
        /*
         *  should be :   c(s)=1-1/max(s,1)
         *  considering   :   c(s)= s
         */
        return credibility;
    }

}
