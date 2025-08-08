/*
 * Copyright 2020-2025 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.chain.event;

import lombok.EqualsAndHashCode;
import lombok.Value;
import org.springframework.context.ApplicationEvent;

import java.math.BigInteger;

@Value
@EqualsAndHashCode(callSuper = true)
public class DealEvent extends ApplicationEvent {

    String chainDealId;
    BigInteger blockNumber;

    public DealEvent(final Object source, final String chainDealId, final BigInteger blockNumber) {
        super(source);
        this.chainDealId = chainDealId;
        this.blockNumber = blockNumber;
    }

}
