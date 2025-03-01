package com.example.encryption;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.*;
import java.security.SecureRandom;
import java.security.spec.KeySpec;

public class EncryptedFileSystem {
    private SecretKeySpec key;
    private static final int SALT_LENGTH = 16;
    private static final int IV_LENGTH = 16;
    private static final int KEY_LENGTH = 256;
    private static final int ITERATION_COUNT = 100000;
    private static final String ALGORITHM = "AES/CBC/PKCS5Padding";

    public EncryptedFileSystem() {
        this.key = null;
    }

    public void generateKey(String keyPath, String password) throws Exception {
        SecureRandom random = new SecureRandom();
        byte[] salt = new byte[SALT_LENGTH];
        random.nextBytes(salt);

        SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
        KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
        byte[] keyBytes = factory.generateSecret(spec).getEncoded();
        this.key = new SecretKeySpec(keyBytes, "AES");

        try (FileOutputStream fos = new FileOutputStream(keyPath);
             DataOutputStream dos = new DataOutputStream(fos)) {
            dos.writeInt(SALT_LENGTH);
            dos.write(salt);
            dos.writeInt(keyBytes.length);
            dos.write(keyBytes);
        }
    }

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

            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            KeySpec spec = new PBEKeySpec(password.toCharArray(), salt, ITERATION_COUNT, KEY_LENGTH);
            byte[] generatedKey = factory.generateSecret(spec).getEncoded();

            if (!java.util.Arrays.equals(generatedKey, storedKey)) {
                throw new Exception("잘못된 비밀번호");
            }
            this.key = new SecretKeySpec(generatedKey, "AES");
        }
    }

    public String encryptFile(String filePath, int chunkSize) throws Exception {
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

            dos.writeInt(chunkSize);
            dos.write(iv);

            byte[] buffer = new byte[chunkSize];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                byte[] encryptedChunk = cipher.update(buffer, 0, bytesRead);
                if (encryptedChunk != null) {
                    dos.writeInt(encryptedChunk.length);
                    dos.write(encryptedChunk);
                }
            }
            byte[] finalChunk = cipher.doFinal();
            if (finalChunk != null) {
                dos.writeInt(finalChunk.length);
                dos.write(finalChunk);
            }
        }

        return encryptedFilePath; // 암호화된 파일 경로 반환
    }

    public String decryptFile(String encryptedFilePath) throws Exception {
        return decryptFile(encryptedFilePath, encryptedFilePath.substring(0, encryptedFilePath.length() - 5));
    }

    public String decryptFile(String encryptedFilePath, String outputPath) throws Exception {
        if (this.key == null) {
            throw new Exception("키가 로드되지 않음");
        }

        try (FileInputStream fis = new FileInputStream(encryptedFilePath);
             DataInputStream dis = new DataInputStream(fis);
             FileOutputStream fos = new FileOutputStream(outputPath)) {

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
        return outputPath; // 복호화된 파일 경로 반환
    }

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
