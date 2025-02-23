package com.example.encryption;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ModernEncryptionApp extends Application {
    private ModernEncryptionController controller;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // FXML 파일 로드
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/styles/modern.css").toExternalForm());

        primaryStage.setTitle("파일 암호화 시스템");
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    // 애플리케이션 종료 시 리소스 정리
    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static void main(String[] args) {
        launch(args); // JavaFX 애플리케이션 실행
    }
}