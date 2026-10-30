package org.telegram.messenger;

import java.io.File;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/** Uses disposable fixtures only. Never invokes the workstation su or examines user media. */
public final class DeviceFileToolsTest {
    private static int checks;
    private static void check(boolean value, String name) { if (!value) throw new AssertionError(name); checks++; }
    private static File file(Path root, String name, String data) throws Exception {
        Path p = root.resolve(name); Files.write(p, data.getBytes(StandardCharsets.UTF_8)); return p.toFile();
    }
    private static String quote(String value) { return "'" + value.replace("'", "'\\''") + "'"; }
    private static File sparse(Path root, String name, long bytes) throws Exception {
        File file = root.resolve(name).toFile();
        try (RandomAccessFile output = new RandomAccessFile(file, "rw")) { output.setLength(bytes); }
        return file;
    }
    private static Set<String> paths(File... files) {
        Set<String> result = new HashSet<>(); for (File f : files) result.add(f.getAbsolutePath()); return result;
    }
    private static final class Run implements RootCommandRunner.Listener {
        final StringBuilder output = new StringBuilder();
        final CountDownLatch done = new CountDownLatch(1);
        int code; boolean root, cancelled, timeout, truncated;
        public synchronized void onOutput(String text) { output.append(text); }
        public void onComplete(int c, boolean r, boolean x, boolean t, boolean l) {
            code=c; root=r; cancelled=x; timeout=t; truncated=l; done.countDown();
        }
        void await() throws Exception { check(done.await(15, TimeUnit.SECONDS), "runner completes within bound"); }
    }
    public static void main(String[] args) throws Exception {
        Path temporary = new File(args[0]).toPath().toRealPath();
        DeviceToolsUnlockGate gate = new DeviceToolsUnlockGate();
        for (int i=0;i<4;i++) check(!gate.tap(true, i*100), "first four taps locked");
        check(gate.tap(true, 400), "fifth consecutive tap unlocks");
        check(!gate.tap(true, 500), "sequence resets after unlock");
        gate.tap(false, 550);
        for (int i=0;i<4;i++) check(!gate.tap(true, 600+i*100), "other row interrupts taps");
        check(!gate.tap(true, 3000), "long gap resets taps");
        gate.reset();
        check(!gate.tap(true, 3001), "lifecycle reset");

        Path media=Files.createDirectory(temporary.resolve("media"));
        File a=file(media,"a.jpg","same-content"), b=file(media,"b.jpg","same-content"), c=file(media,"c.jpg","same-content");
        file(media,"different.jpg","other-bytes!");
        file(media,"partial.temp","same-content");
        file(media,"secret.enc","same-content");
        File outside=file(temporary,"outside.jpg","same-content");
        Files.createSymbolicLink(media.resolve("symlink.jpg"), outside.toPath());
        DuplicateFileScanner scan=new DuplicateFileScanner(Collections.singletonList(media.toFile()), f -> false);
        DuplicateFileScanner.Result found=scan.scan(null);
        check(found.groups.size()==1 && found.groups.get(0).entries.size()==3,"size and SHA256 isolate real duplicates and skip temp/encrypted/symlink");
        DuplicateFileScanner.CleanupResult all=scan.clean(found.groups,paths(a,b,c));
        check(all.deleted==0 && all.skipped==3,"all-selected cannot remove last copy");
        DuplicateFileScanner.CleanupResult cleaned=scan.clean(found.groups,paths(b,c));
        check(cleaned.deleted==2 && a.exists() && !b.exists() && !c.exists(),"one-click keeps designated original");
        check(outside.exists() && Files.isSymbolicLink(media.resolve("symlink.jpg")),"outside and symlink untouched");

        b=file(media,"b.jpg","same-content");
        found=new DuplicateFileScanner(Arrays.asList(media.toFile(),media.toFile()),null).scan(null);
        check(found.groups.size()==1 && found.groups.get(0).entries.size()==2,"overlapping roots do not duplicate entries");
        long mtime=b.lastModified(); file(media,"b.jpg","evil-content"); b.setLastModified(mtime);
        cleaned=new DuplicateFileScanner(Collections.singletonList(media.toFile()),null).clean(found.groups,paths(b));
        check(cleaned.deleted==0 && b.exists(),"same-size same-mtime changed content is rehashed");
        file(media,"b.jpg","same-content");
        found=new DuplicateFileScanner(Collections.singletonList(media.toFile()),null).scan(null);
        Files.delete(a.toPath());
        cleaned=new DuplicateFileScanner(Collections.singletonList(media.toFile()),null).clean(found.groups,paths(b));
        check(cleaned.deleted==0 && b.exists(),"missing keeper stops deletion");
        a=file(media,"a.jpg","same-content");
        found=new DuplicateFileScanner(Collections.singletonList(media.toFile()),null).scan(null);
        cleaned=new DuplicateFileScanner(Collections.singletonList(media.toFile()),f -> f.getName().equals("b.jpg")).clean(found.groups,paths(b));
        check(cleaned.deleted==0 && b.exists(),"busy candidate protected during deletion");
        cleaned=new DuplicateFileScanner(Collections.singletonList(media.toFile()),f -> f.getName().equals("a.jpg")).clean(found.groups,paths(b));
        check(cleaned.deleted==0 && a.exists(),"busy keeper cannot authorize deletion");
        Files.delete(b.toPath()); Files.createSymbolicLink(b.toPath(), outside.toPath());
        cleaned=new DuplicateFileScanner(Collections.singletonList(media.toFile()),null).clean(found.groups,paths(b));
        check(cleaned.deleted==0 && outside.exists(),"symlink replacement after scan protected");
        DuplicateFileScanner cancelled=new DuplicateFileScanner(Collections.singletonList(media.toFile()),null);
        cancelled.cancel(); check(cancelled.scan(null).cancelled,"scan cancellation");

        Path largeRoot=Files.createDirectory(temporary.resolve("large-media"));
        long threshold=DuplicateFileScanner.LARGE_FILE_MIN_BYTES;
        File thresholdFile=sparse(largeRoot,"threshold.mp4",threshold);
        File largest=sparse(largeRoot,"largest.mp4",threshold+20);
        File smaller=sparse(largeRoot,"smaller.mp4",threshold-1);
        File managed=sparse(largeRoot,"retained.mp4",threshold+30);
        Set<String> protectedFiles=paths(managed);
        DuplicateFileScanner largeScan=new DuplicateFileScanner(Collections.singletonList(largeRoot.toFile()),f -> protectedFiles.contains(f.getAbsolutePath()));
        DuplicateFileScanner.Result largeFound=largeScan.scan(null);
        check(largeFound.largeFiles.size()==2,"large threshold inclusive and retained files excluded");
        check(largeFound.largeFiles.get(0).file.equals(largest) && largeFound.largeFiles.get(1).file.equals(thresholdFile),"large files sorted largest first");
        check(largeFound.groups.isEmpty(),"large files do not require duplicate copy");
        protectedFiles.add(largest.getAbsolutePath());
        DuplicateFileScanner.CleanupResult largeClean=largeScan.cleanLargeFiles(largeFound.largeFiles,paths(largest));
        check(largeClean.deleted==0 && largeClean.skipped==1 && largest.exists(),"newly busy or retention-managed large file protected at deletion");
        protectedFiles.remove(largest.getAbsolutePath());
        long oldModified=thresholdFile.lastModified();
        check(thresholdFile.setLastModified(oldModified+10000),"modify large file fixture timestamp");
        largeClean=largeScan.cleanLargeFiles(largeFound.largeFiles,paths(thresholdFile));
        check(largeClean.deleted==0 && thresholdFile.exists(),"changed large file timestamp prevents deletion");
        sparse(largeRoot,"largest.mp4",threshold+40);
        largeClean=largeScan.cleanLargeFiles(largeFound.largeFiles,paths(largest));
        check(largeClean.deleted==0 && largest.exists(),"changed large file size prevents deletion");
        largeFound=largeScan.scan(null);
        largeClean=largeScan.cleanLargeFiles(largeFound.largeFiles,paths(largest));
        check(largeClean.deleted==1 && largeClean.bytes==threshold+40 && !largest.exists(),"explicit selected standalone large file removed");
        check(thresholdFile.exists() && smaller.exists() && managed.exists(),"unselected and protected large files retained");
        Files.delete(thresholdFile.toPath());
        Files.createSymbolicLink(thresholdFile.toPath(),managed.toPath());
        largeClean=largeScan.cleanLargeFiles(largeFound.largeFiles,paths(thresholdFile));
        check(largeClean.deleted==0 && managed.exists(),"large file symlink replacement protected");

        Path bin=Files.createDirectory(temporary.resolve("fake-bin"));
        File id=file(bin,"id","#!/bin/sh\nprintf '0\\n'\n");
        check(id.setExecutable(true), "fake id executable");
        File su=file(bin,"fake-su","#!/bin/sh\nPATH="+quote(bin.toString())+":/bin:/usr/bin\nexport PATH\nexec /bin/sh \"$@\"\n");
        check(su.setExecutable(true), "fake su executable");
        Run success=new Run(); new RootCommandRunner(su.getAbsolutePath(),10000).start("printf 'root-ok'; exit 7",success); success.await();
        check(success.root && success.code==7 && success.output.toString().equals("root-ok"),"same fake root shell executes command after uid check: root="+success.root+", code="+success.code+", cancelled="+success.cancelled+", timeout="+success.timeout+", truncated="+success.truncated+", output="+success.output);
        file(bin,"id","#!/bin/sh\nprintf '1000\\n'\n");
        File sentinel=temporary.resolve("must-not-execute").toFile();
        Run denied=new Run(); new RootCommandRunner(su.getAbsolutePath(),10000).start("touch "+quote(sentinel.getAbsolutePath()),denied); denied.await();
        check(!denied.root && denied.code==126 && !sentinel.exists(),"non-root su output rejects command without fallback");
        Run absent=new Run(); new RootCommandRunner(temporary.resolve("absent-su").toString(),10000).start("touch "+quote(sentinel.getAbsolutePath()),absent); absent.await();
        check(!absent.root && !sentinel.exists(),"missing su rejects command without fallback");
        file(bin,"id","#!/bin/sh\nprintf '0\\n'\n");
        Run timeout=new Run(); new RootCommandRunner(su.getAbsolutePath(),100).start("sleep 2",timeout); timeout.await();
        check(timeout.timeout && timeout.root,"timeout completes while command sleeps");
        Run stopped=new Run(); RootCommandRunner runner=new RootCommandRunner(su.getAbsolutePath(),2000);
        runner.start("sleep 2",stopped); Thread.sleep(100); runner.cancel(); stopped.await();
        check(stopped.cancelled,"cancel completes while command sleeps");
        Run bounded=new Run(); new RootCommandRunner(su.getAbsolutePath(),10000).start("head -c 90000 /dev/zero",bounded); bounded.await();
        check(bounded.output.length()==RootCommandRunner.OUTPUT_LIMIT && bounded.truncated,"output bounded at 64KB");
        System.out.println("DeviceFileToolsTest: "+checks+" checks passed");
    }
}
