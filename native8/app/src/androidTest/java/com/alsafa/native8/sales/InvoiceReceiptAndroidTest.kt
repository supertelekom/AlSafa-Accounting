package com.alsafa.native8.sales

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import org.junit.Test
import org.junit.Assert.*

/** Requires Android: the JVM compiler smoke cannot exercise native PDF canvas lifecycle. */
class InvoiceReceiptAndroidTest {
 @Test fun completedPagesCanBeOpenedByAndroidPdfRenderer() {
  val context=ApplicationProvider.getApplicationContext<Context>()
  val receipt=InvoiceReceipt("فاتورة مبيع","7","",(1..30).map{"الصنف $it" to "ساعة عربية — 10 USD"})
  val file=receipt.pdf(context)
  try {
   assertTrue(file.length()>0)
   val fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
   val renderer=PdfRenderer(fd)
   try {assertEquals(2,renderer.pageCount);val page=renderer.openPage(0);try{assertEquals(227,page.width)}finally{page.close()}}
   finally{renderer.close()}
  }finally{file.delete()}
 }
}
