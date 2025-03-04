package com.ddlatte.encryption;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class ModernEncryptionApp extends Application {
    private static final String VERSION = "1.0.0-alpha";
    private ModernEncryptionController controller;

    // 초기 창 크기 상수 추가
    private static final double INITIAL_WIDTH = 500;
    private static final double INITIAL_HEIGHT = 500;
    private static final double MIN_WIDTH = 500;
    private static final double MIN_HEIGHT = 400;

    @Override
    public void start(Stage primaryStage) throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("/views/MainView.fxml"));
        Parent root = loader.load();
        controller = loader.getController();

        Scene scene = new Scene(root, INITIAL_WIDTH, INITIAL_HEIGHT);
        scene.getStylesheets().add(getClass().getResource("/styles/modern.css").toExternalForm());

        primaryStage.setTitle("PASSCODE v" + VERSION);
        primaryStage.getIcons().add(new javafx.scene.image.Image(getClass().getResourceAsStream("/icons/favicon.png")));

        // 최소 창 크기 설정
        primaryStage.setMinWidth(MIN_WIDTH);
        primaryStage.setMinHeight(MIN_HEIGHT);

        // 초기 창 크기 설정
        primaryStage.setWidth(INITIAL_WIDTH);
        primaryStage.setHeight(INITIAL_HEIGHT);

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