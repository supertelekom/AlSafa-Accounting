package com.alsafa.native8.sales

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class OperationalFinancialRoomTest {
 private lateinit var db:AppDatabase
 @Before fun setup() {
  db=Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(),
   AppDatabase::class.java).build()
 }
 @After fun close() { db.close() }

 @Test fun expenseIsUniqueAndQueryIsDateBounded()=runBlocking {
  val row=OperationalExpenseEntity("expense-1","2026-11-01","10:00",
   "تشغيل","اختبار","USD","10","نقدي","drawer","1",1L,"financial-expense-1")
  val dao=db.operationalExpensePartnerDao()
  dao.insertExpense(row)
  assertEquals(row,dao.expense("expense-1"))
  assertEquals(1,dao.expenses("2026-11-01","2026-11-30").size)
  assertTrue(dao.expenses("2026-12-01","2026-12-31").isEmpty())
  try { dao.insertExpense(row);fail("duplicate must fail") }
  catch (_:android.database.sqlite.SQLiteConstraintException) { }
 }

 @Test fun withdrawalAndExpenseRollbackTogether()=runBlocking {
  val dao=db.operationalExpensePartnerDao()
  val expense=OperationalExpenseEntity("e1","2026-11-01","09:00",
   "تشغيل","","USD","4","نقدي","drawer","1",1L,"financial-e1")
  val withdrawal=OperationalPartnerWithdrawalEntity("w1","2026-11-01","09:01",
   "father","USD","3","drawer","1","",2L,"financial-w1")
  try {
   db.withTransaction {
    dao.insertExpense(expense)
    dao.insertWithdrawal(withdrawal)
    error("force rollback")
   }
  } catch (_:IllegalStateException) { }
  assertNull(dao.expense("e1"))
  assertNull(dao.withdrawal("w1"))
 }

 @Test fun duplicateCashMovementReferenceIsRejected()=runBlocking {
  val dao=db.operationalExpensePartnerDao()
  val first=OperationalExpenseEntity("e1","2026-11-01","09:00",
   "تشغيل","","USD","4","نقدي","drawer","1",1L,"same-movement")
  val second=first.copy(id="e2")
  dao.insertExpense(first)
  var rejected=false
  try { dao.insertExpense(second) } catch (_:android.database.sqlite.SQLiteConstraintException) {
   rejected=true
  }
  assertTrue("cash movement reference must be unique",rejected)
 }
 @Test fun nonCashExpenseDoesNotCreateCashboxMovement()=runBlocking {
  val expense=OperationalExpenseEntity("expense-noncash","2026-11-05","10:00",
   "تشغيل","", "USD","12","آجل",null,"1",5L,null)
  val repository=OperationalExpensePartnerRepository(db)
  assertEquals(expense,repository.saveExpense(expense))
  assertEquals(expense,repository.saveExpense(expense))
  assertEquals(1,db.operationalExpensePartnerDao()
   .expenses("2026-11-05","2026-11-05").size)
 }

}
