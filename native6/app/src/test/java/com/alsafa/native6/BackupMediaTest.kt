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
        val original=byteArrayOf(0xFF.toByte(),0xD8.toByte(),1,2,3,0xFF.toByte(),0xD9.toByte())
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
    @Test fun rejectsPhotoWithFakeJpegExtension() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val dir=File(activity.filesDir,"images").apply { mkdirs() }
        val file=File(dir,"${UUID.randomUUID()}.jpg")
        file.writeText("not a jpeg")
        try {
            assertThrows(IllegalArgumentException::class.java) {
                BackupMedia.collect(activity)
            }
        } finally { file.delete() }
    }

    @Test fun failedSecondPhotoRollsBackFirstPhoto() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val first="${UUID.randomUUID()}.jpg"
        val second="${UUID.randomUUID()}.jpg"
        val bytes=byteArrayOf(0xFF.toByte(),0xD8.toByte(),1,0xFF.toByte(),0xD9.toByte())
        val encoded=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)
        val entries=JSONArray()
            .put(JSONObject().put("name",first).put("sha256",Backup.sha(bytes)).put("base64",encoded))
            .put(JSONObject().put("name",second).put("sha256","invalid").put("base64",encoded))
        assertThrows(IllegalArgumentException::class.java) {
            BackupMedia.restore(activity,entries)
        }
        assertEquals(false,File(activity.filesDir,"images/$first").exists())
        assertEquals(false,File(activity.filesDir,"images/$second").exists())
    }

    @Test fun malformedBase64PaddingIsRejectedBeforeWriting() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val name="${UUID.randomUUID()}.jpg"
        val entries=JSONArray().put(JSONObject().put("name",name)
            .put("sha256","invalid").put("base64","AQ=I"))
        assertThrows(IllegalArgumentException::class.java) {
            BackupMedia.restore(activity,entries)
        }
        assertEquals(false,File(activity.filesDir,"images/$name").exists())
    }

    @Test fun restoresTwoPhotosWithoutOverwritingExistingFiles() {
        val activity=Robolectric.buildActivity(Activity::class.java).setup().get()
        val bytes=byteArrayOf(0xFF.toByte(),0xD8.toByte(),4,5,0xFF.toByte(),0xD9.toByte())
        val encoded=android.util.Base64.encodeToString(bytes,android.util.Base64.NO_WRAP)
        val names=listOf("${UUID.randomUUID()}.jpg","${UUID.randomUUID()}.jpg")
        val entries=JSONArray()
        names.forEach { name ->
            entries.put(JSONObject().put("name",name).put("sha256",Backup.sha(bytes)).put("base64",encoded))
        }
        try {
            val created=BackupMedia.restore(activity,entries)
            assertEquals(2,created.size)
            names.forEach { name -> assertArrayEquals(bytes,File(activity.filesDir,"images/$name").readBytes()) }
        } finally {
            names.forEach { name -> File(activity.filesDir,"images/$name").delete() }
        }
    }

}
