package com.example.encryption;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.FileChooser;
import javafx.concurrent.Task;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import org.kordamp.ikonli.javafx.FontIcon;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipException;
import java.nio.file.AccessDeniedException;

public class ModernEncryptionController {
    @FXML private TableView<FileItem> fileTable;
    @FXML private ComboBox<String> chunkSizeCombo;
    @FXML private ProgressBar progressBar;
    @FXML private Label progressLabel;
    @FXML private Label statusLabel;
    @FXML private Button encryptButton;
    @FXML private Button decryptButton;
    @FXML private Label memoryLabel;
    @FXML private Label itemCountLabel;
    @FXML private Menu themeMenu;

    private EncryptedFileSystem efs;
    private File currentDirectory;
    private ObservableList<FileItem> fileItems;
    private ScheduledExecutorService executorService;
    private Task<Void> currentTask;

    @FXML
    public void initialize() {
        efs = new EncryptedFileSystem();
        fileItems = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());

        try {
            setupUI();
            Platform.runLater(this::setupTableColumns);
            setupChunkSizeCombo();
            setupMemoryMonitoring();
            setupThemes();
            fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
            loadSettings();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "초기화 오류", "UI 로드 실패: " + e.getMessage());
            Platform.exit();
        }
    }

    private void setupUI() {
        fileTable.setItems(fileItems);
        try {
            encryptButton.setGraphic(new FontIcon("fas-lock"));
            decryptButton.setGraphic(new FontIcon("fas-unlock"));
        } catch (IllegalArgumentException e) {
            showAlert(Alert.AlertType.WARNING, "아이콘 오류", "아이콘 로드 실패: " + e.getMessage());
        }
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
        chunkSizeCombo.getItems().addAll("1 MB", "16 MB", "32 MB", "64 MB", "128 MB", "256 MB", "512 MB", "1 GB");
        chunkSizeCombo.setValue("32 MB");
    }

    private void setupMemoryMonitoring() {
        executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(() -> {
            Runtime runtime = Runtime.getRuntime();
            long maxMemory = runtime.maxMemory() / (1024 * 1024);
            long usedMemory = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long freeMemory = runtime.freeMemory() / (1024 * 1024);
            String memoryInfo = String.format("메모리: 사용 %d MB / 최대 %d MB / 여유 %d MB", usedMemory, maxMemory, freeMemory);
            Platform.runLater(() -> memoryLabel.setText(memoryInfo));
        }, 0, 5, TimeUnit.SECONDS);
    }

    private void setupThemes() {
        MenuItem darkTheme = new MenuItem("다크 테마");
        darkTheme.setOnAction(e -> {
            try {
                fileTable.getScene().getStylesheets().setAll(getClass().getResource("/styles/dark.css").toExternalForm());
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "테마 오류", "다크 테마 로드 실패: " + ex.getMessage());
            }
        });
        MenuItem lightTheme = new MenuItem("라이트 테마");
        lightTheme.setOnAction(e -> {
            try {
                fileTable.getScene().getStylesheets().setAll(getClass().getResource("/styles/modern.css").toExternalForm());
            } catch (Exception ex) {
                showAlert(Alert.AlertType.ERROR, "테마 오류", "라이트 테마 로드 실패: " + ex.getMessage());
            }
        });
        themeMenu.getItems().addAll(darkTheme, lightTheme);
    }

    public void shutdown() {
        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
            if (!executorService.isShutdown()) {
                showAlert(Alert.AlertType.WARNING, "종료 경고", "메모리 모니터링 종료 실패");
            }
        }
    }

    @FXML
    private void onOpenFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("폴더 선택");
        try {
            File directory = chooser.showDialog(null);
            if (directory != null) {
                currentDirectory = directory;
                updateFileList();
            }
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "폴더 선택 오류", "디렉토리 선택 실패: " + e.getMessage());
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
            if (dialogButton == ButtonType.OK && password.getText().equals(confirm.getText())) {
                return password.getText();
            }
            showAlert(Alert.AlertType.ERROR, "오류", "비밀번호가 일치하지 않습니다");
            return null;
        });

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            String pwd = result.get();
            FileChooser keyChooser = new FileChooser();
            keyChooser.setTitle("키 파일 저장");
            keyChooser.setInitialFileName("mykey.key");
            keyChooser.setInitialDirectory(new File(System.getProperty("user.home")));
            keyChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("Encryption Key (*.key)", "*.key"));
            File keyFile = keyChooser.showSaveDialog(fileTable.getScene().getWindow());
            if (keyFile != null) {
                try {
                    efs.generateKey(keyFile.getPath(), pwd);
                    showAlert(Alert.AlertType.INFORMATION, "성공", "키가 성공적으로 생성되었습니다");
                    statusLabel.setText("키 로드됨: " + keyFile.getPath());
                } catch (Exception e) {
                    showAlert(Alert.AlertType.ERROR, "오류", e.getMessage());
                }
            }
        }
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

        dialog.setResultConverter(dialogButton -> dialogButton == ButtonType.OK ? password.getText() : null);

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            try {
                FileChooser chooser = new FileChooser();
                chooser.setTitle("키 파일 선택");
                File keyFile = chooser.showOpenDialog(null);
                if (keyFile != null) {
                    efs.loadKey(keyFile.getPath(), result.get());
                    showAlert(Alert.AlertType.INFORMATION, "성공", "키가 성공적으로 로드되었습니다");
                    statusLabel.setText("키 로드됨: " + keyFile.getPath());
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "오류", e.getMessage());
            }
        }
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

        ExecutorService executor;
        try {
            executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "스레드 오류", "작업 스레드 생성 실패: " + e.getMessage());
            return;
        }
        List<Future<?>> futures = new ArrayList<>();

        currentTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = selectedItems.size();
                if (total == 1 && !new File(currentDirectory, selectedItems.get(0).getName()).isDirectory()) {
                    FileItem item = selectedItems.get(0);
                    File file = new File(currentDirectory, item.getName());
                    File backupFile = new File(file.getPath() + ".backup");
                    try {
                        Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (IOException e) {
                        showAlert(Alert.AlertType.ERROR, "백업 오류", "백업 생성 실패: " + e.getMessage());
                        return null;
                    }

                    File tempDecrypted = null;
                    try {
                        tempDecrypted = new File(currentDirectory, "temp_" + item.getName());
                        Future<?> future = executor.submit(() -> {
                            try {
                                updateMessage("암호화 중: " + item.getName());
                                int chunkSize;
                                try {
                                    chunkSize = parseChunkSize(chunkSizeCombo.getValue());
                                } catch (NumberFormatException e) {
                                    chunkSize = 32 * 1024 * 1024; // 기본값 32MB
                                    Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "청크 오류", "청크 크기 형식이 잘못됨, 기본값 32MB 사용"));
                                }
                                String encryptedPath = efs.encryptFile(file.getPath(), chunkSize);
                                String decryptedPath = efs.decryptFile(encryptedPath, tempDecrypted.getPath());
                                String originalHash = calculateFileHash(file);
                                String decryptedHash = calculateFileHash(tempDecrypted);

                                if (originalHash.equals(decryptedHash)) {
                                    efs.secureDelete(backupFile.getPath());
                                    efs.secureDelete(file.getPath());
                                    efs.secureDelete(tempDecrypted.getPath());
                                    efs.deleteEncryptedFile(encryptedPath);
                                    Platform.runLater(() -> item.setStatus("암호화됨"));
                                } else {
                                    Files.copy(backupFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                    efs.secureDelete(backupFile.getPath());
                                    efs.secureDelete(encryptedPath);
                                    efs.secureDelete(tempDecrypted.getPath());
                                    throw new Exception("무결성 검증 실패: " + item.getName());
                                }
                            } catch (AccessDeniedException e) {
                                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "권한 오류", item.getName() + "에 대한 접근 권한이 없습니다"));
                            } catch (Exception e) {
                                Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "오류", e.getMessage()));
                            }
                        });
                        futures.add(future);
                    } finally {
                        if (tempDecrypted != null && tempDecrypted.exists()) {
                            efs.secureDelete(tempDecrypted.getPath());
                        }
                    }
                } else {
                    File zipFile = new File(currentDirectory, "encrypted_bundle.zip");
                    File tempDecryptedZip = new File(currentDirectory, "temp_encrypted_bundle.zip");
                    File backupZip = new File(zipFile.getPath() + ".backup");

                    try {
                        zipFiles(selectedItems, zipFile);
                        Files.copy(zipFile.toPath(), backupZip.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    } catch (AccessDeniedException e) {
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "권한 오류", "압축 중 접근 권한이 없는 파일이 있습니다"));
                        return null;
                    } catch (IOException e) {
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "백업 오류", "압축 파일 백업 실패: " + e.getMessage()));
                        return null;
                    }

                    Future<?> future = executor.submit(() -> {
                        try {
                            updateProgress(0.5, 1);
                            updateMessage("암호화 중: " + zipFile.getName());
                            int chunkSize;
                            try {
                                chunkSize = parseChunkSize(chunkSizeCombo.getValue());
                            } catch (NumberFormatException e) {
                                chunkSize = 32 * 1024 * 1024; // 기본값 32MB
                                Platform.runLater(() -> showAlert(Alert.AlertType.WARNING, "청크 오류", "청크 크기 형식이 잘못됨, 기본값 32MB 사용"));
                            }
                            String encryptedPath = efs.encryptFile(zipFile.getPath(), chunkSize);
                            String decryptedPath = efs.decryptFile(encryptedPath, tempDecryptedZip.getPath());
                            String originalHash = calculateFileHash(zipFile);
                            String decryptedHash = calculateFileHash(tempDecryptedZip);

                            if (originalHash.equals(decryptedHash)) {
                                efs.secureDelete(backupZip.getPath());
                                efs.secureDelete(zipFile.getPath());
                                efs.secureDelete(tempDecryptedZip.getPath());
                                Platform.runLater(() -> {
                                    synchronized (fileItems) {
                                        fileItems.clear();
                                        fileItems.add(new FileItem(new File(encryptedPath)));
                                        fileTable.refresh();
                                    }
                                });
                            } else {
                                Files.copy(backupZip.toPath(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                efs.secureDelete(backupZip.getPath());
                                efs.secureDelete(encryptedPath);
                                efs.secureDelete(tempDecryptedZip.getPath());
                                throw new Exception("압축 파일 무결성 검증 실패");
                            }
                        } catch (AccessDeniedException e) {
                            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "권한 오류", zipFile.getName() + "에 대한 접근 권한이 없습니다"));
                        } catch (Exception e) {
                            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "오류", e.getMessage()));
                        }
                    });
                    futures.add(future);
                }

                for (Future<?> future : futures) {
                    future.get();
                }
                executor.shutdownNow();

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

        ExecutorService executor;
        try {
            executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "스레드 오류", "작업 스레드 생성 실패: " + e.getMessage());
            return;
        }
        List<Future<?>> futures = new ArrayList<>();

        currentTask = new Task<>() {
            @Override
            protected Void call() throws Exception {
                int total = encryptedFiles.size();
                for (int i = 0; i < total; i++) {
                    FileItem item = encryptedFiles.get(i);
                    File file = new File(currentDirectory, item.getName());
                    String outputPath = generateUniqueOutputPath(file.getPath().substring(0, file.getPath().length() - 5));

                    Future<?> future = executor.submit(() -> {
                        try {
                            updateProgress(i, total);
                            updateMessage("복호화 중: " + item.getName());
                            String decryptedPath = efs.decryptFile(file.getPath(), outputPath);
                            File decryptedFile = new File(decryptedPath);
                            if (decryptedFile.getName().endsWith(".zip")) {
                                unzipFile(decryptedFile, currentDirectory);
                            }
                            efs.deleteEncryptedFile(file.getPath());
                            Platform.runLater(() -> {
                                synchronized (fileItems) {
                                    item.setStatus("복호화 및 해제 완료");
                                    fileTable.refresh();
                                }
                            });
                        } catch (AccessDeniedException e) {
                            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "권한 오류", item.getName() + "에 대한 접근 권한이 없습니다"));
                        } catch (Exception e) {
                            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "오류", e.getMessage()));
                        }
                    });
                    futures.add(future);
                }

                for (Future<?> future : futures) {
                    future.get();
                }
                executor.shutdownNow();

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

    private void zipFiles(ObservableList<FileItem> items, File zipFile) throws IOException {
        try (FileOutputStream fos = new FileOutputStream(zipFile);
             ZipOutputStream zos = new ZipOutputStream(fos)) {
            for (FileItem item : items) {
                File file = new File(currentDirectory, item.getName());
                addToZip(file, zos, "");
            }
        } catch (AccessDeniedException e) {
            throw new AccessDeniedException("파일 접근 권한 부족: " + zipFile.getName());
        } catch (IOException e) {
            throw new IOException("압축 오류: " + e.getMessage());
        }
    }

    @FXML
    private void onExit() {
        saveSettings();
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
            if (currentTask.cancel()) {
                progressLabel.setText("작업 취소됨");
                progressBar.setProgress(0);
            } else {
                showAlert(Alert.AlertType.WARNING, "취소 오류", "작업 취소 실패");
            }
        }
    }

    private void updateFileList() {
        synchronized (fileItems) {
            fileItems.clear();
            if (currentDirectory != null && currentDirectory.exists()) {
                File[] files = currentDirectory.listFiles();
                if (files != null) {
                    for (File file : files) {
                        fileItems.add(new FileItem(file));
                    }
                    Platform.runLater(() -> itemCountLabel.setText("항목 수: " + files.length + "개"));
                } else {
                    showAlert(Alert.AlertType.ERROR, "목록 오류", "디렉토리 접근 권한 없음 또는 I/O 오류 발생");
                    Platform.runLater(() -> itemCountLabel.setText("항목 수: 0개"));
                }
            }
        }
    }

    private int parseChunkSize(String sizeStr) {
        try {
            String[] parts = sizeStr.split(" ");
            int size = Integer.parseInt(parts[0]);
            if (parts[1].equals("GB")) size *= 1024;
            return size * 1024 * 1024;
        } catch (Exception e) {
            throw new NumberFormatException("청크 크기 파싱 오류: " + e.getMessage());
        }
    }

    private void showAlert(Alert.AlertType type, String title, String content) {
        try {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setContentText(content);
            alert.showAndWait();
        } catch (Exception e) {
            System.err.println("알림 표시 실패: " + e.getMessage());
        }
    }

    private void addToZip(File file, ZipOutputStream zos, String parentPath) throws IOException {
        String zipEntryName = parentPath + file.getName();
        if (zipEntryName.length() > 65535) {
            throw new IllegalArgumentException("ZIP 엔트리 이름이 너무 김: " + zipEntryName);
        }
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
                    if (!newFile.mkdirs()) {
                        throw new IOException("부모 디렉토리 생성 실패: " + newFile.getPath());
                    }
                } else {
                    File parent = newFile.getParentFile();
                    if (!parent.exists() && !parent.mkdirs()) {
                        throw new IOException("부모 디렉토리 생성 실패: " + parent.getPath());
                    }
                    try (FileOutputStream fos = new FileOutputStream(newFile)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        } catch (ZipException e) {
            throw new IOException("ZIP 파일 손상: " + e.getMessage());
        }
        efs.secureDelete(zipFile.getPath());
    }

    private String calculateFileHash(File file) throws Exception {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new Exception("해시 알고리즘 오류: " + e.getMessage());
        }
        try (FileInputStream fis = new FileInputStream(file)) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        return Base64.getEncoder().encodeToString(digest.digest());
    }

    private void saveSettings() {
        Properties props = new Properties();
        props.setProperty("chunkSize", chunkSizeCombo.getValue());
        props.setProperty("lastDirectory", currentDirectory != null ? currentDirectory.getPath() : System.getProperty("user.home"));
        try (FileOutputStream fos = new FileOutputStream("settings.properties")) {
            props.store(fos, "PASSCODE Settings");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "설정 저장 실패", e.getMessage());
            return;
        }
    }

    private void loadSettings() {
        Properties props = new Properties();
        File settingsFile = new File("settings.properties");
        try (FileInputStream fis = new FileInputStream(settingsFile)) {
            props.load(fis);
            chunkSizeCombo.setValue(props.getProperty("chunkSize", "32 MB"));
            currentDirectory = new File(props.getProperty("lastDirectory", System.getProperty("user.home")));
            updateFileList();
        } catch (IOException e) {
            if (!settingsFile.exists()) {
                try (FileOutputStream fos = new FileOutputStream(settingsFile)) {
                    props.setProperty("chunkSize", "32 MB");
                    props.setProperty("lastDirectory", System.getProperty("user.home"));
                    props.store(fos, "PASSCODE Default Settings");
                } catch (IOException ex) {
                    showAlert(Alert.AlertType.ERROR, "기본 설정 생성 실패", ex.getMessage());
                }
            }
            currentDirectory = new File(System.getProperty("user.home"));
            updateFileList();
        }
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
            "1. '폴더 열기'를 통해 폴더를 선택하세요.\n" +
            "2. '새 키 생성' 또는 '키 로드'를 통해 암호화 키를 설정하세요.\n" +
            "3. '암호화' 버튼으로 파일/폴더를 암호화하거나, '복호화' 버튼으로 복원하세요.\n\n" +
            "사용된 라이브러리:\n" +
            "- JavaFX: UI 구현\n" +
            "- Ikonli: 아이콘 제공\n" +
            "- Java Cryptography Architecture (JCA): 암호화/복호화\n"
        );

        dialog.getDialogPane().setContent(infoText);
        dialog.getDialogPane().getButtonTypes().add(ButtonType.OK);
        try {
            dialog.showAndWait();
        } catch (Exception e) {
            showAlert(Alert.AlertType.ERROR, "정보 표시 오류", "정보 다이얼로그 표시 실패: " + e.getMessage());
        }
    }

    private String generateUniqueOutputPath(String basePath) {
        File file = new File(basePath);
        if (!file.exists()) return basePath;
        int counter = 1;
        String newPath;
        do {
            newPath = basePath + "-" + counter++;
            file = new File(newPath);
            if (counter > 100) { // 무한 루프 방지
                throw new RuntimeException("너무 많은 파일 이름 충돌");
            }
        } while (file.exists());
        return newPath;
    }
}
