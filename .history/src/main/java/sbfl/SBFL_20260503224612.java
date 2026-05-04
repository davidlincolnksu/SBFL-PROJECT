package main.java.sbfl;

import model.CoverageData;
import model.Result;

import java.util.*;
import java.util.stream.Collectors;

public class SBFL {

    public static void injectFailures(CoverageData data) {
        int i = 0;
        for (String test : data.testToMethods.keySet()) {
            if (i % 5 == 0) {
                data.failedTests.add(test);
            }
            i++;
        }
    }

    public static Map<String, int[]> computeEP_EF(CoverageData data) {

        Map<String, int[]> map = new HashMap<>();
        for (String m : data.allMethods) {
            map.put(m, new int[]{0, 0}); // ef, ep
        }

        for (String test : data.testToMethods.keySet()) {
            boolean isFail = data.failedTests.contains(test);

            for (String m : data.allMethods) {
                if (data.testToMethods.get(test).contains(m)) {
                    if (isFail)
                        map.get(m)[0]++; // ef
                    else
                        map.get(m)[1]++; // ep
                }
            }
        }

        return map;
    }


    public static double tarantula(int ef, int ep, int nf, int np) {
        double a = ef / (double)(ef + nf + 1e-9);
        double b = ep / (double)(ep + np + 1e-9);
        return a / (a + b + 1e-9);
    }

    public static double ochiai(int ef, int ep, int nf, int np) {
        return ef / Math.sqrt((ef + ep + 1e-9) * (ef + nf + 1e-9));
    }

    public static double jaccard(int ef, int ep, int nf, int np) {
        return ef / (double)(ef + ep + nf + 1e-9);
    }

    public static double zoltar(int ef, int ep, int nf, int np) {
        if (ef == 0) return 0;
        return ef / (ef + ep + nf + (10000.0 * nf * ep / ef));
    }

    public static List<Result> rank(CoverageData data, String formula) {

        Map<String, int[]> stats = computeEP_EF(data);

        int totalTests = data.testToMethods.size();
        int failed = data.failedTests.size();
        int passed = totalTests - failed;

        List<Result> results = new ArrayList<>();

        for (String m : data.allMethods) {

            int ef = stats.get(m)[0];
            int ep = stats.get(m)[1];

            int nf = failed - ef;
            int np = passed - ep;

            double score;

            switch (formula) {
                case "Tarantula":
                    score = tarantula(ef, ep, nf, np);
                    break;
                case "Ochiai":
                    score = ochiai(ef, ep, nf, np);
                    break;
                case "Jaccard":
                    score = jaccard(ef, ep, nf, np);
                    break;
                default:
                    score = zoltar(ef, ep, nf, np);
            }

            results.add(new Result(m, score));
        }

        return results.stream()
                .sorted((a, b) -> Double.compare(b.score, a.score))
                .collect(Collectors.toList());
    }
}