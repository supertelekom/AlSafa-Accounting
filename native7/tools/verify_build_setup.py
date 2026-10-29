"""Offline checks for Native 7 build files; does not replace Gradle/Android compilation."""
from pathlib import Path
import hashlib
import json
import gzip
import xml.etree.ElementTree as ET
import zipfile

root = Path(__file__).resolve().parents[1]
required = [
    'gradlew', 'gradlew.bat', 'settings.gradle.kts', 'build.gradle.kts', 'gradle.properties',
    'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
    'app/build.gradle.kts', 'app/proguard-rules.pro', 'app/src/main/AndroidManifest.xml',
    'app/src/main/res/values/styles.xml', 'app/src/main/assets/Tajawal-Medium.ttf',
    'app/src/main/assets/invoice-logo.png', 'app/src/main/assets/original-structure.json.gz',
]
for path in required:
    assert (root / path).is_file(), f'Missing: {path}'
jar = root / 'gradle/wrapper/gradle-wrapper.jar'
assert hashlib.sha256(jar.read_bytes()).hexdigest() == '3b8a25775a69158b5ad2b1d17a80a88dc7b40a352a73eb27d06c612a7ce68e98'
with zipfile.ZipFile(jar) as archive:
    assert archive.testzip() is None
    assert 'org/gradle/wrapper/GradleWrapperMain.class' in archive.namelist()
properties = dict(line.split('=', 1) for line in (root / 'gradle/wrapper/gradle-wrapper.properties').read_text().splitlines() if '=' in line)
assert properties['distributionUrl'] == r'https\://services.gradle.org/distributions/gradle-9.8.2-bin.zip'
assert properties['distributionSha256Sum'] == '21bd5da2de49b642904077f38c0efdc7778e92b3ed0889392dca61b97e389607'
assert properties['validateDistributionUrl'] == 'true'
project = (root / 'build.gradle.kts').read_text()
app = (root / 'app/build.gradle.kts').read_text()
assert 'version "9.4.1"' in project
assert 'kotlin-gradle-plugin:2.4.21' in project
assert 'org.jetbrains.kotlin.android' not in project + app
assert 'kotlinOptions' not in app
assert 'compileSdk = 37' in app and 'targetSdk = 37' in app and 'minSdk = 26' in app
assert 'buildToolsVersion =' not in app
assert 'VERSION_17' in app
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
ns = '{http://schemas.android.com/apk/res/android}'
assert not manifest.findall('uses-permission'), 'UI preview must not gain runtime/network permissions'
application = manifest.find('application')
assert application is not None and application.get(ns + 'theme') == '@style/Theme.AlSafa.Native7'
assert application.get(ns + 'allowBackup') == 'false'
assert application.get(ns + 'enableOnBackInvokedCallback') == 'true'
ET.parse(root / 'app/src/main/res/values/styles.xml')
spec = json.loads(gzip.decompress((root / 'app/src/main/assets/original-structure.json.gz').read_bytes()))
assert len(spec['pages']) >= 76
for path in (root / 'app/src/main/assets').iterdir():
    assert path.suffix not in ['.js', '.html', '.css'], 'Runtime web assets are not part of Native 7'
print('Build file inventory, official wrapper hash, version pins, XML and native UI assets: OK')
print('Android compilation, dependency resolution and device testing are separate checks.')
