package org.telegram.messenger;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;

/** Durable copies of wallpapers imported into the device's wallpaper library. */
public final class CustomWallpaperStore {
    private static final String PREFIX = "custom_wallpaper_";

    private CustomWallpaperStore() {
    }

    private static File directory() {
        return new File(ApplicationLoader.getFilesDirFixed(), "custom_wallpapers");
    }

    public static boolean isCustomSlug(String slug) {
        return slug != null && slug.matches(PREFIX + "[a-f0-9]{64}");
    }

    public static String slugForFile(File file) {
        String name = file.getName();
        return name.endsWith(".jpg") ? name.substring(0, name.length() - 4) : name;
    }

    /** The gallery updater supplies an already decoded and normalized JPEG. */
    public static File importWallpaper(File source) throws Exception {
        File directory = directory();
        if (!directory.isDirectory() && !directory.mkdirs()) {
            throw new IOException("Cannot create wallpaper directory");
        }
        File temporary = File.createTempFile("wallpaper_", ".tmp", directory);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            long size = 0;
            try (FileInputStream input = new FileInputStream(source);
                 FileOutputStream output = new FileOutputStream(temporary)) {
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    digest.update(buffer, 0, count);
                    size += count;
                }
            }
            if (size == 0) {
                throw new IOException("Empty wallpaper");
            }
            StringBuilder hash = new StringBuilder();
            for (byte value : digest.digest()) {
                hash.append(Character.forDigit((value & 0xff) >>> 4, 16));
                hash.append(Character.forDigit(value & 0xf, 16));
            }
            File destination = new File(directory, PREFIX + hash + ".jpg");
            if (!destination.isFile() && !temporary.renameTo(destination)) {
                throw new IOException("Cannot save wallpaper");
            }
            destination.setLastModified(System.currentTimeMillis());
            return destination;
        } finally {
            temporary.delete();
        }
    }

    public static ArrayList<File> getWallpapers() {
        ArrayList<File> result = new ArrayList<>();
        File[] files = directory().listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isFile() && file.length() > 0 && file.getName().endsWith(".jpg") && isCustomSlug(slugForFile(file))) {
                    result.add(file);
                }
            }
        }
        Collections.sort(result, (a, b) -> Long.compare(b.lastModified(), a.lastModified()));
        return result;
    }

    public static boolean deleteWallpaper(String slug) {
        return isCustomSlug(slug) && new File(directory(), slug + ".jpg").delete();
    }

    public static boolean containsWallpaper(String slug) {
        return isCustomSlug(slug) && new File(directory(), slug + ".jpg").isFile();
    }
}
