// 2026-05-03
package model;

import java.util.*;

public class CoverageData {
    public Map<String, Set<String>> testToMethods = new HashMap<>();
    public Set<String> allMethods = new HashSet<>();
    public Set<String> failedTests = new HashSet<>();
}
