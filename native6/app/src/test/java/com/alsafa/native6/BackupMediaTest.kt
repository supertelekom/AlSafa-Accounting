package com.alsafa.native6

import android.app.Activity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import java.io.File
import java.util.UUID

@RunWith(RobolectricTestRunner::class)
class BackupMediaTest {
    @Test fun photoRoundTripAndNoOverwrite() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val dir=File(activity.filesDir,"images").apply { mkdirs() }
        val file=File(dir,"${UUID.randomUUID()}.jpg")
        val original=byteArrayOf(1,2,3,4,5)
        file.writeBytes(original)
        try {
            val archive=BackupMedia.collect(activity)
            file.delete()
            val created=BackupMedia.restore(activity,archive)
            assertEquals(1,created.size)
            assertArrayEquals(original,file.readBytes())
            assertThrows(IllegalArgumentException::class.java) {
                BackupMedia.restore(activity,archive)
            }
        } finally { file.delete() }
    }

    @Test fun tamperedPhotoIsRejected() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val name="${UUID.randomUUID()}.jpg"
        val entries=JSONArray().put(JSONObject().put("name",name)
            .put("sha256","wrong").put("base64","AQID"))
        assertThrows(IllegalArgumentException::class.java) {
            BackupMedia.restore(activity,entries)
        }
    }
}
