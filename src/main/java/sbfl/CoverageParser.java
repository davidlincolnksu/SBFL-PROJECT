// 2026-05-03
package sbfl;

import model.CoverageData;
import java.io.*;
import java.util.*;

public class CoverageParser {

    public static CoverageData parse(File folder) throws IOException {
        CoverageData data = new CoverageData();

        for (File file : Objects.requireNonNull(folder.listFiles())) {
            if (!file.getName().endsWith(".txt")) continue;

            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String firstLine = br.readLine();
                if (firstLine == null) continue;
                firstLine = firstLine.trim();

                int lastSpace = firstLine.lastIndexOf(' ');
                if (lastSpace < 0) continue;

                String testName = firstLine.substring(0, lastSpace).trim();
                boolean failed = firstLine.substring(lastSpace + 1).trim().equalsIgnoreCase("false");

                Set<String> methods = new HashSet<>();
                String line;
                while ((line = br.readLine()) != null) {
                    line = line.trim();
                    if (!line.isEmpty()) {
                        methods.add(line);
                        data.allMethods.add(line);
                    }
                }

                data.testToMethods.put(testName, methods);
                if (failed) data.failedTests.add(testName);
            }
        }

        return data;
    }
}