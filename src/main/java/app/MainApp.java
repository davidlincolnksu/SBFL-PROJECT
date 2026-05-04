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

    @Override
    public void start(Stage stage) {
        Button loadBtn = new Button("Load Data");
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll("Tarantula", "Ochiai", "Jaccard", "Zoltar");
        combo.setValue("Tarantula");
        Button runBtn = new Button("Run SBFL");

        loadBtn.setCursor(Cursor.HAND);
        runBtn.setCursor(Cursor.HAND);
        combo.setCursor(Cursor.HAND);

        progressBar.setVisible(false);
        progressBar.setPrefWidth(200);

        setupTable();
        setupOverview();
        setupLog();

        loadBtn.setOnAction(e -> loadData(loadBtn, runBtn));
        runBtn.setOnAction(e -> run(combo.getValue(), loadBtn, runBtn));

        HBox controls = new HBox(10, loadBtn, combo, runBtn);
        controls.setAlignment(Pos.CENTER_LEFT);

        HBox statusBar = new HBox(10, statusLabel, progressBar);
        statusBar.setAlignment(Pos.CENTER_LEFT);

        TitledPane logPane = new TitledPane("Log", logArea);
        logPane.setCollapsible(true);
        logPane.setExpanded(false);

        VBox root = new VBox(10, controls, statusBar, overviewPanel, table, logPane);
        root.setStyle("-fx-padding: 12; -fx-background-color: #f4f4f4;");
        VBox.setVgrow(table, Priority.ALWAYS);

        stage.setScene(new Scene(root, 900, 700));
        stage.setTitle("SBFL Tool");
        stage.show();
    }

    private void setupLog() {
        logArea.setEditable(false);
        logArea.setPrefHeight(120);
        logArea.setStyle(
            "-fx-font-family: 'Courier New', monospace;" +
            "-fx-font-size: 11;" +
            "-fx-control-inner-background: #1e1e1e;" +
            "-fx-text-fill: #d4d4d4;"
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
        Tooltip.install(card, new Tooltip(tooltip));
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

        setWorking(true, loadBtn, runBtn, "Loading coverage data...");
        log("Loading coverage data from: " + folder.getAbsolutePath());

        Task<CoverageData> task = new Task<>() {
            @Override protected CoverageData call() throws Exception {
                return CoverageParser.parse(folder);
            }
        };

        task.setOnSucceeded(e -> {
            data = task.getValue();
            String msg = "Loaded " + data.testToMethods.size() + " tests, "
                    + data.allMethods.size() + " methods, "
                    + data.failedTests.size() + " failures.";
            log(msg);
            setWorking(false, loadBtn, runBtn, msg);
        });

        task.setOnFailed(e -> {
            log("ERROR loading data: " + task.getException().getMessage());
            task.getException().printStackTrace();
            setWorking(false, loadBtn, runBtn, "Failed to load data.");
        });

        new Thread(task).start();
    }

    private void run(String formula, Button loadBtn, Button runBtn) {
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
            if (!results.isEmpty()) {
                maxScore = results.get(0).score;
                minScore = results.get(results.size() - 1).score;
                log(formula + " complete. " + results.size() + " methods ranked.");
                log("Top suspect: " + results.get(0).method + " (score: " + String.format("%.4f", results.get(0).score) + ")");
            }
            table.getItems().setAll(results);
            updateOverview(results);
            setWorking(false, loadBtn, runBtn, formula + " complete. " + results.size() + " methods ranked.");
        });

        task.setOnFailed(e -> {
            log("ERROR running formula: " + task.getException().getMessage());
            task.getException().printStackTrace();
            setWorking(false, loadBtn, runBtn, "Run failed.");
        });

        new Thread(task).start();
    }

    private void setWorking(boolean working, Button loadBtn, Button runBtn, String message) {
        Platform.runLater(() -> {
            loadBtn.setDisable(working);
            runBtn.setDisable(working);
            progressBar.setVisible(working);
            statusLabel.setText(message);
        });
    }

    private void setupTable() {
        TableColumn<Result, String> col1 = new TableColumn<>("Method");
        col1.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().method));

        TableColumn<Result, Number> col2 = new TableColumn<>("Score");
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
            @Override protected void updateItem(Result item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null || maxScore == minScore) {
                    setStyle("");
                } else {
                    double ratio = (item.score - minScore) / (maxScore - minScore);
                    int r = (int)(180 + ratio * 55);
                    int g = (int)(230 - ratio * 75);
                    int b = (int)(180 - ratio * 25);
                    setStyle(String.format("-fx-background-color: rgb(%d,%d,%d);", r, g, b));
                }
            }
        });

        table.getColumns().addAll(col1, col2);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
    }

    public static void main(String[] args) {
        launch();
    }
}
