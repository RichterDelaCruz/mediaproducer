package com.mediaproducer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ProducerApp {

    private static final Logger logger = LoggerFactory.getLogger(ProducerApp.class);
    private static final String DEFAULT_HOST = "localhost";
    private static final int DEFAULT_PORT = 9090; // Must match consumer

    public static void main(String[] args) {
        // --- Argument Parsing ---
        if (args.length < 3) {
            printUsage();
            return;
        }

        String consumerHost = args[0];
        int consumerPort;
        List<Path> producerFolders = new ArrayList<>();

        try {
            consumerPort = Integer.parseInt(args[1]);
            if (consumerPort <= 0 || consumerPort > 65535) {
                throw new NumberFormatException("Port must be between 1 and 65535");
            }
        } catch (NumberFormatException e) {
            logger.error("Invalid port number '{}': {}. Using default {}.", args[1], e.getMessage(), DEFAULT_PORT);
            consumerPort = DEFAULT_PORT; // Or exit if strict validation needed
            // printUsage(); System.exit(1); return;
        }

        // Remaining arguments are folder paths
        for (int i = 2; i < args.length; i++) {
            try {
                Path folderPath = Paths.get(args[i]).toAbsolutePath();
                if (!Files.isDirectory(folderPath)) {
                    logger.warn("Argument '{}' is not a valid directory. Skipping.", folderPath);
                    continue;
                }
                if (!Files.isReadable(folderPath)) {
                    logger.warn("Directory '{}' is not readable. Skipping.", folderPath);
                    continue;
                }
                producerFolders.add(folderPath);
                logger.info("Producer folder added: {}", folderPath);
            } catch (InvalidPathException e) {
                logger.warn("Invalid path string provided: '{}'. Skipping.", args[i]);
            }
        }

        if (producerFolders.isEmpty()) {
            logger.error("No valid producer folders specified. Exiting.");
            printUsage();
            return;
        }

        int p = producerFolders.size(); // Number of producers based on folders
        logger.info("Starting {} producer tasks.", p);
        logger.info("Target Consumer: {}:{}", consumerHost, consumerPort);

        // --- Thread Pool and Task Execution ---
        ExecutorService executor = Executors.newFixedThreadPool(p);
        for (int i = 0; i < p; i++) {
            Path folder = producerFolders.get(i);
            VideoProducerTask task = new VideoProducerTask(i + 1, folder, consumerHost, consumerPort);
            executor.execute(task);
        }

        // --- Shutdown ---
        executor.shutdown(); // Disable new tasks from being submitted
        try {
            logger.info("All producer tasks submitted. Waiting for completion (max 1 hour)...");
            // Wait a reasonable amount of time for existing tasks to terminate
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) { // Adjust timeout as needed
                logger.warn("Executor did not terminate within the timeout. Forcing shutdown...");
                executor.shutdownNow(); // Cancel currently executing tasks
                // Wait a little more for tasks to respond to being cancelled
                if (!executor.awaitTermination(60, TimeUnit.SECONDS))
                    logger.error("Executor did not terminate even after shutdownNow().");
            } else {
                logger.info("All producer tasks completed.");
            }
        } catch (InterruptedException ie) {
            logger.error("Main thread interrupted while waiting for producers. Forcing shutdown...");
            // (Re-)Cancel if current thread was interrupted
            executor.shutdownNow();
            // Preserve interrupt status
            Thread.currentThread().interrupt();
        }

        logger.info("Producer application finished.");
    }

    private static void printUsage() {
        System.err.println("\nUsage: java -jar mediaproducer-jar-with-dependencies.jar <host> <port> <folder1> [folder2] ...");
        System.err.println("  <host>      : Hostname or IP address of the Media Consumer service.");
        System.err.println("  <port>      : Port number the Media Consumer service is listening on (e.g., 9090).");
        System.err.println("  <folder1>   : Path to the first folder containing videos to upload.");
        System.err.println("  [folder2]...: Optional paths to additional folders (each starts a separate producer task).");
        System.err.println("\nExample: java -jar mediaproducer.jar localhost 9090 /path/to/videos1 /path/to/videos2");
        System.err.println("Example: java -jar mediaproducer.jar 192.168.1.100 9090 /media/folder_a");
    }
}