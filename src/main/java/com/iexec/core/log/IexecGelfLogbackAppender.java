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

package com.iexec.core.log;

import biz.paluch.logging.gelf.logback.GelfLogbackAppender;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.iexec.core.chain.CredentialsService;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

@Component
public class IexecGelfLogbackAppender extends GelfLogbackAppender implements ApplicationContextAware {

    private static String address;

    public IexecGelfLogbackAppender() {
        super();
    }

    /*
     * Graylog note: The `originHost` will only contain what we want after CredentialService is loaded.
     *
     * Very first logs will have:
     *                          originHost=user@user.com
     * Next logs will have:
     *                          originHost=0x12..34
     *
     * */
    @Override
    protected void append(ILoggingEvent event) {
        if (address != null) {
            gelfMessageAssembler.setOriginHost(address);
        }
        super.append(event);
    }

    @Override
    public void setApplicationContext(@NonNull ApplicationContext applicationContext) throws BeansException {
        CredentialsService credentialsService = (CredentialsService) applicationContext
                .getAutowireCapableBeanFactory().getBean("credentialsService");
        address = credentialsService.getCredentials().getAddress();
    }
}

