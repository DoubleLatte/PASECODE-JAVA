package com.example.encryption;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.concurrent.Task;
import java.io.*;
import java.util.*;
import java.util.concurrent.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.input.TransferMode;
import org.kordamp.ikonli.javafx.FontIcon;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;

public class ModernEncryptionController {
    @FXML private TableView<FileItem> fileTable;
    @FXML private ComboBox<String> chunkSizeCombo;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Label statusLabel;
    @FXML private Button encryptButton;
    @FXML private Button decryptButton;
    @FXML private Label memoryLabel;
    @FXML private Label itemCountLabel; // 파일 개수 라벨 추가

    private EncryptedFileSystem efs;
    private File currentDirectory;
    private ObservableList<FileItem> fileItems;
    private ScheduledExecutorService executorService;
    private Task<Void> currentTask; // 작업 취소용

    @FXML
    public void initialize() {
        efs = new EncryptedFileSystem();
        fileItems = FXCollections.observableArrayList();

        setupUI();
        setupTableColumns();
        setupChunkSizeCombo();
        setupDragAndDrop();
        setupMemoryMonitoring();
        fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE); // 다중 선택 활성화
    }

    private void setupUI() {
        fileTable.setItems(fileItems);
        encryptButton.setGraphic(new FontIcon("fas-lock"));
        decryptButton.setGraphic(new FontIcon("fas-unlock"));
        progressBar.setProgress(0);
        progressLabel.setText("준비");
        memoryLabel.setText("메모리: 초기화 중...");
        itemCountLabel.setText("항목 수: 0개");
    }

    private void setupTableColumns() {
        TableColumn<FileItem, String> nameCol = new TableColumn<>("이름");
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());

        TableColumn<FileItem, String> typeCol = new TableColumn<>("유형");
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());

        TableColumn<FileItem, String> sizeCol = new TableColumn<>("크기");
        sizeCol.setCellValueFactory(data -> data.getValue().sizeProperty());

        TableColumn<FileItem, String> statusCol = new TableColumn<>("상태");
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
        fileTable.setOINTDragOver(event -> {
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

    private void setupMemoryMonitoring() {
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory() / (1024 * 1024);
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long freeMemory = runtime.freeMemory() / (1024 * 1024);

            String memoryInfo = String.format("메모리: 사용 %d MB / 최대 %d MB / 여유 %d MB",
                                              usedMemory, maxMemory, freeMemory);

            Platform.runLater(() -> memoryLabel.setText(memoryInfo));
        }, 0, 5, TimeUnit.SECONDS);
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdown();
        }
    }

    @FXML
    private void onOpenFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("폴더 선택");
        File directory = chooser.showDialog(null);

        if (directory != null) {
            currentDirectory = directory;
            updateFileList();
        }
    }

    @FXML
    private void onCreateKey() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("새 키 생성");
        dialog.setHeaderText("새 키를 위한 비밀번호 입력");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        PasswordField password = new PasswordField();
        PasswordField confirm = new PasswordField();

        grid.add(new Label("비밀번호:"), 0, 0);
        grid.add(password, 1, 0);
        grid.add(new Label("확인:"), 0, 1);
        grid.add(confirm, 1, 1);

        dialog.getDialogPane().setContent(grid);
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == ButtonType.OK) {
                if (password.getText().equals(confirm.getText())) {
                    return password.getText();
                } else {
                    showAlert(Alert.AlertType.ERROR, "오류", "비밀번호가 일치하지 않습니다");
                    return null;
                }
            }
            return null;
        });

        dialog.showAndWait().ifPresent(password -> {
            try {
                FileChooser keyChooser = new FileChooser();
                keyChooser.setTitle("키 파일 저장");
                keyChooser.setInitialFileName("mykey.key");
                keyChooser.setInitialDirectory(new File(System.getProperty("user.home")));
                keyChooser.getExtensionFilters().add(
                    new FileChooser.ExtensionFilter("Encryption Key (*.key)", "*.key")
                );

                File keyFile = keyChooser.showSaveDialog(fileTable.getScene().getWindow());

                if (keyFile != null) {
                    efs.generateKey(keyFile.getPath(), password);
                    showAlert(Alert.AlertType.INFORMATION, "성공", "키가 성공적으로 생성되었습니다");
                    statusLabel.setText("키 로드됨: " + keyFile.getPath());
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "오류", e.getMessage());
            }
        });
    }

    @FXML
    private void onLoadKey() {
        Dialog<String> dialog = new Dialog<>();
        dialog.setTitle("키 로드");
        dialog.setHeaderText("키를 위한 비밀번호 입력");

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);

        PasswordField password = new PasswordField();
        grid.add(new Label("비밀번호:"), 0, 0);
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
                chooser.setTitle("키 파일 선택");
                File keyFile = chooser.showOpenDialog(null);

                if (keyFile != null) {
                    efs.loadKey(keyFile.getPath(), password);
                    showAlert(Alert.AlertType.INFORMATION, "성공", "키가 성공적으로 로드되었습니다");
                    statusLabel.setText("키 로드됨: " + keyFile.getPath());
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "오류", e.getMessage());
            }
        });
    }

    @FXML
    private void onEncrypt() {
        ObservableList<FileItem> selectedItems = fileTable.getSelectionModel().getSelectedItems();
        if (selectedItems.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "경고", "선택된 파일이 없습니다");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("암호화 확인");
        confirm.setHeaderText("선택한 항목을 암호화하시겠습니까?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        currentTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = selectedItems.size();
                if (total == 1 && !new File(currentDirectory, selectedItems.get(0).getName()).isDirectory()) {
                    // 단일 파일: 바로 암호화
                    FileItem item = selectedItems.get(0);
                    File file = new File(currentDirectory, item.getName());
                    updateMessage("암호화 중: " + item.getName());
                    int chunkSize = parseChunkSize(chunkSizeCombo.getValue());
                    efs.encryptFile(file.getPath(), chunkSize);
                    Platform.runLater(() -> {
                        item.setStatus("암호화됨");
                        fileTable.refresh();
                    });
                } else {
                    // 폴더 또는 다중 파일: 압축 후 암호화
                    File zipFile = new File(currentDirectory, "encrypted_bundle.zip");
                    zipFiles(selectedItems, zipFile);
                    updateProgress(0.5, 1);
                    updateMessage("암호화 중: " + zipFile.getName());
                    int chunkSize = parseChunkSize(chunkSizeCombo.getValue());
                    efs.encryptFile(zipFile.getPath(), chunkSize);
                    Platform.runLater(() -> {
                        fileItems.clear();
                        fileItems.add(new FileItem(new File(zipFile.getPath() + ".lock")));
                        fileTable.refresh();
                    });
                }
                updateProgress(1, 1);
                updateMessage("암호화 완료 (100%)");
                return null;
            }
        };

        progressBar.progressProperty().bind(currentTask.progressProperty());
        progressLabel.textProperty().bind(currentTask.messageProperty());

        currentTask.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            progressBar.setProgress(1);
            progressLabel.setText("암호화 완료 (100%)");
            updateFileList();
        });

        currentTask.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            showAlert(Alert.AlertType.ERROR, "오류", currentTask.getException().getMessage());
        });

        new Thread(currentTask).start();
    }

    @FXML
    private void onDecrypt() {
        List<FileItem> encryptedFiles = fileTable.getSelectionModel().getSelectedItems().filtered(item ->
            item.getName().endsWith(".lock"));

        if (encryptedFiles.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "경고", "선택된 암호화 파일이 없습니다");
            return;
        }

        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("복호화 확인");
        confirm.setHeaderText("선택한 파일을 복호화하시겠습니까?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != ButtonType.OK) return;

        currentTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = encryptedFiles.size();
                for (int i = 0; i < total; i++) {
                    FileItem item = encryptedFiles.get(i);
                    File file = new File(currentDirectory, item.getName());

                    updateProgress(i, total);
                    updateMessage("복호화 중: " + item.getName());

                    String decryptedPath = efs.decryptFile(file.getPath());
                    File decryptedFile = new File(decryptedPath);
                    if (decryptedFile.getName().endsWith(".zip")) {
                        unzipFile(decryptedFile, currentDirectory);
                    }

                    Platform.runLater(() -> {
                        item.setStatus("복호화 및 해제 완료");
                        fileTable.refresh();
                    });
                }
                updateProgress(1, 1);
                updateMessage("복호화 완료 (100%)");
                return null;
            }
        };

        progressBar.progressProperty().bind(currentTask.progressProperty());
        progressLabel.textProperty().bind(currentTask.messageProperty());

        currentTask.setOnSucceeded(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            progressBar.setProgress(1);
            progressLabel.setText("복호화 완료 (100%)");
            updateFileList();
        });

        currentTask.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            showAlert(Alert.AlertType.ERROR, "오류", currentTask.getException().getMessage());
        });

        new Thread(currentTask).start();
    }

    @FXML
    private void onExit() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("종료 확인");
        confirm.setHeaderText("프로그램을 종료하시겠습니까?");
        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            shutdown();
            Platform.exit();
        }
    }

    @FXML
    private void cancelTask() {
        if (currentTask != null && currentTask.isRunning()) {
            currentTask.cancel();
            progressLabel.setText("작업 취소됨");
            progressBar.setProgress(0);
        }
    }

    private void updateFileList() {
        fileItems.clear();
        if (currentDirectory != null && currentDirectory.exists()) {
            File[] files = currentDirectory.listFiles();
            if (files != null) {
                for (File file : files) {
                    fileItems.add(new FileItem(file));
                }
                Platform.runLater(() -> 
                    itemCountLabel.setText("항목 수: " + files.length + "개")
                );
            } else {
                Platform.runLater(() -> itemCountLabel.setText("항목 수: 0개"));
            }
        }
    }

    private void handleFileDrop(List<File> files) {
        for (File file : files) {
            fileItems.add(new FileItem(file));
        }
        updateFileList();
    }

    private int parseChunkSize(String sizeStr) {
        String[] parts = sizeStr.split(" ");
        int size = Integer.parseInt(parts[0]);
        if (parts[1].equals("GB")) {
            size *= 1024;
        }
        return size * 1024 * 1024;
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void zipFiles(ObservableList<FileItem> items, File zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (FileItem item : items) {
                File file = new File(currentDirectory, item.getName());
                addToZip(file, zos, "");
            }
        }
    }

    private void addToZip(File file, ZipOutputStream zos, String parentPath) throws IOException {
        String zipEntryName = parentPath + file.getName();
        if (file.isDirectory()) {
            zipEntryName += "/";
            zos.putNextEntry(new ZipEntry(zipEntryName));
            zos.closeEntry();
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    addToZip(child, zos, zipEntryName);
                }
            }
        } else {
            zos.putNextEntry(new ZipEntry(zipEntryName));
            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buffer = new byte[1024];
                int len;
                while ((len = fis.read(buffer)) > 0) {
                    zos.write(buffer, 0, len);
                }
            }
            zos.closeEntry();
        }
    }

    private void unzipFile(File zipFile, File destDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[1024];
            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(destDir, entry.getName());
                if (entry.isDirectory()) {
                    newFile.mkdirs();
                } else {
                    newFile.getParentFile().mkdirs();
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
        zipFile.delete(); // 해제 후 ZIP 파일 삭제
    }

    @FXML
    private void showInfo() {
        Dialog<Void> dialog = new Dialog<>();
        dialog.setTitle("PASSCODE 정보");
        dialog.setHeaderText("프로그램 정보");

        TextArea infoText = new TextArea();
        infoText.setEditable(false);
        infoText.setText(
            "PASSCODE v" + ModernEncryptionApp.getVersion() + "\n\n" +
            "사용법:\n" +
            "1. 폴더 또는 파일을 드래그 앤 드롭하거나 '폴더 열기'를 통해 선택하세요.\n" +
            "2. '새 키 생성' 또는 '키 로드'를 통해 암호화 키를 설정하세요.\n" +
            "3. '암호화' 버튼으로 파일/폴더를 암호화하거나, '복호화' 버튼으로 복원하세요.\n\n" +
            "사용된 라이브러리:\n" +
            "- JavaFX: UI 구현\n" +
            "- Ikonli: 아이콘 제공\n" +
            "- Java Cryptography Architecture (JCA): 암호화/복호화\n"
        );

        dialog.getDialogPane().setContent(infoText);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        dialog.showAndWait();
    }
}
