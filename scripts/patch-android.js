// Run after `npx cap add android`: installs the native plugin, permissions, cleartext.
const fs = require('fs'), path = require('path');
const root = path.join(__dirname, '..'), javaDir = path.join(root, 'android/app/src/main/java/com/maya/os');
fs.mkdirSync(javaDir, { recursive: true });
fs.copyFileSync(path.join(root, 'native/MayaPlugin.java'), path.join(javaDir, 'MayaPlugin.java'));
fs.writeFileSync(path.join(javaDir, 'MainActivity.java'), `package com.maya.os;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;
public class MainActivity extends BridgeActivity {
  @Override public void onCreate(Bundle s) { registerPlugin(MayaPlugin.class); super.onCreate(s); }
}
`);
const mf = path.join(root, 'android/app/src/main/AndroidManifest.xml');
let x = fs.readFileSync(mf, 'utf8');
if (!x.includes('RECORD_AUDIO')) {
  const perms = ['INTERNET', 'RECORD_AUDIO', 'MODIFY_AUDIO_SETTINGS', 'CALL_PHONE', 'SEND_SMS', 'CAMERA', 'FLASHLIGHT', 'QUERY_ALL_PACKAGES', 'WRITE_SETTINGS', 'ACCESS_NOTIFICATION_POLICY']
    .map(p => `    <uses-permission android:name="android.permission.${p}" />`).join('\n') +
    '\n    <uses-permission android:name="com.android.alarm.permission.SET_ALARM" />' +
    '\n    <queries>\n      <intent><action android:name="android.speech.RecognitionService" /></intent>\n      <intent><action android:name="android.intent.action.TTS_SERVICE" /></intent>\n    </queries>\n';
  x = x.replace('<application', () => perms + '    <application android:usesCleartextTraffic="true"');
  fs.writeFileSync(mf, x);
}
console.log('Android project patched.');
