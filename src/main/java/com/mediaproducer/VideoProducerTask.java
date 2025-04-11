package com.mediaproducer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.Socket;
import java.net.UnknownHostException;
import java.nio.file.*;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Stream;

public class VideoProducerTask implements Runnable {

    private static final Logger logger = LoggerFactory.getLogger(VideoProducerTask.class);
    private static final int BUFFER_SIZE = 4096;
    // Define recognizable video file extensions (add more if needed)
    private static final Set<String> VIDEO_EXTENSIONS = new HashSet<>(Arrays.asList(
            ".mp4", ".mov", ".avi", ".mkv", ".wmv", ".flv"
    ));

    private final Path folderPath;
    private final String consumerHost;
    private final int consumerPort;
    private final String producerId; // Unique ID for logging

    public VideoProducerTask(int id, Path folderPath, String consumerHost, int consumerPort) {
        this.producerId = "Producer-" + id;
        this.folderPath = folderPath;
        this.consumerHost = consumerHost;
        this.consumerPort = consumerPort;
    }

    @Override
    public void run() {
        logger.info("[{}] Starting to watch folder: {}", producerId, folderPath);
        try {
            // Simple scan: find all existing video files and try to upload them once.
            // For continuous watching, you'd use Java's WatchService API.
            scanAndUploadFiles();

        } catch (Exception e) {
            logger.error("[{}] Unexpected error in producer task for folder {}: {}",
                    producerId, folderPath, e.getMessage(), e);
        }
        logger.info("[{}] Finished watching folder: {}", producerId, folderPath);
    }

    private void scanAndUploadFiles() {
        try (Stream<Path> stream = Files.list(folderPath)) {
            stream
                    .filter(Files::isRegularFile)
                    .filter(this::hasVideoExtension)
                    .forEach(this::attemptUpload);
        } catch (IOException e) {
            logger.error("[{}] Failed to list files in directory {}: {}",
                    producerId, folderPath, e.getMessage());
        }
    }

    private boolean hasVideoExtension(Path path) {
        String fileName = path.getFileName().toString().toLowerCase();
        for (String ext : VIDEO_EXTENSIONS) {
            if (fileName.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private void attemptUpload(Path filePath) {
        logger.info("[{}] Found video file: {}", producerId, filePath.getFileName());
        long fileSize = -1;
        String fileName = filePath.getFileName().toString();

        try {
            fileSize = Files.size(filePath);
            if (fileSize == 0) {
                logger.warn("[{}] Skipping empty file: {}", producerId, fileName);
                return;
            }
        } catch (IOException e) {
            logger.error("[{}] ERROR: Cannot get size for file {}: {}",
                    producerId, fileName, e.getMessage());
            return; // Cannot proceed without size
        }

        logger.debug("[{}] Attempting upload: {} ({} bytes) to {}:{}",
                producerId, fileName, fileSize, consumerHost, consumerPort);

        // Try-with-resources for automatic closing
        try (Socket socket = new Socket(consumerHost, consumerPort);
             OutputStream socketOut = socket.getOutputStream();
             DataOutputStream dos = new DataOutputStream(socketOut);
             InputStream socketIn = socket.getInputStream();
             DataInputStream dis = new DataInputStream(socketIn);
             InputStream fileIn = Files.newInputStream(filePath))
        {
            logger.debug("[{}] Connected to consumer for file {}", producerId, fileName);

            // 1. Send metadata
            dos.writeUTF(fileName);
            dos.writeLong(fileSize);
            dos.flush();
            logger.trace("[{}] Metadata sent for {}", producerId, fileName);

            // 2. Send file data
            byte[] buffer = new byte[BUFFER_SIZE];
            int bytesRead;
            long totalBytesSent = 0;
            long startTime = System.currentTimeMillis();
            logger.debug("[{}] Sending data for {}...", producerId, fileName);

            while ((bytesRead = fileIn.read(buffer)) != -1) {
                dos.write(buffer, 0, bytesRead);
                totalBytesSent += bytesRead;
            }
            dos.flush(); // Ensure all data is sent
            long endTime = System.currentTimeMillis();
            double durationSeconds = (endTime - startTime) / 1000.0;
            logger.info("[{}] Data sent for {} ({} bytes in {:.2f}s).",
                    producerId, fileName, totalBytesSent, durationSeconds);


            // 3. Receive response
            String response = dis.readUTF();
            logger.info("[{}] SERVER RESPONSE for {}: {}", producerId, fileName, response);

            // Handle specific responses (optional actions based on response)
            switch (response) {
                case "SUCCESS":
                    // Optionally move/delete the source file after successful upload
                    // logger.debug("[{}] Upload successful for {}, potentially moving/deleting source.", producerId, fileName);
                    // Files.move(filePath, processedPath); // Example move
                    break;
                case "QUEUE_FULL":
                    logger.warn("[{}] Consumer queue full for {}. Upload rejected. Consider retrying later.", producerId, fileName);
                    // Maybe add a delay before trying next file?
                    Thread.sleep(5000); // Simple 5-sec backoff
                    break;
                case "DUPLICATE_FILE":
                    logger.warn("[{}] Consumer reported duplicate for {}. Assuming already processed.", producerId, fileName);
                    // Optionally move/delete source if duplicate means success elsewhere
                    break;
                case "COMPRESSION_FAILED":
                case "TRANSFER_ERROR":
                case "INTERNAL_ERROR":
                    logger.error("[{}] Consumer reported error '{}' for {}. Upload failed.", producerId, response, fileName);
                    break;
                default:
                    logger.warn("[{}] Unknown consumer response '{}' for {}.", producerId, response, fileName);
                    break;
            }

        } catch (UnknownHostException e) {
            logger.error("[{}] ERROR: Unknown host '{}': {}", producerId, consumerHost, e.getMessage());
        } catch (IOException e) {
            logger.error("[{}] ERROR: Network/IO failure uploading {}: {}", producerId, fileName, e.getMessage());
        } catch (InterruptedException e) {
            logger.warn("[{}] Task interrupted during backoff sleep.", producerId);
            Thread.currentThread().interrupt(); // Preserve interrupt status
        } catch (Exception e) {
            logger.error("[{}] ERROR: Unexpected failure uploading {}: {}", producerId, fileName, e.getMessage(), e);
        }
        logger.debug("[{}] Finished attempt for file: {}", producerId, fileName);
    }
}