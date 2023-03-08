package com.iexec.core.replicate;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Exception that can be thrown
 * when searching for a {@link com.iexec.common.replicate.ReplicateStatus} fails.
 */
@AllArgsConstructor
@Getter
public class NoReplicateStatusException extends RuntimeException {
    private final String chainTaskId;
}
