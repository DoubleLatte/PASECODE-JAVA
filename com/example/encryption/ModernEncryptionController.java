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
    // FXML 파일에서 연결된 UI 요소들
    @FXML private TableView<FileItem> fileTable;      // 파일 목록을 표시하는 테이블
    @FXML private ComboBox<String> chunkSizeCombo;    // 청크 크기 선택 드롭다운
    @FXML private ProgressBar progressBar;            // 작업 진행률을 표시하는 프로그레스 바
    @FXML private Label progressLabel;                // 진행 상태 메시지 레이블
    @FXML private Label statusLabel;                  // 현재 상태를 표시하는 레이블
    @FXML private Button encryptButton;               // 암호화 버튼
    @FXML private Button decryptButton;               // 복호화 버튼
    
    private EncryptedFileSystem efs;                  // 암호화 시스템 객체
    private File currentDirectory;                    // 현재 선택된 디렉토리
    private ObservableList<FileItem> fileItems;       // 테이블에 표시될 파일 리스트
    
    // 컨트롤러 초기화 메서드
    @FXML
    public void initialize() {
        efs = new EncryptedFileSystem();              // 암호화 시스템 객체 생성
        fileItems = FXCollections.observableArrayList();  // 관찰 가능한 파일 리스트 초기화
        
        setupUI();           // UI 요소 초기화
        setupTableColumns(); // 테이블 열 설정
        setupChunkSizeCombo(); // 청크 크기 선택 설정
        setupDragAndDrop();   // 드래그 앤 드롭 기능 설정
    }
    
    // UI 요소를 초기화하는 메서드
    private void setupUI() {
        fileTable.setItems(fileItems);                // 테이블에 파일 리스트 연결
        encryptButton.setGraphic(new FontIcon("fas-lock"));   // 암호화 버튼에 자물쇠 아이콘 추가
        decryptButton.setGraphic(new FontIcon("fas-unlock")); // 복호화 버튼에 열린 자물쇠 아이콘 추가
        progressBar.setProgress(0);                   // 진행 바 초기값 0으로 설정
        progressLabel.setText("Ready");               // 진행 상태 레이블 초기값 설정
    }
    
    // 테이블 열을 설정하는 메서드
    private void setupTableColumns() {
        TableColumn<FileItem, String> nameCol = new TableColumn<>("Name"); // 파일명 열
        nameCol.setCellValueFactory(data -> data.getValue().nameProperty());
        
        TableColumn<FileItem, String> typeCol = new TableColumn<>("Type"); // 파일 유형 열
        typeCol.setCellValueFactory(data -> data.getValue().typeProperty());
        
        TableColumn<FileItem, String> sizeCol = new TableColumn<>("Size"); // 파일 크기 열
        sizeCol.setCellValueFactory(data -> data.getValue().sizeProperty());
        
        TableColumn<FileItem, String> statusCol = new TableColumn<>("Status"); // 상태 열
        statusCol.setCellValueFactory(data -> data.getValue().statusProperty());
        
        fileTable.getColumns().addAll(nameCol, typeCol, sizeCol, statusCol); // 열 추가
    }
    
    // 청크 크기 선택 드롭다운 설정
    private void setupChunkSizeCombo() {
        chunkSizeCombo.getItems().addAll(
            "1 MB", "16 MB", "32 MB", "64 MB",
            "128 MB", "256 MB", "512 MB", "1 GB"
        ); // 선택 가능한 청크 크기 목록 추가
        chunkSizeCombo.setValue("32 MB");             // 기본값 32MB로 설정
    }
    
    // 드래그 앤 드롭 기능 설정
    private void setupDragAndDrop() {
        fileTable.setOnDragOver(event -> {
            if (event.getDragboard().hasFiles()) {
                event.acceptTransferModes(TransferMode.COPY); // 파일 드롭 허용
            }
            event.consume();
        });
        
        fileTable.setOnDragDropped(event -> {
            List<File> files = event.getDragboard().getFiles(); // 드롭된 파일 목록 가져오기
            handleFileDrop(files);                     // 파일 처리
            event.consume();
        });
    }
    
    // 폴더 열기 버튼 클릭 시 호출
    @FXML
    private void onOpenFolder() {
        DirectoryChooser chooser = new DirectoryChooser(); // 디렉토리 선택기 생성
        chooser.setTitle("Select Folder");                 // 제목 설정
        File directory = chooser.showDialog(null);         // 폴더 선택 대화상자 표시
        
        if (directory != null) {
            currentDirectory = directory;                  // 선택된 디렉토리 저장
            updateFileList();                             // 파일 리스트 갱신
        }
    }
    
    // 새 키 생성 버튼 클릭 시 호출
    @FXML
    private void onCreateKey() {
        Dialog<String> dialog = new Dialog<>();           // 키 생성 다이얼로그 생성
        dialog.setTitle("Create New Key");                // 제목 설정
        dialog.setHeaderText("Enter password for new key"); // 헤더 텍스트 설정
        
        GridPane grid = new GridPane();                   // 입력 필드 배치를 위한 그리드
        grid.setHgap(10);
        grid.setVgap(10);
        
        PasswordField password = new PasswordField();     // 비밀번호 입력 필드
        PasswordField confirm = new PasswordField();      // 비밀번호 확인 필드
        
        grid.add(new Label("Password:"), 0, 0);           // 비밀번호 레이블
        grid.add(password, 1, 0);                         // 비밀번호 필드
        grid.add(new Label("Confirm:"), 0, 1);            // 확인 레이블
        grid.add(confirm, 1, 1);                          // 확인 필드
        
        dialog.getDialogPane().setContent(grid);          // 다이얼로그에 그리드 추가
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL); // 버튼 추가
        
        dialog.setResultConverter(dialogButton -> {       // 버튼 클릭 결과 처리
            if (dialogButton == ButtonType.OK) {
                if (password.getText().equals(confirm.getText())) {
                    return password.getText();            // 비밀번호 일치 시 반환
                } else {
                    showAlert(Alert.AlertType.ERROR, "Error", "Passwords do not match"); // 불일치 알림
                    return null;
                }
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(password -> {      // 다이얼로그 결과 처리
            try {
                FileChooser keyChooser = new FileChooser(); // 키 파일 저장 위치 선택기
                keyChooser.setTitle("Save Key File");      // 제목 설정
                File keyFile = keyChooser.showSaveDialog(null); // 저장 위치 선택
                
                if (keyFile != null) {
                    efs.generateKey(keyFile.getPath(), password); // 키 생성
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Key created successfully"); // 성공 알림
                    statusLabel.setText("Key loaded");     // 상태 업데이트
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error", e.getMessage()); // 오류 알림
            }
        });
    }
    
    // 키 로드 버튼 클릭 시 호출
    @FXML
    private void onLoadKey() {
        Dialog<String> dialog = new Dialog<>();           // 키 로드 다이얼로그 생성
        dialog.setTitle("Load Key");                      // 제목 설정
        dialog.setHeaderText("Enter password for key");   // 헤더 텍스트 설정
        
        GridPane grid = new GridPane();                   // 입력 필드 배치를 위한 그리드
        grid.setHgap(10);
        grid.setVgap(10);
        
        PasswordField password = new PasswordField();     // 비밀번호 입력 필드
        grid.add(new Label("Password:"), 0, 0);           // 비밀번호 레이블
        grid.add(password, 1, 0);                         // 비밀번호 필드
        
        dialog.getDialogPane().setContent(grid);          // 다이얼로그에 그리드 추가
        dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL); // 버튼 추가
        
        dialog.setResultConverter(dialogButton -> {       // 버튼 클릭 결과 처리
            if (dialogButton == ButtonType.OK) {
                return password.getText();                // 비밀번호 반환
            }
            return null;
        });
        
        dialog.showAndWait().ifPresent(password -> {      // 다이얼로그 결과 처리
            try {
                FileChooser chooser = new FileChooser();  // 키 파일 선택기
                chooser.setTitle("Select Key File");       // 제목 설정
                File keyFile = chooser.showOpenDialog(null); // 키 파일 선택
                
                if (keyFile != null) {
                    efs.loadKey(keyFile.getPath(), password); // 키 로드
                    showAlert(Alert.AlertType.INFORMATION, "Success", "Key loaded successfully"); // 성공 알림
                    statusLabel.setText("Key loaded");     // 상태 업데이트
                }
            } catch (Exception e) {
                showAlert(Alert.AlertType.ERROR, "Error", e.getMessage()); // 오류 알림
            }
        });
    }
    
    // 암호화 버튼 클릭 시 호출
    @FXML
    private void onEncrypt() {
        if (fileItems.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No files selected"); // 파일 없음 경고
            return;
        }
        
        Task<Void> task = new Task<>() {                  // 암호화 작업 태스크 생성
            @Override
            protected Void call() throws Exception {
                int total = fileItems.size();
                for (int i = 0; i < total; i++) {
                    FileItem item = fileItems.get(i);
                    File file = new File(currentDirectory, item.getName()); // 암호화할 파일
                    
                    updateProgress(i, total);             // 진행률 업데이트
                    updateMessage("Encrypting: " + item.getName()); // 진행 메시지 업데이트
                    
                    int chunkSize = parseChunkSize(chunkSizeCombo.getValue()); // 청크 크기 파싱
                    efs.encryptFile(file.getPath(), chunkSize); // 파일 암호화
                    
                    Platform.runLater(() -> {             // UI 스레드에서 상태 업데이트
                        item.setStatus("Encrypted");
                        fileTable.refresh();
                    });
                }
                return null;
            }
        };
        
        progressBar.progressProperty().bind(task.progressProperty()); // 진행 바 바인딩
        progressLabel.textProperty().bind(task.messageProperty());    // 진행 메시지 바인딩
        
        task.setOnSucceeded(e -> {                    // 태스크 성공 시
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            progressBar.setProgress(1);
            progressLabel.setText("Encryption complete"); // 완료 메시지
            updateFileList();                         // 파일 리스트 갱신
        });
        
        task.setOnFailed(e -> {                       // 태스크 실패 시
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            showAlert(Alert.AlertType.ERROR, "Error", task.getException().getMessage()); // 오류 알림
        });
        
        new Thread(task).start();                     // 태스크 실행
    }
    
    // 복호화 버튼 클릭 시 호출
    @FXML
    private void onDecrypt() {
        List<FileItem> encryptedFiles = fileItems.filtered(item -> 
            item.getName().endsWith(".lock"));        // 암호화된 파일 필터링
            
        if (encryptedFiles.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Warning", "No encrypted files selected"); // 파일 없음 경고
            return;
        }
        
        Task<Void> task = new Task<>() {              // 복호화 작업 태스크 생성
            @Override
            protected Void call() throws Exception {
                int total = encryptedFiles.size();
                for (int i = 0; i < total; i++) {
                    FileItem item = encryptedFiles.get(i);
                    File file = new File(currentDirectory, item.getName()); // 복호화할 파일
                    
                    updateProgress(i, total);             // 진행률 업데이트
                    updateMessage("Decrypting: " + item.getName()); // 진행 메시지 업데이트
                    
                    efs.decryptFile(file.getPath());      // 파일 복호화
                    
                    Platform.runLater(() -> {             // UI 스레드에서 상태 업데이트
                        item.setStatus("Decrypted");
                        fileTable.refresh();
                    });
                }
                return null;
            }
        };
        
        progressBar.progressProperty().bind(task.progressProperty()); // 진행 바 바인딩
        progressLabel.textProperty().bind(task.messageProperty());    // 진행 메시지 바인딩
        
        task.setOnSucceeded(e -> {                    // 태스크 성공 시
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            progressBar.setProgress(1);
            progressLabel.setText("Decryption complete"); // 완료 메시지
            updateFileList();                         // 파일 리스트 갱신
        });
        
        task.setOnFailed(e -> {                       // 태스크 실패 시
            progressBar.progressProperty().unbind();
            progressLabel.textProperty().unbind();
            showAlert(Alert.AlertType.ERROR, "Error", task.getException().getMessage()); // 오류 알림
        });
        
        new Thread(task).start();                     // 태스크 실행
    }
    
    // 파일 리스트를 갱신하는 메서드
    private void updateFileList() {
        fileItems.clear();                            // 기존 리스트 초기화
        if (currentDirectory != null && currentDirectory.exists()) {
            File[] files = currentDirectory.listFiles(); // 디렉토리의 파일 목록 가져오기
            if (files != null) {
                for (File file : files) {
                    fileItems.add(new FileItem(file));    // 파일 추가
                }
            }
        }
    }
    
    // 드롭된 파일을 처리하는 메서드
    private void handleFileDrop(List<File> files) {
        for (File file : files) {
            fileItems.add(new FileItem(file));        // 드롭된 파일 리스트에 추가
        }
    }
    
    // 청크 크기를 파싱하여 바이트 단위로 변환
    private int parseChunkSize(String sizeStr) {
        String[] parts = sizeStr.split(" ");          // 크기와 단위 분리
        int size = Integer.parseInt(parts[0]);        // 숫자 파싱
        if (parts[1].equals("GB")) {
            size *= 1024;                            // GB 단위면 MB로 변환
        }
        return size * 1024 * 1024;                    // 바이트 단위로 변환
    }
    
    // 알림창을 표시하는 메서드
    private void showAlert(Alert.AlertType type, String title, String content) {
        Alert alert = new Alert(type);                // 알림 객체 생성
        alert.setTitle(title);                        // 제목 설정
        alert.setContentText(content);                // 내용 설정
        alert.showAndWait();                          // 알림 표시 및 대기
    }
}
