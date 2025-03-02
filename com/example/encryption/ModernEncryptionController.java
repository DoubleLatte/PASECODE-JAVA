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

    ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    List<Future<?>> futures = new ArrayList<>();

    currentTask = new Task<>() {
        @Override
        protected Void call() throws Exception {
            int total = selectedItems.size();
            if (total == 1 && !new File(currentDirectory, selectedItems.get(0).getName()).isDirectory()) {
                FileItem item = selectedItems.get(0);
                File file = new File(currentDirectory, item.getName());
                File backupFile = new File(file.getPath() + ".backup");
                Files.copy(file.toPath(), backupFile.toPath(), StandardCopyOption.REPLACE_EXISTING);

                File tempDecrypted = null;
                try {
                    tempDecrypted = new File(currentDirectory, "temp_" + item.getName());
                    Future<?> future = executor.submit(() -> {
                        try {
                            updateMessage("암호화 중: " + item.getName());
                            int chunkSize = parseChunkSize(chunkSizeCombo.getValue());
                            String encryptedPath = efs.encryptFile(file.getPath(), chunkSize);
                            String decryptedPath = efs.decryptFile(encryptedPath, tempDecrypted.getPath());
                            String originalHash = calculateFileHash(file);
                            String decryptedHash = calculateFileHash(tempDecrypted);

                            if (originalHash.equals(decryptedHash)) {
                                secureDelete(backupFile.getPath());
                                file.delete();
                                tempDecrypted.delete();
                                efs.deleteEncryptedFile(encryptedPath);
                                Platform.runLater(() -> item.setStatus("암호화됨"));
                            } else {
                                Files.copy(backupFile.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
                                secureDelete(backupFile.getPath());
                                new File(encryptedPath).delete();
                                tempDecrypted.delete();
                                throw new Exception("무결성 검증 실패: " + item.getName());
                            }
                        } catch (AccessDeniedException e) {
                            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "권한 오류",
                                item.getName() + "에 대한 접근 권한이 없습니다."));
                        } catch (Exception e) {
                            Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "오류", e.getMessage()));
                        }
                    });
                    futures.add(future);
                } finally {
                    if (tempDecrypted != null && tempDecrypted.exists()) {
                        tempDecrypted.delete();
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
                    Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "권한 오류",
                        "압축 중 접근 권한이 없는 파일이 있습니다."));
                    return;
                }

                Future<?> future = executor.submit(() -> {
                    try {
                        updateProgress(0.5, 1);
                        updateMessage("암호화 중: " + zipFile.getName());
                        int chunkSize = parseChunkSize(chunkSizeCombo.getValue());
                        String encryptedPath = efs.encryptFile(zipFile.getPath(), chunkSize);
                        String decryptedPath = efs.decryptFile(encryptedPath, tempDecryptedZip.getPath());
                        String originalHash = calculateFileHash(zipFile);
                        String decryptedHash = calculateFileHash(tempDecryptedZip);

                        if (originalHash.equals(decryptedHash)) {
                            secureDelete(backupZip.getPath());
                            zipFile.delete();
                            tempDecryptedZip.delete();
                            Platform.runLater(() -> {
                                synchronized (fileItems) {
                                    fileItems.clear();
                                    fileItems.add(new FileItem(new File(encryptedPath)));
                                    fileTable.refresh();
                                }
                            });
                        } else {
                            Files.copy(backupZip.toPath(), zipFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                            secureDelete(backupZip.getPath());
                            new File(encryptedPath).delete();
                            tempDecryptedZip.delete();
                            throw new Exception("압축 파일 무결성 검증 실패");
                        }
                    } catch (AccessDeniedException e) {
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "권한 오류",
                            zipFile.getName() + "에 대한 접근 권한이 없습니다."));
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "오류", e.getMessage()));
                    }
                });
                futures.add(future);
            }

            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);

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

    ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
    List<Future<?>> futures = new ArrayList<>();

    currentTask = new Task<>() {
        @Override
        protected Void call() throws Exception {
            int total = encryptedFiles.size();
            for (int i = 0; i < total; i++) {
                FileItem item = encryptedFiles.get(i);
                File file = new File(currentDirectory, item.getName());

                Future<?> future = executor.submit(() -> {
                    try {
                        updateProgress(i, total);
                        updateMessage("복호화 중: " + item.getName());
                        String decryptedPath = efs.decryptFile(file.getPath());
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
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "권한 오류",
                            item.getName() + "에 대한 접근 권한이 없습니다."));
                    } catch (Exception e) {
                        Platform.runLater(() -> showAlert(Alert.AlertType.ERROR, "오류", e.getMessage()));
                    }
                });
                futures.add(future);
            }

            for (Future<?> future : futures) {
                future.get();
            }
            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.HOURS);

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

private void handleFileDrop(List<File> files) {
    synchronized (fileItems) {
        for (File file : files) {
            fileItems.add(new FileItem(file));
        }
        updateFileList();
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
                Platform.runLater(() -> itemCountLabel.setText("항목 수: 0개"));
            }
        }
    }
}

private void initialize() {
    efs = new EncryptedFileSystem();
    fileItems = FXCollections.synchronizedObservableList(FXCollections.observableArrayList());

    setupUI();
    setupTableColumns();
    setupChunkSizeCombo();
    setupDragAndDrop();
    setupMemoryMonitoring();
    setupThemes();
    fileTable.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    loadSettings();
}
