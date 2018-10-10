package com.iexec.core.workflow;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class Workflow<T> {

    private HashMap<T, List<T>> possibleTransitions;

    public Workflow() {
        possibleTransitions = new HashMap<>();
    }

    boolean addTransition(T from, T to){
        if(possibleTransitions.containsKey(from)){
            return possibleTransitions.get(from).add(to);
        } else {
            List<T> nextStates = new ArrayList<>();
            nextStates.add(to);
            List<T> added = possibleTransitions.put(from, nextStates);
            return added != null;
        }
    }

    public boolean isValidTransition(T from, T to){
        if(!possibleTransitions.containsKey(from)){
            return false;
        }

        return possibleTransitions.get(from).contains(to);
    }
}
