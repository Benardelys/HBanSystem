package com.ardelys.hbansystem.manager.log;

import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public final class StaffLogger {

    private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final Logger consoleLogger;
    private final File logFile;
    private final ExecutorService logExecutor;

    public StaffLogger(@NotNull Logger consoleLogger, @NotNull File dataFolder) {
        this.consoleLogger = consoleLogger;
        this.logFile = new File(dataFolder, "hbansystem_admin.log");
        this.logExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "HBanSystem-AuditLogger");
            t.setDaemon(true);
            return t;
        });
    }

    public void logAction(@NotNull String staffName, @NotNull String action, @NotNull String details) {
        String logEntry = String.format("[%s] [YETKİLİ: %s] [İŞLEM: %s] %s",
                LocalDateTime.now().format(TIME_FORMAT),
                staffName,
                action,
                details
        );

        consoleLogger.info(logEntry);

        logExecutor.execute(() -> {
            try (FileWriter fw = new FileWriter(logFile, true);
                 PrintWriter pw = new PrintWriter(fw)) {
                pw.println(logEntry);
            } catch (IOException ignored) {}
        });
    }

    public void shutdown() {
        logExecutor.shutdown();
        try {
            if (!logExecutor.awaitTermination(2, TimeUnit.SECONDS)) {
                logExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            logExecutor.shutdownNow();
        }
    }
}
