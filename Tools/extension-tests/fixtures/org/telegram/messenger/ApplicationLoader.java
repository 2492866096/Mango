package org.telegram.messenger;

import java.io.File;

public class ApplicationLoader {
    public static android.app.Activity applicationContext = new android.app.Activity();
    public static File directory;
    public static File getFilesDirFixed() { return directory; }
}
