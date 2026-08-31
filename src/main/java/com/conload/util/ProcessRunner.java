package com.conload.util;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;

/** Runs a process and captures its completion status and output streams. */
public final class ProcessRunner {

    private ProcessRunner() {}

    /** The result of a completed process. */
    public record Result(int exitCode, String stdout, String stderr) {}

    /**
     * Runs {@code command} with the requested working directory and stderr
     * handling. A {@code null} working directory uses the current directory.
     */
    public static Result run(List<String> command, Path workingDirectory,
                             boolean mergeStderr)
            throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        if (workingDirectory != null) {
            processBuilder.directory(workingDirectory.toFile());
        }
        processBuilder.redirectErrorStream(mergeStderr);

        Process process = processBuilder.start();
        FutureTask<String> stdoutReader = new FutureTask<>(
                () -> read(process.getInputStream()));
        FutureTask<String> stderrReader = mergeStderr ? null : new FutureTask<>(
                () -> read(process.getErrorStream()));
        Thread.startVirtualThread(stdoutReader);
        if (stderrReader != null) Thread.startVirtualThread(stderrReader);
        int exitCode = process.waitFor();

        return new Result(exitCode, getOutput(stdoutReader),
                stderrReader == null ? "" : getOutput(stderrReader));
    }

    private static String read(java.io.InputStream stream) throws IOException {
        try (stream) {
            return new String(stream.readAllBytes(), Charset.defaultCharset());
        }
    }

    private static String getOutput(FutureTask<String> output)
            throws IOException, InterruptedException {
        try {
            return output.get();
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof IOException ioException) throw ioException;
            if (cause instanceof RuntimeException runtimeException) throw runtimeException;
            throw new IOException("Failed to read process output", cause);
        }
    }

}
