package com.bms;

import com.bms.database.DatabaseInitializer;
import com.bms.database.DemoDataLoader;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs EVERY self-check program, one after the other, and writes the real results to
 *   docs/testing/automated-results.md   (a table you can show in your portfolio)
 *   docs/testing/automated-details.txt  (every PASS/FAIL line of every check)
 *
 * How to run: right-click this file -> Run File. It takes about half a minute.
 * Each check runs as its own program (so a crash in one cannot hide the others) and cleans up its own test rows.
 * Nothing in the table is typed by hand: the numbers are read from what each check printed.
 */
public final class AllChecks {

    /** feature area, class to run */
    private static final String[][] SUITES = {
        {"Database rules (constraints)", "com.bms.database.SchemaCheck"},
        {"Login and passwords", "com.bms.service.AuthServiceCheck"},
        {"Dashboard", "com.bms.service.DashboardServiceCheck"},
        {"Customers", "com.bms.service.CustomerServiceCheck"},
        {"Products", "com.bms.service.ProductServiceCheck"},
        {"Suppliers", "com.bms.service.SupplierServiceCheck"},
        {"Employees and user accounts", "com.bms.service.EmployeeServiceCheck"},
        {"Inventory and stock history", "com.bms.service.InventoryServiceCheck"},
        {"Orders (create, cancel, permissions)", "com.bms.service.OrderServiceCheck"},
        {"Reports", "com.bms.service.ReportServiceCheck"},
        {"Search and filtering", "com.bms.service.SearchFilterCheck"},
        {"Validation and error handling", "com.bms.service.ErrorHandlingCheck"},
        {"Volume and speed (thousands of rows)", "com.bms.service.VolumeCheck"},
        {"Data integrity of the whole database", "com.bms.service.DataIntegrityCheck"},
    };

    private static final Pattern SUMMARY = Pattern.compile("(\\d+) of (\\d+) checks passed");

    private AllChecks() {
    }

    private record Result(String feature, String className, int passed, int total, int failLines, boolean ranOk, double seconds, String output) {
        boolean allPassed() {
            return ranOk && total > 0 && passed == total && failLines == 0;
        }
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Preparing the database...");
        DatabaseInitializer.initialise();
        System.out.println(DemoDataLoader.load() ? "Demo data was added (it was missing)." : "Demo data already present.");
        System.out.println();

        List<Result> results = new ArrayList<>();
        for (String[] suite : SUITES) {
            System.out.print(String.format("%-45s", suite[0]) + " ... ");
            Result r = run(suite[0], suite[1]);
            results.add(r);
            System.out.println(r.ranOk() ? (r.passed() + " of " + r.total() + (r.allPassed() ? "  PASS" : "  FAIL"))
                    : "COULD NOT RUN");
        }

        String markdown = buildMarkdown(results);
        System.out.println();
        System.out.println(markdown);

        Path folder = Path.of("docs", "testing");
        Files.createDirectories(folder);
        Files.writeString(folder.resolve("automated-results.md"), markdown, StandardCharsets.UTF_8);
        Files.writeString(folder.resolve("automated-details.txt"), buildDetails(results), StandardCharsets.UTF_8);
        System.out.println("Saved: " + folder.resolve("automated-results.md").toAbsolutePath());
        System.out.println("Saved: " + folder.resolve("automated-details.txt").toAbsolutePath());

        boolean everythingPassed = results.stream().allMatch(Result::allPassed);
        System.out.println(everythingPassed ? "ALL SUITES PASSED." : "SOME SUITES FAILED - open automated-details.txt and search for FAIL.");
        System.exit(everythingPassed ? 0 : 1);
    }

    /** Starts one check as a separate program and reads what it prints. */
    private static Result run(String feature, String className) {
        long started = System.nanoTime();
        try {
            String javaName = System.getProperty("os.name", "").toLowerCase().contains("win") ? "java.exe" : "java";
            String java = Path.of(System.getProperty("java.home"), "bin", javaName).toString();
            Process process = new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"), className)
                    .directory(new File("").getAbsoluteFile())
                    .redirectErrorStream(true)
                    .start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            process.waitFor();
            double seconds = (System.nanoTime() - started) / 1_000_000_000.0;

            int passed = 0;
            int total = 0;
            Matcher m = SUMMARY.matcher(output);
            while (m.find()) {              // use the LAST summary line
                passed = Integer.parseInt(m.group(1));
                total = Integer.parseInt(m.group(2));
            }
            int failLines = 0;
            for (String line : output.split("\\R")) {
                if (line.startsWith("FAIL")) {
                    failLines++;
                }
            }
            return new Result(feature, className, passed, total, failLines, total > 0, seconds, output);
        } catch (IOException | InterruptedException e) {
            return new Result(feature, className, 0, 0, 0, false, 0, "Could not run: " + e);
        }
    }

    private static String buildMarkdown(List<Result> results) {
        int passed = results.stream().mapToInt(Result::passed).sum();
        int total = results.stream().mapToInt(Result::total).sum();
        StringBuilder sb = new StringBuilder();
        sb.append("# Automated test results\n\n");
        sb.append("Run on ").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")))
          .append(" | Java ").append(System.getProperty("java.version"))
          .append(" | ").append(System.getProperty("os.name")).append("\n\n");
        sb.append("Produced by `com.bms.AllChecks`. Every number below was read from the output of the real check programs; ")
          .append("each program adds its own test rows, tests the rules, and removes its rows again.\n\n");
        sb.append("| Feature area | Check program | Checks | Passed | Failed | Result | Seconds |\n");
        sb.append("|---|---|---:|---:|---:|---|---:|\n");
        for (Result r : results) {
            sb.append("| ").append(r.feature()).append(" | `").append(r.className().substring(r.className().lastIndexOf('.') + 1))
              .append("` | ").append(r.total()).append(" | ").append(r.passed()).append(" | ")
              .append(r.total() - r.passed()).append(" | ")
              .append(r.ranOk() ? (r.allPassed() ? "PASS" : "FAIL") : "COULD NOT RUN")
              .append(" | ").append(String.format("%.1f", r.seconds())).append(" |\n");
        }
        sb.append("| **Total** | | **").append(total).append("** | **").append(passed).append("** | **")
          .append(total - passed).append("** | **")
          .append(results.stream().allMatch(Result::allPassed) ? "PASS" : "FAIL").append("** | |\n");

        List<String> problems = new ArrayList<>();
        for (Result r : results) {
            for (String line : r.output().split("\\R")) {
                if (line.startsWith("FAIL")) {
                    problems.add(r.feature() + ": " + line);
                }
            }
            if (!r.ranOk()) {
                problems.add(r.feature() + ": the check program did not print a result");
            }
        }
        if (!problems.isEmpty()) {
            sb.append("\n## Failures\n\n");
            problems.forEach(p -> sb.append("- ").append(p).append("\n"));
        }
        return sb.toString();
    }

    private static String buildDetails(List<Result> results) {
        StringBuilder sb = new StringBuilder();
        for (Result r : results) {
            sb.append("==================== ").append(r.feature()).append(" (").append(r.className()).append(") ====================\n");
            for (String line : r.output().split("\\R")) {
                if (!line.startsWith("Picked up JAVA_TOOL_OPTIONS")) {
                    sb.append(line).append("\n");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }
}
