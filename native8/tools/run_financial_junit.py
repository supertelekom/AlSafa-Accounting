"""Run financial JVM tests with predownloaded jars (not an Android/Room build).
Usage: python tools/run_financial_junit.py PATH_TO_JARS
Required: Kotlin compiler/stdlib/script-runtime/reflect/coroutines, annotations,
Room common JVM annotations, org.json 20240303, JUnit 4.13.2, hamcrest-core 1.3.
"""
import os
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[1]
deps = Path(sys.argv[1]).resolve()
classpath = os.pathsep.join(str(p) for p in deps.glob('*.jar'))
main = root / 'app/src/main/java/com/alsafa/native8/sales'
test = root / 'app/src/test/java/com/alsafa/native8/sales'
names = ['LegacyHistoricalSalesProfitReader', 'LegacySaleEntity', 'LegacySaleChannel',
         'SalesChannelProfit', 'LegacyFinancialExchange', 'FinancialSourceReconciliation',
         'LegacyFinancialRules', 'CumulativePartnerProfit', 'VerifiedFinancialConsolidation',
         'VerifiedFinancialEventAdapters', 'LegacyMaintenanceProfitReader', 'LegacyExpenseWithdrawalReader', 'NativeMaintenanceProfitReader', 'PartnerFinancialStatement']
tests = [n + 'Test' for n in names if (test / (n + 'Test.kt')).exists()]
tests += ['LegacyFinancialSourceParityTest', 'OriginalFinancialStatementParityTest', 'MaintenanceReturnRulesTest']
with tempfile.TemporaryDirectory(prefix='alsafa-financial-junit-') as output:
    subprocess.run(['java', '-cp', classpath, 'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler',
                    '-no-stdlib', '-no-reflect', '-classpath', classpath, '-d', output]
                   + [str(main / (n + '.kt')) for n in names]
                   + [str(main.parent / 'maintenance/MaintenanceReturnRules.kt')]
                   + [str(test / (n + '.kt')) for n in tests], check=True)
    subprocess.run(['java', '-cp', os.pathsep.join([classpath, output, str(root / 'app/src/test/resources')]),
                    'org.junit.runner.JUnitCore']
                   + ['com.alsafa.native8.sales.' + n for n in tests], check=True)
