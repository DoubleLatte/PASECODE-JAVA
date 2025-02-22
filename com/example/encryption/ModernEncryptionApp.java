package com.example.encryption;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ModernEncryptionApp extends Application {
    @Override
    public void start(Stage primaryStage) throws Exception {
        // FXML 파일을 로드하여 UI의 기본 구조를 설정
        Parent root = FXMLLoader.load(getClass().getResource("/views/MainView.fxml"));
        
        // Scene 객체를 생성하여 UI의 컨테이너 역할 수행
        Scene scene = new Scene(root);
        // CSS 스타일시트를 추가하여 UI에 모던한 디자인 적용
        scene.getStylesheets().add(getClass().getResource("/styles/modern.css").toExternalForm());
        
        // 윈도우(Stage)의 제목을 "파일 암호화 시스템"으로 설정
        primaryStage.setTitle("File Encryption System");
        // 생성된 Scene을 Stage에 설정
        primaryStage.setScene(scene);
        // 애플리케이션 창을 화면에 표시
        primaryStage.show();
    }

    // 프로그램의 진입점 역할을 하는 메인 메서드
    public static void main(String[] args) {
        // JavaFX 애플리케이션을 실행
        launch(args);
    }
}
