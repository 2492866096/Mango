package org.telegram.messenger;
import java.nio.file.*; import java.io.*;
public class CustomWallpaperStoreSmoke {
  private static void check(boolean value, String msg) { if (!value) throw new AssertionError(msg); }
  public static void main(String[] args) throws Exception {
    ApplicationLoader.directory = new File(System.getProperty("extension.test.dir"), "wallpaper-storage");
    ApplicationLoader.directory.mkdirs();
    File source = new File(ApplicationLoader.directory,"source.jpg");
    byte[] bytes = new byte[] {(byte)255,(byte)216,1,2,3,(byte)255,(byte)217};
    Files.write(source.toPath(), bytes);
    File imported = CustomWallpaperStore.importWallpaper(source);
    String slug = CustomWallpaperStore.slugForFile(imported);
    check(CustomWallpaperStore.isCustomSlug(slug), "valid stable slug");
    check(java.util.Arrays.equals(bytes, Files.readAllBytes(imported.toPath())), "preserved content");
    check(CustomWallpaperStore.importWallpaper(source).equals(imported), "deduplicated import");
    check(CustomWallpaperStore.getWallpapers().size() == 1, "one library item");
    source.delete();
    check(CustomWallpaperStore.containsWallpaper(slug), "survives cache source removal");
    check(!CustomWallpaperStore.deleteWallpaper("../../source"), "reject unsafe slug");
    File empty = new File(ApplicationLoader.directory,"empty.jpg"); empty.createNewFile();
    boolean rejected = false; try { CustomWallpaperStore.importWallpaper(empty); } catch (IOException expected) { rejected = true; }
    check(rejected, "reject empty import");
    check(new File(ApplicationLoader.directory,"custom_wallpapers").list().length == 1,"no leaked temp file");
    check(CustomWallpaperStore.deleteWallpaper(slug), "remove imported image");
    check(CustomWallpaperStore.getWallpapers().isEmpty(), "empty after removal");
    System.out.println("PASS: imported content, stable ID, deduplication, durability, unsafe path rejection, empty file rejection, temp cleanup, deletion");
  }
}