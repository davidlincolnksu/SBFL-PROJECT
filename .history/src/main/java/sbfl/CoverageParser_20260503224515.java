package main.java.sbfl;
import main.java.model.CoverageData;
import java.io.*;
import java.util.*;

public class CoverageParser {

    public static CoverageData parse(File folder) throws IOException {
        CoverageData data = new CoverageData();

        for (File file : Objects.requireNonNull(folder.listFiles())) {
            if (!file.getName().endsWith(".txt")) continue;

            List<String> lines = new ArrayList<>();
            try (BufferedReader br = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (!line.trim().isEmpty())
                        lines.add(line.trim());
                }
            }

            int i = 0;
            while (i < lines.size()) {
                String test = lines.get(i++);
                Set<String> methods = new HashSet<>();

                while (i < lines.size() && !lines.get(i).startsWith("Test")) {
                    methods.add(lines.get(i));
                    data.allMethods.add(lines.get(i));
                    i++;
                }

                data.testToMethods.put(test, methods);
            }
        }

        return data;
    }
}