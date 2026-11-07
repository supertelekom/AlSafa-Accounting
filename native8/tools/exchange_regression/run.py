"""Run production engine against explicit in-memory boundary doubles, not Room/SQLite.
Pass a directory containing Kotlin compiler/runtime, coroutines, annotations and org.json jars.
Example: python run.py /path/to/kotlin-jars
No Android jar: Context.kt is the boundary double. Dependencies are not downloaded by this script.
"""
from pathlib import Path
import subprocess,sys,tempfile,os
here=Path(__file__).resolve().parent
root=here.parents[1]/'app/src/main/java/com/alsafa/native8/sales'
jars=[p for p in Path(sys.argv[1]).glob('*.jar') if p.name!='android.jar']
cp=os.pathsep.join(map(str,jars))
names=['LegacySaleEntity','ArchivedSaleExchange','ArchivedSaleBalance','OperationalExpensePartnerEntities','RoomSalesEngine','SalesEngine','SalesEntities','SalesRoomEntities','SalesFinancialAuditEntities','SaleExchangeMath','ProportionalProfit','PaymentExchangeSnapshot','StoreProfitAdjustmentMath','StoreProfitAdjustment','StoreSaleProfitEvent','StoreSaleProfitMath','RefundAllocationMath','SaleCurrency','SaleChangePlan','SalesChannelClassification']
files=[root/(n+'.kt') for n in names]+list(here.glob('*.kt'))
with tempfile.TemporaryDirectory() as out:
 subprocess.run(['java','-cp',cp,'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-nowarn','-no-stdlib','-no-reflect','-classpath',cp,'-d',out]+list(map(str,files)),check=True)
 subprocess.run(['java','-cp',out+os.pathsep+cp,'com.alsafa.native8.sales.RunKt'],check=True)
