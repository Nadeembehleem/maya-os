# Maya OS – Gemini brain + direct Android phone control

## 1. Run the brain in Termux (on the same phone)
    pkg update && pkg install nodejs git
    cd maya-os-android/server
    echo 'GEMINI_API_KEY=your_key_here' > .env       # key from aistudio.google.com
    termux-wake-lock
    node server.js                                    # prints "Maya brain on :3000"
Optional in .env: GEMINI_MODEL=gemini-2.5-flash  MAYA_TOKEN=secret  PORT=3000

## 2. Build the APK on GitHub (from Termux)
    cd ~/maya-os-android
    git init && git add . && git commit -m "Maya OS"
    git branch -M main
    git remote add origin https://github.com/YOU/maya-os.git
    git push -u origin main          # use a GitHub personal access token as password
GitHub > Actions > "Build Maya OS APK" > download artifact **MayaOS-APK** > install app-debug.apk.

## 3. First run
Open Maya > Setup > server URL `http://127.0.0.1:3000` > Save > Test server (should say BRAIN ONLINE).
Grant mic/phone/SMS permissions when asked. For brightness: allow "Modify system settings". For Do Not Disturb: allow DND access.
Different phone/PC server? Use its LAN IP, e.g. http://192.168.1.5:3000 (and set MAYA_TOKEN).

## Android limits (OS rules, not bugs)
Wi-Fi/Bluetooth/airplane open the quick control panel (Android 10+ blocks silent toggling). Reading other apps' screens or tapping inside them needs an Accessibility Service (not included).
