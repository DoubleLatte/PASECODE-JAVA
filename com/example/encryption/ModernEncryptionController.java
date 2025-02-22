package com.example.encryption;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.concurrent.Task;
import java.io.File;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.kordamp.ikonli.javafx.FontIcon;

public class ModernEncryptionController {
    @FXML private TableView<FileItem> fileTable;
    @FXML private ComboBox<String> chunkSizeCombo;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Label statusLabel;
    @FXML private Button encryptButton;
    @FXML private Button decryptButton;
    
    private EncryptedFileSystem efs;
    private File currentDirectory;
    private ObservableList<FileItem> fileItems;
    
    @FXML
    public void initialize() {
        efs = new EncryptedFileSystem();
        fileItems = FXCollections.observableArrayList();
        
        setupUI();
        setupTableColumns();
        setupChunkSizeCombo();
        setupDragAndDrop();
    }
    
    private void setupUI() {
        fileTable.setItems(fileItems);
        encryptButton.setGraphic(new FontIcon("fas-lock"));
        decryptButton.setGraphic(new FontIcon("fas-unlock"));
        progressBar.setProgress(0);
        progressLabel.setText("Ready");
    }
    
    private void setupTableColumns() {
        TableColumn<FileItem, String> nameCol = new TableColumn<>("Name");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        
        TableColumn<FileItem, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        
        TableColumn<FileItem, String> sizeCol = new TableColumn<>("Size");
        sizeCol.setCellValueFactory(data -> data.getValue().sizeProperty());
        
        TableColumn<FileItem, String> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        
        fileTable.getColumns().addAll(nameCol, typeCol, sizeCol, statusCol);
    }
    
    private void setupChunkSizeCombo() {
        chunkSizeCombo.getItems().addAll(
            "1 MB", "16 MB", "32 MB", "64 MB",
            "128 MB", "256 MB", "512 MB", "1 GB"
        );
        chunkSizeCombo.setValue("32 MB");
    }
    
    private void setupDragAndDrop() {
        fileTable.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY);
            }
            event.consume();
        });
        
        fileTable.setOnDragDropped(event -> {
            List<File> files = event.getDragboard().getFiles();
            handleFileDrop(files);
            event.consume();
        });
    }
    
    @FXML
    private void onOpenFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Folder");
        File directory = chooser.showDialog(null);
        
        if (directory != null) {
            currentDirectory = directory;
            updateFileList();
        }
    }
    
    @FXML
    private void onCreateKey() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Create New Key");
        dialog.setHeaderText("Enter password for new key");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        
        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();
        
        grid.add(new Label("Password:"), 0, 0);
        grid.add(password, 1, 0);
        grid.add(new Label("Confirm:"), 0, 1);
        grid.add(confirm, 1, 1);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                if (password.getText().equals(confirm.getText())) {
                    return password.getText();
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error", "Passwords do not match");
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(password -> {
            try {
                FileChooser keyChooser = new FileChooser();
                keyChooser.setTitle("Save Key File");
                File keyFile = keyChooser.showSaveDialog(null);
                
                if (keyFile != null) {
                    efs.generateKey(keyFile.getPath(), password);
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Key created successfully");
                    statusLabel.setText("Key loaded");
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
            }
        });
    }
    
    @FXML
    private void onLoadKey() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("Load Key");
        dialog.setHeaderText("Enter password for key");
        
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        
        PasswordField password = new PasswordField();
        grid.add(new Label("Password:"), 0, 0);
        grid.add(password, 1, 0);
        
        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
        
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                return password.getText();
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(password -> {
            try {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("Select Key File");
                File keyFile = chooser.showOpenDialog(null);
                
                if (keyFile != null) {
                    efs.loadKey(keyFile.getPath(), password);
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Key loaded successfully");
                    statusLabel.setText("Key loaded");
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error", e.getMessage());
            }
        });
    }
    
    @FXML
    private void onEncrypt() {
        if (fileItems.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No files selected");
            return;
        }
        
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = fileItems.size();
                for (int i = 0; i < total; i++) {
                    FileItem item = fileItems.get(i);
                    File file = new File(currentDirectory, item.getName());
                    
                    updateProgress(i, total);
                    updateMessage("Encrypting: " + item.getName());
                    
                    int chunkSize = parseChunkSize(chunkSizeCombo.getValue());
                    efs.encryptFile(file.getPath(), chunkSize);
                    
                    Platform.runLater(() -> {
                        item.setStatus("Encrypted");
                        fileTable.refresh();
                    });
                }
                return null;
            }
        };
        
        progressBar.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());
        
        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            progressBar.setProgress(1);
            progressLabel.setText("Encryption complete");
            updateFileList();
        });
        
        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            showAlert(Alert.AlertType.ERROR, "Error", task.getException().getMessage());
        });
        
        new Thread(task).start();
    }
    
    @FXML
    private void onDecrypt() {
        List<FileItem> encryptedFiles = fileItems.filtered(item -> 
            item.getName().endsWith(".lock"));
            
        if (encryptedFiles.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No encrypted files selected");
            return;
        }
        
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = encryptedFiles.size();
                for (int i = 0; i < total; i++) {
                    FileItem item = encryptedFiles.get(i);
                    File file = new File(currentDirectory, item.getName());
                    
                    updateProgress(i, total);
                    updateMessage("Decrypting: " + item.getName());
                    
                    efs.decryptFile(file.getPath());
                    
                    Platform.runLater(() -> {
                        item.setStatus("Decrypted");
                        fileTable.refresh();
                    });
                }
                return null;
            }
        };
        
        progressBar.progressProperty().bind(task.progressProperty());
        progressLabel.textProperty().bind(task.messageProperty());
        
        task.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            progressBar.setProgress(1);
            progressLabel.setText("Decryption complete");
            updateFileList();
        });
        
        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            showAlert(Alert.AlertType.ERROR, "Error", task.getException().getMessage());
        });
        
        new Thread(task).start();
    }
    
    private void updateFileList() {
        fileItems.clear();
        if (currentDirectory != null && currentDirectory.exists()) {
            File[] files = currentDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    fileItems.add(new FileItem(file));
                }
            }
        }
    }
    
    private void handleFileDrop(List<File> files) {
        for (File file : files) {
            fileItems.add(new FileItem(file));
        }
    }
    
    private int parseChunkSize(String sizeStr) {
        String[] parts = sizeStr.split(" ");
        int size = Integer.parseInt(parts[0]);
        if (parts[1].equals("GB")) {
            size *= 1024;
        }
        return size * 1024 * 1024; // Convert to bytes
    }
    
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}
