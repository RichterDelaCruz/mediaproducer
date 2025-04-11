# MediaProducer Application

## Overview

MediaProducer is a command-line Java application designed to act as the "Producer" in a producer-consumer system. Its primary role is to monitor specified folders for video files, connect to a running MediaConsumer service over the network, and upload those video files according to a defined protocol.

This application simulates clients uploading media to a central service. It's designed to work in conjunction with the separate **MediaConsumer** application.

## Features

*   **Folder Monitoring:** Monitors one or more specified folders for video files.
*   **Concurrent Producers:** Launches a dedicated thread for each specified folder, allowing concurrent scanning and uploading from multiple sources (`p` producers = `p` folders).
*   **Video File Detection:** Identifies potential video files based on common file extensions (e.g., `.mp4`, `.mov`, `.avi`, etc.).
*   **Network Upload:** Connects to a configurable MediaConsumer host and port using TCP sockets.
*   **Upload Protocol:** Sends video metadata (filename, size) followed by the raw file content.
*   **Consumer Feedback:** Receives and logs status responses from the MediaConsumer (`SUCCESS`, `QUEUE_FULL`, `DUPLICATE_FILE`, `COMPRESSION_FAILED`, etc.).
*   **Configurable Target:** Consumer host and port are specified via command-line arguments.
*   **Logging:** Uses SLF4J and Logback for detailed logging of producer activities and statuses.

*(Note: The current implementation performs a one-time scan of the specified folders upon startup. For continuous monitoring, the `VideoProducerTask` would need to be enhanced using Java's `WatchService` API.)*

## Prerequisites

1.  **Java Development Kit (JDK):** Version 17 or 21 (as specified in `pom.xml`). Make sure `JAVA_HOME` is set correctly.
2.  **Maven:** Apache Maven (version 3.6.0 or later recommended) for building the project.
3.  **MediaConsumer Application:** The **MediaConsumer** application **must be running** and accessible over the network from the machine where the MediaProducer will run. You need to know the hostname/IP address and port number the Consumer is listening on.

*(Note: FFmpeg is **NOT** required for the Producer application itself.)*

## Build Instructions

1.  **Clone/Download:** Get the source code for the `mediaproducer` project.
2.  **Open Terminal:** Navigate to the root directory of the `mediaproducer` project (where the `pom.xml` file is located).
3.  **Build with Maven:** Run the following command:
    ```bash
    mvn clean package
    ```
4.  **Output:** This command will compile the source code and use the configured build plugin (e.g., `maven-shade-plugin` or `maven-assembly-plugin`) to create a runnable "fat" JAR file in the `target/` directory.
    *   If using the **Shade plugin** (as configured in recent examples), the runnable JAR will likely be named `mediaproducer-1.0-SNAPSHOT.jar`.
    *   If using the **Assembly plugin** (as configured previously), the runnable JAR will be named `mediaproducer-1.0-SNAPSHOT-jar-with-dependencies.jar`.
    *   Check the build output or the `target/` directory to confirm the exact name of the created runnable JAR.

## Running the Application

1.  **Prepare Source Folders:** Create the directories on your system that the producer(s) will monitor. Place the video files you want to upload into these folders.
    ```bash
    # Example folder structure
    mkdir -p /path/to/my/videos/set1
    mkdir -p /path/to/my/videos/set2
    cp movie1.mp4 /path/to/my/videos/set1/
    cp clip_a.mov /path/to/my/videos/set1/
    cp recording_xyz.mkv /path/to/my/videos/set2/
    ```
2.  **Navigate:** Open a terminal in the `mediaproducer` project's root directory (or anywhere, if using the full path to the JAR).
3.  **Run Command:** Execute the `java -jar` command, providing the path to the runnable JAR, the consumer's host and port, and the full paths to the source video folders.

    ```bash
    java -jar <path_to_runnable_jar> <consumer_host> <consumer_port> <folder1_path> [folder2_path] ...
    ```

    **Replace placeholders:**
    *   `<path_to_runnable_jar>`: The actual path to the JAR file created in the build step (e.g., `target/mediaproducer-1.0-SNAPSHOT.jar`).
    *   `<consumer_host>`: The hostname or IP address where the MediaConsumer application is running (e.g., `localhost`, `192.168.1.100`).
    *   `<consumer_port>`: The port number the MediaConsumer is listening on (e.g., `9090`).
    *   `<folder1_path>`: The full path to the first folder containing videos.
    *   `[folder2_path] ...`: Optional: Full paths to any additional folders. Each folder path will start a separate producer thread.

**Examples:**

*   **Running locally, monitoring 2 folders:**
    ```bash
    java -jar target/mediaproducer-1.0-SNAPSHOT.jar localhost 9090 /Users/richter/producer_vids/folder_A /Users/richter/producer_vids/folder_B
    ```
    *(This starts 2 producer threads, one for `folder_A`, one for `folder_B`)*

*   **Running against a remote consumer, monitoring 1 folder:**
    ```bash
    java -jar target/mediaproducer-1.0-SNAPSHOT.jar 192.168.1.55 9090 /mnt/shared_videos/exports
    ```
    *(This starts 1 producer thread)*

*   **Running locally, monitoring 3 folders:**
    ```bash
    java -jar target/mediaproducer-1.0-SNAPSHOT.jar localhost 9090 /Users/richter/Videos/Set1 /Users/richter/Videos/Set2 /Users/richter/Videos/Set3
    ```
    *(This starts 3 producer threads)*

## Configuration (`p` Producers)

The number of concurrent producer instances/threads (`p`) is determined **implicitly** by the number of valid `<folder_path>` arguments you provide when running the application. Each folder path starts one `VideoProducerTask` running in its own thread.

## Consumer Interaction Protocol

The producer uses the following steps to communicate with the consumer for each file:

1.  Connects to `<consumer_host>:<consumer_port>`.
2.  Sends filename (UTF-8 String).
3.  Sends file size (long).
4.  Streams the raw byte content of the file.
5.  Waits for and reads a status response (UTF-8 String).
6.  Closes the connection.

**Expected Statuses Logged:** `SUCCESS`, `QUEUE_FULL`, `DUPLICATE_FILE`, `COMPRESSION_FAILED`, `TRANSFER_ERROR`, `INTERNAL_ERROR`.

## Logging

*   The application uses **SLF4J** and **Logback**.
*   Configuration is controlled by `src/main/resources/logback.xml` (if present).
*   Logs include timestamps, thread names (e.g., `[Producer-1]`, `[Producer-2]`), log level, class name, and the message.
*   Check the console output for information about which files are found, connection attempts, upload progress (data sent), and the final response received from the consumer for each file.
*   Default logging level shows `INFO` and higher, with `DEBUG` enabled for the `com.mediaproducer` package for more details.

## Troubleshooting

*   **`Error: Unable to access jarfile ...`**: Make sure you ran `mvn clean package` successfully and are providing the correct path to the generated runnable JAR (the one with dependencies or the shaded JAR). Ensure you are in the correct directory or using the full path.
*   **`Error: no main manifest attribute ...`**: You are likely running the "thin" JAR instead of the runnable JAR created by the Shade or Assembly plugin. Use the JAR named `...-jar-with-dependencies.jar` (Assembly) or the primary JAR name `...SNAPSHOT.jar` (Shade).
*   **`SLF4J: Failed to load class "org.slf4j.impl.StaticLoggerBinder"`**: The `logback-classic` dependency was likely not packaged correctly into the runnable JAR. Ensure it's a compile-scope dependency in `pom.xml` and rebuild using `mvn clean package` with the Shade or Assembly plugin configured correctly.
*   **`UnknownHostException`**: The `<consumer_host>` provided is incorrect or cannot be resolved by your network/DNS.
*   **`Connection refused`**: The MediaConsumer application is likely not running, not listening on the specified `<consumer_port>`, or a firewall is blocking the connection between the producer and consumer machines.
*   **`IOException` / `Transfer failed`**: Network issues, the consumer closed the connection unexpectedly, or disk errors occurred while reading the source file on the producer side. Check consumer logs for corresponding errors.
*   **Consumer Responses (`QUEUE_FULL`, `DUPLICATE_FILE`, etc.):** These indicate specific conditions on the consumer side. Check consumer logs for more details about why the upload was rejected.
