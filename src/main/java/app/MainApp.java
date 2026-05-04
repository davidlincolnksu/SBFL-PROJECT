// 2026-05-03
package app;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

import model.CoverageData;
import model.Result;
import sbfl.CoverageParser;
import sbfl.SBFL;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;

public class MainApp extends Application {

    private CoverageData data = null;
    private TableView<Result> table = new TableView<>();
    private Label statusLabel = new Label("No data loaded.");
    private ProgressBar progressBar = new ProgressBar();
    private TextArea logArea = new TextArea();

    private VBox overviewPanel = new VBox(10);
    private Label statTests = new Label("-");
    private Label statFailed = new Label("-");
    private Label statPassed = new Label("-");
    private Label statMethods = new Label("-");

    private double minScore = 0, maxScore = 1;
    private String lastFormula = "";
    private List<Result> lastResults = null;
    private Stage primaryStage;

    @Override
    public void start(Stage stage) {
        primaryStage = stage;

        Button loadBtn = new Button("Load Data");
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll("Tarantula", "Ochiai", "Jaccard", "Zoltar");
        combo.setValue("Tarantula");
        Button runBtn = new Button("Run SBFL");
        Button exportBtn = new Button("Export Results");

        loadBtn.setCursor(Cursor.HAND);
        runBtn.setCursor(Cursor.HAND);
        exportBtn.setCursor(Cursor.HAND);
        combo.setCursor(Cursor.HAND);
        exportBtn.setDisable(true);

        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);

        setupTable();
        setupOverview();
        setupLog();

        loadBtn.setOnAction(e -> loadData(loadBtn, runBtn));
        runBtn.setOnAction(e -> run(combo.getValue(), loadBtn, runBtn, exportBtn));
        exportBtn.setOnAction(e -> export());

        HBox controls = new HBox(10, loadBtn, combo, runBtn, exportBtn);
        controls.setAlignment(Pos.CENTER_LEFT);

        HBox statusBar = new HBox(10, statusLabel, progressBar);
        statusBar.setAlignment(Pos.CENTER_LEFT);

        TitledPane logPane = new TitledPane("Log", logArea);
        logPane.setCollapsible(true);
        logPane.setExpanded(false);
        logPane.setAnimated(false);

        VBox root = new VBox(10, controls, statusBar, overviewPanel, table, logPane);
        root.setStyle("-fx-padding: 12; -fx-background-color: #f4f4f4;");
        VBox.setVgrow(table, Priority.ALWAYS);

        stage.setScene(new Scene(root, 900, 700));
        stage.setTitle("SBFL Tool");
        stage.show();
    }

    private void setupLog() {
        logArea.setEditable(false);
        logArea.setMouseTransparent(true);
        logArea.setPrefHeight(200);
        logArea.setStyle(
            "-fx-font-family: 'Courier New', monospace;" +
            "-fx-font-size: 11;" +
            "-fx-control-inner-background: white;" +
            "-fx-text-fill: black;"
        );
    }

    private void log(String msg) {
        System.out.println(msg);
        Platform.runLater(() -> logArea.appendText(msg + "\n"));
    }

    private void setupOverview() {
        overviewPanel.setVisible(false);
        overviewPanel.setManaged(false);

        HBox statsRow = new HBox(12,
                statCard(statTests,   "Total Tests", "Total number of test cases loaded from the dataset"),
                statCard(statFailed,  "Failed",      "Tests that failed, used as the fault signal in SBFL formulas"),
                statCard(statPassed,  "Passed",      "Tests that passed, used to distinguish safe methods from suspicious ones"),
                statCard(statMethods, "Methods",     "Unique methods observed across all test executions"));

        overviewPanel.getChildren().add(statsRow);
    }

    private VBox statCard(Label valueLabel, String title, String tooltip) {
        valueLabel.setFont(Font.font("System", FontWeight.BOLD, 22));
        valueLabel.setStyle("-fx-text-fill: black;");

        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-text-fill: black; -fx-font-size: 11;");

        VBox card = new VBox(2, valueLabel, titleLabel);
        card.setAlignment(Pos.CENTER);
        card.setStyle(
            "-fx-background-color: white;" +
            "-fx-border-color: #dddddd;" +
            "-fx-border-radius: 6;" +
            "-fx-background-radius: 6;" +
            "-fx-padding: 12 20 12 20;"
        );
        card.setPrefWidth(160);
        Tooltip t = new Tooltip(tooltip);
        t.setShowDelay(javafx.util.Duration.millis(100));
        t.setShowDuration(javafx.util.Duration.seconds(10));
        Tooltip.install(card, t);
        return card;
    }

    private void updateOverview(List<Result> results) {
        int total = data.testToMethods.size();
        int failed = data.failedTests.size();

        statTests.setText(String.valueOf(total));
        statFailed.setText(String.valueOf(failed));
        statPassed.setText(String.valueOf(total - failed));
        statMethods.setText(String.valueOf(results.size()));

        overviewPanel.setVisible(true);
        overviewPanel.setManaged(true);
    }

    private void loadData(Button loadBtn, Button runBtn) {
        File folder = new File("CoverageData");
        if (!folder.exists() || !folder.isDirectory()) {
            new Alert(Alert.AlertType.ERROR, "CoverageData folder not found next to project root.").showAndWait();
            return;
        }

        setWorking(true, null, runBtn, "Loading coverage data...");
        log("Loading coverage data from: " + folder.getAbsolutePath());

        Task<CoverageData> task = new Task<>() {
            @Override protected CoverageData call() throws Exception {
                return CoverageParser.parse(folder);
            }
        };

        task.setOnSucceeded(e -> {
            data = task.getValue();
            if (data.failedTests.isEmpty()) {
                SBFL.injectFailures(data);
                log("No failures in dataset. Marked every 5th test as failed.");
            }
            String msg = "Loaded " + data.testToMethods.size() + " tests, "
                    + data.allMethods.size() + " methods, "
                    + data.failedTests.size() + " failures.";
            log(msg);
            setWorking(false, null, runBtn, msg);
        });

        task.setOnFailed(e -> {
            log("ERROR loading data: " + task.getException().getMessage());
            task.getException().printStackTrace();
            setWorking(false, null, runBtn, "Failed to load data.");
        });

        new Thread(task).start();
    }

    private void run(String formula, Button loadBtn, Button runBtn, Button exportBtn) {
        if (data == null) {
            statusLabel.setText("Load data first.");
            return;
        }

        setWorking(true, loadBtn, runBtn, "Running " + formula + "...");
        log("Running formula: " + formula);

        Task<List<Result>> task = new Task<>() {
            @Override protected List<Result> call() {
                return SBFL.rank(data, formula);
            }
        };

        task.setOnSucceeded(e -> {
            List<Result> results = task.getValue();
            lastResults = results;
            lastFormula = formula;
            if (!results.isEmpty()) {
                maxScore = results.get(0).score;
                minScore = results.get(results.size() - 1).score;
                log(formula + " complete. " + results.size() + " methods ranked.");
                log("Top suspect: " + results.get(0).method + " (score: " + String.format("%.4f", results.get(0).score) + ")");
            }
            table.getItems().setAll(results);
            updateOverview(results);
            exportBtn.setDisable(false);
            setWorking(false, loadBtn, runBtn, formula + " complete. " + results.size() + " methods ranked.");
        });

        task.setOnFailed(e -> {
            log("ERROR running formula: " + task.getException().getMessage());
            task.getException().printStackTrace();
            setWorking(false, loadBtn, runBtn, "Run failed.");
        });

        new Thread(task).start();
    }

    private void export() {
        if (lastResults == null || lastResults.isEmpty()) return;

        javafx.stage.FileChooser fc = new javafx.stage.FileChooser();
        fc.setTitle("Export Ranking");
        fc.setInitialFileName("sbfl_ranking_" + lastFormula.toLowerCase() + ".txt");
        fc.getExtensionFilters().add(new javafx.stage.FileChooser.ExtensionFilter("Text file", "*.txt"));
        File file = fc.showSaveDialog(primaryStage);
        if (file == null) return;

        try (PrintWriter pw = new PrintWriter(file)) {
            pw.println("SBFL Ranking - " + lastFormula);
            pw.println("Methods: " + lastResults.size()
                    + "  |  Failed tests: " + data.failedTests.size()
                    + "  |  Total tests: " + data.testToMethods.size());
            pw.println();
            pw.printf("%-6s  %-8s  %s%n", "Rank", "Score", "Method");
            pw.println("-".repeat(80));
            for (int i = 0; i < lastResults.size(); i++) {
                Result r = lastResults.get(i);
                pw.printf("%-6d  %-8s  %s%n", i + 1, String.format("%.4f", r.score), r.method);
            }
            log("Exported " + lastResults.size() + " results to " + file.getName());
        } catch (Exception ex) {
            log("ERROR exporting: " + ex.getMessage());
        }
    }

    private void setWorking(boolean working, Button loadBtn, Button runBtn, String message) {
        Platform.runLater(() -> {
            if (loadBtn != null) loadBtn.setDisable(working);
            runBtn.setDisable(working);
            progressBar.setVisible(working);
            statusLabel.setText(message);
        });
    }

    private void setupTable() {
        TableColumn<Result, Void> colRank = new TableColumn<>("#");
        colRank.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty ? null : String.valueOf(getIndex() + 1));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        colRank.setMinWidth(45);
        colRank.setMaxWidth(55);

        TableColumn<Result, String> col1 = new TableColumn<>("Method");
        col1.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().method));
        col1.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : formatMethod(item));
            }
        });

        TableColumn<Result, Number> col2 = new TableColumn<>("Suspiciousness");
        col2.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().score));
        col2.setCellFactory(tc -> new TableCell<>() {
            @Override protected void updateItem(Number item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) setText(null);
                else setText(String.format("%.4f", item.doubleValue()));
                setAlignment(Pos.CENTER_RIGHT);
            }
        });
        col2.setMaxWidth(120);
        col2.setMinWidth(80);

        table.setRowFactory(tv -> new TableRow<>() {
            {
                selectedProperty().addListener((obs, wasSelected, isSelected) -> applyColor());
            }

            @Override protected void updateItem(Result item, boolean empty) {
                super.updateItem(item, empty);
                applyColor();
            }

            private void applyColor() {
                Result item = getItem();
                if (item == null || isEmpty() || maxScore == minScore) {
                    setStyle("");
                    return;
                }
                double ratio = (item.score - minScore) / (maxScore - minScore);
                String color;
                if (isSelected()) {
                    if (ratio > 0.66)      color = "#e57373";
                    else if (ratio > 0.33) color = "#ffd54f";
                    else                   color = "#66bb6a";
                } else {
                    if (ratio > 0.66)      color = "#ffcccc";
                    else if (ratio > 0.33) color = "#fff3cc";
                    else                   color = "#ccffcc";
                }
                setStyle("-fx-background-color: " + color + ";");
            }
        });

        table.getColumns().addAll(colRank, col1, col2);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    private String formatMethod(String raw) {
        String[] parts = raw.split(":");
        String className = parts[0].replace('/', '.');
        String methodName = parts.length > 1 ? parts[1] : "";
        return methodName.isEmpty() ? className : className + "." + methodName;
    }

    public static void main(String[] args) {
        launch();
    }
}
