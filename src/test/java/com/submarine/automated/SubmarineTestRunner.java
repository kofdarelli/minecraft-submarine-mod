package com.submarine.automated;

import java.awt.Robot;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

public final class SubmarineTestRunner {
    private static final String TEST_COMMAND = "/submarine test run";
    private static final String QUIT_COMMAND = "/submarine test quit-client";
    private static final Path RESULT_FILE = Path.of("test-result.txt");
    private static final Path LOG_FILE = Path.of("run", "logs", "latest.log");
    private static final Path OPTIONS_FILE = Path.of("run", "options.txt");
    private static final int LAUNCH_WAIT_MS = intSetting("SUBMARINE_TEST_LAUNCH_WAIT_MS", 15000);
    private static final int WORLD_LOAD_WAIT_MS = intSetting("SUBMARINE_TEST_WORLD_LOAD_WAIT_MS", 5000);
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
        Files.deleteIfExists(LOG_FILE);

        Process minecraft = launchMinecraft();
        Thread logThread = startLogWatcher();
        Robot robot = null;
        try {
            System.out.println("Waiting for Minecraft to launch...");
            Thread.sleep(LAUNCH_WAIT_MS);

            robot = new Robot();
            robot.setAutoDelay(20);

            if (!booleanSetting("SUBMARINE_TEST_SKIP_MENU", false)) {
                navigateToFirstSingleplayerWorld(robot);
                System.out.println("Waiting for world to load...");
                Thread.sleep(WORLD_LOAD_WAIT_MS);
            }

            sendCommand(robot, TEST_COMMAND);
            boolean passed = waitForTestResult();

            try {
                sendCommand(robot, QUIT_COMMAND);
            } catch (Exception ignored) {
            }

            writeResult(passed ? "PASS" : "FAIL");
            return passed ? 0 : 1;
        } finally {
            closeMinecraft(minecraft);
            logThread.interrupt();
        }
    }

    private Process launchMinecraft() throws IOException {
        String gradle = isWindows() ? "gradlew.bat" : "./gradlew";
        ProcessBuilder builder = new ProcessBuilder(gradle, "runClient");
        builder.directory(new File("."));
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

    private boolean waitForTestResult() throws InterruptedException {
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
            Thread.sleep(POLL_INTERVAL_MS);
        }
        System.err.println("Timed out waiting for submarine test results.");
        return false;
    }

    private void navigateToFirstSingleplayerWorld(Robot robot) throws InterruptedException {
        System.out.println("Opening the first singleplayer world...");
        press(robot, KeyEvent.VK_TAB);
        Thread.sleep(200);
        press(robot, KeyEvent.VK_ENTER);
        Thread.sleep(1500);
        press(robot, KeyEvent.VK_TAB);
        Thread.sleep(500);
        press(robot, KeyEvent.VK_ENTER);
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

    private static void writeResult(String value) {
        try {
            Files.writeString(RESULT_FILE, value, StandardCharsets.UTF_8);
        } catch (IOException exception) {
            System.err.println("Could not write test result: " + exception.getMessage());
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
        String value = System.getenv(name);
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
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : Boolean.parseBoolean(value);
    }

    private static final class TestStatus {
        private enum Value {
            PASS,
            FAIL,
            UNKNOWN
        }

        private Value value = Value.UNKNOWN;

        synchronized void update(String line) {
            if (line.contains("All submarine tests passed")) {
                value = Value.PASS;
            } else if (line.contains("Some submarine tests failed") || line.contains("Submarine integration test failed")) {
                value = Value.FAIL;
            }
        }

        synchronized Value get() {
            return value;
        }
    }
}
