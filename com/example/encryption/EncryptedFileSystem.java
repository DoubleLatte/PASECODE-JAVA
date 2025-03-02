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

    return outputPath;
}

public boolean deleteEncryptedFile(String encryptedFilePath) {
    File file = new File(encryptedFilePath);
    return file.exists() && file.delete();
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
         FileChannel channel = fos.getChannel();
         FileLock lock = channel.tryLock()) {
        if (lock == null) {
            throw new Exception("파일이 다른 프로세스에서 사용 중입니다");
        }

        DataOutputStream dos = new DataOutputStream(fos);
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

    return encryptedFilePath;
}
