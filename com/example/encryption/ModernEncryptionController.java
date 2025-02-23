package com.example.encryption;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.concurrent.Task;
import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.input.TransferMode;
import org.kordamp.ikonli.javafx.FontIcon;

public class ModernEncryptionController {
    @FXML private TableView<FileItem> fileTable; // 파일 목록 테이블
    @FXML private ComboBox<String> chunkSizeCombo; // 청크 크기 선택 드롭다운
    @FXML private ProgressBar progressBar; // 진행률 표시 바
    @FXML private Label progressLabel; // 진행 상태 메시지
    @FXML private Label statusLabel; // 현재 상태 표시
    @FXML private Button encryptButton; // 암호화 버튼
    @FXML private Button decryptButton; // 복호화 버튼
    @FXML private Label memoryLabel; // 메모리 사용량 표시

    private EncryptedFileSystem efs;
    private File currentDirectory;
    private ObservableList<FileItem> fileItems;
    private ScheduledExecutorService executorService;

    @FXML
    public void initialize() {
        efs = new EncryptedFileSystem();
        fileItems = FXCollections.observableArrayList();

        setupUI();
        setupTableColumns();
        setupChunkSizeCombo();
        setupDragAndDrop();
        setupMemoryMonitoring();
    }

    // UI 초기화
    private void setupUI() {
        fileTable.setItems(fileItems);
        encryptButton.setGraphic(new FontIcon("fas-lock"));
        decryptButton.setGraphic(new FontIcon("fas-unlock"));
        progressBar.setProgress(0);
        progressLabel.setText("준비");
        memoryLabel.setText("메모리: 초기화 중...");
    }

    // 테이블 열 설정
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

    // 청크 크기 드롭다운 설정
    private void setupChunkSizeCombo() {
        chunkSizeCombo.getItems().addAll(
            "1 MB", "16 MB", "32 MB", "64 MB",
            "128 MB", "256 MB", "512 MB", "1 GB"
        );
        chunkSizeCombo.setValue("32 MB");
    }

    // 드래그 앤 드롭 설정
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

    // 메모리 모니터링 설정
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
        }, 0, 5, TimeUnit.SECONDS); // 5초마다 갱신
    }

    // 애플리케이션 종료 시 리소스 정리
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
                File keyFile = keyChooser.showSaveDialog(null);

                if (keyFile != null) {
                    efs.generateKey(keyFile.getPath(), password);
                    showAlert(Alert.AlertType.INFORMATION, "성공", "키가 성공적으로 생성되었습니다");
                    statusLabel.setText("키 로드됨");
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
                    statusLabel.setText("키 로드됨");
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "오류", e.getMessage());
            }
        });
    }

    @FXML
    private void onEncrypt() {
        if (fileItems.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "경고", "선택된 파일이 없습니다");
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
                    updateMessage("암호화 중: " + item.getName());

                    int chunkSize = parseChunkSize(chunkSizeCombo.getValue());
                    efs.encryptFile(file.getPath(), chunkSize);

                    Platform.runLater(() -> {
                        item.setStatus("암호화됨");
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
            progressLabel.setText("암호화 완료");
            updateFileList();
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            showAlert(Alert.AlertType.ERROR, "오류", task.getException().getMessage());
        });

        new Thread(task).start();
    }

    @FXML
    private void onDecrypt() {
        List<FileItem> encryptedFiles = fileItems.filtered(item -> 
            item.getName().endsWith(".lock"));

        if (encryptedFiles.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "경고", "선택된 암호화 파일이 없습니다");
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
                    updateMessage("복호화 중: " + item.getName());

                    efs.decryptFile(file.getPath());

                    Platform.runLater(() -> {
                        item.setStatus("복호화됨");
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
            progressLabel.setText("복호화 완료");
            updateFileList();
        });

        task.setOnFailed(e -> {
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            showAlert(Alert.AlertType.ERROR, "오류", task.getException().getMessage());
        });

        new Thread(task).start();
    }

    // 파일 목록 갱신
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

    // 드롭된 파일 처리
    private void handleFileDrop(List<File> files) {
        for (File file : files) {
            fileItems.add(new FileItem(file));
        }
    }

    // 청크 크기 파싱
    private int parseChunkSize(String sizeStr) {
        String[] parts = sizeStr.split(" ");
        int size = Integer.parseInt(parts[0]);
        if (parts[1].equals("GB")) {
            size *= 1024;
        }
        return size * 1024 * 1024;
    }

    // 알림창 표시
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);
        alert.setTitle(title);
        alert.setContentText(content);
        alert.showAndWait();
    }
}