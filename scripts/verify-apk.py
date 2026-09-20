"""Fail the build if the APK cannot carry and launch the rebuilt ARM64 server."""
import hashlib
import os
from pathlib import Path
import struct
import subprocess
import zipfile

apk = Path('app/build/outputs/apk/debug/app-debug.apk')
native = Path('app/src/main/jniLibs/arm64-v8a/libudpfsd.so')
with zipfile.ZipFile(apk) as archive:
    packaged = archive.read('lib/arm64-v8a/libudpfsd.so')
    assert archive.testzip() is None, 'Corrupt APK ZIP entry'
    assert packaged == native.read_bytes(), 'Packaged udpfsd differs from rebuilt executable'
    assert packaged[:6] == b'\x7fELF\x02\x01', 'Expected little-endian ELF64'
    assert struct.unpack_from('<H', packaged, 16)[0] == 3, 'Expected PIE (ET_DYN)'
    assert struct.unpack_from('<H', packaged, 18)[0] == 183, 'Expected AArch64 executable'
    assert archive.read('classes.dex')[:4] == b'dex\n', 'Missing Android application code'

sdk = Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
versions = sorted((sdk / 'build-tools').glob('*'), key=lambda p: p.name, reverse=True)
build_tools = next(p for p in versions if (p / 'apksigner').exists() and (p / 'aapt').exists())
subprocess.run([str(build_tools / 'apksigner'), 'verify', '--verbose', str(apk)], check=True)
badging = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'badging', str(apk)], text=True)
assert "name='com.greshserg.ps2udpfs'" in badging, 'Wrong application ID'
assert "versionCode='4'" in badging and "versionName='1.0.3'" in badging, 'Wrong application version'
assert "launchable-activity: name='com.greshserg.ps2udpfs.MainActivity'" in badging, 'Launcher activity missing'
xml = subprocess.check_output([str(build_tools / 'aapt'), 'dump', 'xmltree', str(apk), 'AndroidManifest.xml'], text=True)
assert any('android:extractNativeLibs' in line and '0xffffffff' in line for line in xml.splitlines()), 'Native executable must be extracted at install time'
print('Verified signed APK 1.0.3 (4), launcher, native extraction and exact rebuilt ARM64 PIE bytes.')
print('udpfsd SHA-256:', hashlib.sha256(packaged).hexdigest())
