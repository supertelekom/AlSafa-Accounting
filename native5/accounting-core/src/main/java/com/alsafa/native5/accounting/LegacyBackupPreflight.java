package com.alsafa.native5.accounting;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Read-only inspection of a legacy ZIP backup before ANY import writes.
 * Does not assume the legacy schema or claim that data was imported.
 */
public final class LegacyBackupPreflight {
    private LegacyBackupPreflight() {}
    private static final int MAX_FILES=10000;
    private static final long MAX_ENTRY_BYTES=100L*1024*1024;
    private static final long MAX_TOTAL_BYTES=500L*1024*1024;

    public static final class EntryInfo {
        public final long bytes;
        public final String sha256;
        EntryInfo(long bytes,String sha256) {this.bytes=bytes;this.sha256=sha256;}
    }
    public static final class Inspection {
        public final Map<String,EntryInfo> entries;
        public final long totalBytes;
        Inspection(Map<String,EntryInfo> entries,long totalBytes) {
            this.entries=Collections.unmodifiableMap(new LinkedHashMap<>(entries));
            this.totalBytes=totalBytes;
        }
    }

    public static Inspection inspect(InputStream source) throws IOException {
        if(source==null) throw new IllegalArgumentException("Missing backup stream");
        Map<String,EntryInfo> files=new LinkedHashMap<>();
        long total=0;
        try(ZipInputStream zip=new ZipInputStream(source)) {
            ZipEntry entry;
            byte[] buffer=new byte[8192];
            while((entry=zip.getNextEntry())!=null) {
                String name=entry.getName();
                if(name==null || name.isEmpty() || name.startsWith("/") ||
                    name.startsWith("\\") || name.indexOf('\\')>=0 ||
                    name.indexOf('\u0000')>=0 || name.matches("^[A-Za-z]:.*")) {
                    throw new IOException("Unsafe backup entry path");
                }
                for(String segment:name.split("/")) {
                    if(segment.equals("..") || segment.equals("."))
                        throw new IOException("Unsafe backup entry traversal");
                }
                if(entry.isDirectory()) {zip.closeEntry();continue;}
                if(files.size()>=MAX_FILES || files.containsKey(name))
                    throw new IOException("Too many or duplicate backup entries");
                MessageDigest digest;
                try {digest=MessageDigest.getInstance("SHA-256");}
                catch(NoSuchAlgorithmException impossible) {throw new AssertionError(impossible);}
                long size=0;
                int count;
                while((count=zip.read(buffer))!=-1) {
                    size+=count;
                    total+=count;
                    if(size>MAX_ENTRY_BYTES || total>MAX_TOTAL_BYTES)
                        throw new IOException("Backup exceeds safety limits");
                    digest.update(buffer,0,count);
                }
                StringBuilder hex=new StringBuilder(64);
                for(byte value:digest.digest())
                    hex.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
                files.put(name,new EntryInfo(size,hex.toString()));
                zip.closeEntry();
            }
        }
        if(files.isEmpty()) throw new IOException("Backup ZIP is empty");
        return new Inspection(files,total);
    }
}
