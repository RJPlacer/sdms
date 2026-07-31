package com.sdms.controller;

import com.sdms.model.SearchResult;
import com.sdms.service.DatabaseService;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;

import java.sql.SQLException;
import java.util.List;

public class GlobalSearchController {

    @FXML private TextField txtSearch;
    @FXML private TableView<SearchResult> resultsTable;
    @FXML private TableColumn<SearchResult, String> colModule, colId, colName, colDetail;
    @FXML private Label lblCount;

    private final ObservableList<SearchResult> results = FXCollections.observableArrayList();

    @FXML public void initialize() {
        colModule.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getModuleLabel()));
        colId.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getId()));
        colName.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getName()));
        colDetail.setCellValueFactory(d -> new javafx.beans.property.SimpleStringProperty(d.getValue().getDetail()));
        resultsTable.setItems(results);

        // Color-code rows by module
        resultsTable.setRowFactory(tv -> new TableRow<>() {
            @Override protected void updateItem(SearchResult item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) { setStyle(""); return; }
                setStyle(switch (item.getModule()) {
                    case ATHLETE   -> "-fx-background-color: #EBF5FB;";
                    case OFFICIAL  -> "-fx-background-color: #E9F7EF;";
                    case COACH     -> "-fx-background-color: #FEF9E7;";
                    case EQUIPMENT -> "-fx-background-color: #FDEDEC;";
                });
            }
        });

        txtSearch.textProperty().addListener((obs, old, text) -> {
            if (text != null && text.length() >= 2) doSearch(text);
            else { results.clear(); lblCount.setText(""); }
        });
        txtSearch.requestFocus();
    }

    private void doSearch(String query) {
        try {
            List<SearchResult> found = DatabaseService.getInstance().globalSearch(query);
            results.setAll(found);
            lblCount.setText(found.size() + " result" + (found.size() == 1 ? "" : "s") + " found");
        } catch (SQLException e) {
            lblCount.setText("Search error: " + e.getMessage());
        }
    }

    @FXML private void onClose() { ((Stage) txtSearch.getScene().getWindow()).close(); }
}
