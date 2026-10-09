#!/usr/bin/env python3
"""Modifie le manifeste décodé par apktool : Application de l'éditeur, activités, nom et package."""
import re, sys, glob

dec, pkg = sys.argv[1], sys.argv[2]
path = f"{dec}/AndroidManifest.xml"
m = open(path, encoding="utf-8").read()

if "com.sky.totmeditor" not in m:
    m = m.replace("<application ", '<application android:name="com.sky.totmeditor.EditorApp" ', 1)
    activities = '''
        <activity android:name="com.sky.totmeditor.LevelListActivity" android:label="TotM Editor"
            android:icon="@mipmap/app_icon" android:roundIcon="@mipmap/app_icon_round"
            android:taskAffinity="com.sky.totmeditor" android:theme="@android:style/Theme.Material.NoActionBar"
            android:screenOrientation="portrait" android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN"/>
                <category android:name="android.intent.category.LAUNCHER"/>
            </intent-filter>
        </activity>
        <activity android:name="com.sky.totmeditor.EditorActivity" android:label="TotM Editor"
            android:taskAffinity="com.sky.totmeditor" android:theme="@android:style/Theme.Material.NoActionBar"
            android:screenOrientation="portrait" android:configChanges="orientation|screenSize|keyboardHidden"
            android:exported="false"/>
        <activity android:name="com.sky.totmeditor.OfficialPickerActivity" android:label="Stages officiels"
            android:taskAffinity="com.sky.totmeditor" android:theme="@android:style/Theme.Material.NoActionBar"
            android:screenOrientation="portrait" android:exported="false"/>
    </application>'''
    m = m.replace("</application>", activities, 1)
open(path, "w", encoding="utf-8").write(m)

for f in glob.glob(f"{dec}/res/values*/strings.xml"):
    s = open(f, encoding="utf-8").read()
    s = re.sub(r'<string name="app_name">[^<]*</string>', '<string name="app_name">TotM Mod</string>', s)
    open(f, "w", encoding="utf-8").write(s)

y = open(f"{dec}/apktool.yml", encoding="utf-8").read()
y = re.sub(r"renameManifestPackage: .*", f"renameManifestPackage: {pkg}", y)
open(f"{dec}/apktool.yml", "w", encoding="utf-8").write(y)
print("manifeste ok, package", pkg)
