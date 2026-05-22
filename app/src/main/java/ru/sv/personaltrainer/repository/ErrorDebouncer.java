package ru.sv.personaltrainer.repository;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public class ErrorDebouncer {
    private static final long DEBOUNCE_MS = 250L;
    private final HashMap<String, Long> firstSeenMap = new HashMap<>();
    private final HashSet<String> confirmedErrors = new HashSet<>();

    public List<String> filter(List<String> rawErrors, long nowMs) {
        firstSeenMap.keySet().retainAll(rawErrors);
        confirmedErrors.retainAll(rawErrors);

        for (String error : rawErrors) {
            if (!firstSeenMap.containsKey(error)) {
                firstSeenMap.put(error, nowMs);
            }
        }

        List<String> result = new ArrayList<>();
        for (String error : rawErrors) {
            Long firstSeen = firstSeenMap.get(error);
            if (firstSeen != null && (nowMs - firstSeen) >= DEBOUNCE_MS) {
                confirmedErrors.add(error);
                result.add(error);
            }
        }
        return result;
    }

    public void reset() {
        firstSeenMap.clear();
        confirmedErrors.clear();
    }
}