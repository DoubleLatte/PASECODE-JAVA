package com.example.encryption;

import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import java.io.File;

public class FileItem {
    private final StringProperty name = new SimpleStringProperty(); // 파일 이름
    private final StringProperty type = new SimpleStringProperty(); // 파일 유형
    private final StringProperty size = new SimpleStringProperty(); // 파일 크기
    private final StringProperty status = new SimpleStringProperty(); // 상태

    public FileItem(File file) {
        this.name.set(file.getName());
        this.type.set(getFileExtension(file));
        this.size.set(formatSize(file.length()));
        this.status.set("준비");
    }

    public StringProperty nameProperty() { return name; }
    public StringProperty typeProperty() { return type; }
    public StringProperty sizeProperty() { return size; }
    public StringProperty statusProperty() { return status; }

    public String getName() { return name.get(); }
    public void setStatus(String status) { this.status.set(status); }

    // 파일 확장자 추출
    private String getFileExtension(File file) {
        String name = file.getName();
        int lastDot = name.lastIndexOf('.');
        return (lastDot == -1) ? "" : name.substring(lastDot + 1);
    }

    // 파일 크기를 사람이 읽기 쉬운 형식으로 변환
    private String formatSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }
}