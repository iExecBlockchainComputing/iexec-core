/*
 * Copyright 2020-2024 IEXEC BLOCKCHAIN TECH
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

package com.iexec.core.notification;

public enum TaskNotificationType {
    PLEASE_CONTINUE,
    PLEASE_WAIT,                    // subscribe & do nothing (wait)
    PLEASE_START,
    PLEASE_DOWNLOAD_APP,
    PLEASE_DOWNLOAD_DATA,
    PLEASE_COMPUTE,
    PLEASE_CONTRIBUTE,              // subscribe & contribute if result found, else compute
    PLEASE_REVEAL,                  // subscribe & reveal
    PLEASE_UPLOAD,                  // subscribe & upload result
    PLEASE_CONTRIBUTE_AND_FINALIZE, // contribute & finalize on-chain
    PLEASE_COMPLETE,                // complete + unsubscribe
    PLEASE_ABORT,                   // abort + unsubscribe
}
