package org.telegram.messenger;

import android.content.Context;
import android.net.Uri;
import android.text.TextUtils;

import androidx.documentfile.provider.DocumentFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.OutputStream;

public class CustomDownloadPathHelper {

    public static boolean hasTreeUri() {
        return !TextUtils.isEmpty(SharedConfig.getCustomDownloadTreeUri());
    }

    public static boolean canWriteTreeUri(Context context, String treeUri) {
        DocumentFile root = getTreeRoot(context, treeUri);
        return root != null && root.exists() && root.canWrite();
    }

    public static void mirrorDownloadedFile(File source, int mediaType) {
        if (source == null || !source.exists() || source.isDirectory() || !hasTreeUri() || mediaType == FileLoader.MEDIA_DIR_CACHE) {
            return;
        }
        if (isInsideCustomFilePath(source)) {
            return;
        }
        Utilities.globalQueue.postRunnable(() -> copyToTree(source, mediaType));
    }

    private static boolean isInsideCustomFilePath(File source) {
        String customPath = SharedConfig.getCustomDownloadPath();
        if (TextUtils.isEmpty(customPath)) {
            return false;
        }
        try {
            String sourcePath = source.getAbsolutePath();
            String basePath = new File(customPath).getAbsolutePath();
            return sourcePath.equals(basePath) || sourcePath.startsWith(basePath + File.separator);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return false;
    }

    private static void copyToTree(File source, int mediaType) {
        Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        DocumentFile root = getTreeRoot(context, SharedConfig.getCustomDownloadTreeUri());
        if (root == null || !root.canWrite()) {
            return;
        }
        DocumentFile folder = getOrCreateDirectory(root, getMediaDirectoryName(mediaType));
        if (folder == null || !folder.canWrite()) {
            return;
        }

        String fileName = source.getName();
        DocumentFile oldFile = folder.findFile(fileName);
        if (oldFile != null) {
            try {
                oldFile.delete();
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        String mimeType = AndroidUtilities.getMimeTypeForFile(source, fileName, "application/octet-stream");
        DocumentFile target = folder.createFile(mimeType, fileName);
        if (target == null) {
            return;
        }

        try (FileInputStream inputStream = new FileInputStream(source);
             OutputStream outputStream = context.getContentResolver().openOutputStream(target.getUri(), "w")) {
            if (outputStream == null) {
                return;
            }
            byte[] buffer = new byte[1024 * 64];
            int read;
            while ((read = inputStream.read(buffer)) >= 0) {
                outputStream.write(buffer, 0, read);
            }
        } catch (Exception e) {
            FileLog.e(e);
            try {
                target.delete();
            } catch (Exception deleteError) {
                FileLog.e(deleteError);
            }
        }
    }

    private static DocumentFile getTreeRoot(Context context, String treeUri) {
        if (context == null || TextUtils.isEmpty(treeUri)) {
            return null;
        }
        try {
            return DocumentFile.fromTreeUri(context, Uri.parse(treeUri));
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private static DocumentFile getOrCreateDirectory(DocumentFile parent, String name) {
        DocumentFile directory = parent.findFile(name);
        if (directory != null && directory.isDirectory()) {
            return directory;
        }
        try {
            return parent.createDirectory(name);
        } catch (Exception e) {
            FileLog.e(e);
        }
        return null;
    }

    private static String getMediaDirectoryName(int mediaType) {
        if (mediaType == FileLoader.MEDIA_DIR_IMAGE || mediaType == FileLoader.MEDIA_DIR_IMAGE_PUBLIC) {
            return "Telegram Images";
        } else if (mediaType == FileLoader.MEDIA_DIR_VIDEO || mediaType == FileLoader.MEDIA_DIR_VIDEO_PUBLIC) {
            return "Telegram Video";
        } else if (mediaType == FileLoader.MEDIA_DIR_AUDIO) {
            return "Telegram Audio";
        } else if (mediaType == FileLoader.MEDIA_DIR_FILES) {
            return "Telegram Files";
        } else if (mediaType == FileLoader.MEDIA_DIR_STORIES) {
            return "Telegram Stories";
        }
        return "Telegram Documents";
    }
}
