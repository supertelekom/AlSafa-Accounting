package com.alsafa.native6

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[28])
class BackupGalleryTest {
    private val first="11111111-1111-1111-1111-111111111111.jpg"
    private val second="22222222-2222-2222-2222-222222222222.jpg"
    private fun record(details:JSONObject)=JSONObject().put("details",details.toString())
    private fun backup(details:JSONObject)=JSONObject()
        .put("products",JSONArray().put(record(details)))
        .put("repairs",JSONArray())
    private fun media()=JSONArray().put(JSONObject().put("name",first))
        .put(JSONObject().put("name",second))

    @Test fun rebasesEveryGalleryPhotoAndPrimary() {
        val details=JSONObject().put("imagePath","/old/images/$second")
            .put("imagePaths",JSONArray().put("/old/images/$second").put("/old/images/$first"))
        val data=backup(details)
        val context=RuntimeEnvironment.getApplication()
        BackupMediaPaths.rebase(context,data,media())
        val result=JSONObject(data.getJSONArray("products").getJSONObject(0).getString("details"))
        val directory=java.io.File(context.filesDir,"images")
        assertEquals(java.io.File(directory,second).canonicalPath,result.getString("imagePath"))
        assertEquals(java.io.File(directory,first).canonicalPath,result.getJSONArray("imagePaths").getString(1))
    }

    @Test fun rejectsGalleryPhotoMissingFromBackup() {
        val data=backup(JSONObject().put("imagePath","/old/images/$first")
            .put("imagePaths",JSONArray().put("/old/images/$first").put("/old/images/$second")))
        try {
            BackupMediaPaths.rebase(RuntimeEnvironment.getApplication(),data,
                JSONArray().put(JSONObject().put("name",first)))
            fail("A referenced photo cannot be omitted from the backup")
        } catch (_:IllegalArgumentException) { }
    }

    @Test fun rejectsPrimaryPhotoNotListedInGallery() {
        val data=backup(JSONObject().put("imagePath","/old/images/$first")
            .put("imagePaths",JSONArray().put("/old/images/$second")))
        try {
            BackupMediaPaths.rebase(RuntimeEnvironment.getApplication(),data,media())
            fail("Primary photo must belong to gallery")
        } catch (_:IllegalArgumentException) { }
    }
}
