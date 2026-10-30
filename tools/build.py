#!/usr/bin/env python3
"""Build a signed, dependency-free Android APK using JDK 17+ and SDK build-tools 36."""
from pathlib import Path
import argparse, hashlib, os, platform, secrets, shutil, subprocess, zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]

def files(folder, pattern):
    return sorted(p for p in folder.rglob(pattern) if not any(part.startswith('.') for part in p.relative_to(folder).parts))

def run(args, env=None):
    subprocess.run([str(x) for x in args], check=True, cwd=ROOT, env=env)

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--sdk', default=os.environ.get('ANDROID_SDK_ROOT', os.environ.get('ANDROID_HOME', '')))
    parser.add_argument('--android-jar')
    parser.add_argument('--build-tools')
    parser.add_argument('--keystore')
    parser.add_argument('--password-file')
    parser.add_argument('--alias', default='orderlab')
    parser.add_argument('--out', default=str(ROOT/'dist'/'ORDILOOM-Community-0.1.0.apk'))
    args = parser.parse_args()
    sdk=Path(args.sdk).expanduser()
    jar=(Path(args.android_jar) if args.android_jar else sdk/'platforms/android-36/android.jar').expanduser().resolve()
    bt=(Path(args.build_tools) if args.build_tools else sdk/'build-tools/36.0.0').expanduser().resolve()
    suffix='.exe' if platform.system()=='Windows' else ''
    if not jar.is_file() or not (bt/('aapt2'+suffix)).is_file():
        parser.error('Install Android SDK Platform 36 and Build-Tools 36.0.0, then pass --sdk or --android-jar and --build-tools.')
    java=shutil.which('java')
    if not java:parser.error('JDK 17 or newer must be available on PATH.')
    javac=[shutil.which('javac')] if shutil.which('javac') else [java,'-m','jdk.compiler/com.sun.tools.javac.Main']
    work=ROOT/'build/native';work.mkdir(parents=True,exist_ok=True)
    for folder in ['classes','generated','dex']:
        target=work/folder
        if target.exists():shutil.rmtree(target)
        target.mkdir()
    res=work/'resources.zip'; unsigned=work/'unsigned.apk';aligned=work/'aligned.apk'
    for path in [res,unsigned,aligned]:
        if path.exists():path.unlink()
    run([bt/('aapt2'+suffix),'compile','--dir',ROOT/'app/src/main/res','-o',res])
    # Gradle gets the namespace from app/build.gradle; direct aapt2 needs it here.
    ET.register_namespace('android','http://schemas.android.com/apk/res/android')
    manifest=ET.parse(ROOT/'app/src/main/AndroidManifest.xml')
    manifest.getroot().set('package','com.hongseop.ordiloom.community')
    manifest_path=work/'AndroidManifest.xml'
    manifest.write(manifest_path,encoding='utf-8',xml_declaration=True)
    run([bt/('aapt2'+suffix),'link','-I',jar,'--manifest',manifest_path,'--min-sdk-version','21','--target-sdk-version','36','--java',work/'generated','-o',unsigned,res])
    sources=files(ROOT/'app/src/main/java','*.java')+files(work/'generated','*.java')
    # JDK bootstrap classes supply LambdaMetafactory; D8 lowers lambdas for API 21.
    run(javac+['-encoding','UTF-8','-source','8','-target','8','-classpath',jar,'-d',work/'classes']+sources)
    run([java,'-cp',bt/'lib/d8.jar','com.android.tools.r8.D8','--lib',jar,'--min-api','21','--output',work/'dex']+files(work/'classes','*.class'))
    with zipfile.ZipFile(unsigned,'a',zipfile.ZIP_DEFLATED) as apk:
        for path in sorted((work/'dex').glob('*.dex')):apk.write(path,path.name)
    run([bt/('zipalign'+suffix),'-f','4',unsigned,aligned])
    key=Path(args.keystore).resolve() if args.keystore else ROOT/'signing/orderlab.jks'
    password_file=Path(args.password_file).resolve() if args.password_file else ROOT/'signing/password.txt'
    if key.exists() and not password_file.exists():parser.error('The signing key exists but the password file is missing. Supply --password-file.')
    key.parent.mkdir(parents=True,exist_ok=True)
    if not key.exists():
        if not password_file.exists():
            password_file.parent.mkdir(parents=True,exist_ok=True);password_file.write_text(secrets.token_urlsafe(32),encoding='utf-8');password_file.chmod(0o600)
        keytool=shutil.which('keytool')
        if not keytool:parser.error('keytool is required to create the independent app signing key.')
        run([keytool,'-genkeypair','-keystore',key,'-alias',args.alias,'-keyalg','RSA','-keysize','3072','-validity','10000','-dname','CN=ORDILOOM Academic Project','-storepass:file',password_file,'-keypass:file',password_file])
        key.chmod(0o600)
    env=os.environ.copy();env['ORDERLAB_SIGN_PASSWORD']=password_file.read_text(encoding='utf-8').strip()
    out=Path(args.out).resolve();out.parent.mkdir(parents=True,exist_ok=True)
    run([java,'-jar',bt/'lib/apksigner.jar','sign','--ks',key,'--ks-key-alias',args.alias,'--ks-pass','env:ORDERLAB_SIGN_PASSWORD','--key-pass','env:ORDERLAB_SIGN_PASSWORD','--out',out,aligned],env)
    run([java,'-jar',bt/'lib/apksigner.jar','verify','--verbose','--print-certs',out])
    print('APK:',out)
    print('SHA-256:',hashlib.sha256(out.read_bytes()).hexdigest())
    print('Keep the signing directory and password private; future updates need the same key.')

if __name__=='__main__':main()
