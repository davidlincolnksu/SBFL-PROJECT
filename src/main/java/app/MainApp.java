package main.java.app;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

import model.CoverageData;
import model.Result;
import sbfl.*;

import java.io.File;
import java.util.List;

public class MainApp extends Application {

    private CoverageData data = null;
    private TableView<Result> table = new TableView<>();

    @Override
    public void start(Stage stage) {

        Button loadBtn = new Button("Load Data");
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().addAll("Tarantula", "Ochiai", "Jaccard", "Zoltar");
        combo.setValue("Tarantula");

        Button runBtn = new Button("Run SBFL");

        setupTable();

        loadBtn.setOnAction(e -> loadData(stage));
        runBtn.setOnAction(e -> run(combo.getValue()));

        VBox root = new VBox(10, loadBtn, combo, runBtn, table);

        stage.setScene(new Scene(root, 800, 600));
        stage.setTitle("SBFL Tool");
        stage.show();
    }

    private void loadData(Stage stage) {
        DirectoryChooser dc = new DirectoryChooser();
        File folder = dc.showDialog(stage);

        try {
            data = CoverageParser.parse(folder);
            SBFL.injectFailures(data);
            System.out.println("Loaded + failures injected");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void run(String formula) {
        if (data == null) return;

        List<Result> results = SBFL.rank(data, formula);
        table.getItems().setAll(results);
    }

    private void setupTable() {
        TableColumn<Result, String> col1 = new TableColumn<>("Method");
        col1.setCellValueFactory(c -> new javafx.beans.property.SimpleStringProperty(c.getValue().method));

        TableColumn<Result, Number> col2 = new TableColumn<>("Score");
        col2.setCellValueFactory(c -> new javafx.beans.property.SimpleDoubleProperty(c.getValue().score));

        table.getColumns().addAll(col1, col2);
    }

    public static void main(String[] args) {
        launch();
    }
}