// 2026-05-03
package sbfl;

import model.CoverageData;
import model.Result;

import java.util.*;

public class SBFL {

    public static void injectFailures(CoverageData data) {
        int i = 0;
        for (String test : data.testToMethods.keySet()) {
            if (i % 5 == 0) data.failedTests.add(test);
            i++;
        }
    }

    public static Map<String, int[]> computeEP_EF(CoverageData data) {
        Map<String, int[]> map = new HashMap<>();

        for (String m : data.allMethods) {
            map.put(m, new int[]{0, 0});
        }

        for (String test : data.testToMethods.keySet()) {
            boolean isFail = data.failedTests.contains(test);
            Set<String> executed = data.testToMethods.get(test);

            for (String m : executed) {
                int[] arr = map.get(m);
                if (isFail) arr[0]++;
                else arr[1]++;
            }
        }

        return map;
    }

    public static List<Result> rank(CoverageData data, String formula) {
        Formula f = Formula.valueOf(formula.toUpperCase());
        Map<String, int[]> stats = computeEP_EF(data);

        int total = data.testToMethods.size();
        int failed = data.failedTests.size();
        int passed = total - failed;

        List<Result> results = new ArrayList<>();

        for (String m : data.allMethods) {
            int ef = stats.get(m)[0];
            int ep = stats.get(m)[1];
            int nf = failed - ef;
            int np = passed - ep;

            double score = f.compute(ef, ep, nf, np);
            results.add(new Result(m, score, ef > 0));
        }

        results.sort((a, b) -> Double.compare(b.score, a.score));
        return results;
    }
}