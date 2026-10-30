#!/usr/bin/env python3
"""Build and run repository checks on a connected Android device or emulator.

Run tools/build.py first with the same SDK. The checks use their own disposable
database and never clear the application's working database.
"""
from pathlib import Path
import argparse, os, platform, shutil, subprocess, zipfile
from build import ROOT, files, run

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--sdk',default=os.environ.get('ANDROID_SDK_ROOT',os.environ.get('ANDROID_HOME','')))
    parser.add_argument('--android-jar')
    parser.add_argument('--build-tools')
    parser.add_argument('--adb')
    parser.add_argument('--serial')
    parser.add_argument('--build-only',action='store_true')
    args=parser.parse_args()
    sdk=Path(args.sdk);jar=(Path(args.android_jar) if args.android_jar else sdk/'platforms/android-36/android.jar').expanduser().resolve()
    bt=(Path(args.build_tools) if args.build_tools else sdk/'build-tools/36.0.0').expanduser().resolve()
    suffix='.exe' if platform.system()=='Windows' else ''
    java=shutil.which('java');javac=[shutil.which('javac')] if shutil.which('javac') else [java,'-m','jdk.compiler/com.sun.tools.javac.Main']
    app_classes=ROOT/'build/native/classes';work=ROOT/'build/android-checks'
    if not (app_classes/'com/hongseop/ordiloom/Store.class').is_file():parser.error('Build the main APK with tools/build.py first.')
    if work.exists():shutil.rmtree(work)
    for name in ['classes','dex']:(work/name).mkdir(parents=True,exist_ok=True)
    unsigned=work/'unsigned.apk';aligned=work/'aligned.apk';signed=work/'repository-checks.apk'
    run([bt/('aapt2'+suffix),'link','-I',jar,'--manifest',ROOT/'tests/AndroidManifest.xml','-o',unsigned])
    run(javac+['-encoding','UTF-8','-source','8','-target','8','-classpath',str(jar)+os.pathsep+str(app_classes),'-d',work/'classes',ROOT/'tests/RepositoryChecks.java'])
    classpath=work/'app-classes.jar'
    with zipfile.ZipFile(classpath,'w',zipfile.ZIP_DEFLATED) as z:
        for p in files(app_classes,'*.class'):z.write(p,p.relative_to(app_classes))
    run([java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',jar,'--classpath',classpath,'--min-api','21','--output',work/'dex']+files(work/'classes','*.class'))
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as z:
        for p in (work/'dex').glob('*.dex'):z.write(p,p.name)
    run([bt/('zipalign'+suffix),'-f','4',unsigned,aligned])
    env=os.environ.copy();env['ORDERLAB_SIGN_PASSWORD']=(ROOT/'signing/password.txt').read_text().strip()
    run([java,'-jar',bt/'lib/apksigner.jar','sign','--ks',ROOT/'signing/orderlab.jks','--ks-key-alias','orderlab','--ks-pass','env:ORDERLAB_SIGN_PASSWORD','--out',signed,aligned],env)
    if args.build_only:
        print('Built:',signed);return
    adb=[args.adb or str(sdk/'platform-tools'/('adb'+suffix))]
    if args.serial:adb+=['-s',args.serial]
    run(adb+['install','-r',ROOT/'dist/ORDILOOM-0.1.0.apk'])
    run(adb+['install','-r',signed])
    completed=subprocess.run(adb+['shell','am','instrument','-w','com.hongseop.ordiloom.community.tests/.RepositoryChecks'],text=True,capture_output=True,check=True)
    print(completed.stdout);print(completed.stderr)
    if 'PASS:' not in completed.stdout or 'FAIL' in completed.stdout:raise SystemExit('Android checks failed.')

if __name__=='__main__':main()
