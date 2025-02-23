package com.example.encryption;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.nio.ByteBuffer;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

public class EncryptedFileSystem {
    private SecretKeySpec key;
    private static final int SALT_LENGTH = 16; // 솔트 길이
    private static final int IV_LENGTH = 16;   // 초기화 벡터 길이
    private static final int KEY_LENGTH = 256; // AES-256 키 길이
    private static final int ITERATION_COUNT = 100000; // 반복 횟수
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding"; // 암호화 알고리즘

    public EncryptedFileSystem() {
        this.key = null; // 초기 키는 null
    }

    // 키 생성: 비밀번호로 AES-256 키를 생성하고 바이너리 형식으로 저장
    public void generateKey(String keyPath, String password) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);

        // PBKDF2로 키 생성
        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        this.key = new SecretKeySpec(keyBytes, "AES");

        // 키와 솔트를 바이너리 파일로 저장
        try (FileOutputStream fos = new FileOutputStream(keyPath);
             DataOutputStream dos = new DataOutputStream(fos)) {
            dos.writeInt(SALT_LENGTH); // 솔트 길이 기록
            dos.write(salt);           // 솔트 기록
            dos.writeInt(keyBytes.length); // 키 길이 기록
            dos.write(keyBytes);       // 키 기록
        }
    }

    // 키 로드: 바이너리 키 파일을 읽고 비밀번호로 검증
    public void loadKey(String keyPath, String password) throws Exception {
        try (FileInputStream fis = new FileInputStream(keyPath);
             DataInputStream dis = new DataInputStream(fis)) {
            int saltLength = dis.readInt();
            if (saltLength != SALT_LENGTH) {
                throw new Exception("잘못된 키 파일 형식");
            }
            byte[] salt = new byte[saltLength];
            dis.readFully(salt);

            int keyLength = dis.readInt();
            byte[] storedKey = new byte[keyLength];
            dis.readFully(storedKey);

            // 비밀번호로 키 재생성 및 검증
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
            byte[] generatedKey = factory.generateSecret(spec).getEncoded();

            if (!java.util.Arrays.equals(generatedKey, storedKey)) {
                throw new Exception("잘못된 비밀번호");
            }
            this.key = new SecretKeySpec(generatedKey, "AES");
        }
    }

    // 파일 암호화: AES-256으로 청크 단위 암호화
    public void encryptFile(String filePath, int chunkSize) throws Exception {
        if (this.key == null) {
            throw new Exception("키가 로드되지 않음");
        }

        String encryptedFilePath = filePath + ".lock";
        SecureRandom random = new SecureRandom();
        byte[] iv = new byte[IV_LENGTH];
        random.nextBytes(iv);

        Cipher cipher = Cipher.getInstance(ALGORITHM);
        cipher.init(Cipher.ENCRYPT_MODE, key, new IvParameterSpec(iv));

        try (FileInputStream fis = new FileInputStream(filePath);
             FileOutputStream fos = new FileOutputStream(encryptedFilePath);
             DataOutputStream dos = new DataOutputStream(fos)) {

            // 헤더: 청크 크기(4바이트) + IV(16바이트)
            dos.writeInt(chunkSize);
            dos.write(iv);

            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] encryptedChunk = cipher.update(buffer, 0, bytesRead);
                if (encryptedChunk != null) {
                    dos.writeInt(encryptedChunk.length); // 암호화된 청크 길이 기록
                    dos.write(encryptedChunk);
                }
            }
            byte[] finalChunk = cipher.doFinal();
            if (finalChunk != null) {
                dos.writeInt(finalChunk.length);
                dos.write(finalChunk);
            }
        }

        secureDelete(filePath);
    }

    // 파일 복호화: 청크 단위로 자동 감지 후 복호화
    public void decryptFile(String encryptedFilePath) throws Exception {
        if (this.key == null) {
            throw new Exception("키가 로드되지 않음");
        }

        String originalPath = encryptedFilePath.substring(0, encryptedFilePath.length() - 5);

        try (FileInputStream fis = new FileInputStream(encryptedFilePath);
             DataInputStream dis = new DataInputStream(fis);
             FileOutputStream fos = new FileOutputStream(originalPath)) {

            // 헤더 읽기
            int chunkSize = dis.readInt();
            byte[] iv = new byte[IV_LENGTH];
            dis.readFully(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, key, new IvParameterSpec(iv));

            while (dis.available() > 0) {
                int encryptedChunkLength = dis.readInt();
                byte[] encryptedChunk = new byte[encryptedChunkLength];
                dis.readFully(encryptedChunk);

                byte[] decryptedChunk = cipher.update(encryptedChunk);
                if (decryptedChunk != null) {
                    fos.write(decryptedChunk);
                }
            }
            byte[] finalChunk = cipher.doFinal();
            if (finalChunk != null) {
                fos.write(finalChunk);
            }
        }

        new File(encryptedFilePath).delete();
    }

    // 안전 삭제: 파일을 무작위 데이터로 덮어쓴 후 삭제
    private void secureDelete(String filePath) throws Exception {
        File file = new File(filePath);
        if (!file.exists()) {
            return;
        }

        long length = file.length();
        try (RandomAccessFile raf = new RandomAccessFile(file, "rw")) {
            byte[] randomData = new byte[1024];
            SecureRandom random = new SecureRandom();
            long written = 0;
            while (written < length) {
                random.nextBytes(randomData);
                int toWrite = (int) Math.min(1024, length - written);
                raf.write(randomData, 0, toWrite);
                written += toWrite;
            }
        }
        file.delete();
    }
}