// SPDX-License-Identifier: MIT AND GPL-3.0-or-later

package io.github.muntashirakon.AppManager.servermanager;

import android.content.Context;
import android.content.res.AssetFileDescriptor;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import org.jetbrains.annotations.NotNull;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import io.github.muntashirakon.AppManager.BuildConfig;
import io.github.muntashirakon.AppManager.server.common.ConfigParams;
import io.github.muntashirakon.AppManager.server.common.Constants;
import io.github.muntashirakon.io.IoUtils;

// Copyright 2016 Zheng Li
@SuppressWarnings("ResultOfMethodCallIgnored")
class AssetsUtils {
    @WorkerThread
    public static void copyFile(@NonNull Context context, String fileName, File destFile, boolean force)
            throws IOException {
        long assetLength;
        try (AssetFileDescriptor openFd = context.getAssets().openFd(fileName)) {
            assetLength = openFd.getLength();
        }
        if (force) {
            destFile.delete();
        } else if (destFile.exists()) {
            if (hasSameAssetContent(context, fileName, destFile, assetLength)) {
                return;
            }
            destFile.delete();
        }

        try (InputStream open = context.getAssets().open(fileName);
             FileOutputStream fos = new FileOutputStream(destFile)) {
            byte[] buff = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
            int len;
            while ((len = open.read(buff)) != -1) {
                fos.write(buff, 0, len);
            }
            fos.flush();
            fos.getFD().sync();
        }
    }

    private static boolean hasSameAssetContent(@NonNull Context context, @NonNull String fileName,
                                               @NonNull File destFile, long assetLength)
            throws IOException {
        if (assetLength >= 0 && destFile.length() != assetLength) {
            return false;
        }
        try (InputStream assetStream = context.getAssets().open(fileName);
             FileInputStream destStream = new FileInputStream(destFile)) {
            return MessageDigest.isEqual(getSha256(assetStream), getSha256(destStream));
        }
    }

    private static byte[] getSha256(@NonNull InputStream inputStream) throws IOException {
        try {
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
            int len;
            while ((len = inputStream.read(buffer)) != -1) {
                messageDigest.update(buffer, 0, len);
            }
            return messageDigest.digest();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }

    @WorkerThread
    @NonNull
    static byte[] readAsset(@NonNull Context context, @NonNull String fileName) throws IOException {
        try (InputStream in = context.getAssets().open(fileName);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buff = new byte[IoUtils.DEFAULT_BUFFER_SIZE];
            int len;
            while ((len = in.read(buff)) != -1) {
                out.write(buff, 0, len);
            }
            return out.toByteArray();
        }
    }

    @WorkerThread
    static void writeServerExecScript(@NonNull Context context, @NonNull File destFile, @NonNull String jarPath,
                                      @NonNull String execJarPath) throws IOException {
        String script = buildServerExecScript(context, jarPath, execJarPath);
        if (destFile.exists()) {
            destFile.delete();
        }
        try (FileOutputStream fos = new FileOutputStream(destFile, false)) {
            fos.write(script.getBytes(StandardCharsets.UTF_8));
            fos.flush();
        }
    }

    /**
     * The launcher script with its variables filled in. Lines end in {@code \n} whatever the host,
     * since the result also gets pushed to the device as is.
     *
     * @param jarPath     Where am.jar is when the launcher starts
     * @param execJarPath Where the server runs it from. The launcher copies am.jar there first
     *                    when the two differ.
     */
    @WorkerThread
    @NonNull
    static String buildServerExecScript(@NonNull Context context, @NonNull String jarPath,
                                        @NonNull String execJarPath) throws IOException {
        try (AssetFileDescriptor openFd = context.getAssets().openFd(ServerConfig.SERVER_RUNNER_EXEC_NAME);
             BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(openFd.createInputStream(),
                     StandardCharsets.UTF_8))) {
            // Set variables
            StringBuilder vars = new StringBuilder();
            vars.append("SERVER_NAME=").append(Constants.SERVER_NAME).append("\n")
                    .append("JAR_PATH=").append(jarPath).append("\n")
                    .append("EXEC_JAR_PATH=").append(execJarPath).append("\n")
                    .append("ARGS=").append(getServerArgs()).append("\n");
            StringBuilder script = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) {
                if ("%ENV_VARS%".equals(line.trim())) {
                    script.append(vars);
                } else {
                    script.append(line);
                }
                script.append("\n");
            }
            return script.toString();
        }
    }

    @NotNull
    private static String getServerArgs() {
        StringBuilder argsBuilder = new StringBuilder();
        argsBuilder.append(',').append(ConfigParams.PARAM_APP).append(':').append(BuildConfig.APPLICATION_ID);
        if (ServerConfig.getAllowBgRunning()) {
            argsBuilder.append(',').append(ConfigParams.PARAM_RUN_IN_BACKGROUND).append(':').append(1);
        }
        if (BuildConfig.DEBUG) {
            argsBuilder.append(',').append(ConfigParams.PARAM_DEBUG).append(':').append(1);
        }
        return argsBuilder.toString();
    }
}
