package com.alsafa.native5.accounting;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Test;
import static org.junit.Assert.*;

public class LegacyBackupPreflightTest {
    private static byte[] zip(String name,String content) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream stream=new ZipOutputStream(out)) {
            stream.putNextEntry(new ZipEntry(name));
            stream.write(content.getBytes(StandardCharsets.UTF_8));
            stream.closeEntry();
        }
        return out.toByteArray();
    }

    @Test public void inventoriesBackupWithoutExtractingOrImporting() throws Exception {
        LegacyBackupPreflight.Inspection inspected=LegacyBackupPreflight.inspect(
            new ByteArrayInputStream(zip("data/products.json","[]")));
        assertEquals(1,inspected.entries.size());
        assertEquals(2,inspected.totalBytes);
        assertEquals(64,inspected.entries.get("data/products.json").sha256.length());
    }

    @Test public void rejectsTraversalAndAbsolutePaths() throws Exception {
        for(String path:new String[]{"../data.json","data/../secret.json",
                "/absolute.json","C:/backup.json","folder\\secret.json"}) {
            try {
                LegacyBackupPreflight.inspect(new ByteArrayInputStream(zip(path,"test")));
                fail("Unsafe backup path accepted: "+path);
            } catch(IOException expected) {}
        }
    }

    @Test public void rejectsEmptyArchive() throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        try(ZipOutputStream zip=new ZipOutputStream(out)) {}
        try {
            LegacyBackupPreflight.inspect(new ByteArrayInputStream(out.toByteArray()));
            fail("Empty archive accepted");
        } catch(IOException expected) {}
    }
}
