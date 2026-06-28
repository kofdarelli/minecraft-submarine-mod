package com.submarine.automated;

import java.awt.Rectangle;
import java.awt.Robot;
import java.awt.Toolkit;
import java.awt.event.KeyEvent;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import javax.imageio.ImageIO;

public final class SubmarineTestRunner {
    private static final String TEST_COMMAND = "/submarine test run";
    private static final String QUIT_COMMAND = "/submarine test quit-client";
    private static final String WORLD_NAME = stringSetting("SUBMARINE_TEST_WORLD_NAME", "SubmarineAutomationTest");
    private static final Path RESULT_FILE = Path.of("test-result.txt");
    private static final Path LOG_FILE = Path.of("run", "logs", "latest.log");
    private static final Path OPTIONS_FILE = Path.of("run", "options.txt");
    private static final Path SAVES_DIR = Path.of("run", "saves");
    private static final Path ARTIFACT_DIR = Path.of("build", "test-results", "submarine-smoke");
    private static final int LAUNCH_WAIT_MS = intSetting("SUBMARINE_TEST_LAUNCH_WAIT_MS", 15000);
    private static final int WORLD_READY_TIMEOUT_MS = intSetting("SUBMARINE_TEST_WORLD_READY_TIMEOUT_MS", 300000);
    private static final int TEST_TIMEOUT_MS = intSetting("SUBMARINE_TEST_TIMEOUT_MS", 60000);
    private static final int POLL_INTERVAL_MS = 250;

    private final TestStatus status = new TestStatus();

    public static void main(String[] args) throws Exception {
        int code = new SubmarineTestRunner().run();
        System.exit(code);
    }

    private int run() throws Exception {
        cleanupPreviousResult();
        ensureWindowedMode();
        if (booleanSetting("SUBMARINE_TEST_RESET_WORLD", true)) {
            resetAutomationWorld();
        }
        Files.deleteIfExists(LOG_FILE);

        Process minecraft = null;
        Thread logThread = startLogWatcher();
        Robot robot = null;
        boolean passed = false;
        Exception runnerFailure = null;
        try {
            minecraft = launchMinecraft();
            System.out.println("Waiting for Minecraft to launch...");
            Thread.sleep(LAUNCH_WAIT_MS);

            robot = new Robot();
            robot.setAutoDelay(20);

            System.out.println("Waiting for automation world to be ready...");
            if (!waitForAutomationWorld(minecraft)) {
                writeResult("FAIL");
                return 1;
            }

            sendCommand(robot, TEST_COMMAND);
            passed = waitForTestResult(minecraft);

            try {
                sendCommand(robot, QUIT_COMMAND);
            } catch (Exception ignored) {
            }

            writeResult(passed ? "PASS" : "FAIL");
            return passed ? 0 : 1;
        } catch (Exception exception) {
            runnerFailure = exception;
            writeResult("FAIL");
            throw exception;
        } finally {
            closeMinecraft(minecraft);
            logThread.interrupt();
            writeRunnerArtifacts(passed, runnerFailure, robot);
        }
    }

    private Process launchMinecraft() throws IOException {
        String gradle = isWindows() ? "gradlew.bat" : "./gradlew";
        ProcessBuilder builder = new ProcessBuilder(gradle, "runClient");
        builder.directory(new File("."));
        builder.environment().put("SUBMARINE_AUTOMATION_WORLD", WORLD_NAME);
        builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        return builder.start();
    }

    private Thread startLogWatcher() {
        Thread thread = new Thread(() -> {
            long lastKnownPosition = 0L;
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    if (Files.exists(LOG_FILE)) {
                        try (RandomAccessFile file = new RandomAccessFile(LOG_FILE.toFile(), "r")) {
                            if (file.length() < lastKnownPosition) {
                                lastKnownPosition = 0L;
                            }
                            if (file.length() > lastKnownPosition) {
                                file.seek(lastKnownPosition);
                                String line;
                                while ((line = file.readLine()) != null) {
                                    status.update(line);
                                }
                                lastKnownPosition = file.getFilePointer();
                            }
                        }
                    }
                    Thread.sleep(POLL_INTERVAL_MS);
                } catch (IOException ignored) {
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                }
            }
        }, "submarine-test-log-watcher");
        thread.setDaemon(true);
        thread.start();
        return thread;
    }

    private boolean waitForAutomationWorld(Process minecraft) throws InterruptedException {
        long deadline = System.currentTimeMillis() + WORLD_READY_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (status.isWorldReady()) {
                System.out.println("Automation world is ready: " + WORLD_NAME);
                return true;
            }
            if (minecraft != null && !minecraft.isAlive()) {
                System.err.println("Minecraft process exited before automation world was ready with code " + minecraft.exitValue() + ".");
                return false;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        System.err.println("Timed out waiting for automation world: " + WORLD_NAME);
        return false;
    }

    private boolean waitForTestResult(Process minecraft) throws InterruptedException {
        long deadline = System.currentTimeMillis() + TEST_TIMEOUT_MS;
        while (System.currentTimeMillis() < deadline) {
            TestStatus.Value value = status.get();
            if (value == TestStatus.Value.PASS) {
                System.out.println("Submarine integration tests passed.");
                return true;
            }
            if (value == TestStatus.Value.FAIL) {
                System.err.println("Submarine integration tests failed.");
                return false;
            }
            if (minecraft != null && !minecraft.isAlive()) {
                System.err.println("Minecraft process exited before test completion with code " + minecraft.exitValue() + ".");
                return false;
            }
            Thread.sleep(POLL_INTERVAL_MS);
        }
        System.err.println("Timed out waiting for submarine test results.");
        return false;
    }

    private void sendCommand(Robot robot, String command) throws InterruptedException {
        Thread.sleep(500);
        press(robot, KeyEvent.VK_T);
        Thread.sleep(250);
        type(robot, command);
        Thread.sleep(250);
        press(robot, KeyEvent.VK_ENTER);
    }

    private static void ensureWindowedMode() {
        try {
            Files.createDirectories(OPTIONS_FILE.getParent());
            String existing = Files.exists(OPTIONS_FILE)
                    ? Files.readString(OPTIONS_FILE, StandardCharsets.UTF_8)
                    : "";
            StringBuilder updated = new StringBuilder();
            boolean foundFullscreen = false;
            for (String line : existing.split("\\R")) {
                if (line.startsWith("fullscreen:")) {
                    updated.append("fullscreen:false").append(System.lineSeparator());
                    foundFullscreen = true;
                } else if (!line.isBlank()) {
                    updated.append(line).append(System.lineSeparator());
                }
            }
            if (!foundFullscreen) {
                updated.append("fullscreen:false").append(System.lineSeparator());
            }
            Files.writeString(OPTIONS_FILE, updated.toString(), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("Could not force windowed mode: " + exception.getMessage());
        }
    }

    private static void cleanupPreviousResult() throws IOException {
        Files.deleteIfExists(RESULT_FILE);
    }

    private static void resetAutomationWorld() throws IOException {
        Path savesRoot = SAVES_DIR.toAbsolutePath().normalize();
        Path worldPath = savesRoot.resolve(WORLD_NAME).normalize();
        if (!worldPath.startsWith(savesRoot) || WORLD_NAME.isBlank()) {
            throw new IOException("Refusing to delete unsafe automation world path: " + worldPath);
        }
        if (!Files.exists(worldPath)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(worldPath)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
        System.out.println("Reset automation world: " + worldPath);
    }

    private static void writeResult(String value) {
        try {
            Files.writeString(RESULT_FILE, value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("Could not write test result: " + exception.getMessage());
        }
    }

    private static void writeRunnerArtifacts(boolean passed, Exception failure, Robot robot) {
        try {
            Files.createDirectories(ARTIFACT_DIR);
            writeRunnerSummary(passed, failure);
            copyLogArtifact();
            writeLogTail();
            if (!passed) {
                captureFailureScreenshot(robot);
            }
        } catch (IOException exception) {
            System.err.println("Could not write runner artifacts: " + exception.getMessage());
        }
    }

    private static void writeRunnerSummary(boolean passed, Exception failure) throws IOException {
        StringBuilder summary = new StringBuilder();
        summary.append("status=").append(passed ? "PASS" : "FAIL").append(System.lineSeparator());
        summary.append("timestamp=").append(Instant.now()).append(System.lineSeparator());
        summary.append("command=").append(TEST_COMMAND).append(System.lineSeparator());
        summary.append("world=").append(WORLD_NAME).append(System.lineSeparator());
        summary.append("worldReset=").append(booleanSetting("SUBMARINE_TEST_RESET_WORLD", true)).append(System.lineSeparator());
        summary.append("resultFile=").append(RESULT_FILE.toAbsolutePath()).append(System.lineSeparator());
        if (failure != null) {
            summary.append("runnerError=").append(failure.getClass().getName()).append(": ")
                    .append(failure.getMessage()).append(System.lineSeparator());
        }
        Files.writeString(ARTIFACT_DIR.resolve("runner-summary.txt"), summary.toString(), StandardCharsets.UTF_8);
    }

    private static void copyLogArtifact() throws IOException {
        if (Files.exists(LOG_FILE)) {
            Files.copy(LOG_FILE, ARTIFACT_DIR.resolve("latest.log"), StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void writeLogTail() throws IOException {
        if (!Files.exists(LOG_FILE)) {
            Files.writeString(ARTIFACT_DIR.resolve("latest-tail.log"), "No Minecraft log file was found.", StandardCharsets.UTF_8);
            return;
        }

        List<String> lines = Files.readAllLines(LOG_FILE, StandardCharsets.UTF_8);
        int fromIndex = Math.max(0, lines.size() - 200);
        Files.write(ARTIFACT_DIR.resolve("latest-tail.log"), lines.subList(fromIndex, lines.size()), StandardCharsets.UTF_8);
    }

    private static void captureFailureScreenshot(Robot robot) {
        if (robot == null) {
            return;
        }
        try {
            Rectangle bounds = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            BufferedImage image = robot.createScreenCapture(bounds);
            ImageIO.write(image, "png", ARTIFACT_DIR.resolve("failure-screen.png").toFile());
        } catch (Exception exception) {
            System.err.println("Could not capture failure screenshot: " + exception.getMessage());
        }
    }

    private static void closeMinecraft(Process process) {
        if (process == null || !process.isAlive()) {
            stopGradleDaemon();
            return;
        }
        process.destroy();
        try {
            if (!process.waitFor(8, TimeUnit.SECONDS)) {
                process.destroyForcibly();
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        } finally {
            stopGradleDaemon();
        }
    }

    private static void stopGradleDaemon() {
        try {
            String gradle = isWindows() ? "gradlew.bat" : "./gradlew";
            new ProcessBuilder(gradle, "--stop")
                    .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                    .redirectError(ProcessBuilder.Redirect.DISCARD)
                    .start();
        } catch (IOException ignored) {
        }
    }

    private static void press(Robot robot, int keyCode) {
        robot.keyPress(keyCode);
        robot.keyRelease(keyCode);
    }

    private static void type(Robot robot, String text) {
        for (char c : text.toCharArray()) {
            typeChar(robot, c);
        }
    }

    private static void typeChar(Robot robot, char c) {
        switch (c) {
            case '/':
                press(robot, KeyEvent.VK_SLASH);
                return;
            case ' ':
                press(robot, KeyEvent.VK_SPACE);
                return;
            case '-':
                press(robot, KeyEvent.VK_MINUS);
                return;
            default:
                int keyCode = KeyEvent.getExtendedKeyCodeForChar(c);
                if (keyCode == KeyEvent.VK_UNDEFINED) {
                    return;
                }
                if (Character.isUpperCase(c)) {
                    robot.keyPress(KeyEvent.VK_SHIFT);
                }
                robot.keyPress(keyCode);
                robot.keyRelease(keyCode);
                if (Character.isUpperCase(c)) {
                    robot.keyRelease(KeyEvent.VK_SHIFT);
                }
        }
    }

    private static boolean isWindows() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    private static int intSetting(String name, int fallback) {
        String value = stringSetting(name, "");
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static boolean booleanSetting(String name, boolean fallback) {
        String value = stringSetting(name, "");
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static String stringSetting(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static final class TestStatus {
        private enum Value {
            PASS,
            FAIL,
            UNKNOWN
        }

        private Value value = Value.UNKNOWN;
        private boolean worldReady;

        synchronized void update(String line) {
            if (line.contains("All submarine tests passed")) {
                value = Value.PASS;
            } else if (line.contains("Some submarine tests failed") || line.contains("Submarine integration test failed")) {
                value = Value.FAIL;
            }
            if (line.contains("Submarine automation world ready:")) {
                worldReady = true;
            }
        }

        synchronized Value get() {
            return value;
        }

        synchronized boolean isWorldReady() {
            return worldReady;
        }
    }
}
