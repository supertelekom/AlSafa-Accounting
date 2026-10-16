package com.alsafa.native5.accounting;

import org.junit.Test;
import static org.junit.Assert.*;

public class UploadOnlySyncTest {
    static class Queue implements UploadOnlySync.Queue {
        UploadOnlySync.Item item = new UploadOnlySync.Item("sale-1","SALE","{}",1);
        boolean confirmed, retried;
        @Override public UploadOnlySync.Item claim(long now,long lease) {
            UploadOnlySync.Item next=item; item=null; return next;
        }
        @Override public boolean confirm(String id,long attempt,long revision) {
            confirmed = id.equals("sale-1") && attempt==1 && revision==9; return confirmed;
        }
        @Override public boolean retry(String id,long attempt,String error) {
            retried = id.equals("sale-1") && attempt==1; return retried;
        }
    }
    @Test public void successfulUploadConfirms() {
        Queue queue=new Queue();
        UploadOnlySync sync=new UploadOnlySync(queue,(id,kind,payload)->9);
        assertTrue(sync.uploadNext(10000,1000));
        assertTrue(queue.confirmed); assertFalse(queue.retried);
        assertFalse(sync.uploadNext(10000,1000));
    }
    @Test public void networkFailureRetainsRetryableOperation() {
        Queue queue=new Queue();
        UploadOnlySync sync=new UploadOnlySync(queue,(id,kind,payload)->{throw new IllegalStateException("offline");});
        assertTrue(sync.uploadNext(10000,1000));
        assertFalse(queue.confirmed); assertTrue(queue.retried);
    }
    @Test public void invalidRevisionCannotConfirm() {
        Queue queue=new Queue();
        UploadOnlySync sync=new UploadOnlySync(queue,(id,kind,payload)->0);
        assertTrue(sync.uploadNext(10000,1000));
        assertFalse(queue.confirmed); assertTrue(queue.retried);
    }
}
