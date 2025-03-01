package com.example.encryption;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ModernEncryptionApp extends Application {
    private static final String VERSION = "1.0.0"; // 버전 정보 추가
    private ModernEncryptionController controller;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        Scene scene = new Scene(root);
        scene.getStylesheets().add(getClass().getResource("/styles/modern.css").toExternalForm());

        primaryStage.setTitle("PASSCODE v" + VERSION); // 타이틀에 버전 추가
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    @Override
    public void stop() {
        if (controller != null) {
            controller.shutdown();
        }
    }

    public static String getVersion() {
        return VERSION;
    }

    public static void main(String[] args) {
        launch(args);
    }
}
