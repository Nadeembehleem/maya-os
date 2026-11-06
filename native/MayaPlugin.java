package com.maya.os;

import android.Manifest;
import android.app.NotificationManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.hardware.camera2.CameraManager;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.AlarmClock;
import android.provider.CalendarContract;
import android.provider.MediaStore;
import android.provider.Settings;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.telephony.SmsManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;

@CapacitorPlugin(name = "Maya", permissions = {
    @Permission(alias = "mic", strings = {Manifest.permission.RECORD_AUDIO}),
    @Permission(alias = "phone", strings = {Manifest.permission.CALL_PHONE}),
    @Permission(alias = "sms", strings = {Manifest.permission.SEND_SMS})
})
public class MayaPlugin extends Plugin {
    private TextToSpeech tts;
    private SpeechRecognizer sr;
    private boolean torchOn = false;

    // ---------- Speech in ----------
    @PluginMethod
    public void listen(PluginCall call) {
        if (getPermissionState("mic") != PermissionState.GRANTED) { requestPermissionForAlias("mic", call, "micCb"); return; }
        startListen(call);
    }

    @PermissionCallback
    private void micCb(PluginCall call) {
        if (getPermissionState("mic") == PermissionState.GRANTED) startListen(call);
        else call.reject("Microphone permission denied");
    }

    private void startListen(final PluginCall call) {
        getActivity().runOnUiThread(() -> {
            if (!SpeechRecognizer.isRecognitionAvailable(getContext())) { call.reject("Speech recognition not available (install Google app)"); return; }
            if (sr != null) sr.destroy();
            sr = SpeechRecognizer.createSpeechRecognizer(getContext());
            Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, call.getString("lang", "hi-IN"));
            sr.setRecognitionListener(new RecognitionListener() {
                public void onReadyForSpeech(Bundle b) {}
                public void onBeginningOfSpeech() {}
                public void onRmsChanged(float v) {}
                public void onBufferReceived(byte[] b) {}
                public void onEndOfSpeech() {}
                public void onPartialResults(Bundle b) {}
                public void onEvent(int t, Bundle b) {}
                public void onError(int e) { call.reject("stt_error_" + e); }
                public void onResults(Bundle b) {
                    ArrayList<String> l = b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                    JSObject o = new JSObject();
                    o.put("text", (l != null && !l.isEmpty()) ? l.get(0) : "");
                    call.resolve(o);
                }
            });
            sr.startListening(i);
        });
    }

    // ---------- Speech out ----------
    @PluginMethod
    public void speak(final PluginCall call) {
        final String text = call.getString("text", "");
        getActivity().runOnUiThread(() -> {
            final Runnable go = () -> {
                tts.setLanguage(Locale.forLanguageTag(call.getString("lang", "hi-IN")));
                tts.setPitch(call.getFloat("pitch", 1.2f));
                tts.setSpeechRate(call.getFloat("rate", 1f));
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    public void onStart(String id) {}
                    public void onDone(String id) { call.resolve(); }
                    public void onError(String id) { call.resolve(); }
                });
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "m" + System.nanoTime());
            };
            if (tts == null) {
                tts = new TextToSpeech(getContext(), st -> { if (st == TextToSpeech.SUCCESS) go.run(); else call.resolve(); });
            } else go.run();
        });
    }

    // ---------- Phone control ----------
    @PluginMethod
    public void execute(PluginCall call) {
        String t = call.getString("type", "").toUpperCase(Locale.ROOT);
        String need = t.equals("CALL") ? "phone" : t.equals("SEND_SMS") ? "sms" : null;
        if (need != null && getPermissionState(need) != PermissionState.GRANTED) { requestPermissionForAlias(need, call, "permCb"); return; }
        run(call);
    }

    @PermissionCallback
    private void permCb(PluginCall call) { run(call); }

    private void run(PluginCall call) {
        JSObject r = new JSObject();
        try {
            JSObject p = call.getObject("params");
            if (p == null) p = new JSObject();
            r.put("msg", doAction(call.getString("type", "").toUpperCase(Locale.ROOT), p));
            r.put("ok", true);
        } catch (Exception e) { r.put("ok", false); r.put("msg", String.valueOf(e.getMessage())); }
        call.resolve(r);
    }

    private void go(Intent i) { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); getContext().startActivity(i); }

    private String doAction(String t, JSObject p) throws Exception {
        Context c = getContext();
        switch (t) {
            case "CALL": {
                String n = p.optString("number"); boolean ok = getPermissionState("phone") == PermissionState.GRANTED;
                go(new Intent(ok ? Intent.ACTION_CALL : Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(n))));
                return ok ? "Calling " + n : "Dialer opened for " + n;
            }
            case "SEND_SMS": {
                String n = p.optString("number"), x = p.optString("text");
                if (getPermissionState("sms") == PermissionState.GRANTED) {
                    SmsManager sm = SmsManager.getDefault();
                    sm.sendMultipartTextMessage(n, null, sm.divideMessage(x), null, null);
                    return "SMS sent to " + n;
                }
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(n))); i.putExtra("sms_body", x); go(i);
                return "SMS composer opened";
            }
            case "WHATSAPP": {
                String n = p.optString("number").replaceAll("[^0-9]", "");
                go(new Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/" + n + "?text=" + Uri.encode(p.optString("text")))));
                return "WhatsApp opened";
            }
            case "SET_ALARM": {
                Intent i = new Intent(AlarmClock.ACTION_SET_ALARM).putExtra(AlarmClock.EXTRA_HOUR, p.optInt("hour")).putExtra(AlarmClock.EXTRA_MINUTES, p.optInt("minute"))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, p.optString("label", "Maya")).putExtra(AlarmClock.EXTRA_SKIP_UI, true);
                go(i); return String.format(Locale.US, "Alarm set %02d:%02d", p.optInt("hour"), p.optInt("minute"));
            }
            case "SET_TIMER": {
                go(new Intent(AlarmClock.ACTION_SET_TIMER).putExtra(AlarmClock.EXTRA_LENGTH, p.optInt("seconds", 60))
                    .putExtra(AlarmClock.EXTRA_MESSAGE, p.optString("label", "Maya")).putExtra(AlarmClock.EXTRA_SKIP_UI, true));
                return "Timer started";
            }
            case "TORCH": {
                CameraManager cm = (CameraManager) c.getSystemService(Context.CAMERA_SERVICE);
                torchOn = p.has("state") ? p.optBoolean("state") : !torchOn;
                cm.setTorchMode(cm.getCameraIdList()[0], torchOn);
                return "Torch " + (torchOn ? "on" : "off");
            }
            case "SET_VOLUME": {
                AudioManager am = (AudioManager) c.getSystemService(Context.AUDIO_SERVICE);
                am.setStreamVolume(AudioManager.STREAM_MUSIC, am.getStreamMaxVolume(AudioManager.STREAM_MUSIC) * p.optInt("level", 50) / 100, AudioManager.FLAG_SHOW_UI);
                return "Volume " + p.optInt("level", 50) + "%";
            }
            case "SET_BRIGHTNESS": {
                if (Settings.System.canWrite(c)) {
                    Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL);
                    Settings.System.putInt(c.getContentResolver(), Settings.System.SCREEN_BRIGHTNESS, Math.max(1, p.optInt("level", 50) * 255 / 100));
                    return "Brightness " + p.optInt("level", 50) + "%";
                }
                go(new Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:" + c.getPackageName())));
                return "Allow 'Modify system settings' for Maya, then ask again";
            }
            case "TOGGLE_SETTING": {
                String s = p.optString("setting").toLowerCase(Locale.ROOT);
                if (s.equals("dnd")) {
                    NotificationManager nm = (NotificationManager) c.getSystemService(Context.NOTIFICATION_SERVICE);
                    if (nm.isNotificationPolicyAccessGranted()) {
                        boolean on = p.optBoolean("state", true);
                        nm.setInterruptionFilter(on ? NotificationManager.INTERRUPTION_FILTER_NONE : NotificationManager.INTERRUPTION_FILTER_ALL);
                        return "Do Not Disturb " + (on ? "on" : "off");
                    }
                    go(new Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)); return "Grant DND access for Maya, then ask again";
                }
                String a = s.equals("bluetooth") ? Settings.ACTION_BLUETOOTH_SETTINGS : s.equals("airplane") ? Settings.ACTION_AIRPLANE_MODE_SETTINGS
                    : s.equals("location") ? Settings.ACTION_LOCATION_SOURCE_SETTINGS : s.equals("nfc") ? Settings.ACTION_NFC_SETTINGS
                    : s.equals("battery_saver") ? Settings.ACTION_BATTERY_SAVER_SETTINGS
                    : (Build.VERSION.SDK_INT >= 29 ? Settings.Panel.ACTION_WIFI : Settings.ACTION_WIFI_SETTINGS);
                go(new Intent(a)); return "Opened " + s + " control (Android doesn't let apps flip it silently)";
            }
            case "OPEN_APP": {
                PackageManager pm = c.getPackageManager();
                String pkg = p.optString("package"), q = p.optString("app").toLowerCase(Locale.ROOT).trim();
                Intent li = pkg.isEmpty() ? null : pm.getLaunchIntentForPackage(pkg);
                if (li == null) {
                    Intent main = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
                    List<ResolveInfo> l = pm.queryIntentActivities(main, 0);
                    for (ResolveInfo ri : l) {
                        String lab = ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
                        if (lab.equals(q)) { li = pm.getLaunchIntentForPackage(ri.activityInfo.packageName); break; }
                    }
                    if (li == null) for (ResolveInfo ri : l) {
                        String lab = ri.loadLabel(pm).toString().toLowerCase(Locale.ROOT);
                        if (!q.isEmpty() && (lab.contains(q) || q.contains(lab))) { li = pm.getLaunchIntentForPackage(ri.activityInfo.packageName); break; }
                    }
                }
                if (li == null) throw new Exception("App not found: " + q);
                go(li); return "Opened " + q;
            }
            case "OPEN_URL": go(new Intent(Intent.ACTION_VIEW, Uri.parse(p.optString("url")))); return "Opened link";
            case "NAVIGATE": {
                String d = p.optString("destination");
                try { go(new Intent(Intent.ACTION_VIEW, Uri.parse("google.navigation:q=" + Uri.encode(d)))); }
                catch (Exception e) { go(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=" + Uri.encode(d)))); }
                return "Navigating to " + d;
            }
            case "PLAY_MUSIC": {
                String q = p.optString("query");
                try { go(new Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).putExtra(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/*").putExtra(SearchManager.QUERY, q)); }
                catch (Exception e) { go(new Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/results?search_query=" + Uri.encode(q)))); }
                return "Playing " + q;
            }
            case "SEARCH_WEB": go(new Intent(Intent.ACTION_WEB_SEARCH).putExtra(SearchManager.QUERY, p.optString("query"))); return "Searching";
            case "SEND_EMAIL": {
                Intent i = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:" + p.optString("to")));
                i.putExtra(Intent.EXTRA_SUBJECT, p.optString("subject")).putExtra(Intent.EXTRA_TEXT, p.optString("body")); go(i); return "Email draft opened";
            }
            case "ADD_CALENDAR": {
                Intent i = new Intent(Intent.ACTION_INSERT, CalendarContract.Events.CONTENT_URI).putExtra(CalendarContract.Events.TITLE, p.optString("title"));
                if (p.optLong("start") > 0) i.putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, p.optLong("start"));
                go(i); return "Calendar event opened";
            }
            case "OPEN_CAMERA": go(new Intent(MediaStore.ACTION_IMAGE_CAPTURE)); return "Camera opened";
            case "OPEN_SETTINGS": go(new Intent(Settings.ACTION_SETTINGS)); return "Settings opened";
            case "INTENT": {
                Intent i = new Intent(p.optString("action", Intent.ACTION_VIEW));
                if (!p.optString("data").isEmpty()) i.setData(Uri.parse(p.optString("data")));
                if (!p.optString("package").isEmpty()) i.setPackage(p.optString("package"));
                JSONObject ex = p.optJSONObject("extras");
                if (ex != null) for (Iterator<String> it = ex.keys(); it.hasNext();) { String k = it.next(); i.putExtra(k, ex.optString(k)); }
                go(i); return "Intent sent";
            }
            default: throw new Exception("Unknown action " + t);
        }
    }
}
