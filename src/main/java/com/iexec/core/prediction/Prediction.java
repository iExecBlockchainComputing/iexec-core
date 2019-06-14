package com.iexec.core.prediction;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
@Builder
public class Prediction {
    private String contribution;
    private int weight;
}