package com.lightos.minimalchat;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.text.Editable;
import android.text.InputType;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.RelativeSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;
import android.util.Base64;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.HttpURLConnection;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1";
    private static final int VOICE = 11;
    private static final int MESSAGE_WINDOW = 80;
    private static final int MESSAGE_PAGE = 30;
    private static final float BASE_WIDTH_DP = 360f;
    private static final String LOADING = "__loading__";
    private static final String APP_VERSION = "1.0.2";
    private SharedPreferences prefs;
    private FrameLayout screen;
    private LinearLayout root, messageList, folderList, chatList;
    private ScrollView scroll, settingsScrollView;
    private ScrollIndicator scrollIndicator;
    private EditText input, apiKey, endpointInput, jinaKeyInput, voiceEndpointInput;
    private TextView modelText, contextText, attachText, replyChip, notice, voiceStatus, voiceText, voiceReply, bulkButton, emptyPrompt, bottomButton;
    private GlobeButton webSearchIcon;
    private View chatFade;
    private ContextMeter meter;
    private SpeechRecognizer speechRecognizer;
    private TextToSpeech tts;
    private MediaPlayer ttsPlayer;
    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private Msg activeTtsOwner;
    private final HashMap<String, File> ttsReadyFiles = new HashMap<String, File>();
    private final HashSet<String> pcm16AudioFiles = new HashSet<String>();
    private AudioRecord audioRecord;
    private AudioTrack currentAudioTrack;
    private Thread wavThread;
    private MediaRecorder mediaRecorder;
    private File voiceFile;
    private FrameLayout voiceOverlay;
    private ScrollView voiceTextScroll;
    private WaveView voiceWaves;
    private BorderWaveView voiceBorder;
    private final ArrayList<String> models = new ArrayList<String>();
    private final ArrayList<String> myModels = new ArrayList<String>();
    private final ArrayList<String> customEndpoints = new ArrayList<String>();
    private final HashMap<String, Integer> modelContexts = new HashMap<String, Integer>();
    private final HashMap<String, String> modelSources = new HashMap<String, String>();
    private final HashMap<String, String> modelEndpoints = new HashMap<String, String>();
    private final HashMap<String, String> modelSearchText = new HashMap<String, String>();
    private final HashMap<String, String> folderInstructions = new HashMap<String, String>();
    private final HashMap<String, ArrayList<String>> discoveredVoices = new HashMap<String, ArrayList<String>>();
    private final HashSet<String> audioInputModels = new HashSet<String>();
    private final HashSet<String> audioOutputModels = new HashSet<String>();
    private final HashSet<String> reasoningModels = new HashSet<String>();
    private final HashSet<String> speedModels = new HashSet<String>();
    private final HashSet<String> discoveringVoiceEndpoints = new HashSet<String>();
    private final ArrayList<String> folders = new ArrayList<String>();
    private final ArrayList<Msg> messages = new ArrayList<Msg>();
    private final ArrayList<Chat> chats = new ArrayList<Chat>();
    private final ArrayList<String> lastSearchSources = new ArrayList<String>();
    private final HashSet<String> selectedChats = new HashSet<String>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String currentChatId = "", selectedFolder = "Inbox", projectView = "", expandedFolder = "", imageBase64 = "", imageMime = "image/jpeg", pendingVoiceText = "", replyQuote = "", voiceThinkingWord = "thinking", settingsPage = "";
    private int pane = 1, messageStart = 0, messageEnd = 0, savedChatScrollY = 0, savedSettingsScrollY = 0, emptyPromptRun = 0, voiceThinkingRun = 0, voiceListenRun = 0, recorderSpeechFrames = 0, voiceSession = 0;
    private long recordingStartedAt = 0, quietSince = 0;
    private float downX, downY;
    private boolean messageWindowReady = false, renderingMessages = false, savedChatScrollKnown = false, restoreScrollOnce = false, forceAutoScrollBottom = false, userAtChatBottom = true, voiceMode = false, voiceFullMode = false, voiceThinking = false, voiceAwaitingSpeechResult = false, ttsReady = false, hookVoiceMode = false, projectEditorOpen = false, recordingFallback = false, wavRecording = false, wavSubmitAfterStop = false, webSearchChat = false;

    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        hookVoiceMode = this instanceof VoiceHookActivity;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setStatusBarColor(hookVoiceMode ? Color.TRANSPARENT : Color.BLACK);
        getWindow().setNavigationBarColor(hookVoiceMode ? Color.TRANSPARENT : Color.BLACK);
        if (hookVoiceMode) applyVoiceWindowBlur(true);
        prefs = getSharedPreferences("minimal-chat", MODE_PRIVATE);
        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        loadState();
        buildChrome();
        applyKeepScreenAwake();
        initTts();
        if (!hookVoiceMode) showChatPane();
        handleIncoming(getIntent());
        if (hookVoiceMode && !voiceMode) startVoice(true);
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); handleIncoming(intent); }

    @Override protected void onDestroy() {
        super.onDestroy();
        stopAllVoiceAudio();
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        stopRecorder(false);
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    }

    @Override protected void onPause() {
        super.onPause();
        if (voiceMode && voiceFullMode) stopVoiceMode();
    }

    private void buildChrome() {
        screen = new FrameLayout(this);
        screen.setBackgroundColor(hookVoiceMode ? Color.TRANSPARENT : Color.BLACK);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(26), dp(14), dp(26), dp(10));
        root.setBackgroundColor(hookVoiceMode ? Color.TRANSPARENT : Color.BLACK);
        screen.addView(root, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
    }

    @Override public boolean dispatchTouchEvent(MotionEvent e) {
        if (e.getAction() == MotionEvent.ACTION_DOWN) {
            downX = e.getX(); downY = e.getY();
            collapseKeyboardIfOutsideInput(e);
        }
        if (e.getAction() == MotionEvent.ACTION_UP) {
            float dx = e.getX() - downX, dy = e.getY() - downY;
            if (voiceMode && voiceFullMode) {
                if (dx > dp(90) && Math.abs(dx) > Math.abs(dy) * 1.5f) stopVoiceMode();
                else if (Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy) * 1.2f) return true;
                else if (voiceAwaitingSpeechResult && isMiddleVoiceTap(e) && Math.abs(dx) < dp(24) && Math.abs(dy) < dp(24)) { finishListeningNow(); return true; }
            }
            if (dy > dp(90) && Math.abs(dy) > Math.abs(dx) * 1.4f && clearFocusedTextField()) return true;
            if (Math.abs(dx) > dp(72) && Math.abs(dx) > Math.abs(dy) * 1.2f && Math.abs(dy) < dp(220)) {
                if (dx > 0 && pane == 0 && projectView.length() > 0) { projectView = ""; showChatsPane(); return true; }
                if (dx > 0 && pane == 2 && settingsPage.length() > 0) { settingsPage = ""; showSettingsPane(); return true; }
                if (dx < 0 && pane < 2) { pane++; if (pane == 2) settingsPage = ""; renderPane(); return true; }
                if (dx > 0 && pane > 0) { pane--; renderPane(); return true; }
            }
        }
        return super.dispatchTouchEvent(e);
    }

    private boolean isMiddleVoiceTap(MotionEvent e) {
        float y = e.getY();
        int h = screen == null ? getResources().getDisplayMetrics().heightPixels : screen.getHeight();
        return y > h * 0.25f && y < h * 0.78f;
    }

    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_UP) {
            int code = e.getKeyCode();
            if (code == KeyEvent.KEYCODE_HOME || code == KeyEvent.KEYCODE_BACK || code == KeyEvent.KEYCODE_ESCAPE || code == KeyEvent.KEYCODE_MOVE_HOME) {
                if (voiceMode && voiceFullMode) stopVoiceMode();
                else if (projectEditorOpen) showChatsPane();
                else if (pane == 0 && projectView.length() > 0) { projectView = ""; showChatsPane(); }
                else if (pane == 2 && settingsPage.length() > 0) { settingsPage = ""; showSettingsPane(); }
                else goHome();
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private void goHome() {
        try {
            Intent i = new Intent(Intent.ACTION_MAIN);
            i.addCategory(Intent.CATEGORY_HOME);
            i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) { moveTaskToBack(true); }
    }

    private void collapseKeyboardIfOutsideInput(MotionEvent e) {
        View focused = getCurrentFocus();
        if (!(focused instanceof EditText)) return;
        int[] loc = new int[2];
        focused.getLocationOnScreen(loc);
        float x = e.getRawX(), y = e.getRawY();
        boolean inside = x >= loc[0] && x <= loc[0] + focused.getWidth() && y >= loc[1] && y <= loc[1] + focused.getHeight();
        if (!inside) clearFocusedTextField();
    }

    private boolean clearFocusedTextField() {
        View focused = getCurrentFocus();
        if (!(focused instanceof EditText)) return false;
        hideKeyboardFrom(focused);
        focused.clearFocus();
        return true;
    }

    private void renderPane() {
        hideKeyboard();
        removeScreenChild(bulkButton); bulkButton = null;
        if (pane == 0) showChatsPane(); else if (pane == 2) showSettingsPane(); else showChatPane();
    }

    private void clearPaneViews() {
        root.removeAllViews();
        input = null;
        apiKey = null;
        endpointInput = null;
        jinaKeyInput = null;
        voiceEndpointInput = null;
        contextText = null;
        attachText = null;
        replyChip = null;
        removeScreenChild(notice); notice = null;
        modelText = null;
        meter = null;
        emptyPrompt = null;
        webSearchIcon = null;
        removeScreenChild(bulkButton); bulkButton = null;
        removeScreenChild(scrollIndicator); scrollIndicator = null;
        removeScreenChild(chatFade); chatFade = null;
        removeScreenChild(bottomButton); bottomButton = null;
        emptyPromptRun++;
        if (pane != 1) {
            scroll = null;
            messageList = null;
        }
        root.requestLayout();
    }

    private void removeScreenChild(View v) {
        try { if (v != null && screen != null && v.getParent() == screen) screen.removeView(v); } catch (Exception ignored) { }
    }

    private void showChatPane() {
        projectEditorOpen = false;
        pane = 1;
        clearPaneViews();
        root.setOnClickListener(null);
        root.setPadding(dp(26), dp(14), dp(26), dp(10));
        restoreScrollOnce = savedChatScrollKnown;
        LinearLayout header = row();
        modelText = text(modelLabel() + " >", 13, Color.WHITE);
        modelText.setGravity(Gravity.CENTER_VERTICAL);
        modelText.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chooseModel(); } });
        meter = new ContextMeter(this); meter.percent = contextPercent();
        meter.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showContext(); } });
        contextText = text(contextPercentText(), 10, Color.rgb(135, 135, 135));
        contextText.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        contextText.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showContext(); } });
        LinearLayout leftHeader = row();
        if ("custom".equals(selectedModelSource()) && selectedModel().length() > 0) {
            ImageView link = new ImageView(this);
            link.setImageResource(R.drawable.ic_link);
            link.setColorFilter(Color.WHITE);
            link.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
            link.setPadding(0, dp(5), dp(2), dp(9));
            link.setTranslationY(-dp(2));
            leftHeader.addView(link, new LinearLayout.LayoutParams(dp(16), dp(28)));
        }
        leftHeader.addView(modelText, new LinearLayout.LayoutParams(0, dp(28), 1));
        header.addView(leftHeader, new LinearLayout.LayoutParams(0, dp(28), 1));
        TextView tools = text("+", 18, Color.WHITE);
        tools.setGravity(Gravity.CENTER);
        tools.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showToolsMenu(); } });
        header.addView(tools, new LinearLayout.LayoutParams(dp(28), dp(28)));
        LinearLayout rightHeader = row();
        rightHeader.setGravity(Gravity.CENTER_VERTICAL);
        webSearchIcon = new GlobeButton(this);
        webSearchIcon.active = webSearchChat;
        webSearchIcon.setTranslationY(dp(2));
        webSearchIcon.setClickable(webSearchChat);
        webSearchIcon.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (!webSearchChat) return; webSearchChat = false; saveCurrentChat(); toast("web search off"); if (webSearchIcon != null) { webSearchIcon.active = false; webSearchIcon.setClickable(false); webSearchIcon.invalidate(); } } });
        rightHeader.addView(webSearchIcon, new LinearLayout.LayoutParams(dp(32), dp(28)));
        rightHeader.addView(space(1), new LinearLayout.LayoutParams(0, dp(28), 1));
        contextText.setPadding(0, 0, dp(8), 0);
        rightHeader.addView(contextText, new LinearLayout.LayoutParams(dp(58), dp(28)));
        LinearLayout.LayoutParams meterLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        meterLp.setMargins(dp(2), 0, 0, 0);
        rightHeader.addView(meter, meterLp);
        header.addView(rightHeader, new LinearLayout.LayoutParams(0, dp(28), 1));
        root.addView(header);
        root.addView(separator());

        scroll = new ScrollView(this);
        messageList = new LinearLayout(this);
        messageList.setOrientation(LinearLayout.VERTICAL);
        messageList.setPadding(0, dp(12), 0, dp(8));
        messageList.setBackgroundColor(Color.BLACK);
        scroll.addView(messageList);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOnScrollChangeListener(new View.OnScrollChangeListener() {
            @Override public void onScrollChange(View v, int sx, int sy, int oldSx, int oldSy) {
                if (scrollIndicator != null) scrollIndicator.invalidate();
                int bottom = messageList.getHeight() - scroll.getHeight();
                userAtChatBottom = bottom <= 0 || sy >= bottom - dp(48);
                updateBottomButton();
                if (renderingMessages || messages.size() <= MESSAGE_WINDOW) return;
                if (sy <= dp(4)) loadOlderMessages();
                else if (bottom > 0 && sy >= bottom - dp(8)) loadNewerMessages();
            }
        });
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        attachText = text("", 11, Color.LTGRAY);
        root.addView(attachText, new LinearLayout.LayoutParams(-1, dp(18)));
        replyChip = text("", 11, Color.LTGRAY);
        replyChip.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { replyQuote = ""; updateReplyChip(); } });
        root.addView(replyChip, new LinearLayout.LayoutParams(-1, dp(20)));
        attachText.setVisibility(View.GONE);
        replyChip.setVisibility(View.GONE);
        updateReplyChip();
        root.addView(separator());

        LinearLayout composer = row();
        input = new EditText(this);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(120, 120, 120));
        input.setHint("ask");
        setTextPx(input, 18);
        input.setMinLines(1);
        input.setMaxLines(5);
        input.setGravity(Gravity.CENTER_VERTICAL);
        input.setBackgroundColor(Color.BLACK);
        input.setPadding(0, 0, 0, 0);
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) { input.setHint(hasFocus ? "" : "ask"); }
        });
        composer.addView(input, new LinearLayout.LayoutParams(0, dp(50), 1));
        LinearLayout.LayoutParams micLp = new LinearLayout.LayoutParams(dp(30), dp(30));
        micLp.setMargins(0, 0, dp(12), 0);
        ImageButton mic = iconButton(R.drawable.ic_mic, new View.OnClickListener() { @Override public void onClick(View v) { startVoice(false); } }, 5);
        mic.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { startVoice(true); return true; } });
        composer.addView(mic, micLp);
        composer.addView(iconButton(R.drawable.ic_send, new View.OnClickListener() { @Override public void onClick(View v) { send(); } }, 4), new LinearLayout.LayoutParams(dp(32), dp(32)));
        root.addView(composer);
        renderMessages();
    }

    private void showSettingsPane() {
        projectEditorOpen = false;
        if (pane == 2 && settingsPage.length() == 0 && settingsScrollView != null) savedSettingsScrollY = settingsScrollView.getScrollY();
        pane = 2;
        captureChatScroll();
        clearPaneViews();
        root.setOnClickListener(null);
        root.setPadding(dp(26), dp(14), dp(26), dp(10));
        apiKey = null;
        endpointInput = null;
        jinaKeyInput = null;
        voiceEndpointInput = null;
        root.addView(settingsTitle());
        ScrollView settingsScroll = new ScrollView(this);
        settingsScrollView = settingsScroll;
        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setBackgroundColor(Color.BLACK);
        settingsScroll.addView(settings);
        settingsScroll.setVerticalScrollBarEnabled(false);

        if (settingsPage.length() == 0) addSettingsIndex(settings);
        else if ("model source".equals(settingsPage)) addModelSourceSettings(settings);
        else if ("search".equals(settingsPage)) addJinaSettings(settings);
        else if ("voice".equals(settingsPage)) addVoiceSettings(settings);
        else if ("display".equals(settingsPage)) addDisplaySettings(settings);
        else if ("memory".equals(settingsPage)) addMemorySettings(settings);
        else if ("models".equals(settingsPage)) addModelsSettings(settings);
        root.addView(settingsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        settingsScroll.post(new Runnable() { @Override public void run() { settingsScroll.scrollTo(0, settingsPage.length() == 0 ? savedSettingsScrollY : 0); } });
    }

    private View collapsibleHeader(final String title, final String pref) {
        LinearLayout h = row();
        h.setPadding(0, dp(8), 0, dp(8));
        TextView name = text(title, 16, Color.WHITE);
        TextView arrow = text(isExpanded(pref) ? "v" : ">", 14, Color.LTGRAY);
        arrow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        h.addView(name, new LinearLayout.LayoutParams(0, dp(34), 1));
        h.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(34)));
        h.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (settingsScrollView != null) savedSettingsScrollY = settingsScrollView.getScrollY(); prefs.edit().putBoolean(pref, !isExpanded(pref)).apply(); showSettingsPane(); } });
        return h;
    }

    private boolean isExpanded(String pref) { return prefs.getBoolean(pref, false); }

    private View settingsTitle() {
        LinearLayout h = row();
        h.setPadding(0, dp(8), 0, dp(18));
        TextView title = text("settings", 23, Color.WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (settingsPage.length() > 0) { settingsPage = ""; showSettingsPane(); } } });
        h.addView(title, new LinearLayout.LayoutParams(-2, dp(34)));
        if (settingsPage.length() > 0) {
            TextView sub = text("  " + settingsPageTitle(settingsPage), 13, Color.rgb(150,150,150));
            sub.setGravity(Gravity.CENTER_VERTICAL);
            sub.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { settingsPage = ""; showSettingsPane(); } });
            h.addView(sub, new LinearLayout.LayoutParams(0, dp(34), 1));
        }
        return h;
    }

    private String settingsPageTitle(String page) {
        if ("model source".equals(page)) return "model source";
        if ("search".equals(page)) return "web search";
        if ("memory".equals(page)) return "memory";
        return page;
    }

    private void addSettingsIndex(LinearLayout settings) {
        settings.addView(settingsLink("model source", "model source"));
        settings.addView(separator());
        settings.addView(settingsLink("web search", "search"));
        settings.addView(separator());
        settings.addView(settingsLink("voice", "voice"));
        settings.addView(separator());
        settings.addView(settingsLink("display", "display"));
        settings.addView(separator());
        settings.addView(settingsLink("memory", "memory"));
        settings.addView(separator());
        settings.addView(settingsLink("models", "models"));
        addVersionFooter(settings);
    }

    private void addVersionFooter(LinearLayout settings) {
        maybeCheckLatestVersion();
        TextView v = text(versionFooterText(), 11, Color.rgb(130,130,130));
        v.setGravity(Gravity.CENTER_VERTICAL);
        settings.addView(v, new LinearLayout.LayoutParams(-1, dp(22)));
    }

    private String versionFooterText() {
        String latest = prefs == null ? "" : prefs.getString("latestGitHubVersion", "");
        boolean outdated = latest.length() > 0 && compareVersions(latest, APP_VERSION) > 0;
        return "version " + APP_VERSION + (outdated ? " - outdated!" : "");
    }

    private void maybeCheckLatestVersion() {
        if (prefs == null) return;
        long now = System.currentTimeMillis();
        if (now - prefs.getLong("latestVersionCheckedAt", 0) < 6L * 60L * 60L * 1000L) return;
        prefs.edit().putLong("latestVersionCheckedAt", now).apply();
        new Thread(new Runnable() { @Override public void run() {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL("https://api.github.com/repos/awpsec/lightui/releases/latest").openConnection();
                c.setConnectTimeout(10000); c.setReadTimeout(10000); c.setRequestProperty("Accept", "application/vnd.github+json"); c.setRequestProperty("User-Agent", "lightui-android");
                if (c.getResponseCode() >= 400) return;
                final String tag = new JSONObject(readAll(c.getInputStream())).optString("tag_name", "").replaceFirst("^[vV]", "").trim();
                if (tag.length() > 0) {
                    prefs.edit().putString("latestGitHubVersion", tag).apply();
                    runOnUiThread(new Runnable() { @Override public void run() { if (pane == 2 && settingsPage.length() == 0) showSettingsPane(); } });
                }
            } catch (Exception ignored) { }
        } }).start();
    }

    private int compareVersions(String a, String b) {
        String[] as = (a == null ? "" : a).replaceFirst("^[vV]", "").split("\\.");
        String[] bs = (b == null ? "" : b).replaceFirst("^[vV]", "").split("\\.");
        for (int i = 0; i < Math.max(as.length, bs.length); i++) {
            int av = i < as.length ? parseVersionPart(as[i]) : 0, bv = i < bs.length ? parseVersionPart(bs[i]) : 0;
            if (av != bv) return av > bv ? 1 : -1;
        }
        return 0;
    }

    private int parseVersionPart(String s) { try { return Integer.parseInt((s == null ? "" : s).replaceAll("[^0-9].*$", "")); } catch (Exception e) { return 0; } }

    private void addMemorySettings(LinearLayout settings) {
        LinearLayout memory = row();
        TextView memoryLabel = text("memory", 16, Color.WHITE);
        memoryLabel.setGravity(Gravity.CENTER_VERTICAL);
        memory.addView(memoryLabel, new LinearLayout.LayoutParams(0, dp(42), 1));
        final TogglePill memoryToggle = new TogglePill(this);
        memoryToggle.checked = memoryEnabled();
        memoryToggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { boolean next = !memoryEnabled(); prefs.edit().putBoolean("memoryEnabled", next).apply(); memoryToggle.checked = next; memoryToggle.invalidate(); toast(next ? "memory on" : "memory off"); } });
        memory.addView(memoryToggle, new LinearLayout.LayoutParams(dp(48), dp(28)));
        settings.addView(memory);
        settings.addView(separator());

        settings.addView(text(memoryStatusText(), 12, Color.rgb(150,150,150)), new LinearLayout.LayoutParams(-1, dp(42)));
        final EditText editor = plainEdit("# MEMORY.md\n\n1. User's preference or fact (source, yyyy-mm-dd)");
        String mem = normalizeMemoryMd(memoryMd());
        editor.setText(mem.trim().length() == 0 ? "# MEMORY.md\n\n" : mem);
        editor.setGravity(Gravity.TOP | Gravity.LEFT);
        editor.setSingleLine(false);
        editor.setMinLines(7);
        setTextPx(editor, 13);
        editor.setHorizontallyScrolling(false);
        editor.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        editor.setPadding(dp(12), dp(10), dp(12), dp(10));
        editor.setBackground(grayBorder());
        settings.addView(editor, new LinearLayout.LayoutParams(-1, dp(160)));
        settings.addView(space(10));
        LinearLayout actions = row();
        TextView wipe = panelAction("wipe");
        wipe.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { confirmWipeMemory(); } });
        TextView save = panelAction("save");
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { writeMemoryMd(editor.getText().toString()); hideKeyboardFrom(editor); toast("memory saved"); showSettingsPane(); } });
        actions.addView(wipe, new LinearLayout.LayoutParams(0, dp(50), 1));
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(50), 1));
        settings.addView(actions);
    }

    private void confirmWipeMemory() {
        final Dialog d = panel("wipe memory");
        LinearLayout box = panelBox();
        box.addView(panelTitle("wipe memory?"));
        TextView msg = text("this clears MEMORY.md. chats stay intact.", 13, Color.LTGRAY);
        msg.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(msg, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout actions = row();
        TextView cancel = panelAction("cancel");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        TextView yes = panelAction("wipe");
        yes.setTextColor(Color.WHITE);
        yes.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { writeMemoryMd(""); d.dismiss(); toast("memory wiped"); showSettingsPane(); } });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(yes, new LinearLayout.LayoutParams(0, dp(52), 1));
        box.addView(actions);
        showPanel(d, box);
    }

    private View settingsLink(final String title, final String page) {
        LinearLayout h = row();
        h.setPadding(0, dp(6), 0, dp(6));
        TextView name = text(title, 16, Color.WHITE);
        TextView arrow = text(">", 14, Color.LTGRAY);
        arrow.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        h.addView(name, new LinearLayout.LayoutParams(0, dp(33), 1));
        h.addView(arrow, new LinearLayout.LayoutParams(dp(34), dp(33)));
        h.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (settingsScrollView != null) savedSettingsScrollY = settingsScrollView.getScrollY(); settingsPage = page; showSettingsPane(); } });
        return h;
    }

    private void addModelsSettings(LinearLayout settings) {
        LinearLayout modelsHeader = row();
        modelsHeader.addView(space(1), new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView add = text("+", 20, Color.WHITE);
        add.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        add.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { addModel(); } });
        modelsHeader.addView(add, new LinearLayout.LayoutParams(dp(42), dp(34)));
        settings.addView(modelsHeader);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        if (myModels.size() == 0) list.addView(text("none added", 13, Color.rgb(120,120,120)));
        for (int i = 0; i < myModels.size(); i++) {
            final String m = myModels.get(i).trim();
            if (m.length() == 0) continue;
            LinearLayout line = row();
            line.setPadding(0, dp(7), 0, dp(7));
            TextView name = text(shortModel(m), 15, Color.WHITE);
            TextView remove = text("remove", 12, Color.LTGRAY);
            remove.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            remove.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { removeMyModel(m); } });
            if ("custom".equals(modelSources.get(m))) {
                ImageView link = new ImageView(this);
                link.setImageResource(R.drawable.ic_link);
                link.setColorFilter(Color.WHITE);
                link.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
                link.setPadding(0, dp(5), dp(2), dp(11));
                link.setTranslationY(-dp(2));
                line.addView(link, new LinearLayout.LayoutParams(dp(15), dp(32)));
            }
            line.addView(name, new LinearLayout.LayoutParams(0, dp(32), 1));
            line.addView(remove, new LinearLayout.LayoutParams(dp(82), dp(32)));
            list.addView(line);
        }
        settings.addView(list);
    }

    private void addModelSourceSettings(LinearLayout settings) {
        settings.addView(sectionHeader("openrouter"));
        addOpenRouterSettings(settings);
        settings.addView(separator());
        settings.addView(sectionHeader("openai compatible endpoint"));
        addEndpointSettings(settings);
    }

    private void addOpenRouterSettings(LinearLayout settings) {
        settings.addView(text("api key", 11, Color.LTGRAY));
        apiKey = plainEdit("sk-or-...");
        apiKey.setSingleLine(true);
        apiKey.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        apiKey.setText(savedApiKey());
        settings.addView(apiKey, new LinearLayout.LayoutParams(-1, dp(42)));
        LinearLayout actions = row();
        TextView save = smallPill("save");
        TextView refresh = smallPill("refresh models");
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { saveApiKey(); toast("saved"); } });
        refresh.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { refreshModels(); } });
        actions.addView(save);
        actions.addView(refresh);
        settings.addView(actions, new LinearLayout.LayoutParams(-1, dp(34)));
        settings.addView(space(8));
    }

    private void addEndpointSettings(LinearLayout settings) {
        settings.addView(text("saved endpoints", 11, Color.LTGRAY));
        if (customEndpoints.size() == 0) settings.addView(text("none", 13, Color.rgb(120,120,120)), new LinearLayout.LayoutParams(-1, dp(30)));
        for (int i = 0; i < customEndpoints.size(); i++) {
            final String endpoint = customEndpoints.get(i);
            LinearLayout line = row();
            TextView name = text(endpoint, 12, Color.WHITE);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            TextView remove = text("remove", 11, Color.LTGRAY);
            remove.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            remove.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { removeCustomEndpoint(endpoint); showSettingsPane(); } });
            line.addView(name, new LinearLayout.LayoutParams(0, dp(30), 1));
            line.addView(remove, new LinearLayout.LayoutParams(dp(76), dp(30)));
            settings.addView(line);
        }
        settings.addView(text("endpoint", 11, Color.LTGRAY));
        endpointInput = plainEdit("http://100.x.x.x:11434/v1");
        endpointInput.setSingleLine(true);
        endpointInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpointInput.setText("");
        settings.addView(endpointInput, new LinearLayout.LayoutParams(-1, dp(42)));
        LinearLayout actions = row();
        TextView save = smallPill("save");
        TextView test = smallPill("test");
        TextView refresh = smallPill("refresh models");
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { addEndpointFromInput(); } });
        test.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { testCustomEndpoint(); } });
        refresh.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { refreshModels(); } });
        actions.addView(save);
        actions.addView(test);
        actions.addView(refresh);
        settings.addView(actions, new LinearLayout.LayoutParams(-1, dp(34)));
        settings.addView(space(8));
    }

    private void addJinaSettings(LinearLayout settings) {
        LinearLayout auto = row();
        auto.addView(text("auto search", 14, Color.WHITE), new LinearLayout.LayoutParams(0, dp(38), 1));
        final TogglePill toggle = new TogglePill(this);
        toggle.checked = prefs.getBoolean("autoSearch", false);
        toggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { boolean next = !prefs.getBoolean("autoSearch", false); prefs.edit().putBoolean("autoSearch", next).apply(); toggle.checked = next; toggle.invalidate(); } });
        auto.addView(toggle, new LinearLayout.LayoutParams(dp(48), dp(28)));
        settings.addView(auto);
        settings.addView(settingChoice("provider", searchProvider(), new View.OnClickListener() { @Override public void onClick(View v) { chooseVoiceProvider("searchProvider", new String[]{"jina", "brave"}); } }));
        final EditText[] braveKeyInput = new EditText[1];
        if ("brave".equals(searchProvider())) {
            settings.addView(text("brave api key", 11, Color.LTGRAY));
            braveKeyInput[0] = plainEdit("required");
            braveKeyInput[0].setSingleLine(true);
            braveKeyInput[0].setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            braveKeyInput[0].setText(prefs.getString("braveApiKey", ""));
            settings.addView(braveKeyInput[0], new LinearLayout.LayoutParams(-1, dp(42)));
        } else {
            settings.addView(text("jina api key", 11, Color.LTGRAY));
            jinaKeyInput = plainEdit("optional");
            jinaKeyInput.setSingleLine(true);
            jinaKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            jinaKeyInput.setText(prefs.getString("jinaApiKey", ""));
            settings.addView(jinaKeyInput, new LinearLayout.LayoutParams(-1, dp(42)));
        }
        LinearLayout actions = row();
        TextView save = smallPill("save");
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (jinaKeyInput != null) saveJinaSettings(); if (braveKeyInput[0] != null) prefs.edit().putString("braveApiKey", braveKeyInput[0].getText().toString().trim()).apply(); toast("saved"); } });
        actions.addView(save);
        settings.addView(actions, new LinearLayout.LayoutParams(-1, dp(34)));
        settings.addView(text("/search works anytime", 11, Color.rgb(120,120,120)), new LinearLayout.LayoutParams(-1, dp(24)));
        settings.addView(space(8));
    }

    private void addVoiceSettings(LinearLayout settings) {
        settings.addView(sectionHeader("general"));
        LinearLayout speak = row();
        speak.addView(text("speak responses", 14, Color.WHITE), new LinearLayout.LayoutParams(0, dp(38), 1));
        final TogglePill speakToggle = new TogglePill(this);
        speakToggle.checked = prefs.getBoolean("voiceSpeak", true);
        speakToggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { boolean next = !prefs.getBoolean("voiceSpeak", true); prefs.edit().putBoolean("voiceSpeak", next).apply(); speakToggle.checked = next; speakToggle.invalidate(); } });
        speak.addView(speakToggle, new LinearLayout.LayoutParams(dp(48), dp(28)));
        settings.addView(speak);

        settings.addView(sectionHeader("voice assistant / two way voice"));
        settings.addView(settingChoice("web search", voiceWebSearchMode(), new View.OnClickListener() { @Override public void onClick(View v) { chooseVoiceProvider("voiceWebSearchMode", new String[]{"off", "auto", "on"}); } }));

        LinearLayout continuous = row();
        continuous.addView(text("keep listening", 14, Color.WHITE), new LinearLayout.LayoutParams(0, dp(38), 1));
        final TogglePill continuousToggle = new TogglePill(this);
        continuousToggle.checked = prefs.getBoolean("voiceLoop", true);
        continuousToggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { boolean next = !prefs.getBoolean("voiceLoop", true); prefs.edit().putBoolean("voiceLoop", next).apply(); continuousToggle.checked = next; continuousToggle.invalidate(); } });
        continuous.addView(continuousToggle, new LinearLayout.LayoutParams(dp(48), dp(28)));
        settings.addView(continuous);
        settings.addView(sectionHeader("assistant answer"));
        settings.addView(settingChoice("model", voiceAnswerModelLabel(), new View.OnClickListener() { @Override public void onClick(View v) { chooseVoiceAnswerModel(); } }));
        settings.addView(text(voicePipelineLabel(), 10, Color.rgb(120,120,120)), new LinearLayout.LayoutParams(-1, dp(28)));

        settings.addView(sectionHeader("input"));
        settings.addView(settingChoice("provider", voiceInputProvider(), new View.OnClickListener() { @Override public void onClick(View v) { chooseVoiceProvider("voiceInputProvider", new String[]{"auto", "system", "openrouter", "endpoint"}); } }));
        if ("openrouter".equals(voiceInputProvider())) settings.addView(settingChoice("model", prefs.getString("voiceTranscribeModel", "whisper-1"), new View.OnClickListener() { @Override public void onClick(View v) { chooseTranscriptionModel(); } }));
        else if ("endpoint".equals(voiceInputProvider())) settings.addView(settingChoice("model", "endpoint default", null));
        if ("endpoint".equals(voiceInputProvider()) || "auto".equals(voiceInputProvider())) settings.addView(settingChoice("endpoint", endpointLabel(prefs.getString("voiceTranscribeEndpoint", "")), new View.OnClickListener() { @Override public void onClick(View v) { chooseSavedEndpoint("voiceTranscribeEndpoint"); } }));

        settings.addView(sectionHeader("output"));
        settings.addView(settingChoice("provider", voiceOutputProvider(), new View.OnClickListener() { @Override public void onClick(View v) { chooseVoiceProvider("voiceOutputProvider", new String[]{"system", "openrouter", "endpoint"}); } }));
        if ("openrouter".equals(voiceOutputProvider())) {
            settings.addView(settingChoice("model", prefs.getString("voiceTtsModel", "choose"), new View.OnClickListener() { @Override public void onClick(View v) { chooseTtsModel(); } }));
            settings.addView(settingChoice("voice", ttsVoiceForModel(prefs.getString("voiceTtsModel", "")), new View.OnClickListener() { @Override public void onClick(View v) { chooseVoiceName(); } }));
            addVoiceSpeedSetting(settings);
        } else if ("endpoint".equals(voiceOutputProvider())) {
            settings.addView(settingChoice("model", voiceEndpointModelLabel(), new View.OnClickListener() { @Override public void onClick(View v) { promptVoiceCustom("voiceEndpointTtsModel", "tts-1"); } }));
            settings.addView(settingChoice("voice", ttsVoiceForModel(prefs.getString("voiceEndpointTtsModel", "")), new View.OnClickListener() { @Override public void onClick(View v) { chooseVoiceName(); } }));
            addVoiceSpeedSetting(settings);
            settings.addView(settingChoice("endpoint", endpointLabel(prefs.getString("voiceTtsEndpoint", "")), new View.OnClickListener() { @Override public void onClick(View v) { chooseSavedEndpoint("voiceTtsEndpoint"); } }));
        }

        settings.addView(space(8));
    }

    private View settingChoice(String label, String value, View.OnClickListener l) {
        LinearLayout row = row();
        row.setPadding(0, dp(4), 0, dp(4));
        row.addView(text(label, 14, Color.WHITE), new LinearLayout.LayoutParams(0, dp(34), 1));
        TextView choice = text((value == null || value.length() == 0 ? "none" : value) + " >", 13, Color.LTGRAY);
        choice.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(choice, new LinearLayout.LayoutParams(dp(210), dp(34)));
        if (l != null) row.setOnClickListener(l);
        return row;
    }

    private String voiceInputProvider() { return prefs.getString("voiceInputProvider", "auto"); }
    private String voiceOutputProvider() { return prefs.getString("voiceOutputProvider", prefs.getBoolean("voiceEndpointTts", false) ? "endpoint" : "system"); }
    private String voiceWebSearchMode() { return prefs.getString("voiceWebSearchMode", "auto"); }
    private String searchProvider() { return prefs.getString("searchProvider", "jina"); }
    private String voiceAnswerModel() { return prefs.getString("voiceAnswerModel", "").trim(); }
    private String voiceAnswerModelLabel() { String m = voiceAnswerModel(); return m.length() == 0 ? "same as chat" : shortModel(m); }
    private String voicePipelineLabel() { String m = configuredVoiceAnswerModel(); if (sameOpenRouterVoiceModelForAllThree() && isAllInOneVoiceModel(m)) return "single multimodal request when fullscreen voice records"; if (sameOpenRouterVoiceModelForAllThree()) return "same model set, but catalog does not show audio in + out"; if (voiceAnswerModel().length() == 0) return "two-way voice answers use the current chat model"; return isAllInOneVoiceModel(m) ? "all-in-one capable model selected" : "two-way voice answers only; normal chats unchanged"; }
    private boolean isAllInOneVoiceModel(String model) { return model.length() > 0 && audioInputModels.contains(model) && audioOutputModels.contains(model); }
    private String configuredVoiceAnswerModel() { String m = voiceAnswerModel(); return m.length() == 0 ? selectedModel() : m; }
    private String configuredVoiceInputModel() { return "openrouter".equals(voiceInputProvider()) ? prefs.getString("voiceTranscribeModel", "whisper-1").trim() : ""; }
    private String configuredVoiceOutputModel() { return "openrouter".equals(voiceOutputProvider()) ? openRouterTtsModel(prefs.getString("voiceTtsModel", "")).trim() : ""; }
    private String voiceEndpointModelLabel() { String m = prefs.getString("voiceEndpointTtsModel", "").trim(); return m.length() == 0 ? "auto detect" : shortModel(m); }
    private boolean isDefaultVoiceEndpointModel(String model) { String m = model == null ? "" : model.trim(); return m.length() == 0 || "tts-1".equals(m) || "openai/tts-1".equals(m); }
    private boolean sameOpenRouterVoiceModelForAllThree() {
        if (!"openrouter".equals(voiceInputProvider()) || !"openrouter".equals(voiceOutputProvider()) || !prefs.getBoolean("voiceSpeak", true)) return false;
        String in = configuredVoiceInputModel(), answer = configuredVoiceAnswerModel(), out = configuredVoiceOutputModel();
        return in.length() > 0 && in.equals(answer) && in.equals(out);
    }

    private void chooseVoiceProvider(final String pref, String[] values) {
        final Dialog d = panel("provider");
        LinearLayout box = panelBox();
        box.addView(panelTitle("provider"));
        for (int i = 0; i < values.length; i++) {
            final String value = values[i];
            TextView item = panelItem(value, "");
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { prefs.edit().putString(pref, value).apply(); d.dismiss(); if ("voiceOutputProvider".equals(pref) && "endpoint".equals(value)) discoverVoiceEndpoint(voiceTtsEndpoint()); showSettingsPane(); } });
            box.addView(item);
        }
        TextView cancel = panelAction("cancel");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(52)));
        showPanel(d, box);
    }

    private void discoverVoiceEndpoint(final String endpointRaw) {
        final String endpoint = normalizeEndpoint(endpointRaw);
        if (endpoint.length() == 0) return;
        if (discoveringVoiceEndpoints.contains(endpoint)) return;
        discoveringVoiceEndpoints.add(endpoint);
        new Thread(new Runnable() { @Override public void run() {
            String foundModel = "";
            ArrayList<String> foundVoices = new ArrayList<String>();
            try { foundModel = detectTtsModel(endpoint); } catch (Exception ignored) { }
            try { foundVoices = detectVoices(endpoint); } catch (Exception ignored) { }
            final String model = foundModel;
            final ArrayList<String> voices = foundVoices;
            runOnUiThread(new Runnable() { @Override public void run() {
                discoveringVoiceEndpoints.remove(endpoint);
                SharedPreferences.Editor e = prefs.edit();
                String currentModel = prefs.getString("voiceEndpointTtsModel", "").trim();
                boolean changed = false;
                if (model.length() > 0 && isDefaultVoiceEndpointModel(currentModel)) e.putString("voiceEndpointTtsModel", model);
                if (model.length() > 0 && isDefaultVoiceEndpointModel(currentModel) && !model.equals(currentModel)) changed = true;
                if (voices.size() > 0) {
                    ArrayList<String> before = discoveredVoiceList("endpoint:" + endpoint);
                    discoveredVoices.put("endpoint:" + endpoint, voices);
                    saveDiscoveredVoices();
                    if (!before.equals(voices)) changed = true;
                    String current = prefs.getString("voiceTtsVoice", "");
                    if (current.length() == 0 || "alloy".equals(current) || !voices.contains(current)) { e.putString("voiceTtsVoice", voices.get(0)); changed = true; }
                }
                e.apply();
                if (changed) { toast("voice endpoint detected" + (model.length() > 0 ? ": " + shortModel(model) : "")); if (pane == 2) showSettingsPane(); }
            } });
        } }).start();
    }

    private String detectTtsModel(String endpoint) throws Exception {
        String raw = "";
        Exception last = null;
        String[] paths = endpoint.toLowerCase(Locale.US).endsWith("/v1") ? new String[]{"/models"} : new String[]{"/models", "/v1/models"};
        for (String path : paths) {
            try { raw = httpGet(endpoint + path); break; }
            catch (Exception e) { last = e; }
        }
        if (raw.length() == 0) { if (last != null) throw last; return ""; }
        String s = raw == null ? "" : raw.trim();
        JSONArray data = s.startsWith("[") ? new JSONArray(s) : new JSONObject(s).optJSONArray("data");
        if (data == null || data.length() == 0) return "";
        String first = "", preferred = "";
        for (int i = 0; i < data.length(); i++) {
            JSONObject m = data.optJSONObject(i);
            String id = m == null ? data.optString(i, "") : m.optString("id", m.optString("name", ""));
            if (id.length() == 0) continue;
            if (first.length() == 0) first = id;
            String hay = (id + " " + (m == null ? "" : m.optString("name", "")) + " " + (m == null ? "" : m.optString("description", ""))).toLowerCase(Locale.US);
            if (m != null && hasSpeedParameter(m, id, m.optString("name", ""), m.optString("description", ""))) { speedModels.add(id); speedModels.add("endpoint:" + endpoint); saveSpeedModels(); }
            if (hay.contains("tts") || hay.contains("speech") || hay.contains("kokoro") || hay.contains("orpheus")) { preferred = id; break; }
        }
        return preferred.length() > 0 ? preferred : (data.length() == 1 ? first : "");
    }

    private ArrayList<String> detectVoices(String endpoint) throws Exception {
        String[] paths = new String[]{"/v1/audio/voices", "/audio/voices", "/v1/voices", "/voices"};
        Exception last = null;
        for (String path : paths) {
            try {
                ArrayList<String> voices = parseVoices(httpGet(endpoint + path));
                if (voices.size() > 0) return voices;
            } catch (Exception e) { last = e; }
        }
        if (last != null) throw last;
        return new ArrayList<String>();
    }

    private String httpGet(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(3500);
        c.setReadTimeout(6000);
        String key = savedApiKey();
        if (url.toLowerCase(Locale.US).contains("openrouter.ai") && key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new RuntimeException(raw);
        return raw;
    }

    private ArrayList<String> parseVoices(String raw) throws Exception {
        ArrayList<String> out = new ArrayList<String>();
        String s = raw == null ? "" : raw.trim();
        if (s.length() == 0) return out;
        if (s.startsWith("[")) addVoicesFromArray(out, new JSONArray(s));
        else if (s.startsWith("{")) {
            JSONObject o = new JSONObject(s);
            JSONArray arr = o.optJSONArray("voices");
            if (arr == null) arr = o.optJSONArray("data");
            if (arr == null) arr = o.optJSONArray("items");
            if (arr != null) addVoicesFromArray(out, arr);
        } else for (String v : s.split("[\\r\\n,]+")) addVoice(out, v.trim());
        return out;
    }

    private void addVoicesFromArray(ArrayList<String> out, JSONArray arr) {
        for (int i = 0; i < arr.length(); i++) {
            JSONObject o = arr.optJSONObject(i);
            if (o == null) addVoice(out, arr.optString(i, ""));
            else addVoice(out, o.optString("id", o.optString("name", o.optString("voice", ""))));
        }
    }

    private void addVoice(ArrayList<String> out, String voice) { String v = voice == null ? "" : voice.trim(); if (v.length() > 0 && !out.contains(v)) out.add(v); }

    private String endpointLabel(String endpoint) {
        String clean = normalizeEndpoint(endpoint);
        if (clean.length() == 0) return customEndpointBase().length() == 0 ? "none" : "default: " + shortEndpoint(customEndpointBase());
        return shortEndpoint(clean);
    }

    private String shortEndpoint(String endpoint) {
        String clean = normalizeEndpoint(endpoint);
        if (clean.length() <= 28) return clean;
        return "..." + clean.substring(clean.length() - 25);
    }

    private void chooseSavedEndpoint(final String pref) {
        if (customEndpoints.size() == 0) { toast("add endpoint first"); return; }
        final Dialog d = panel("endpoint");
        LinearLayout box = panelBox();
        box.addView(panelTitle("endpoint"));
        TextView def = panelItem("default", "first saved endpoint");
        def.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { prefs.edit().putString(pref, "").apply(); d.dismiss(); if ("voiceTtsEndpoint".equals(pref)) discoverVoiceEndpoint(voiceTtsEndpoint()); showSettingsPane(); } });
        box.addView(def);
        for (int i = 1; i < customEndpoints.size(); i++) {
            final String endpoint = customEndpoints.get(i);
            TextView item = panelItem(shortEndpoint(endpoint), endpoint);
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { prefs.edit().putString(pref, endpoint).apply(); d.dismiss(); if ("voiceTtsEndpoint".equals(pref)) discoverVoiceEndpoint(endpoint); showSettingsPane(); } });
            box.addView(item);
        }
        TextView cancel = panelAction("cancel");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(52)));
        showPanel(d, box);
    }

    private void chooseVoiceModel(final String pref, String provider, final String fallback) {
        final Dialog d = panel("model");
        LinearLayout box = panelBox();
        box.addView(panelTitle("model"));
        ArrayList<String> choices = voiceModelChoices(provider);
        if (!choices.contains(fallback)) choices.add(0, fallback);
        for (int i = 0; i < choices.size(); i++) {
            final String model = choices.get(i);
            TextView item = panelItem(shortModel(model), modelSourceLabel(model));
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { prefs.edit().putString(pref, model).apply(); d.dismiss(); showSettingsPane(); } });
            box.addView(item);
        }
        TextView custom = panelItem("custom", "type model name");
        custom.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); promptVoiceCustom(pref, fallback); } });
        TextView cancel = panelAction("cancel");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(custom);
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(52)));
        showPanel(d, box);
    }

    private ArrayList<String> voiceModelChoices(String provider) {
        ArrayList<String> out = new ArrayList<String>();
        if ("openrouter".equals(provider)) { for (String m : models) if (!"custom".equals(modelSources.get(m))) out.add(m); }
        else if ("endpoint".equals(provider)) { for (String model : models) if ("custom".equals(modelSources.get(model))) out.add(model); }
        return out;
    }

    private void chooseTtsModel() {
        final Dialog d = panel("tts model");
        LinearLayout box = panelBox();
        box.addView(panelTitle("tts model"));
        final EditText search = panelEdit("search or type");
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        final TextView status = text("loading openrouter voice models...", 11, Color.rgb(130,130,130));
        status.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView s = new ScrollView(this);
        s.setVerticalScrollBarEnabled(false);
        s.addView(list);
        box.addView(s, new LinearLayout.LayoutParams(-1, 0, 1));
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() { @Override public void run() {
            list.removeAllViews();
            final String typed = search.getText().toString().trim();
            String q = typed.toLowerCase(Locale.US);
            ArrayList<String> shown = new ArrayList<String>();
            addKnownTtsModels(list, d, shown, q);
            for (String m : models) if (!"custom".equals(modelSources.get(m)) && isTtsModel(m) && (q.length() == 0 || modelMatches(m, q) || isAudioSearch(q))) addTtsChoice(list, d, shown, m);
            if (q.length() > 0) for (String m : models) if (!"custom".equals(modelSources.get(m)) && isTtsModel(m) && modelMatches(m, q)) addTtsChoice(list, d, shown, m);
            if (typed.length() > 1 && !isAudioSearch(q) && !shown.contains(typed)) addTtsChoice(list, d, shown, typed);
            status.setText(shown.size() + " shown. type to search all openrouter models");
        } };
        search.addTextChangedListener(new TextWatcher() { @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { } @Override public void onTextChanged(CharSequence s, int st, int b, int c) { render[0].run(); } @Override public void afterTextChanged(Editable e) { } });
        render[0].run();
        showFullPanel(d, box);
        loadOpenRouterTtsModels(render[0], status);
        search.requestFocus();
        search.postDelayed(new Runnable() { @Override public void run() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT); } }, 200);
    }

    private void chooseTranscriptionModel() {
        final Dialog d = panel("input model");
        LinearLayout box = panelBox();
        box.addView(panelTitle("input model"));
        final EditText search = panelEdit("search or type");
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        final TextView status = text("loading openrouter models...", 11, Color.rgb(130,130,130));
        status.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView s = new ScrollView(this);
        s.setVerticalScrollBarEnabled(false);
        s.addView(list);
        box.addView(s, new LinearLayout.LayoutParams(-1, 0, 1));
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() { @Override public void run() {
            list.removeAllViews();
            final String typed = search.getText().toString().trim();
            String q = typed.toLowerCase(Locale.US);
            ArrayList<String> shown = new ArrayList<String>();
            addTranscriptionChoice(list, d, shown, "whisper-1");
            addTranscriptionChoice(list, d, shown, "openai/whisper-1");
            for (String m : models) if (!"custom".equals(modelSources.get(m)) && isTranscriptionModel(m) && (q.length() == 0 || modelMatches(m, q) || isTranscriptionSearch(q))) addTranscriptionChoice(list, d, shown, m);
            if (q.length() > 0) for (String m : models) if (!"custom".equals(modelSources.get(m)) && modelMatches(m, q) && (isTranscriptionModel(m) || audioInputModels.contains(m))) addTranscriptionChoice(list, d, shown, m);
            if (typed.length() > 1 && !isTranscriptionSearch(q) && !shown.contains(typed)) addTranscriptionChoice(list, d, shown, typed);
            status.setText(shown.size() + " shown. voice-agent input falls back to whisper unless all-in-one");
        } };
        search.addTextChangedListener(new TextWatcher() { @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { } @Override public void onTextChanged(CharSequence s, int st, int b, int c) { render[0].run(); } @Override public void afterTextChanged(Editable e) { } });
        render[0].run();
        showFullPanel(d, box);
        loadOpenRouterVoiceModels(render[0], status, "input");
        search.requestFocus();
        search.postDelayed(new Runnable() { @Override public void run() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT); } }, 200);
    }

    private void chooseVoiceAnswerModel() {
        final Dialog d = panel("answer model");
        LinearLayout box = panelBox();
        box.addView(panelTitle("answer model"));
        TextView same = panelItem("same as chat", selectedModel().length() == 0 ? "no chat model selected" : shortModel(selectedModel()));
        same.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { prefs.edit().remove("voiceAnswerModel").apply(); d.dismiss(); showSettingsPane(); } });
        box.addView(same);
        final EditText search = panelEdit("search or type");
        search.setSingleLine(true);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        box.addView(search, new LinearLayout.LayoutParams(-1, dp(52)));
        final TextView status = text("loading openrouter models...", 11, Color.rgb(130,130,130));
        status.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(status, new LinearLayout.LayoutParams(-1, dp(28)));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView s = new ScrollView(this);
        s.setVerticalScrollBarEnabled(false);
        s.addView(list);
        box.addView(s, new LinearLayout.LayoutParams(-1, 0, 1));
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() { @Override public void run() {
            list.removeAllViews();
            String q = search.getText().toString().trim().toLowerCase(Locale.US);
            final ArrayList<String> shownModels = new ArrayList<String>();
            int shown = 0;
            for (final String m : models) if ("custom".equals(modelSources.get(m)) && (q.length() == 0 || modelMatches(m, q))) { addAnswerChoice(list, d, shownModels, m); shown++; if (shown >= 50) break; }
            for (final String m : myModels) if (shown < 50 && (q.length() == 0 || modelMatches(m, q))) { addAnswerChoice(list, d, shownModels, m); shown = shownModels.size(); }
            for (final String m : models) if (shown < 50 && !"custom".equals(modelSources.get(m)) && (q.length() == 0 || modelMatches(m, q))) { addAnswerChoice(list, d, shownModels, m); shown = shownModels.size(); }
            final String typed = search.getText().toString().trim();
            if (typed.length() > 1 && !models.contains(typed)) {
                TextView item = searchResultItem("use " + shortModel(typed), customEndpointBase().length() > 0 ? "endpoint" : "openrouter");
                item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (!models.contains(typed)) { models.add(0, typed); modelSources.put(typed, customEndpointBase().length() > 0 ? "custom" : "openrouter"); if (customEndpointBase().length() > 0) modelEndpoints.put(typed, customEndpointBase()); saveModels(); saveModelSources(); saveModelEndpoints(); } prefs.edit().putString("voiceAnswerModel", typed).apply(); d.dismiss(); showSettingsPane(); } });
                list.addView(item); shown++;
            }
            status.setText((shownModels.size() + (typed.length() > 1 && !models.contains(typed) ? 1 : 0)) + " shown. applies only to two-way voice");
        } };
        search.addTextChangedListener(new TextWatcher() { @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { } @Override public void onTextChanged(CharSequence s, int st, int b, int c) { render[0].run(); } @Override public void afterTextChanged(Editable e) { } });
        render[0].run();
        showFullPanel(d, box);
        loadOpenRouterVoiceModels(render[0], status, "answer");
        search.requestFocus();
        search.postDelayed(new Runnable() { @Override public void run() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT); } }, 200);
    }

    private void addAnswerChoice(LinearLayout list, final Dialog d, ArrayList<String> shown, final String model) {
        if (model == null || model.length() == 0 || shown.contains(model)) return;
        shown.add(model);
        TextView item = searchResultItem(shortModel(model), answerModelSubtitle(model));
        item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { prefs.edit().putString("voiceAnswerModel", model).apply(); d.dismiss(); showSettingsPane(); } });
        list.addView(item);
    }

    private String answerModelSubtitle(String model) {
        ArrayList<String> tags = new ArrayList<String>();
        if ("custom".equals(modelSources.get(model))) tags.add("endpoint");
        if (audioInputModels.contains(model)) tags.add("audio in");
        if (audioOutputModels.contains(model)) tags.add("audio out");
        if (isReasoningModel(model)) tags.add("reasoning");
        if (tags.size() == 0) return "answer only";
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < tags.size(); i++) { if (i > 0) b.append(" / "); b.append(tags.get(i)); }
        return b.toString();
    }

    private void addTranscriptionChoice(LinearLayout list, final Dialog d, ArrayList<String> shown, final String model) {
        if (shown.contains(model)) return;
        shown.add(model);
        TextView item = searchResultItem(shortModel(model), transcriptionModelSubtitle(model));
        item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { prefs.edit().putString("voiceTranscribeModel", model).apply(); d.dismiss(); showSettingsPane(); } });
        list.addView(item);
    }

    private String transcriptionModelSubtitle(String model) {
        if (isTranscriptionModel(model)) return model;
        if (audioInputModels.contains(model)) return "audio-input chat model; modular mode uses whisper fallback";
        return model;
    }

    private boolean isTranscriptionModel(String model) {
        String m = model.toLowerCase(Locale.US);
        String meta = modelSearchText.containsKey(model) ? modelSearchText.get(model).toLowerCase(Locale.US) : "";
        return m.contains("whisper") || m.contains("transcrib") || m.contains("speech-to-text") || m.contains("stt") || meta.contains("transcrib") || meta.contains("speech-to-text") || meta.contains("stt") || meta.contains("audio input") || meta.contains("audio-input");
    }

    private boolean isTranscriptionSearch(String q) {
        String clean = q == null ? "" : q.toLowerCase(Locale.US).trim();
        return clean.equals("stt") || clean.equals("asr") || clean.equals("whisper") || clean.equals("transcribe") || clean.equals("transcription") || clean.equals("speech") || clean.equals("audio");
    }

    private void loadOpenRouterTtsModels(final Runnable render, final TextView status) { loadOpenRouterVoiceModels(render, status, "tts"); }

    private void loadOpenRouterVoiceModels(final Runnable render, final TextView status, final String label) {
        final String key = savedApiKey();
        if (key.length() == 0) { status.setText("openrouter key missing; showing cached/common models"); return; }
        status.setText("loading openrouter models...");
        new Thread(new Runnable() { @Override public void run() {
            try {
                final ArrayList<String> found = new ArrayList<String>();
                final HashMap<String, Integer> contexts = new HashMap<String, Integer>();
                final HashMap<String, String> sources = new HashMap<String, String>();
                final HashMap<String, String> endpoints = new HashMap<String, String>();
                final HashSet<String> audio = new HashSet<String>();
                final HashSet<String> audioIn = new HashSet<String>();
                final HashSet<String> reasoning = new HashSet<String>();
                final HashSet<String> speed = new HashSet<String>();
                fetchModelsInto(OPENROUTER_ENDPOINT, key, "openrouter", found, contexts, sources, endpoints, audio, audioIn, reasoning, speed);
                runOnUiThread(new Runnable() { @Override public void run() {
                    for (String m : found) if (!models.contains(m)) models.add(m);
                    modelContexts.putAll(contexts);
                    modelSources.putAll(sources);
                    audioOutputModels.addAll(audio);
                    audioInputModels.addAll(audioIn);
                    reasoningModels.addAll(reasoning);
                    speedModels.addAll(speed);
                    saveModels(); saveModelContexts(); saveModelSources(); saveAudioOutputModels(); saveAudioInputModels(); saveReasoningModels(); saveSpeedModels();
                    status.setText("loaded " + found.size() + " openrouter models for " + label);
                    render.run();
                } });
            } catch (Exception e) { final String msg = friendlyError(e); runOnUiThread(new Runnable() { @Override public void run() { status.setText("openrouter load failed: " + msg); toast(label + " search failed: " + msg); } }); }
        } }).start();
    }

    private void addTtsChoice(LinearLayout list, final Dialog d, ArrayList<String> shown, final String model) {
        if (shown.contains(model)) return;
        shown.add(model);
        TextView item = searchResultItem(ttsModelTitle(model), model);
        item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { String selected = openRouterTtsModel(model); prefs.edit().putString("voiceTtsModel", selected).putString("voiceTtsVoice", defaultVoiceForModel(selected)).apply(); d.dismiss(); showSettingsPane(); } });
        list.addView(item);
    }

    private void addKnownTtsModels(LinearLayout list, Dialog d, ArrayList<String> shown, String q) {
        addKnownTtsModel(list, d, shown, q, "canopylabs/orpheus-3b-0.1-ft", "canopy labs: orpheus 3b", "english text-to-speech natural prosody voice assistant narration");
        addKnownTtsModel(list, d, shown, q, "openai/gpt-4o-mini-tts", "openai: gpt-4o mini tts", "text-to-speech voice tts");
        addKnownTtsModel(list, d, shown, q, "voxtral-mini-tts-2603", "mistral: voxtral mini tts", "mistral endpoint text-to-speech voice_id tts");
        addKnownTtsModel(list, d, shown, q, "google/gemini-3.1-flash-tts-preview", "google: gemini 3.1 flash tts preview", "text-to-speech voice tts");
        addKnownTtsModel(list, d, shown, q, "openai/gpt-audio", "openai: gpt audio", "chat completions audio output voice tts");
        addKnownTtsModel(list, d, shown, q, "openai/gpt-audio-mini", "openai: gpt audio mini", "chat completions audio output voice tts");
        addKnownTtsModel(list, d, shown, q, "openai/gpt-4o-audio-preview", "openai: gpt-4o audio", "chat completions audio output voice tts");
    }

    private void addKnownTtsModel(LinearLayout list, Dialog d, ArrayList<String> shown, String q, String id, String title, String desc) {
        modelSearchText.put(id, title + "\n" + id + "\n" + desc);
        audioOutputModels.add(id);
        modelSources.put(id, "openrouter");
        if (!models.contains(id)) models.add(id);
        if (q.length() == 0 || modelMatches(id, q) || isAudioSearch(q)) addTtsChoice(list, d, shown, id);
    }

    private boolean isTtsModel(String model) {
        String m = model.toLowerCase(Locale.US);
        String meta = modelSearchText.containsKey(model) ? modelSearchText.get(model).toLowerCase(Locale.US) : "";
        return audioOutputModels.contains(model) || m.contains("tts") || m.contains("text-to-speech") || m.contains("lyria") || meta.contains("text-to-speech") || meta.contains("tts") || meta.contains("voice assistant");
    }

    private String ttsModelTitle(String model) {
        String meta = modelSearchText.get(model);
        if (meta != null && meta.length() > 0) {
            int cut = meta.indexOf('\n');
            if (cut > 0) return meta.substring(0, cut);
        }
        return shortModel(model).toLowerCase(Locale.US);
    }

    private boolean isAudioSearch(String q) {
        String clean = q == null ? "" : q.toLowerCase(Locale.US).trim();
        return clean.equals("tts") || clean.equals("speech") || clean.equals("voice") || clean.equals("audio") || clean.equals("speak");
    }

    private String openRouterTtsModel(String model) {
        String m = model == null ? "" : model.trim();
        if (m.length() == 0 || "choose".equalsIgnoreCase(m)) return "";
        String n = m.toLowerCase(Locale.US).replace(' ', '-');
        if ("tts-1".equals(n) || "openai/tts-1".equals(n)) return "openai/tts-1";
        if ("tts-1-hd".equals(n) || "openai/tts-1-hd".equals(n)) return "openai/tts-1-hd";
        if ("gpt-4o-mini-tts".equals(n) || "openai/gpt-4o-mini-tts".equals(n)) return "openai/gpt-4o-mini-tts";
        if ("mistralai/voxtral-mini-tts".equals(n) || "mistralai/voxtral-mini-tts-2603".equals(n)) return "voxtral-mini-tts-2603";
        return m;
    }

    private void promptVoiceCustom(final String pref, String hint) {
        final Dialog d = panel("custom");
        LinearLayout box = panelBox();
        box.addView(panelTitle("custom model"));
        final EditText e = panelEdit(hint);
        box.addView(e, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout actions = row();
        TextView cancel = panelAction("cancel");
        TextView save = panelAction("save");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { String value = e.getText().toString().trim(); if (value.length() > 0) prefs.edit().putString(pref, value).apply(); d.dismiss(); showSettingsPane(); } });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(52), 1));
        box.addView(actions);
        showPanel(d, box);
    }

    private void chooseVoiceName() {
        final Dialog d = panel("voice");
        LinearLayout box = panelBox();
        box.addView(panelTitle("voice"));
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView scroll = new ScrollView(this);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.addView(list);
        String[] voices = voiceNamesForModel(currentVoiceOutputModel());
        for (int i = 0; i < voices.length; i++) {
            final String voice = voices[i];
            TextView item = panelItem(voice, "");
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { prefs.edit().putString("voiceTtsVoice", voice).apply(); d.dismiss(); showSettingsPane(); } });
            list.addView(item);
        }
        TextView custom = panelItem("custom", "type voice name");
        custom.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); promptVoiceCustom("voiceTtsVoice", "alloy"); } });
        list.addView(custom);
        box.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));
        showFullPanel(d, box);
    }

    private String[] voiceNamesForModel(String model) {
        String lower = openRouterTtsModel(model).toLowerCase(Locale.US);
        ArrayList<String> discovered = discoveredVoiceList(currentVoiceDiscoveryKey(model));
        if (discovered.size() > 0) return discovered.toArray(new String[0]);
        if (lower.contains("kokoro")) return new String[]{"af_heart", "af_alloy", "af_aoede", "af_bella", "af_jessica", "af_kore", "af_nicole", "af_nova", "af_river", "af_sarah", "af_sky", "am_adam", "am_echo", "am_eric", "am_fenrir", "am_liam", "am_michael", "am_onyx", "am_puck", "am_santa", "bf_alice", "bf_emma", "bf_isabella", "bf_lily", "bm_daniel", "bm_fable", "bm_george", "bm_lewis"};
        if (lower.contains("orpheus")) return new String[]{"tara", "leah", "jess", "leo", "dan", "mia", "zac", "zoe"};
        if (lower.contains("voxtral")) { String saved = prefs.getString("voiceTtsVoice", "voice_id"); return (saved.length() > 0 && !"alloy".equals(saved)) ? new String[]{saved, "voice_id"} : new String[]{"voice_id"}; }
        if (lower.contains("gemini")) return new String[]{"Zephyr", "Puck", "Charon", "Kore", "Fenrir", "Leda", "Orus", "Aoede", "Callirrhoe", "Autonoe", "Enceladus", "Iapetus", "Umbriel", "Algieba", "Despina", "Erinome", "Algenib", "Rasalgethi", "Laomedeia", "Achernar", "Alnilam", "Schedar", "Gacrux", "Pulcherrima", "Achird", "Zubenelgenubi", "Vindemiatrix", "Sadachbia", "Sadaltager", "Sulafat"};
        if (lower.contains("openai") || lower.contains("gpt-4o") || lower.contains("tts-1")) return new String[]{"alloy", "ash", "ballad", "coral", "echo", "fable", "nova", "onyx", "sage", "shimmer", "verse"};
        return new String[]{"alloy", "tara", "leah", "jess", "leo", "dan", "mia", "zac", "zoe", "aria", "breeze", "cove", "ember", "juniper", "maple", "sol", "spruce", "vale"};
    }

    private String currentVoiceOutputModel() { return "endpoint".equals(voiceOutputProvider()) ? prefs.getString("voiceEndpointTtsModel", "") : prefs.getString("voiceTtsModel", ""); }
    private String currentVoiceDiscoveryKey(String model) { return "endpoint".equals(voiceOutputProvider()) ? "endpoint:" + voiceTtsEndpoint() : "model:" + openRouterTtsModel(model); }
    private ArrayList<String> discoveredVoiceList(String key) { ArrayList<String> voices = discoveredVoices.get(key); return voices == null ? new ArrayList<String>() : new ArrayList<String>(voices); }

    private void addVoiceSpeedSetting(LinearLayout settings) {
        if (!supportsVoiceSpeed(currentVoiceOutputModel())) return;
        LinearLayout row = row();
        row.setPadding(0, dp(4), 0, dp(2));
        row.addView(text("speed", 14, Color.WHITE), new LinearLayout.LayoutParams(0, dp(34), 1));
        final TextView value = text(String.format(Locale.US, "%.1fx", voiceSpeed()), 13, Color.LTGRAY);
        value.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        row.addView(value, new LinearLayout.LayoutParams(dp(70), dp(34)));
        settings.addView(row);
        SeekBar bar = new SeekBar(this);
        bar.setMax(70);
        bar.setProgress(Math.max(0, Math.min(70, Math.round((voiceSpeed() - 0.8f) * 100f))));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) { float s = 0.8f + progress / 100f; value.setText(String.format(Locale.US, "%.1fx", s)); if (fromUser) prefs.edit().putFloat("voiceSpeed", s).apply(); }
            @Override public void onStartTrackingTouch(SeekBar seekBar) { }
            @Override public void onStopTrackingTouch(SeekBar seekBar) { prefs.edit().putFloat("voiceSpeed", 0.8f + seekBar.getProgress() / 100f).apply(); }
        });
        settings.addView(bar, new LinearLayout.LayoutParams(-1, dp(34)));
    }

    private float voiceSpeed() { float s = prefs.getFloat("voiceSpeed", 1.0f); return Math.max(0.8f, Math.min(1.5f, s)); }

    private boolean supportsVoiceSpeed(String model) {
        String m = model == null ? "" : openRouterTtsModel(model).toLowerCase(Locale.US);
        if (speedModels.contains(model) || speedModels.contains(openRouterTtsModel(model)) || speedModels.contains("endpoint:" + voiceTtsEndpoint())) return true;
        if (m.contains("kokoro") || m.contains("tts-1") || m.contains("gpt-4o-mini-tts")) return true;
        return "endpoint".equals(voiceOutputProvider()) && m.length() == 0 && discoveredVoiceList("endpoint:" + voiceTtsEndpoint()).size() > 0;
    }

    private String defaultVoiceForModel(String model) {
        String[] voices = voiceNamesForModel(model);
        return voices.length == 0 ? "alloy" : voices[0];
    }

    private void addDisplaySettings(LinearLayout settings) {
        LinearLayout awake = row();
        awake.addView(text("keep screen awake", 14, Color.WHITE), new LinearLayout.LayoutParams(0, dp(38), 1));
        final TogglePill awakeToggle = new TogglePill(this);
        awakeToggle.checked = prefs.getBoolean("keepScreenAwake", false);
        awakeToggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { boolean next = !prefs.getBoolean("keepScreenAwake", false); prefs.edit().putBoolean("keepScreenAwake", next).apply(); applyKeepScreenAwake(); awakeToggle.checked = next; awakeToggle.invalidate(); } });
        awake.addView(awakeToggle, new LinearLayout.LayoutParams(dp(48), dp(28)));
        settings.addView(awake);

        LinearLayout font = row();
        font.addView(text("font size", 14, Color.WHITE), new LinearLayout.LayoutParams(0, dp(38), 1));
        TextView minus = text("-", 18, Color.WHITE);
        minus.setGravity(Gravity.CENTER);
        final TextView value = text(String.valueOf(fontOffset()), 14, Color.LTGRAY);
        value.setGravity(Gravity.CENTER);
        TextView plus = text("+", 18, Color.WHITE);
        plus.setGravity(Gravity.CENTER);
        minus.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setFontOffset(fontOffset() - 1); showSettingsPane(); } });
        plus.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setFontOffset(fontOffset() + 1); showSettingsPane(); } });
        font.addView(minus, new LinearLayout.LayoutParams(dp(38), dp(38)));
        font.addView(value, new LinearLayout.LayoutParams(dp(52), dp(38)));
        font.addView(plus, new LinearLayout.LayoutParams(dp(38), dp(38)));
        settings.addView(font);
        settings.addView(space(8));
    }

    private void showChatsPane() {
        projectEditorOpen = false;
        saveCurrentChat();
        reloadChatsFromPrefs();
        pane = 0;
        renderingMessages = false;
        forceAutoScrollBottom = false;
        restoreScrollOnce = false;
        captureChatScroll();
        clearPaneViews();
        root.setOnClickListener(null);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("chats", 21, Color.WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (projectView.length() > 0) { projectView = ""; showChatsPane(); } } });
        top.addView(title, new LinearLayout.LayoutParams(-2, dp(38)));
        if (projectView.length() > 0) {
            TextView sub = text("  " + projectView, 13, Color.rgb(150,150,150));
            sub.setGravity(Gravity.CENTER_VERTICAL);
            sub.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { projectView = ""; showChatsPane(); } });
            top.addView(sub, new LinearLayout.LayoutParams(0, dp(38), 1));
        } else top.addView(space(1), new LinearLayout.LayoutParams(0, dp(38), 1));
        ImageButton fresh = iconButton(R.drawable.ic_chat_plus, new View.OnClickListener() { @Override public void onClick(View v) { newChat(); pane = 1; renderPane(); } }, 6);
        top.addView(fresh, new LinearLayout.LayoutParams(dp(42), dp(34)));
        if (projectView.length() > 0) top.addView(iconButton(R.drawable.ic_pencil, new View.OnClickListener() { @Override public void onClick(View v) { editFolder(projectView); } }, 8), new LinearLayout.LayoutParams(dp(42), dp(34)));
        else top.addView(iconButton(R.drawable.ic_folder_plus, new View.OnClickListener() { @Override public void onClick(View v) { addFolder(); } }, 6), new LinearLayout.LayoutParams(dp(42), dp(34)));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(40)));
        ScrollView s = new ScrollView(this);
        s.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        chatList = new LinearLayout(this); chatList.setOrientation(LinearLayout.VERTICAL);
        chatList.setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        s.addView(chatList);
        s.setVerticalScrollBarEnabled(false);
        root.addView(s, new LinearLayout.LayoutParams(-1, 0, 1));
        renderChatList();
    }

    private void renderChatList() {
        chatList.removeAllViews();
        chatList.setPadding(0, dp(3), 0, dp(14));
        if (projectView.length() > 0) {
            boolean hasProjectChats = false;
            for (final Chat c : chats) if (projectView.equals(c.folder)) { hasProjectChats = true; chatList.addView(chatCard(c, false)); }
            if (!hasProjectChats) chatList.addView(emptyLine("no chats yet"));
            updateBulkButton();
            return;
        }
        chatList.addView(sectionHeader("projects  " + realFolderCount()));
        boolean hasFolders = false;
        for (final String f : folders) if (!"Inbox".equals(f)) {
            hasFolders = true;
            chatList.addView(folderCard(f));
        }
        if (!hasFolders) chatList.addView(emptyLine("no projects"));
        chatList.addView(space(10));
        chatList.addView(sectionHeader("general  " + folderCount("Inbox")));
        boolean hasInbox = false;
        for (final Chat c : chats) if ("Inbox".equals(c.folder)) { hasInbox = true; chatList.addView(chatCard(c, false)); }
        if (!hasInbox) chatList.addView(emptyLine("no chats yet"));
        updateBulkButton();
    }

    private View folderCard(final String folder) {
        LinearLayout card = row();
        card.setPadding(dp(10), 0, dp(6), 0);
        card.setBackground(cardBorder());
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { projectView = folder; showChatsPane(); } });
        TextView name = cardText(folder, 14, Color.WHITE);
        name.setGravity(Gravity.CENTER_VERTICAL);
        TextView count = cardText(folderCount(folder) + " chats", 11, Color.LTGRAY);
        count.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        TextView dots = cardText("...", 13, Color.LTGRAY);
        dots.setGravity(Gravity.CENTER);
        dots.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { folderActions(folder); } });
        card.addView(name, new LinearLayout.LayoutParams(0, dp(40), 1));
        card.addView(count, new LinearLayout.LayoutParams(dp(68), dp(40)));
        card.addView(dots, new LinearLayout.LayoutParams(dp(30), dp(40)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(40));
        lp.setMargins(0, 0, 0, dp(4));
        card.setLayoutParams(lp);
        return card;
    }

    private View chatCard(final Chat c, boolean nested) {
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setBackgroundColor(Color.BLACK);
        if (nested) wrap.setPadding(dp(10), 0, 0, 0);
        LinearLayout card = row();
        card.setPadding(dp(10), dp(4), dp(2), dp(4));
        card.setBackground(selectedChats.contains(c.id) ? selectedBorder() : cardBorder());
        card.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { toggleChatSelection(c); return true; } });
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (selectedChats.size() > 0) toggleChatSelection(c); else openChatFromList(c); } });
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);
        textCol.setBackgroundColor(Color.TRANSPARENT);
        TextView name = cardText((selectedChats.contains(c.id) ? "✓ " : "") + chatListTitle(c), 13, Color.WHITE);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView preview = cardText(chatPreview(c), 10, Color.rgb(145,145,145));
        preview.setSingleLine(true);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(name, new LinearLayout.LayoutParams(-1, dp(22)));
        textCol.addView(preview, new LinearLayout.LayoutParams(-1, dp(17)));
        TextView dots = cardText("...", 13, Color.LTGRAY);
        dots.setGravity(Gravity.CENTER);
        dots.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chatActions(c); } });
        card.addView(textCol, new LinearLayout.LayoutParams(0, dp(47), 1));
        card.addView(dots, new LinearLayout.LayoutParams(dp(30), dp(47)));
        wrap.addView(card, new LinearLayout.LayoutParams(-1, dp(53)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(57));
        lp.setMargins(0, 0, 0, dp(4));
        wrap.setLayoutParams(lp);
        return wrap;
    }

    private TextView sectionHeader(String s) {
        TextView v = text(s, 11, Color.rgb(140,140,140));
        v.setPadding(0, dp(4), 0, dp(5));
        return v;
    }

    private TextView emptyLine(String s) {
        TextView v = text(s, 13, Color.rgb(115,115,115));
        v.setPadding(0, dp(6), 0, dp(14));
        return v;
    }

    private int folderCount(String folder) { int n = 0; for (Chat c : chats) if (folder.equals(c.folder)) n++; return n; }
    private int realFolderCount() { int n = 0; for (String f : folders) if (!"Inbox".equals(f)) n++; return n; }
    private String chatListTitle(Chat c) { String title = c.title.length() == 0 ? "untitled" : c.title; return chatIsLoading(c) ? title + "..." : title; }

    private void openChatFromList(final Chat c) {
        if (chatList != null) chatList.setEnabled(false);
        ui.post(new Runnable() { @Override public void run() {
            loadChat(c);
            pane = 1;
            renderPane();
        } });
    }

    private String chatPreview(Chat c) {
        for (int i = c.messages.size() - 1; i >= 0; i--) {
            Msg m = c.messages.get(i);
            if (m.text == null || m.text.trim().length() == 0 || LOADING.equals(m.stats)) continue;
            String text = chatPreviewText(m);
            if (text.length() > 72) text = text.substring(0, 72) + "...";
            return (m.role.equals("user") ? "you: " : "ai: ") + text;
        }
        return "empty chat";
    }

    private String chatPreviewText(Msg m) {
        String s = cleanSearchArtifacts(m.text == null ? "" : m.text).trim();
        int artifacts = 0;
        if (m.imageBase64 != null && m.imageBase64.length() > 0) artifacts++;
        if (s.contains("```")) artifacts++;
        String[] lines = s.replace("\r", "").split("\n");
        for (String line : lines) if (isTableLine(line)) { artifacts++; break; }
        s = s.replaceAll("```[\\s\\S]*?```", " ");
        s = s.replace('\n', ' ').trim();
        s = s.replaceAll("`([^`]+)`", "$1");
        s = s.replaceAll("\\*\\*\\*([^*]+)\\*\\*\\*", "$1");
        s = s.replaceAll("\\*\\*([^*]+)\\*\\*", "$1");
        s = s.replaceAll("\\*([^*]+)\\*", "$1");
        s = s.replaceAll("_([^_]+)_", "$1");
        s = s.replaceAll("~~([^~]+)~~", "$1");
        s = s.replaceAll("\\[([^\\]]+)\\]\\(([^)]+)\\)", "$1");
        s = s.replaceAll("^#+\\s*", "");
        s = s.replaceAll("\\s+", " ").trim();
        if (artifacts > 0) return (s.length() > 0 ? s + "  " : "") + artifacts + " artifact" + (artifacts == 1 ? "" : "s");
        return s;
    }
    private boolean chatIsLoading(Chat c) { for (Msg m : c.messages) if (LOADING.equals(m.stats)) return true; if (c.id.equals(currentChatId)) for (Msg m : messages) if (LOADING.equals(m.stats)) return true; return false; }

    private void toggleChatSelection(Chat c) {
        if (selectedChats.contains(c.id)) selectedChats.remove(c.id); else selectedChats.add(c.id);
        renderChatList();
        updateBulkButton();
    }

    private void updateBulkButton() {
        removeScreenChild(bulkButton); bulkButton = null;
        if (selectedChats.size() == 0 || pane != 0) return;
        bulkButton = text("...", 15, Color.WHITE);
        bulkButton.setGravity(Gravity.CENTER);
        bulkButton.setBackground(grayBorder());
        bulkButton.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showBulkActions(); } });
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(34), dp(28), Gravity.RIGHT | Gravity.TOP);
        lp.setMargins(0, dp(98), dp(18), 0);
        screen.addView(bulkButton, lp);
    }

    private void captureChatScroll() {
        if (scroll != null) {
            savedChatScrollY = scroll.getScrollY();
            savedChatScrollKnown = true;
        }
    }

    private void renderMessages() {
        if (messageList == null) return;
        if (!messageWindowReady) resetMessageWindowToLatest();
        clampMessageWindow();
        final boolean showBottom = messageEnd >= messages.size();
        renderingMessages = true;
        messageList.removeAllViews();
        if (messages.isEmpty()) {
            showEmptyPrompt();
        }
        if (messageStart > 0) messageList.addView(windowMarker("older messages above"));
        for (int i = messageStart; i < messageEnd; i++) {
            Msg m = messages.get(i);
            final boolean hasThinkingRow = m.role.equals("assistant") && ((m.reasoning.length() > 0 && !m.reasoning.trim().equals("null")) || (LOADING.equals(m.stats) && m.reasoningCapable));
            final boolean hasSearchRow = m.role.equals("assistant") && m.searchSources.size() > 0;
            final boolean hasMemoryRow = m.role.equals("assistant") && m.streamDone && m.memorySaved;
            TextView role = text(m.role.equals("user") ? "you" : messageModelLabel(m), 11, Color.LTGRAY);
            TextView body = text("", 16, Color.WHITE);
            body.setText(markdownText(m.text + (m.imageBase64.length() == 0 ? "" : "\n[image attached]")));
            body.setLineSpacing(dp(2), 1.0f);
            final Msg selectedMessage = m;
            body.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { showMessageActions(selectedMessage); return true; } });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2); lp.setMargins(0, dp(8), 0, 0);
            messageList.addView(role, lp);
            if (hasThinkingRow) {
                final Msg thinkingMessage = m;
                LinearLayout thinkRow = row();
                TextView thinking;
                if (!m.streamDone && m.reasoning.length() == 0) { JumpTextView jump = new JumpTextView(this); jump.word = "thinking"; thinking = jump; }
                else if (!m.streamDone) thinking = text("thinking", 11, Color.rgb(135,135,135));
                else thinking = text("thought for " + thoughtDuration(m.thoughtMs), 11, Color.rgb(135,135,135));
                thinking.setTextColor(Color.rgb(135,135,135)); thinking.setGravity(Gravity.CENTER_VERTICAL); setTextPx(thinking, 11); thinking.setPadding(0, 0, 0, 0);
                thinking.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { thinkingMessage.thinkingExpanded = !thinkingMessage.thinkingExpanded; renderMessages(); } });
                thinking.setMinHeight(dp(44));
                thinkRow.setMinimumHeight(dp(44));
                thinkRow.addView(thinking, new LinearLayout.LayoutParams(-1, dp(44)));
                thinkRow.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { thinkingMessage.thinkingExpanded = !thinkingMessage.thinkingExpanded; renderMessages(); } });
                messageList.addView(thinkRow, new LinearLayout.LayoutParams(-1, dp(44)));
                if (m.thinkingExpanded && m.reasoning.length() > 0) {
                    TextView reason = text(privateReasoning(m.reasoning) ? "private reasoning hidden" : m.reasoning, 13, Color.rgb(160,160,160));
                    reason.setLineSpacing(dp(2), 1.0f);
                    reason.setPadding(0, 0, 0, dp(8));
                    messageList.addView(reason, new LinearLayout.LayoutParams(-1, -2));
                }
            }
            if (hasSearchRow) {
                final Msg searchMessage = m;
                TextView searched = text("searched " + m.searchSources.size() + " sources" + (m.searchExpanded ? " -------------" : ""), 11, Color.rgb(135,135,135));
                searched.setGravity(Gravity.CENTER_VERTICAL);
                searched.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { searchMessage.searchExpanded = !searchMessage.searchExpanded; renderMessages(); } });
                messageList.addView(searched, new LinearLayout.LayoutParams(-1, dp(24)));
                if (m.searchExpanded) {
                    TextView src = text(join(m.searchSources), 12, Color.rgb(160,160,160));
                    src.setLineSpacing(dp(1), 1.0f);
                    src.setPadding(0, 0, 0, dp(8));
                    messageList.addView(src, new LinearLayout.LayoutParams(-1, -2));
                }
            }
            messageList.addView(body, new LinearLayout.LayoutParams(-1, -2));
            if (hasMemoryRow) {
                final Msg memoryMessage = m;
                TextView saved = text("memory saved" + (m.memoryExpanded ? " -------------" : ""), 11, Color.rgb(135,135,135));
                saved.setGravity(Gravity.CENTER_VERTICAL);
                saved.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { memoryMessage.memoryExpanded = !memoryMessage.memoryExpanded; renderMessages(); } });
                messageList.addView(saved, new LinearLayout.LayoutParams(-1, dp(24)));
                if (m.memoryExpanded && m.memorySavedText.length() > 0) {
                    TextView memoryText = text(m.memorySavedText, 12, Color.rgb(160,160,160));
                    memoryText.setLineSpacing(dp(1), 1.0f);
                    memoryText.setPadding(0, 0, 0, dp(8));
                    messageList.addView(memoryText, new LinearLayout.LayoutParams(-1, -2));
                }
            }
            if (m.role.equals("assistant") && m.stats.length() > 0 && !LOADING.equals(m.stats)) messageList.addView(text(m.stats, 10, Color.rgb(130,130,130)));
        }
        if (messageEnd < messages.size()) messageList.addView(windowMarker("newer messages below"));
        if (meter != null) { meter.percent = contextPercent(); meter.invalidate(); }
        if (contextText != null) contextText.setText(contextPercentText());
        if (attachText != null) { boolean showAttach = imageBase64.length() > 0; attachText.setVisibility(showAttach ? View.VISIBLE : View.GONE); attachText.setText(showAttach ? "attachment ready" : ""); }
        final ScrollView renderScroll = scroll;
        if (renderScroll != null) renderScroll.post(new Runnable() { @Override public void run() {
            if (renderScroll != scroll || pane != 1) return;
            if (forceAutoScrollBottom && showBottom && userAtChatBottom) renderScroll.fullScroll(View.FOCUS_DOWN);
            else if (restoreScrollOnce) renderScroll.scrollTo(0, savedChatScrollY);
            forceAutoScrollBottom = false;
            restoreScrollOnce = false;
            renderingMessages = false;
            if (scrollIndicator != null) scrollIndicator.invalidate();
            updateBottomButton();
        } });
    }

    private void resetMessageWindowToLatest() {
        messageEnd = messages.size();
        messageStart = Math.max(0, messageEnd - MESSAGE_WINDOW);
        messageWindowReady = true;
    }

    private void clampMessageWindow() {
        if (messageEnd > messages.size()) messageEnd = messages.size();
        if (messageStart < 0) messageStart = 0;
        if (messageEnd < messageStart) messageEnd = messageStart;
        if (messageEnd - messageStart > MESSAGE_WINDOW) messageStart = Math.max(0, messageEnd - MESSAGE_WINDOW);
    }

    private void loadOlderMessages() {
        if (messageStart <= 0) return;
        messageStart = Math.max(0, messageStart - MESSAGE_PAGE);
        messageEnd = Math.min(messages.size(), messageStart + MESSAGE_WINDOW);
        renderMessages();
        scroll.post(new Runnable() { @Override public void run() { scroll.scrollTo(0, dp(28)); } });
    }

    private void loadNewerMessages() {
        if (messageEnd >= messages.size()) return;
        messageEnd = Math.min(messages.size(), messageEnd + MESSAGE_PAGE);
        messageStart = Math.max(0, messageEnd - MESSAGE_WINDOW);
        renderMessages();
    }

    private TextView windowMarker(String s) {
        TextView v = text(s, 10, Color.rgb(110,110,110));
        v.setGravity(Gravity.CENTER);
        v.setPadding(0, dp(6), 0, dp(6));
        return v;
    }

    private void showEmptyPrompt() {
        messageList.addView(space(148));
        emptyPrompt = text("", 14, Color.rgb(135,135,135));
        emptyPrompt.setGravity(Gravity.CENTER);
        messageList.addView(emptyPrompt, new LinearLayout.LayoutParams(-1, -2));
        animateEmptyPrompt(emptyPromptText(), 0, ++emptyPromptRun);
    }

    private void updateBottomButton() {
        bottomButton = null;
    }

    private String emptyPromptText() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String[] pool = hour < 5 ? lateGreetings() : hour < 12 ? morningGreetings() : hour < 18 ? afternoonGreetings() : hour < 22 ? eveningGreetings() : nightGreetings();
        int index = Math.abs((int) ((System.currentTimeMillis() / 60000L) % pool.length));
        return pool[index];
    }

    private String[] morningGreetings() { return new String[]{"good morning. how can i help?", "morning. what's first?", "ready when you are.", "coffee thoughts?", "what are we figuring out?", "fresh start. what's up?", "need a hand this morning?", "what's on deck?", "let's get moving.", "morning brain online.", "what should we tackle?", "ask me anything.", "what's the plan?", "how can i help today?", "what are you curious about?", "need a quick answer?", "let's make this easy.", "what's the move?", "i'm listening.", "start anywhere."}; }
    private String[] afternoonGreetings() { return new String[]{"what are we working on?", "need a second brain?", "what's the question?", "let's sort it out.", "what do you need?", "drop the problem here.", "what are we solving?", "thinking cap on.", "what's next?", "tell me what you're trying to do.", "i can help with that.", "what should we unpack?", "send me the messy version.", "what's the situation?", "let's make progress.", "what needs simplifying?", "what's bothering you?", "i'm here.", "give me a thread to pull.", "what are we building?"}; }
    private String[] eveningGreetings() { return new String[]{"good evening. what's up?", "evening. need help?", "what's on your mind?", "let's wind this down.", "what did we miss today?", "need a quick read?", "what are you thinking about?", "i'm still awake.", "what's worth solving tonight?", "ask away.", "want to plan tomorrow?", "what needs a second pass?", "late-day thoughts?", "what's the vibe?", "let's clean it up.", "what do you want to know?", "ready when you are.", "what should we check?", "need a summary?", "what's the move tonight?"}; }
    private String[] nightGreetings() { return new String[]{"late night questions?", "still thinking?", "what's keeping you up?", "night mode. ask away.", "quiet hours, loud thoughts?", "what do you need before sleep?", "let's keep it simple.", "one last thing?", "what's on your mind?", "i'm here in the dark.", "need a quick answer?", "want to brain dump?", "what are we untangling?", "sleep can wait a minute.", "what's the late idea?", "need a calm explanation?", "let's solve it softly.", "what should we write down?", "midnight assistant mode.", "tell me the thought."}; }
    private String[] lateGreetings() { return new String[]{"wow, it's late. what's up?", "you good?", "3am thoughts?", "okay, i'm listening.", "late late. what happened?", "need something quick?", "can't sleep?", "let's keep this gentle.", "what's on your mind?", "one small thing at a time.", "night owl mode.", "want to get this out of your head?", "i'm awake if you are.", "what needs answering?", "quietly thinking with you.", "tell me the problem.", "want a short answer?", "let's make it easier.", "what's the thought loop?", "i got you."}; }

    private void animateEmptyPrompt(final String text, final int index, final int run) {
        if (run != emptyPromptRun || emptyPrompt == null || messages.size() > 0) return;
        int next = Math.min(text.length(), index + 2);
        emptyPrompt.setText(text.substring(0, next));
        if (next < text.length()) ui.postDelayed(new Runnable() { @Override public void run() { animateEmptyPrompt(text, next, run); } }, 38);
    }

    private void showMessageActions(final Msg m) {
        final Dialog d = panel("message");
        LinearLayout box = panelBox();
        box.addView(panelTitle("message"));
        TextView reply = panelItem("reply to message", trimQuote(m.text));
        reply.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); setReplyQuote(m.text); } });
        box.addView(reply);
        final String clip = clipboardText();
        if (clip.length() > 0) {
            TextView replyClip = panelItem("reply to copied text", trimQuote(clip));
            replyClip.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); setReplyQuote(clip); } });
            box.addView(replyClip);
        }
        TextView copy = panelItem("copy message", "");
        copy.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { copyText(m.text); d.dismiss(); toast("copied"); } });
        TextView cancel = panelAction("cancel");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(copy);
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(52)));
        showPanel(d, box);
    }

    private CharSequence markdownText(String raw) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        String[] lines = cleanSearchArtifacts(raw == null ? "" : raw).replace("\r", "").split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String trim = lines[i].trim();
            if (trim.startsWith("```")) {
                StringBuilder code = new StringBuilder();
                i++;
                while (i < lines.length && !lines[i].trim().startsWith("```")) { code.append(lines[i]); if (i < lines.length - 1) code.append('\n'); i++; }
                appendCodeBlock(out, code.toString().replaceAll("\n$", ""));
                if (i < lines.length - 1) out.append('\n');
                continue;
            }
            if (isTableLine(lines[i])) {
                ArrayList<String> block = new ArrayList<String>();
                while (i < lines.length && isTableLine(lines[i])) block.add(lines[i++]);
                i--;
                appendTable(out, block);
                continue;
            }
            appendMarkdownLine(out, lines[i]);
            if (i < lines.length - 1) out.append('\n');
        }
        return out;
    }

    private void appendMarkdownLine(SpannableStringBuilder out, String line) {
        String l = line == null ? "" : line;
        int hashes = 0;
        while (hashes < l.length() && hashes < 6 && l.charAt(hashes) == '#') hashes++;
        if (hashes > 0) {
            while (hashes < l.length() && l.charAt(hashes) == ' ') hashes++;
            int start = out.length();
            appendInlineMarkdown(out, l.substring(hashes).trim());
            out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            out.setSpan(new RelativeSizeSpan(hashes <= 2 ? 1.22f : 1.08f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return;
        }
        String t = l.trim();
        if (t.matches("^-{3,}$|^\\*{3,}$|^_{3,}$")) { out.append("────────────────"); return; }
        if (l.startsWith("    ") || l.startsWith("\t")) { appendCodeBlock(out, l.replaceFirst("^(    |\\t)", "")); return; }
        if (t.startsWith(">")) { int start = out.length(); out.append("│ "); appendInlineMarkdown(out, t.substring(1).trim()); out.setSpan(new ForegroundColorSpan(Color.rgb(165,165,165)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); return; }
        if (t.startsWith("- ") || t.startsWith("* ")) l = l.substring(0, l.indexOf(t)) + "• " + t.substring(2);
        else if (t.matches("^[0-9]+[.)]\\s+.*")) l = l.substring(0, l.indexOf(t)) + t.replaceFirst("^([0-9]+)[.)]\\s+", "$1. ");
        else if (t.startsWith("- [ ] ") || t.startsWith("* [ ] ")) l = l.substring(0, l.indexOf(t)) + "☐ " + t.substring(6);
        else if (t.toLowerCase(Locale.US).startsWith("- [x] ") || t.toLowerCase(Locale.US).startsWith("* [x] ")) l = l.substring(0, l.indexOf(t)) + "☑ " + t.substring(6);
        appendInlineMarkdown(out, l);
    }

    private void appendInlineMarkdown(SpannableStringBuilder out, String s) {
        int i = 0;
        while (i < s.length()) {
            if (s.startsWith("**", i)) {
                int end = s.indexOf("**", i + 2);
                if (end > i) { int start = out.length(); out.append(s.substring(i + 2, end)); out.setSpan(new StyleSpan(Typeface.BOLD), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i = end + 2; continue; }
            }
            if (s.startsWith("***", i)) {
                int end = s.indexOf("***", i + 3);
                if (end > i) { int start = out.length(); out.append(s.substring(i + 3, end)); out.setSpan(new StyleSpan(Typeface.BOLD_ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i = end + 3; continue; }
            }
            if (s.startsWith("~~", i)) {
                int end = s.indexOf("~~", i + 2);
                if (end > i) { int start = out.length(); out.append(s.substring(i + 2, end)); out.setSpan(new android.text.style.StrikethroughSpan(), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i = end + 2; continue; }
            }
            if (s.charAt(i) == '`') {
                int end = s.indexOf('`', i + 1);
                if (end > i) { int start = out.length(); out.append(s.substring(i + 1, end)); out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i = end + 1; continue; }
            }
            if (s.charAt(i) == '[') {
                int mid = s.indexOf("](", i + 1), end = mid < 0 ? -1 : s.indexOf(')', mid + 2);
                if (mid > i && end > mid) { int start = out.length(); out.append(s.substring(i + 1, mid)); out.setSpan(new ForegroundColorSpan(Color.rgb(190,190,190)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); out.append(" <").append(s.substring(mid + 2, end)).append(">"); i = end + 1; continue; }
            }
            if (s.charAt(i) == '*' && !s.startsWith("**", i)) {
                int end = s.indexOf('*', i + 1);
                if (end > i + 1) { int start = out.length(); out.append(s.substring(i + 1, end)); out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i = end + 1; continue; }
            }
            if (s.charAt(i) == '_' && (i == 0 || !Character.isLetterOrDigit(s.charAt(i - 1)))) {
                int end = s.indexOf('_', i + 1);
                if (end > i + 1 && (end + 1 >= s.length() || !Character.isLetterOrDigit(s.charAt(end + 1)))) { int start = out.length(); out.append(s.substring(i + 1, end)); out.setSpan(new StyleSpan(Typeface.ITALIC), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); i = end + 1; continue; }
            }
            out.append(s.charAt(i));
            i++;
        }
    }

    private void appendCodeBlock(SpannableStringBuilder out, String code) {
        int start = out.length();
        out.append(code == null ? "" : code);
        out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new RelativeSizeSpan(0.9f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new ForegroundColorSpan(Color.rgb(210,210,210)), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private boolean isTableLine(String line) { String t = line == null ? "" : line.trim(); return t.startsWith("|") && t.endsWith("|") && t.indexOf('|', 1) > 0; }

    private void appendTable(SpannableStringBuilder out, ArrayList<String> block) {
        ArrayList<String[]> rows = new ArrayList<String[]>();
        int cols = 0;
        for (String line : block) {
            String t = line.trim();
            if (t.matches("^\\|?[\\s:\\-\\|]+\\|?$")) continue;
            String[] cells = t.substring(1, t.length() - 1).split("\\|");
            for (int i = 0; i < cells.length; i++) cells[i] = cells[i].trim();
            cols = Math.max(cols, cells.length);
            rows.add(cells);
        }
        if (rows.size() == 0) return;
        int[] w = new int[cols];
        for (String[] row : rows) for (int i = 0; i < row.length; i++) w[i] = Math.max(w[i], row[i].length());
        int start = out.length();
        for (int r = 0; r < rows.size(); r++) {
            String[] row = rows.get(r);
            for (int c = 0; c < cols; c++) {
                String cell = c < row.length ? row[c] : "";
                out.append(padRight(cell, w[c]));
                if (c < cols - 1) out.append("  ");
            }
            if (r < rows.size() - 1) out.append('\n');
        }
        out.setSpan(new TypefaceSpan("monospace"), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        out.setSpan(new RelativeSizeSpan(0.88f), start, out.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    }

    private String padRight(String s, int width) { StringBuilder b = new StringBuilder(s == null ? "" : s); while (b.length() < width) b.append(' '); return b.toString(); }

    private void setReplyQuote(String q) { replyQuote = q == null ? "" : q.trim(); updateReplyChip(); if (input != null) input.requestFocus(); }
    private void updateReplyChip() { if (replyChip != null) { boolean show = replyQuote.length() > 0; replyChip.setVisibility(show ? View.VISIBLE : View.GONE); replyChip.setText(show ? "replying to: " + trimQuote(replyQuote) + "  x" : ""); } }
    private String trimQuote(String s) { String q = s == null ? "" : s.replace('\n', ' ').trim(); return q.length() > 72 ? q.substring(0, 72) + "..." : q; }

    private void handleIncoming(Intent intent) {
        if (intent == null || input == null && pane != 1) return;
        String action = intent.getAction();
        if (hookVoiceMode && (Intent.ACTION_ASSIST.equals(action) || Intent.ACTION_VOICE_COMMAND.equals(action))) { startVoice(true); return; }
        if (Intent.ACTION_SEND.equals(action)) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) attachUri(u);
            if (text != null) input.setText(limitIncomingText(text.toString()));
        } else if ("com.minimalchat.ASK".equals(action) || "com.lightos.minimalchat.ASK".equals(action)) {
            pane = 1;
            if (!hookVoiceMode) renderPane();
            String prompt = intent.getStringExtra("prompt");
            if (prompt != null && input != null) input.setText(limitIncomingText(prompt));
        } else if (hookVoiceMode) {
            startVoice(true);
        }
    }

    private String limitIncomingText(String text) {
        String s = text == null ? "" : text;
        if (s.length() <= 20000) return s;
        toast("shared text truncated");
        return s.substring(0, 20000);
    }

    private void showToolsMenu() {
        final Dialog d = panel("tools");
        FrameLayout overlay = new FrameLayout(this);
        overlay.setBackgroundColor(Color.argb(210, 0, 0, 0));
        overlay.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(28), dp(28), dp(28), dp(18));
        box.setBackgroundColor(Color.TRANSPARENT);
        box.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { } });
        box.addView(panelTitle("tools"));
        final TextView web = text(toolLabel("web search", webSearchChat), 16, Color.WHITE);
        web.setGravity(Gravity.CENTER_VERTICAL);
        web.setPadding(0, dp(10), 0, dp(10));
        web.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { webSearchChat = !webSearchChat; saveCurrentChat(); web.setText(toolLabel("web search", webSearchChat)); if (webSearchIcon != null) { webSearchIcon.active = webSearchChat; webSearchIcon.setClickable(webSearchChat); webSearchIcon.invalidate(); } toast(webSearchChat ? "web search on" : "web search off"); } });
        box.addView(web, new LinearLayout.LayoutParams(-1, dp(44)));
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
        boxLp.setMargins(dp(26), dp(58), dp(26), 0);
        overlay.addView(box, boxLp);
        enablePanelSwipeDismiss(d, overlay);
        d.setContentView(overlay);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            enablePanelSwipeDismiss(d, w.getDecorView());
        }
    }

    private String toolLabel(String label, boolean active) { return active ? "✓ " + label : label; }

    private boolean send() {
        saveApiKey();
        String model = activeAnswerModel();
        String source = modelSource(model);
        String key = source.equals("openrouter") ? savedApiKey() : "";
        String text = input == null ? pendingVoiceText.trim() : input.getText().toString().trim();
        if (handleMemoryRecall(text)) return true;
        String pendingUserMemoryNote = pendingUserMemoryNote(text);
        if (model.length() == 0) { toast("select a model first"); return false; }
        if (source.equals("openrouter") && key.length() == 0) { toast("add openrouter key"); return false; }
        if (source.equals("custom") && modelEndpoint(model).length() == 0) { toast("add endpoint"); return false; }
        if (text.length() == 0 && imageBase64.length() == 0) return false;
        if (input != null) hideKeyboardFrom(input);
        if (input != null) input.clearFocus();
        userAtChatBottom = true;
        messages.add(new Msg("user", text, imageBase64, imageMime, "", "", replyQuote));
        final Msg assistant = new Msg("assistant", ".", "", "", LOADING, shortModel(model));
        assistant.slowVoice = voiceMode && voiceFullMode;
        assistant.voiceSessionId = assistant.slowVoice ? voiceSession : 0;
        assistant.reasoningCapable = isReasoningModel(model);
        if (assistant.slowVoice) assistant.text = "";
        messages.add(assistant);
        saveCurrentChat();
        resetMessageWindowToLatest();
        forceAutoScrollBottom = userAtChatBottom;
        if (input != null) input.setText(""); pendingVoiceText = ""; replyQuote = ""; updateReplyChip(); imageBase64 = ""; renderMessages();
        animateLoading(assistant, 0);
        final String userText = text;
        final String sendKey = key;
        final String sendSource = source;
        final String sendModel = model;
        final String sendPendingUserMemoryNote = pendingUserMemoryNote;
        new Thread(new Runnable() { @Override public void run() { callOpenRouter(sendKey, sendSource, sendModel, assistant, userText, sendPendingUserMemoryNote); } }).start();
        return true;
    }

    private void callOpenRouter(String key, String source, String model, final Msg assistant, String userText, String pendingUserMemoryNote) {
        long start = System.nanoTime(), headersAt = start;
        try {
            JSONObject body = new JSONObject(); body.put("model", model);
            JSONArray arr = new JSONArray();
            String searchContext = buildSearchContext(userText);
            if (searchContext.length() > 0) { assistant.searchSources.clear(); assistant.searchSources.addAll(lastSearchSources); arr.put(new JSONObject().put("role", "system").put("content", searchContext)); }
            String toolMemory = buildToolMemoryContext();
            if (toolMemory.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", toolMemory));
            String folderInstruction = buildFolderInstructionContext();
            if (folderInstruction.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", folderInstruction));
            String userMemory = buildUserMemoryContext();
            if (userMemory.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", userMemory));
            if (memoryEnabled()) arr.put(new JSONObject().put("role", "system").put("content", "Memory tool: to save a durable user preference/fact, emit only <tool_call><function=save_memory><parameter=note>short note</parameter></function></tool_call>. Save only stable, useful, non-secret memories unless explicitly requested."));
            if (assistant.slowVoice) arr.put(new JSONObject().put("role", "system").put("content", "This is a spoken two-way voice conversation. Reply in plain text only. Do not use markdown, headings, bullets, tables, code blocks, or formatting symbols. Keep the response natural for text-to-speech."));
            for (Msg m : messages) {
                if (LOADING.equals(m.stats)) continue;
                JSONObject one = new JSONObject(); one.put("role", m.role);
                if (m.imageBase64.length() > 0) {
                    JSONArray content = new JSONArray();
                    content.put(new JSONObject().put("type", "text").put("text", requestText(m)));
                    content.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", "data:" + m.imageMime + ";base64," + m.imageBase64)));
                    one.put("content", content);
                } else one.put("content", requestText(m));
                arr.put(one);
            }
            body.put("messages", arr); body.put("usage", new JSONObject().put("include", true)); body.put("stream", true);
            HttpURLConnection c = (HttpURLConnection) new URL(chatCompletionsUrl(source, model)).openConnection();
            c.setRequestMethod("POST"); c.setConnectTimeout(30000); c.setReadTimeout(120000); c.setDoOutput(true);
            if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Content-Type", "application/json");
            if (source.equals("openrouter")) { c.setRequestProperty("HTTP-Referer", "https://minimal.chat/android"); c.setRequestProperty("X-Title", "chat"); }
            OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes(StandardCharsets.UTF_8)); os.close();
            headersAt = System.nanoTime(); int code = c.getResponseCode();
            if (code >= 400) throw new RuntimeException(readAll(c.getErrorStream()));
            final StringBuilder answer = new StringBuilder();
            final StringBuilder reasoning = new StringBuilder();
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;
                JSONObject chunk = new JSONObject(data);
                JSONArray choices = chunk.optJSONArray("choices");
                if (choices == null || choices.length() == 0) continue;
                JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                if (delta == null) continue;
                String content = cleanJsonString(delta, "content");
                String thought = reasoningDelta(delta);
                if (content.length() == 0 && thought.length() == 0) continue;
                answer.append(content);
                reasoning.append(thought);
                final String partial = answer.toString();
                final String partialReasoning = reasoning.toString();
                runOnUiThread(new Runnable() { @Override public void run() { updateStreamingAssistant(assistant, partial, partialReasoning); } });
            }
            br.close();
            long end = System.nanoTime();
            String finalAnswer = answer.toString();
            String toolQuery = webSearchToolQuery(finalAnswer);
            if (toolQuery.length() > 0) {
                final String result = webSearch(toolQuery);
                assistant.searchSources.clear();
                assistant.searchSources.addAll(extractSearchSources(result));
                finalAnswer = followupAfterWebSearch(key, source, model, toolQuery, result);
            }
            String memoryNote = memoryToolNote(finalAnswer);
            if (memoryNote.length() > 0 || finalAnswer.toLowerCase(Locale.US).contains("save_memory")) {
                if (pendingUserMemoryNote.length() == 0 && memoryEnabled() && memoryNote.length() > 0) {
                    String savedMemory = appendMemory(memoryNote, "model");
                    if (savedMemory.length() > 0) { assistant.memorySaved = true; assistant.memorySavedText = savedMemory; }
                }
                finalAnswer = stripToolCalls(finalAnswer);
                if (finalAnswer.trim().length() == 0) finalAnswer = "noted.";
            }
            if (pendingUserMemoryNote.length() > 0) {
                String savedMemory = appendMemory(pendingUserMemoryNote, "user");
                if (savedMemory.length() > 0) { assistant.memorySaved = true; assistant.memorySavedText = savedMemory; }
            }
            final String finishedAnswer = finalAnswer;
            int completionTokens = estimateTokens(finishedAnswer) + (reasoning.length() == 0 ? 0 : estimateTokens(reasoning.toString()));
            final String stats = String.format(Locale.US, "%.1f tok/s", completionTokens / Math.max(0.001, (end - start) / 1e9));
            runOnUiThread(new Runnable() { @Override public void run() { finishStreamingAssistant(assistant, finishedAnswer, reasoning.toString(), stats); } });
/*            String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream()); long end = System.nanoTime();
            if (code >= 400) throw new RuntimeException(raw);
            JSONObject resp = new JSONObject(raw);
            String answer = resp.getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content", "");
            JSONObject usage = resp.optJSONObject("usage");
            int completionTokens = usage == null ? estimateTokens(answer) : usage.optInt("completion_tokens", estimateTokens(answer));
            final String stats = String.format(Locale.US, "%.1f tok/s", completionTokens / Math.max(0.001, (end - start) / 1e9));
            final String finalAnswer = answer;
            runOnUiThread(new Runnable() { @Override public void run() {
                assistant.stats = "";
                animateAssistant(assistant, finalAnswer, stats, 0);
            } });*/
        } catch (Exception e) {
            final String msg = friendlyError(e);
            runOnUiThread(new Runnable() { @Override public void run() { stopVoiceThinking(); assistant.stats = ""; assistant.text = isModelRefusal(msg) ? "model refused\n" + msg : "failed to load model\n" + msg; setVoiceText(assistant.text); updateVoiceStatus(isModelRefusal(msg) ? "model refused" : "failed"); renderMessages(); } });
        }
    }

    private String reasoningDelta(JSONObject delta) {
        StringBuilder out = new StringBuilder();
        String[] keys = new String[]{"reasoning", "reasoning_content", "thinking", "thought"};
        for (String key : keys) {
            String v = cleanJsonString(delta, key);
            if (v.length() > 0) out.append(v);
        }
        JSONArray details = delta.optJSONArray("reasoning_details");
        if (details != null) for (int i = 0; i < details.length(); i++) {
            JSONObject d = details.optJSONObject(i);
            if (d != null) { String v = cleanJsonString(d, "text"); if (v.length() == 0) v = cleanJsonString(d, "content"); out.append(v); }
        }
        return out.toString();
    }

    private String cleanJsonString(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equals(v) ? "" : v;
    }

    private void updateStreamingAssistant(Msg assistant, String partial, String reasoning) {
        stopVoiceThinking();
        boolean gotReasoning = reasoning.length() > 0;
        if (gotReasoning) { assistant.reasoning = reasoning; assistant.reasoningCapable = true; assistant.text = ""; }
        assistant.stats = gotReasoning && partial.length() == 0 ? LOADING : "";
        if (gotReasoning && partial.length() == 0) { forceAutoScrollBottom = userAtChatBottom; renderMessages(); saveCurrentChat(); return; }
        assistant.text = partial.length() == 0 ? "" : cleanSearchArtifacts(partial);
        forceAutoScrollBottom = userAtChatBottom;
        if (assistant.slowVoice && voiceMode && voiceFullMode) { updateVoiceStatus("responding"); if (assistant.ttsStarted || !prefs.getBoolean("voiceSpeak", true)) renderVoiceConversation(); }
        maybeSpeakStreamingChunk(assistant, partial, false);
        renderMessages();
        saveCurrentChat();
    }

    private void finishStreamingAssistant(Msg assistant, String finalAnswer, String reasoning, String stats) {
        stopVoiceThinking();
        assistant.stats = stats;
        assistant.streamDone = true;
        assistant.text = cleanSearchArtifacts(finalAnswer.length() == 0 ? assistant.text : finalAnswer);
        if (reasoning.length() > 0) { assistant.reasoning = reasoning; assistant.thoughtMs = Math.max(1, System.currentTimeMillis() - assistant.startedAt); }
        if (assistant.slowVoice && isModelRefusal(assistant.text)) {
            assistant.ttsQueue.clear();
            assistant.ttsRequested = false;
            assistant.ttsPlaying = false;
            forceAutoScrollBottom = true;
            renderMessages();
            saveCurrentChat();
            pauseVoiceAfterProviderFailure("model refused", "model refused\n" + assistant.text);
            return;
        }
        forceAutoScrollBottom = userAtChatBottom;
        renderMessages();
        saveCurrentChat();
        if (assistant.slowVoice) { maybeSpeakStreamingChunk(assistant, assistant.text, true); maybeFinishVoiceAfterTts(assistant); }
    }

    private void maybeSpeakStreamingChunk(Msg assistant, String fullText, boolean finish) {
        if (!assistant.slowVoice || !voiceMode || !voiceFullMode || !prefs.getBoolean("voiceSpeak", true)) return;
        if (assistant.voiceSessionId != 0 && assistant.voiceSessionId != voiceSession) return;
        if (isModelRefusal(fullText)) { assistant.ttsQueue.clear(); pauseVoiceAfterProviderFailure("model refused", "model refused\n" + fullText); return; }
        if (fullText == null) return;
        int base = Math.min(assistant.spokenChars, fullText.length());
        while (base < fullText.length()) {
            String pending = fullText.substring(base);
            int cut = firstSpeakableCut(pending, finish);
            if (cut <= 0) break;
            String speak = pending.substring(0, Math.min(cut, pending.length())).trim();
            base += cut;
            if (speak.length() >= 8) assistant.ttsQueue.add(speak);
            if (!finish) break;
        }
        assistant.spokenChars = base;
        playNextQueuedSpeech(assistant);
        prefetchNextQueuedSpeech(assistant);
    }

    private void playNextQueuedSpeech(final Msg owner) {
        if (!voiceMode || !voiceFullMode) return;
        if (owner.voiceSessionId != 0 && owner.voiceSessionId != voiceSession) return;
        if (owner.ttsRequested || owner.ttsPlaying || owner.ttsQueue.size() == 0) return;
        if (!owner.ttsStarted && !owner.ttsStartDelayDone) {
            owner.ttsStartDelayDone = true;
            prefetchNextQueuedSpeech(owner);
            ui.postDelayed(new Runnable() { @Override public void run() { playNextQueuedSpeech(owner); } }, fastTtsOutput() ? 120 : 450);
            return;
        }
        final String text = owner.ttsQueue.get(0);
        final int session = voiceSession;
        activeTtsOwner = owner;
        File ready = ttsReadyFiles.remove(text);
        if (ready != null && ready.exists()) { if (owner.ttsQueue.size() > 0 && owner.ttsQueue.get(0).equals(text)) owner.ttsQueue.remove(0); owner.ttsStarted = true; owner.ttsPlaying = true; owner.ttsPlaybackFailures = 0; renderVoiceConversation(); playSpeechAudio(ready, text, owner); prefetchNextQueuedSpeech(owner); return; }
        owner.ttsRequested = true;
        updateVoiceStatus("speaking");
        new Thread(new Runnable() { @Override public void run() {
            try {
                final File audio = requestSpeechAudio(text);
                runOnUiThread(new Runnable() { @Override public void run() { if (!voiceSessionActive(session)) return; owner.ttsRequested = false; if (owner.ttsQueue.size() > 0 && owner.ttsQueue.get(0).equals(text)) owner.ttsQueue.remove(0); owner.ttsStarted = true; owner.ttsPlaying = true; owner.ttsPlaybackFailures = 0; renderVoiceConversation(); playSpeechAudio(audio, text, owner); } });
            } catch (Exception e) {
                final String msg = friendlyError(e);
                runOnUiThread(new Runnable() { @Override public void run() { if (!voiceSessionActive(session)) return; owner.ttsRequested = false; owner.ttsQueue.clear(); owner.streamDone = true; updateVoiceStatus(isModelRefusal(msg) ? "model refused" : "tts failed"); if (voiceText != null) setVoiceText(voiceText.getText().toString() + "\n\n" + (isModelRefusal(msg) ? "model refused: " : "tts: ") + msg); ui.postDelayed(new Runnable() { @Override public void run() { finishVoiceResponse(); } }, 1200); } });
            }
        } }).start();
    }

    private void prefetchNextQueuedSpeech(final Msg owner) {
        if (!voiceMode || !voiceFullMode) return;
        if (owner.voiceSessionId != 0 && owner.voiceSessionId != voiceSession) return;
        if (owner.ttsRequested || owner.ttsQueue.size() == 0) return;
        final String text = owner.ttsQueue.get(0);
        final int session = voiceSession;
        if (ttsReadyFiles.containsKey(text)) return;
        owner.ttsRequested = true;
        new Thread(new Runnable() { @Override public void run() {
            try {
                final File audio = requestSpeechAudio(text);
                runOnUiThread(new Runnable() { @Override public void run() { if (!voiceSessionActive(session)) return; owner.ttsRequested = false; ttsReadyFiles.put(text, audio); if (!owner.ttsPlaying) playNextQueuedSpeech(owner); } });
            } catch (Exception e) {
                final String msg = friendlyError(e);
                runOnUiThread(new Runnable() { @Override public void run() { if (!voiceSessionActive(session)) return; owner.ttsRequested = false; updateVoiceStatus("tts: " + msg); maybeFinishVoiceAfterTts(owner); } });
            }
        } }).start();
    }

    private void maybeFinishVoiceAfterTts(Msg owner) {
        if (!voiceMode || !voiceFullMode) return;
        if (owner.voiceSessionId != 0 && owner.voiceSessionId != voiceSession) return;
        if (!owner.slowVoice) return;
        if (!prefs.getBoolean("voiceSpeak", true)) { finishVoiceResponse(); return; }
        if (owner.streamDone && !owner.ttsRequested && !owner.ttsPlaying && owner.ttsQueue.size() == 0) finishVoiceResponse();
    }

    private int firstSpeakableCut(String s, boolean finish) {
        if (s == null) return -1;
        String t = s.trim();
        if (t.length() == 0) return -1;
        int words = 0, sentences = 0, firstGood = -1, secondGood = -1, thirdGood = -1, lastSentence = -1;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean endWord = !isSpeechWordChar(s, i) && (i > 0 && isSpeechWordChar(s, i - 1));
            if (endWord) words++;
            if (isSentenceBoundary(s, i)) {
                int boundary = safeSpeechBoundary(s, i + 1);
                sentences++;
                lastSentence = boundary;
                if (words >= 10 && firstGood < 0) firstGood = boundary;
                if (words >= 18 && secondGood < 0) secondGood = boundary;
                if (words >= 28 && thirdGood < 0) thirdGood = boundary;
                if (sentences >= 3 && words >= (fastTtsOutput() ? 18 : 14)) return boundary;
                if (words >= 42) return boundary;
            }
        }
        if (s.length() > 0 && isSpeechWordChar(s, s.length() - 1)) words++;
        if (thirdGood > 0) return thirdGood;
        if (secondGood > 0) return secondGood;
        if (finish && lastSentence > 0) return lastSentence;
        if (finish && firstGood > 0) return firstGood;
        return finish && t.length() > 0 ? s.length() : -1;
    }

    private boolean fastTtsOutput() {
        String model = "endpoint".equals(voiceOutputProvider()) ? prefs.getString("voiceEndpointTtsModel", "") : prefs.getString("voiceTtsModel", "");
        return "endpoint".equals(voiceOutputProvider()) || model.toLowerCase(Locale.US).contains("kokoro");
    }

    private boolean isSentenceBoundary(String s, int i) {
        char c = s.charAt(i);
        if (c != '.' && c != '!' && c != '?' && c != '\n' && c != ';') return false;
        if (c == '.' && i > 0 && i + 1 < s.length() && Character.isDigit(s.charAt(i - 1)) && Character.isDigit(s.charAt(i + 1))) return false;
        int j = i + 1;
        while (j < s.length() && (s.charAt(j) == '"' || s.charAt(j) == '\'' || s.charAt(j) == ')' || s.charAt(j) == ']')) j++;
        return j >= s.length() || Character.isWhitespace(s.charAt(j));
    }

    private boolean isSpeechWordChar(String s, int i) {
        char c = s.charAt(i);
        if (Character.isLetterOrDigit(c)) return true;
        if (c == '\'' || c == 8217) return i > 0 && i + 1 < s.length() && Character.isLetter(s.charAt(i - 1)) && Character.isLetter(s.charAt(i + 1));
        return false;
    }

    private int safeSpeechBoundary(String s, int boundary) {
        int b = Math.min(Math.max(boundary, 0), s.length());
        while (b > 0 && (s.charAt(b - 1) == '\'' || s.charAt(b - 1) == 8217 || s.charAt(b - 1) == '-' || s.charAt(b - 1) == 8211 || s.charAt(b - 1) == 8212)) b--;
        return b;
    }

    private void animateLoading(final Msg msg, final int step) {
        if (!LOADING.equals(msg.stats)) return;
        if (msg.slowVoice) return;
        if (msg.reasoningCapable) return;
        int dots = step % 4;
        if (dots == 0) msg.text = ".";
        else if (dots == 1) msg.text = ". .";
        else if (dots == 2) msg.text = ". . .";
        else msg.text = "";
        forceAutoScrollBottom = userAtChatBottom;
        renderMessages();
        ui.postDelayed(new Runnable() { @Override public void run() { animateLoading(msg, step + 1); } }, 320);
    }

    private void animateAssistant(final Msg msg, final String full, final String stats, final int index) {
        if (index == 0) stopVoiceThinking();
        msg.stats = "";
        int step = msg.slowVoice ? 1 : Math.max(1, Math.min(6, full.length() / 160 + 1));
        int next = Math.min(full.length(), index + step);
        msg.text = full.substring(0, next);
        if (msg.slowVoice && voiceMode && voiceFullMode && voiceText != null) {
            updateVoiceStatus("responding");
            renderVoiceConversation();
            fadeVoiceWaves();
        }
        forceAutoScrollBottom = true;
        renderMessages();
        if (next < full.length()) {
            ui.postDelayed(new Runnable() { @Override public void run() { animateAssistant(msg, full, stats, next); } }, msg.slowVoice ? 34 : 18);
        } else {
            msg.stats = stats;
            saveCurrentChat();
            renderMessages();
            if (msg.slowVoice) speakResponse(full);
        }
    }

    private boolean isReasoningModel(String model) {
        String m = (model == null ? "" : model).toLowerCase(Locale.US);
        if (reasoningModels.contains(model)) return true;
        String meta = modelSearchText.containsKey(model) ? modelSearchText.get(model).toLowerCase(Locale.US) : "";
        String haystack = m + " " + meta;
        return haystack.contains("minimax") || haystack.contains("reasoning") || haystack.contains("thinking") || haystack.contains("deepseek-r1") || haystack.contains("r1-") || haystack.endsWith("/r1") || haystack.contains("qwq") || haystack.contains("qwen3") || haystack.contains("qwen-3") || haystack.contains("qwen/qwen3") || haystack.contains("magistral") || haystack.contains("glm-4.5") || haystack.contains("sonar-reasoning") || haystack.contains("kimi-k2-thinking") || haystack.contains("o1") || haystack.contains("o3") || haystack.contains("o4-");
    }

    private String buildSearchContext(String userText) throws Exception {
        lastSearchSources.clear();
        boolean forceSearch = prefs.getBoolean("searchNext", false);
        if (forceSearch) prefs.edit().remove("searchNext").apply();
        String query = searchQuery(userText);
        if (voiceMode && voiceFullMode && "on".equals(voiceWebSearchMode()) && query.length() == 0) query = userText == null ? "" : userText.trim();
        if (voiceMode && voiceFullMode && "auto".equals(voiceWebSearchMode()) && query.length() == 0 && shouldVoiceAutoSearch(userText)) query = userText == null ? "" : userText.trim();
        String folderInstruction = folderInstruction(selectedFolder);
        if (query.length() == 0 && folderInstructionForcesSearch(folderInstruction)) query = ((userText == null ? "" : userText.trim()) + " " + folderInstruction).trim();
        if (webSearchChat && (query == null || query.length() == 0)) query = userText == null ? "" : userText.trim();
        if (forceSearch && (query == null || query.length() == 0)) query = userText == null ? "" : userText.trim();
        if (query.length() == 0) return "";
        String result = webSearch(query);
        if (result.length() == 0) return "";
        lastSearchSources.addAll(extractSearchSources(result));
        if (result.length() > 6000) result = result.substring(0, 6000);
        return "Web search has already been performed by the app. Do not emit tool calls, XML, function calls, or requests to search. Use the sources below to answer the user's question directly. Cite plain URLs only when useful; do not emit bracketed line citations like [1%L1-L9].\n\nQuery: " + query + "\n\n" + cleanSearchArtifacts(result);
    }

    private String cleanSearchArtifacts(String s) {
        return (s == null ? "" : s)
                .replaceAll("\\[[0-9]+%L[0-9]+(?:-L[0-9]+)?\\]", "")
                .replaceAll("\\[[0-9]+[†‡]L[0-9]+(?:-L[0-9]+)?\\]", "")
                .replaceAll("【[^】]*[†‡%]L[0-9][^】]*】", "")
                .replaceAll("(?<=\\p{Alpha})[†‡](?=\\p{Alpha})", " ")
                .replace("†", "")
                .replace("‡", "");
    }

    private String webSearchToolQuery(String text) {
        String s = text == null ? "" : text.trim();
        String lower = s.toLowerCase(Locale.US);
        if (!lower.contains("web_search") && !lower.contains("<tool_call")) return "";
        String[] markers = new String[]{"<parameter=query>", "query:", "query=", "\"query\":"};
        for (String marker : markers) {
            int at = lower.indexOf(marker);
            if (at < 0) continue;
            int start = at + marker.length();
            int end = s.length();
            String[] stops = new String[]{"</parameter>", "</function>", "</tool_call>", "\n"};
            for (String stop : stops) { int cut = lower.indexOf(stop, start); if (cut >= 0) end = Math.min(end, cut); }
            String q = s.substring(start, end).replace("\"", "").replace("'", "").trim();
            if (q.length() > 0) return q;
        }
        return "";
    }

    private String followupAfterWebSearch(String key, String source, String model, String query, String result) throws Exception {
        if (result.length() > 6000) result = result.substring(0, 6000);
        JSONObject body = new JSONObject();
        body.put("model", model);
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject().put("role", "system").put("content", "The previous assistant response requested a web_search tool. The app executed it. Do not output tool calls. Answer the user's question directly using these search results, citing URLs when helpful.\n\nQuery: " + query + "\n\n" + result));
        String folderInstruction = buildFolderInstructionContext();
        if (folderInstruction.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", folderInstruction));
        for (Msg m : messages) {
            if (LOADING.equals(m.stats)) continue;
            JSONObject one = new JSONObject();
            one.put("role", m.role);
            one.put("content", requestText(m));
            arr.put(one);
        }
        body.put("messages", arr);
        body.put("stream", false);
        HttpURLConnection c = (HttpURLConnection) new URL(chatCompletionsUrl(source, model)).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(30000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json");
        if (source.equals("openrouter")) { c.setRequestProperty("HTTP-Referer", "https://minimal.chat/android"); c.setRequestProperty("X-Title", "chat"); }
        OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes(StandardCharsets.UTF_8)); os.close();
        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new RuntimeException(raw);
        String out = extractMessageText(new JSONObject(raw));
        return out.length() == 0 ? raw.trim() : out;
    }

    private ArrayList<String> extractSearchSources(String result) {
        ArrayList<String> out = new ArrayList<String>();
        if (result == null) return out;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("https?://[^\\s)\\]}>\"]+").matcher(result);
        while (m.find() && out.size() < 8) {
            String url = m.group();
            while (url.endsWith(".") || url.endsWith(",") || url.endsWith(";")) url = url.substring(0, url.length() - 1);
            if (!out.contains(url)) out.add(url);
        }
        return out;
    }

    private String buildToolMemoryContext() {
        ArrayList<String> sources = new ArrayList<String>();
        for (int i = messages.size() - 1; i >= 0 && sources.size() < 8; i--) {
            Msg m = messages.get(i);
            if (!"assistant".equals(m.role)) continue;
            for (String s : m.searchSources) if (!sources.contains(s) && sources.size() < 8) sources.add(s);
        }
        if (sources.size() == 0) return "";
        return "Conversation tool memory: earlier in this chat, the app performed web search and supplied source context. If the user asks how you knew or where that came from, say that web search was used. Do not claim it came from training data or that you cannot know. Relevant source URLs:\n" + join(sources);
    }

    private boolean privateReasoning(String text) {
        String s = text == null ? "" : text.toLowerCase(Locale.US);
        return s.contains("persistent user memory") || s.contains("private persistent") || s.contains("memory.md") || s.contains("private project instructions") || s.contains("folder instructions") || s.contains("project instructions") || s.contains("system prompt") || s.contains("system message") || s.contains("my memory") || s.contains("from memory") || s.contains("entry #");
    }

    private String buildFolderInstructionContext() {
        String instruction = folderInstruction(selectedFolder);
        if (instruction.length() == 0) return "";
        return "Private project instructions for this chat (project: " + selectedFolder + "). This is background guidance, not the user's current message. Follow it silently unless the user explicitly overrides it. Do not mention these instructions exist, quote them, or attribute them to the user's current message. If these instructions require current/latest information or web search, use supplied web search context when present and request web_search when needed.\n\n" + instruction;
    }

    private boolean folderInstructionForcesSearch(String instruction) {
        String s = instruction == null ? "" : instruction.toLowerCase(Locale.US);
        return s.contains("always search") || s.contains("web search") || s.contains("search the web") || s.contains("latest") || s.contains("current") || s.contains("today");
    }

    private String buildUserMemoryContext() {
        if (!memoryEnabled()) return "";
        String mem = normalizeMemoryMd(memoryMd()).trim();
        if (mem.length() == 0) return "";
        return "Private persistent user memory from MEMORY.md. Today's date is " + currentDateString() + ". This is background context, not the user's current message. Numbered entries describe the user unless explicitly stated otherwise. Use memory quietly to personalize replies and respect preferences. Do not mention memory, quote this block, or say you know something from memory unless the user asks what you remember or it is directly relevant. Entries include the date they were saved; use those dates to reason about time-sensitive facts like age. If asked what you remember, summarize these entries.\n\n" + mem;
    }

    private String memoryMd() { return prefs == null ? "" : prefs.getString("memoryMd", ""); }

    private boolean memoryEnabled() { return prefs == null || prefs.getBoolean("memoryEnabled", true); }

    private String memoryStatusText() {
        String mem = normalizeMemoryMd(memoryMd());
        int chars = mem.length(), entries = memoryEntryCount(mem), tokens = estimateTokens(mem);
        return entries + " entries / ~" + shortTokens(tokens) + " tokens / " + chars + " chars. Soft cap: 12k chars.";
    }

    private int memoryEntryCount(String mem) {
        int count = 0;
        String[] lines = (mem == null ? "" : mem).split("\\n");
        for (String line : lines) { String clean = line.trim(); if (clean.startsWith("- ") || clean.matches("^[0-9]+[.)]\\s+.*")) count++; }
        return count;
    }

    private boolean handleMemoryRecall(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.US);
        if (!isMemoryRecallRequest(lower)) return false;
        if (!memoryEnabled()) {
            messages.add(new Msg("user", text, "", "", "", "", replyQuote));
            messages.add(new Msg("assistant", "Memory is currently turned off.", "", "", "", "memory"));
            if (input != null) input.setText("");
            pendingVoiceText = ""; replyQuote = ""; updateReplyChip(); saveCurrentChat(); resetMessageWindowToLatest(); forceAutoScrollBottom = true; renderMessages();
            return true;
        }
        String mem = normalizeMemoryMd(memoryMd()).trim();
        messages.add(new Msg("user", text, "", "", "", "", replyQuote));
        String answer = mem.length() == 0 ? "I don't have anything saved in persistent memory yet." : "What I have in persistent memory:\n\n" + mem.replaceFirst("^# MEMORY\\.md\\s*", "").trim();
        messages.add(new Msg("assistant", answer, "", "", "", "memory"));
        if (input != null) input.setText("");
        pendingVoiceText = "";
        replyQuote = "";
        updateReplyChip();
        saveCurrentChat();
        resetMessageWindowToLatest();
        forceAutoScrollBottom = true;
        renderMessages();
        return true;
    }

    private boolean isMemoryRecallRequest(String lower) {
        String s = lower == null ? "" : lower.replace('’', '\'');
        return s.contains("what do you remember") || s.contains("what you remember") || s.contains("what's in memory") || s.contains("what is in memory") || s.contains("what's in your memory") || s.contains("what is in your memory") || s.contains("what have you remembered") || s.contains("remember about me") || s.contains("show memory") || s.contains("list memory") || s.contains("summarize memory") || s.contains("what do you know about me");
    }

    private String pendingUserMemoryNote(String text) {
        if (!memoryEnabled()) return "";
        String note = explicitMemoryNote(text);
        if (note.length() == 0) note = heuristicMemoryNote(text);
        return note;
    }

    private String explicitMemoryNote(String text) {
        String s = text == null ? "" : text.trim();
        String lower = s.toLowerCase(Locale.US);
        java.util.regex.Matcher remember = java.util.regex.Pattern.compile("(?is)\\b(?:remember|save|memorize)\\b(?:\\s+(?:this|that|for me|to memory|in memory))*\\s+(?:that\\s+)?(.+?)\\s*[?.!]*$").matcher(s);
        if (remember.find()) {
            String note = remember.group(1).trim();
            if (note.length() > 0) return note;
        }
        String[] markers = new String[]{"save this to memory", "remember that", "remember this", "keep this in mind", "keep in mind that", "save that"};
        for (String marker : markers) {
            int at = lower.indexOf(marker);
            if (at >= 0) {
                String note = s.substring(Math.min(s.length(), at + marker.length())).replaceFirst("^[\\s:,-]+", "").trim();
                return note.length() > 0 ? note : s;
            }
        }
        return "";
    }

    private String heuristicMemoryNote(String text) {
        String s = text == null ? "" : text.trim();
        String lower = s.toLowerCase(Locale.US);
        String extracted = extractMemoryFactClause(s);
        if (extracted.length() > 0) return extracted;
        if (lower.matches(".*\\bmy name is\\b.+")) return s;
        if (lower.matches(".*\\bi (prefer|like|love|hate|dislike)\\b.+")) return s;
        if (lower.startsWith("stop being ") || lower.startsWith("don't be ") || lower.startsWith("do not be ")) return "User preference: " + s;
        if (lower.contains("call me ")) return s;
        return "";
    }

    private String extractMemoryFactClause(String text) {
        String s = text == null ? "" : text.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?is)\\b(my name is|my birthday is|my birthday's|call me|i prefer|i like|i love|i hate|i dislike)\\b(.+?)\\s*[?.!]*$").matcher(s);
        if (!m.find()) return "";
        return (m.group(1) + m.group(2)).trim();
    }

    private String appendMemory(String note, String source) {
        if (!memoryEnabled()) return "";
        String clean = canonicalMemoryNote(cleanSearchArtifacts(note == null ? "" : note).replace('\n', ' ').trim(), source);
        if (clean.length() == 0 || clean.length() > 500) return "";
        String existing = normalizeMemoryMd(memoryMd());
        if (existing.toLowerCase(Locale.US).contains(clean.toLowerCase(Locale.US))) return "";
        String line = (memoryEntryCount(existing) + 1) + ". " + clean + " (" + source + ", " + currentDateString() + ")";
        String next = (existing.trim().length() == 0 ? "# MEMORY.md\n\n" : existing.trim() + "\n") + line + "\n";
        if (next.length() > 12000) next = "# MEMORY.md\n\n" + next.substring(Math.max(0, next.length() - 11000));
        writeMemoryMd(next);
        return line;
    }

    private void writeMemoryMd(String text) {
        String next = normalizeMemoryMd(text);
        prefs.edit().putString("memoryMd", next).apply();
        try { FileOutputStream fos = openFileOutput("MEMORY.md", MODE_PRIVATE); fos.write(next.getBytes(StandardCharsets.UTF_8)); fos.close(); } catch (Exception ignored) { }
    }

    private String normalizeMemoryMd(String text) {
        String raw = text == null ? "" : text.trim();
        if (raw.length() == 0) return "";
        raw = raw.replaceFirst("(?is)^#\\s*MEMORY\\.md\\s*", "").trim();
        StringBuilder b = new StringBuilder("# MEMORY.md\n\n");
        HashSet<String> seen = new HashSet<String>();
        int n = 1;
        for (String line : raw.split("\\n")) {
            String entry = line.trim();
            if (entry.length() == 0) continue;
            entry = entry.replaceFirst("^(?:[-*]|[0-9]+[.)])\\s+", "").trim();
            entry = canonicalMemoryNote(entry, "user");
            String key = memoryDedupeKey(entry);
            if (entry.length() > 0 && key.length() > 0 && !seen.contains(key)) { seen.add(key); b.append(n++).append(". ").append(entry).append('\n'); }
        }
        return n == 1 ? "" : b.toString();
    }

    private String canonicalMemoryNote(String note, String source) {
        String s = note == null ? "" : note.trim();
        if (s.length() == 0) return "";
        String requested = explicitMemoryNote(s);
        if (requested.length() > 0 && !requested.equals(s)) s = requested;
        s = s.replaceFirst("^[\"'`]+", "").replaceFirst("[\"'`?.!]+$", "").trim();
        String lower = s.toLowerCase(Locale.US);
        s = s.replaceFirst("(?i)^users\\b", "User's");
        if (lower.startsWith("user ") || lower.startsWith("user's ") || lower.startsWith("users ") || lower.startsWith("user preference:")) return s;
        if (lower.startsWith("my ")) return "User's " + s.substring(3);
        if (lower.startsWith("i am ")) return "User is " + s.substring(5);
        if (lower.startsWith("i'm ")) return "User is " + s.substring(4);
        if (lower.startsWith("i was ")) return "User was " + s.substring(6);
        if (lower.startsWith("i prefer ")) return "User prefers " + s.substring(9);
        if (lower.startsWith("i like ")) return "User likes " + s.substring(7);
        if (lower.startsWith("i love ")) return "User loves " + s.substring(7);
        if (lower.startsWith("i hate ")) return "User hates " + s.substring(7);
        if (lower.startsWith("i dislike ")) return "User dislikes " + s.substring(10);
        if (lower.startsWith("call me ")) return "User wants to be called " + s.substring(8);
        return s;
    }

    private String memoryDedupeKey(String s) { return (s == null ? "" : s).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").replaceAll("\\buser s\\b", "users").trim(); }

    private String currentDateString() { return new java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).format(new java.util.Date()); }

    private String memoryToolNote(String text) {
        String s = text == null ? "" : text;
        String lower = s.toLowerCase(Locale.US);
        if (!lower.contains("save_memory")) return "";
        java.util.regex.Matcher param = java.util.regex.Pattern.compile("(?is)<parameter(?:\\s+name\\s*=\\s*[\"']?note[\"']?|\\s*=\\s*note)[^>]*>(.*?)</parameter>").matcher(s);
        if (param.find()) {
            String note = cleanMemoryToolNote(param.group(1));
            if (note.length() > 0) return note;
        }
        String[] markers = new String[]{"<parameter=note>", "note:", "note=", "\"note\":"};
        for (String marker : markers) {
            int at = lower.indexOf(marker);
            if (at < 0) continue;
            int start = at + marker.length(), end = s.length();
            String[] stops = new String[]{"</parameter>", "</function>", "</tool_call>", "\n"};
            for (String stop : stops) { int cut = lower.indexOf(stop, start); if (cut >= 0) end = Math.min(end, cut); }
            String note = cleanMemoryToolNote(s.substring(start, end));
            if (note.length() > 0) return note;
        }
        return "";
    }

    private String cleanMemoryToolNote(String note) {
        return (note == null ? "" : note).replace("&quot;", "\"").replace("&apos;", "'").replace("\"", "").replace("'", "").trim();
    }

    private String stripToolCalls(String text) { return (text == null ? "" : text).replaceAll("(?is)<tool_call>.*?</tool_call>", "").trim(); }

    private String searchQuery(String text) {
        String trimmed = text == null ? "" : text.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith("/search ")) return trimmed.substring(8).trim();
        if (!prefs.getBoolean("autoSearch", false)) return "";
        if (!isLikelyQuestion(lower)) return "";
        if (lower.contains("right now") || lower.contains("currently") || lower.contains("at the moment") || lower.contains("as of now") || lower.contains("today") || lower.contains("latest") || lower.contains("recent") || lower.contains("newest") || lower.contains("current ") || lower.contains("current-") || lower.contains("news") || lower.contains("this week") || lower.contains("this month") || lower.contains("2025") || lower.contains("2026")) return trimmed;
        return "";
    }

    private boolean shouldVoiceAutoSearch(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.US);
        return lower.contains("search for") || lower.contains("search the web") || lower.contains("web search") || lower.contains("look up") || lower.contains("google") || lower.contains("today") || lower.contains("right now") || lower.contains("current") || lower.contains("latest") || lower.contains("news") || lower.contains("this week") || lower.contains("this month");
    }

    private boolean isLikelyQuestion(String lower) {
        return lower.contains("?") || lower.startsWith("what ") || lower.startsWith("who ") || lower.startsWith("when ") || lower.startsWith("where ") || lower.startsWith("why ") || lower.startsWith("how ") || lower.startsWith("is ") || lower.startsWith("are ") || lower.startsWith("can ") || lower.startsWith("does ") || lower.startsWith("do ") || lower.startsWith("did ") || lower.startsWith("which ");
    }

    private String webSearch(String query) throws Exception {
        return "brave".equals(searchProvider()) ? braveSearch(query) : jinaSearch(query);
    }

    private String jinaSearch(String query) throws Exception {
        String encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20");
        HttpURLConnection c = (HttpURLConnection) new URL("https://s.jina.ai/" + encoded).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "text/plain");
        String jinaKey = prefs.getString("jinaApiKey", "");
        if (jinaKey.length() > 0) c.setRequestProperty("Authorization", "Bearer " + jinaKey);
        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new RuntimeException(raw);
        return raw.trim();
    }

    private String braveSearch(String query) throws Exception {
        String key = prefs.getString("braveApiKey", "");
        if (key.length() == 0) throw new RuntimeException("add brave api key");
        String encoded = URLEncoder.encode(query, "UTF-8");
        HttpURLConnection c = (HttpURLConnection) new URL("https://api.search.brave.com/res/v1/web/search?q=" + encoded + "&count=8").openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("X-Subscription-Token", key);
        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new RuntimeException(raw);
        JSONObject o = new JSONObject(raw);
        JSONArray results = o.optJSONObject("web") == null ? null : o.optJSONObject("web").optJSONArray("results");
        if (results == null) return raw;
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.optJSONObject(i);
            if (r == null) continue;
            b.append(i + 1).append(". ").append(r.optString("title", "untitled")).append('\n');
            b.append(r.optString("url", "")).append('\n');
            String desc = r.optString("description", "");
            if (desc.length() > 0) b.append(desc).append('\n');
            b.append('\n');
        }
        return b.toString().trim();
    }

    private void chooseModel() {
        if (myModels.size() == 0) {
            toast("add models in settings");
            pane = 2;
            renderPane();
            return;
        }
        final Dialog d = panel("models");
        LinearLayout box = panelBox();
        box.addView(panelTitle("models"));
        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(list);
        for (int i = 0; i < myModels.size(); i++) {
            final String m = myModels.get(i);
            TextView item = panelItem(shortModel(m), "");
            if (m.equals(selectedModel())) item.setText(shortModel(m) + " *");
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); setModel(m); } });
            list.addView(item);
        }
        box.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView cancel = panelAction("cancel");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(48)));
        showPanel(d, box);
    }

    private void addModel() {
        final Dialog d = panel("model");
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(46), dp(34), dp(46), 0);
        box.setBackgroundColor(Color.BLACK);
        TextView back = text("<", 30, Color.WHITE);
        back.setGravity(Gravity.CENTER_VERTICAL);
        back.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(back, new LinearLayout.LayoutParams(dp(48), dp(54)));
        final EditText search = panelEdit("");
        setTextPx(search, 20);
        search.setPadding(dp(26), 0, dp(26), 0);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        search.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, android.view.KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_SEARCH || actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_GO || actionId == 0) {
                    hideKeyboardFrom(search);
                    search.clearFocus();
                    return true;
                }
                return false;
            }
        });
        box.addView(search, new LinearLayout.LayoutParams(-1, dp(58)));
        box.addView(space(24));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        ScrollView s = new ScrollView(this);
        s.addView(list);
        s.setVerticalScrollBarEnabled(false);
        box.addView(s, new LinearLayout.LayoutParams(-1, 0, 1));
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() { @Override public void run() {
            list.removeAllViews();
            String q = search.getText().toString().toLowerCase(Locale.US).trim();
            if (q.length() < 2) return;
            int shown = 0;
            for (int i = 0; i < models.size(); i++) {
                final String m = models.get(i);
                if (modelMatches(m, q)) {
                    TextView item = searchResultItem(shortModel(m), modelSourceLabel(m));
                    item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); addMyModel(m); } });
                    list.addView(item);
                    shown++;
                    if (shown >= 12) break;
                }
            }
            final String typed = search.getText().toString().trim();
            if (typed.length() > 1 && !myModels.contains(typed)) {
                TextView item = searchResultItem("use " + shortModel(typed), customEndpointBase().length() > 0 ? "endpoint" : "openrouter");
                item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); addMyModel(typed); } });
                list.addView(item);
            }
        } };
        search.addTextChangedListener(new TextWatcher() { @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { } @Override public void onTextChanged(CharSequence s, int st, int b, int c) { render[0].run(); } @Override public void afterTextChanged(Editable e) { } });
        showFullPanel(d, box);
        search.requestFocus();
        search.postDelayed(new Runnable() { @Override public void run() { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(search, InputMethodManager.SHOW_IMPLICIT); } }, 200);
    }

    private void setModel(String m) { if (!myModels.contains(m)) return; prefs.edit().putString("model", m).putBoolean("modelSelected", true).apply(); renderPane(); }

    private void addMyModel(String m) {
        m = m == null ? "" : m.trim();
        if (m.length() == 0) return;
        if (!models.contains(m)) { models.add(0, m); modelSources.put(m, customEndpointBase().length() > 0 ? "custom" : "openrouter"); if (customEndpointBase().length() > 0) modelEndpoints.put(m, customEndpointBase()); saveModels(); saveModelSources(); saveModelEndpoints(); }
        if (!myModels.contains(m)) { myModels.add(m); saveMyModels(); }
        toast("added " + shortModel(m));
        if (pane == 2) showSettingsPane();
    }

    private void removeMyModel(String m) {
        myModels.remove(m);
        saveMyModels();
        if (m.equals(selectedModel())) prefs.edit().remove("model").putBoolean("modelSelected", false).apply();
        showSettingsPane();
    }

    private boolean modelMatches(String model, String query) {
        String q = query.toLowerCase(Locale.US).trim();
        String full = model.toLowerCase(Locale.US);
        String shortName = shortModel(model).toLowerCase(Locale.US);
        String meta = modelSearchText.containsKey(model) ? modelSearchText.get(model).toLowerCase(Locale.US) : "";
        return full.contains(q) || shortName.contains(q) || meta.contains(q) || normalized(full).contains(normalized(q)) || normalized(meta).contains(normalized(q));
    }

    private String normalized(String s) {
        StringBuilder out = new StringBuilder();
        String lower = s.toLowerCase(Locale.US);
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9')) out.append(c);
        }
        return out.toString();
    }

    private void refreshModels() {
        saveApiKey(); final String key = savedApiKey(); final ArrayList<String> endpoints = new ArrayList<String>(customEndpoints); String typedEndpoint = endpointInput == null ? "" : normalizeEndpoint(endpointInput.getText().toString()); if (typedEndpoint.length() > 0 && !endpoints.contains(typedEndpoint)) endpoints.add(typedEndpoint);
        if (key.length() == 0 && endpoints.size() == 0) { toast("add key or endpoint"); return; }
        new Thread(new Runnable() { @Override public void run() {
            try {
                final ArrayList<String> found = new ArrayList<String>();
                final HashMap<String, Integer> foundContexts = new HashMap<String, Integer>();
                final HashMap<String, String> foundSources = new HashMap<String, String>();
                final HashMap<String, String> foundEndpoints = new HashMap<String, String>();
                final HashSet<String> foundAudioOutput = new HashSet<String>();
                final HashSet<String> foundAudioInput = new HashSet<String>();
                final HashSet<String> foundReasoning = new HashSet<String>();
                final HashSet<String> foundSpeed = new HashSet<String>();
                if (key.length() > 0) fetchModelsInto(OPENROUTER_ENDPOINT, key, "openrouter", found, foundContexts, foundSources, foundEndpoints, foundAudioOutput, foundAudioInput, foundReasoning, foundSpeed);
                for (String endpoint : endpoints) fetchModelsInto(endpoint, "", "custom", found, foundContexts, foundSources, foundEndpoints, foundAudioOutput, foundAudioInput, foundReasoning, foundSpeed);
                runOnUiThread(new Runnable() { @Override public void run() {
                    for (String endpoint : endpoints) if (!customEndpoints.contains(endpoint)) customEndpoints.add(endpoint);
                    models.clear(); models.addAll(found); modelContexts.clear(); modelContexts.putAll(foundContexts); modelSources.clear(); modelSources.putAll(foundSources); modelEndpoints.clear(); modelEndpoints.putAll(foundEndpoints); audioOutputModels.clear(); audioOutputModels.addAll(foundAudioOutput); audioInputModels.clear(); audioInputModels.addAll(foundAudioInput); reasoningModels.clear(); reasoningModels.addAll(foundReasoning); speedModels.clear(); speedModels.addAll(foundSpeed);
                    for (String m : found) if ("custom".equals(foundSources.get(m)) && !myModels.contains(m)) myModels.add(m);
                    saveCustomEndpoints(); saveModels(); saveModelContexts(); saveModelSources(); saveModelEndpoints(); saveAudioOutputModels(); saveAudioInputModels(); saveReasoningModels(); saveSpeedModels(); saveMyModels(); toast("models updated"); renderPane();
                } });
            } catch (Exception e) { final String msg = friendlyError(e); runOnUiThread(new Runnable() { @Override public void run() { toast("models failed: " + msg); } }); }
        } }).start();
    }

    private void fetchModelsInto(String endpoint, String key, String source, ArrayList<String> found, HashMap<String, Integer> foundContexts, HashMap<String, String> foundSources, HashMap<String, String> foundEndpoints, HashSet<String> foundAudioOutput, HashSet<String> foundAudioInput, HashSet<String> foundReasoning, HashSet<String> foundSpeed) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(normalizeEndpoint(endpoint) + "/models").openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if ("custom".equals(source) && (code == 404 || code == 405)) return;
        if (code >= 400) throw new RuntimeException(raw);
        JSONArray data = new JSONObject(raw).getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            JSONObject model = data.getJSONObject(i);
            String id = model.getString("id");
            String name = model.optString("name", "");
            String description = model.optString("description", "");
            if (!found.contains(id)) found.add(id);
            foundSources.put(id, source);
            modelSearchText.put(id, (name.length() > 0 ? name : shortModel(id)) + "\n" + id + "\n" + description);
            if ("custom".equals(source)) foundEndpoints.put(id, normalizeEndpoint(endpoint));
            if (foundAudioOutput != null && hasAudioOutput(model, id, name)) foundAudioOutput.add(id);
            if (foundAudioInput != null && hasAudioInput(model, id, name, description)) foundAudioInput.add(id);
            if (foundReasoning != null && hasReasoningCapability(model, id, name, description)) foundReasoning.add(id);
            if (foundSpeed != null && hasSpeedParameter(model, id, name, description)) foundSpeed.add(id);
            int context = model.optInt("context_length", 0);
            JSONObject top = model.optJSONObject("top_provider");
            if (context <= 0 && top != null) context = top.optInt("context_length", 0);
            if (context > 0) foundContexts.put(id, context);
        }
    }

    private boolean hasAudioOutput(JSONObject model, String id, String name) {
        JSONObject arch = model.optJSONObject("architecture");
        JSONArray output = arch == null ? null : arch.optJSONArray("output_modalities");
        if (output != null) {
            for (int i = 0; i < output.length(); i++) if ("audio".equalsIgnoreCase(output.optString(i))) return true;
            return false;
        }
        String haystack = (id + " " + name + " " + model.optString("description", "")).toLowerCase(Locale.US);
        return haystack.contains("tts") || haystack.contains("text-to-speech") || haystack.contains("speech") || haystack.contains("audio") || haystack.contains("lyria");
    }

    private boolean hasAudioInput(JSONObject model, String id, String name, String description) {
        JSONObject arch = model.optJSONObject("architecture");
        JSONArray input = arch == null ? null : arch.optJSONArray("input_modalities");
        if (input != null) {
            for (int i = 0; i < input.length(); i++) if ("audio".equalsIgnoreCase(input.optString(i))) return true;
            return false;
        }
        String haystack = (id + " " + name + " " + description).toLowerCase(Locale.US);
        return haystack.contains("audio input") || haystack.contains("audio-input") || haystack.contains("speech-to-text") || haystack.contains("transcrib") || haystack.contains("whisper") || haystack.contains("stt") || haystack.contains("asr");
    }

    private boolean hasReasoningCapability(JSONObject model, String id, String name, String description) {
        JSONArray params = model.optJSONArray("supported_parameters");
        if (params != null) for (int i = 0; i < params.length(); i++) {
            String p = params.optString(i).toLowerCase(Locale.US);
            if (p.equals("reasoning") || p.equals("include_reasoning") || p.equals("reasoning_effort")) return true;
        }
        String haystack = (id + " " + name + " " + description).toLowerCase(Locale.US);
        return haystack.contains("reasoning") || haystack.contains("thinking") || haystack.contains("deepseek-r1") || haystack.contains("r1-") || haystack.endsWith("/r1") || haystack.contains("qwq") || haystack.contains("qwen3") || haystack.contains("qwen-3") || haystack.contains("qwen/qwen3") || haystack.contains("minimax") || haystack.contains("magistral") || haystack.contains("glm-4.5") || haystack.contains("sonar-reasoning") || haystack.contains("kimi-k2-thinking") || haystack.contains("o1") || haystack.contains("o3") || haystack.contains("o4-");
    }

    private boolean hasSpeedParameter(JSONObject model, String id, String name, String description) {
        JSONArray params = model.optJSONArray("supported_parameters");
        if (params != null) for (int i = 0; i < params.length(); i++) if ("speed".equalsIgnoreCase(params.optString(i))) return true;
        String haystack = (id + " " + name + " " + description).toLowerCase(Locale.US);
        return haystack.contains("kokoro") || haystack.contains("tts-1") || haystack.contains("gpt-4o-mini-tts");
    }

    private void testCustomEndpoint() {
        saveApiKey();
        final String typed = endpointInput == null ? "" : normalizeEndpoint(endpointInput.getText().toString());
        final String endpoint = typed.length() > 0 ? typed : customEndpointBase();
        if (endpoint.length() == 0) { toast("add endpoint"); return; }
        toast("testing endpoint");
        new Thread(new Runnable() { @Override public void run() {
            try {
                ArrayList<String> found = new ArrayList<String>();
                HashMap<String, Integer> contexts = new HashMap<String, Integer>();
                HashMap<String, String> sources = new HashMap<String, String>();
                HashMap<String, String> endpoints = new HashMap<String, String>();
                fetchModelsInto(endpoint, "", "custom", found, contexts, sources, endpoints, null, null, null, null);
                final int count = found.size();
                runOnUiThread(new Runnable() { @Override public void run() { toast(count == 0 ? "endpoint connected: audio/no models" : "endpoint connected: " + count + " models"); } });
            } catch (Exception e) {
                final String msg = friendlyError(e);
                runOnUiThread(new Runnable() { @Override public void run() { toast("endpoint failed: " + msg); } });
            }
        } }).start();
    }

    private String customEndpointBase() { return customEndpoints.size() == 0 ? "" : customEndpoints.get(0); }
    private String normalizeEndpoint(String endpoint) { String e = endpoint == null ? "" : endpoint.trim(); while (e.endsWith("/")) e = e.substring(0, e.length() - 1); return e; }
    private String chatCompletionsUrl(String source) { return chatCompletionsUrl(source, selectedModel()); }
    private String chatCompletionsUrl(String source, String model) { return (source.equals("custom") ? modelEndpoint(model) : OPENROUTER_ENDPOINT) + "/chat/completions"; }

    private void addEndpointFromInput() {
        if (endpointInput == null) return;
        String endpoint = normalizeEndpoint(endpointInput.getText().toString());
        if (endpoint.length() == 0) { toast("enter endpoint"); return; }
        if (!customEndpoints.contains(endpoint)) customEndpoints.add(endpoint);
        saveCustomEndpoints();
        if ("endpoint".equals(voiceOutputProvider()) && (prefs.getString("voiceTtsEndpoint", "").trim().length() == 0 || endpoint.equals(voiceTtsEndpoint()))) discoverVoiceEndpoint(endpoint);
        endpointInput.setText("");
        toast("endpoint added");
        showSettingsPane();
    }

    private void removeCustomEndpoint(String endpoint) {
        endpoint = normalizeEndpoint(endpoint);
        boolean removedSelected = false;
        customEndpoints.remove(endpoint);
        for (int i = models.size() - 1; i >= 0; i--) {
            String m = models.get(i);
            if (endpoint.equals(modelEndpoints.get(m))) { if (m.equals(selectedModel())) removedSelected = true; models.remove(i); myModels.remove(m); modelContexts.remove(m); modelSources.remove(m); modelEndpoints.remove(m); }
        }
        if (removedSelected) prefs.edit().remove("model").putBoolean("modelSelected", false).apply();
        saveCustomEndpoints(); saveModels(); saveMyModels(); saveModelContexts(); saveModelSources(); saveModelEndpoints();
    }

    private void initTts() {
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() { @Override public void onInit(int status) {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setLanguage(Locale.getDefault());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { runOnUiThread(new Runnable() { @Override public void run() { updateVoiceStatus("speaking"); } }); }
                    @Override public void onDone(String id) { runOnUiThread(new Runnable() { @Override public void run() { if (activeTtsOwner != null) { activeTtsOwner.ttsPlaying = false; activeTtsOwner.ttsRequested = false; } finishVoiceResponse(); } }); }
                    @Override public void onError(String id) { runOnUiThread(new Runnable() { @Override public void run() { if (activeTtsOwner != null) { activeTtsOwner.ttsPlaying = false; activeTtsOwner.ttsRequested = false; } updateVoiceStatus("speech failed"); } }); }
                });
            }
        } });
    }

    private void startVoice() { startVoice(false); }

    private void startVoice(boolean fullMode) {
        voiceSession++;
        voiceMode = true;
        voiceFullMode = fullMode || hookVoiceMode;
        requestVoiceAudioFocus();
        if (voiceFullMode) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        pane = 1;
        if (!hookVoiceMode) renderPane();
        showVoiceOverlay();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, VOICE); return; }
        beginListening();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == VOICE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) beginListening();
        else if (requestCode == VOICE) updateVoiceStatus("mic permission denied");
    }

    private void beginListening() {
        if (voiceReply != null) voiceReply.setVisibility(View.GONE);
        voiceAwaitingSpeechResult = true;
        vibrateListenStarted();
        if (voiceFullMode) renderVoiceConversation();
        startVoiceThinking("listening...");
        scheduleVoiceListenTimeout();
        String provider = voiceInputProvider();
        if ("openrouter".equals(provider) || "endpoint".equals(provider)) { beginFallbackRecording(); return; }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { if ("auto".equals(provider)) beginFallbackRecording(); else { voiceAwaitingSpeechResult = false; stopVoiceThinking(); updateVoiceStatus("no speech recognizer"); if (voiceFullMode && voiceReply != null) voiceReply.setVisibility(View.VISIBLE); } return; }
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { updateVoiceStatus("listening"); setVoiceLevel(0.15f); }
            @Override public void onBeginningOfSpeech() { updateVoiceStatus("listening"); }
            @Override public void onRmsChanged(float rmsdB) { setVoiceLevel(Math.max(0.05f, Math.min(1f, (rmsdB + 2f) / 12f))); }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { vibrateInputEnded(); updateVoiceStatus("thinking"); setVoiceLevel(0.05f); fadeVoiceWaves(); }
            @Override public void onError(int error) { handleVoiceMiss(); }
            @Override public void onResults(Bundle results) { handleVoiceResults(results); }
            @Override public void onPartialResults(Bundle partialResults) { showPartialVoice(partialResults); }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, getPackageName());
        updateVoiceStatus("listening");
        speechRecognizer.startListening(i);
    }

    private void scheduleVoiceListenTimeout() {
        final int session = voiceSession;
        final int run = ++voiceListenRun;
        ui.postDelayed(new Runnable() { @Override public void run() {
            if (!voiceSessionActive(session) || run != voiceListenRun || !voiceAwaitingSpeechResult) return;
            if (recordingFallback) { stopRecorder(true); return; }
            if (speechRecognizer != null) {
                updateVoiceStatus("thinking");
                try { speechRecognizer.stopListening(); } catch (Exception ignored) { }
                ui.postDelayed(new Runnable() { @Override public void run() { if (voiceSessionActive(session) && run == voiceListenRun && voiceAwaitingSpeechResult) handleVoiceMiss(); } }, 1800);
            } else handleVoiceMiss();
        } }, 12000);
    }

    private void finishListeningNow() {
        if (!voiceMode || !voiceFullMode || !voiceAwaitingSpeechResult) return;
        vibrateInputEnded();
        updateVoiceStatus("thinking");
        fadeVoiceWaves();
        if (recordingFallback) { stopRecorder(true); return; }
        if (speechRecognizer != null) {
            try { speechRecognizer.stopListening(); } catch (Exception ignored) { }
            final int session = voiceSession, run = voiceListenRun;
            ui.postDelayed(new Runnable() { @Override public void run() { if (voiceSessionActive(session) && run == voiceListenRun && voiceAwaitingSpeechResult) handleVoiceMiss(); } }, 1800);
        }
    }

    private void handleVoiceResults(Bundle results) {
        voiceAwaitingSpeechResult = false;
        ArrayList<String> r = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (r == null || r.size() == 0) { handleVoiceMiss(); return; }
        String text = r.get(0).trim();
        submitVoiceText(text);
    }

    private void showPartialVoice(Bundle partialResults) {
        ArrayList<String> r = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (r != null && r.size() > 0) setVoiceText(r.get(0));
    }

    private void handleVoiceMiss() {
        voiceAwaitingSpeechResult = false;
        stopVoiceThinking();
        setVoiceLevel(0f);
        if (voiceFullMode) {
            updateVoiceStatus("paused");
            renderVoiceConversation();
            if (voiceReply != null) voiceReply.setVisibility(View.VISIBLE);
        } else updateVoiceStatus("didn't catch that");
    }

    private void submitVoiceText(String text) {
        voiceAwaitingSpeechResult = false;
        String clean = cleanVoiceTranscript(text);
        if (clean.length() == 0) { handleVoiceMiss(); return; }
        if (isModelRefusal(clean)) { pauseVoiceAfterProviderFailure("model refused", "model refused\n" + clean); return; }
        if (voiceFullMode && isBogusVoiceTranscript(clean)) { handleVoiceMiss(); return; }
        setVoiceText(voiceFullMode ? "you\n" + clean : clean);
        pendingVoiceText = clean;
        if (input != null) input.setText(clean);
        updateVoiceStatus("thinking");
        startVoiceThinking("thinking");
        fadeVoiceWaves();
        closeCompactVoiceOverlay();
        if (!send()) stopVoiceThinking();
    }

    private boolean isBogusVoiceTranscript(String text) {
        String t = text.toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        if (t.length() == 0) return true;
        if (t.equals("you") || t.equals("thank you") || t.equals("thank you very much") || t.equals("thanks for watching") || t.equals("bye") || t.equals("goodbye")) return true;
        String[] words = t.split(" ");
        return words.length <= 1 && t.length() < 5;
    }

    private void beginFallbackRecording() {
        if (!"openrouter".equals(voiceInputProvider()) && voiceTranscriptionEndpoint().length() == 0) { voiceAwaitingSpeechResult = false; stopVoiceThinking(); updateVoiceStatus("add voice endpoint"); return; }
        if (shouldUseSingleMultimodalVoice() || shouldUseOpenRouterChatAudioTranscription()) { beginWavRecording(); return; }
        try {
            stopRecorder(false);
            voiceFile = new File(getCacheDir(), "voice.m4a");
            mediaRecorder = new MediaRecorder();
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mediaRecorder.setAudioSamplingRate(16000);
            mediaRecorder.setAudioEncodingBitRate(64000);
            mediaRecorder.setOutputFile(voiceFile.getAbsolutePath());
            mediaRecorder.prepare();
            mediaRecorder.start();
            recordingFallback = true;
            recorderSpeechFrames = 0;
            if (voiceWaves != null) voiceWaves.animate().alpha(1f).setDuration(120).start();
            recordingStartedAt = System.currentTimeMillis();
            quietSince = 0;
            updateVoiceStatus("recording");
            startVoiceThinking("listening...");
            if (voiceFullMode) renderVoiceConversation(); else setVoiceText("");
            animateRecorderLevel();
        } catch (Exception e) {
            recordingFallback = false;
            voiceAwaitingSpeechResult = false;
            stopVoiceThinking();
            updateVoiceStatus("record failed");
        }
    }

    private void animateRecorderLevel() {
        if (!recordingFallback || mediaRecorder == null) return;
        try {
            float level = Math.max(0.05f, Math.min(1f, mediaRecorder.getMaxAmplitude() / 14000f));
            setVoiceLevel(level);
            if (level > 0.13f) recorderSpeechFrames++;
            long now = System.currentTimeMillis();
            if (now - recordingStartedAt > 12000) { stopRecorder(true); return; }
            if (now - recordingStartedAt > 1400) {
                if (level < 0.08f) {
                    if (quietSince == 0) quietSince = now;
                    if (now - quietSince > 1500) { stopRecorder(true); return; }
                } else quietSince = 0;
            }
        } catch (Exception ignored) { }
        ui.postDelayed(new Runnable() { @Override public void run() { animateRecorderLevel(); } }, 80);
    }

    private void beginWavRecording() {
        try {
            stopRecorder(false);
            final int sampleRate = 16000;
            int min = AudioRecord.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            final int bufferSize = Math.max(min, sampleRate / 5 * 2);
            voiceFile = new File(getCacheDir(), "voice-" + System.nanoTime() + ".wav");
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC, sampleRate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            audioRecord.startRecording();
            wavRecording = true;
            wavSubmitAfterStop = false;
            recordingFallback = true;
            recorderSpeechFrames = 0;
            recordingStartedAt = System.currentTimeMillis();
            quietSince = 0;
            if (voiceWaves != null) voiceWaves.animate().alpha(1f).setDuration(120).start();
            updateVoiceStatus("recording wav");
            startVoiceThinking("listening...");
            if (voiceFullMode) renderVoiceConversation(); else setVoiceText("");
            wavThread = new Thread(new Runnable() { @Override public void run() { recordWavLoop(sampleRate, bufferSize); } });
            wavThread.start();
        } catch (Exception e) {
            wavRecording = false;
            recordingFallback = false;
            voiceAwaitingSpeechResult = false;
            stopVoiceThinking();
            updateVoiceStatus("record failed");
        }
    }

    private void recordWavLoop(int sampleRate, int bufferSize) {
        int pcmBytes = 0;
        try {
            RandomAccessFile out = new RandomAccessFile(voiceFile, "rw");
            writeWavHeader(out, sampleRate, 0);
            byte[] buf = new byte[bufferSize];
            while (wavRecording && audioRecord != null) {
                int n = audioRecord.read(buf, 0, buf.length);
                if (n <= 0) continue;
                out.write(buf, 0, n);
                pcmBytes += n;
                int max = 0;
                for (int i = 0; i + 1 < n; i += 2) {
                    int sample = (short) ((buf[i] & 0xff) | (buf[i + 1] << 8));
                    max = Math.max(max, Math.abs(sample));
                }
                final float level = Math.max(0.05f, Math.min(1f, max / 14000f));
                if (level > 0.13f) recorderSpeechFrames++;
                long now = System.currentTimeMillis();
                if (now - recordingStartedAt > 12000) ui.post(new Runnable() { @Override public void run() { stopRecorder(true); } });
                if (now - recordingStartedAt > 1400) {
                    if (level < 0.08f) {
                        if (quietSince == 0) quietSince = now;
                        if (now - quietSince > 1500) ui.post(new Runnable() { @Override public void run() { stopRecorder(true); } });
                    } else quietSince = 0;
                }
                ui.post(new Runnable() { @Override public void run() { setVoiceLevel(level); } });
            }
            writeWavHeader(out, sampleRate, pcmBytes);
            out.close();
        } catch (Exception ignored) { }
        final boolean submit = wavSubmitAfterStop;
        ui.post(new Runnable() { @Override public void run() { finishWavRecording(submit); } });
    }

    private void finishWavRecording(boolean submit) {
        recordingFallback = false;
        wavSubmitAfterStop = false;
        stopVoiceThinking();
        recordingStartedAt = 0;
        quietSince = 0;
        setVoiceLevel(0.05f);
        if (submit && voiceFile != null && voiceFile.exists()) processRecordedVoiceFile(voiceFile);
    }

    private void writeWavHeader(RandomAccessFile out, int sampleRate, int pcmBytes) throws Exception {
        out.seek(0);
        out.writeBytes("RIFF"); writeLeInt(out, 36 + pcmBytes); out.writeBytes("WAVEfmt "); writeLeInt(out, 16); writeLeShort(out, 1); writeLeShort(out, 1); writeLeInt(out, sampleRate); writeLeInt(out, sampleRate * 2); writeLeShort(out, 2); writeLeShort(out, 16); out.writeBytes("data"); writeLeInt(out, pcmBytes);
        out.seek(44 + pcmBytes);
    }

    private void writePcm16AsWav(File file, byte[] pcm, int sampleRate) throws Exception {
        RandomAccessFile out = new RandomAccessFile(file, "rw");
        writeWavHeader(out, sampleRate, pcm.length);
        out.write(pcm);
        out.close();
    }

    private void writeLeInt(RandomAccessFile out, int v) throws Exception { out.write(v & 0xff); out.write((v >> 8) & 0xff); out.write((v >> 16) & 0xff); out.write((v >> 24) & 0xff); }
    private void writeLeShort(RandomAccessFile out, int v) throws Exception { out.write(v & 0xff); out.write((v >> 8) & 0xff); }

    private void stopRecorder(boolean transcribe) {
        if (transcribe) voiceAwaitingSpeechResult = false;
        if (audioRecord != null) {
            wavSubmitAfterStop = transcribe;
            wavRecording = false;
            try { audioRecord.stop(); } catch (Exception ignored) { }
            try { audioRecord.release(); } catch (Exception ignored) { }
            audioRecord = null;
            return;
        }
        if (mediaRecorder == null) return;
        try { mediaRecorder.stop(); } catch (Exception ignored) { }
        try { mediaRecorder.release(); } catch (Exception ignored) { }
        mediaRecorder = null;
        recordingFallback = false;
        stopVoiceThinking();
        recordingStartedAt = 0;
        quietSince = 0;
        setVoiceLevel(0.05f);
        if (transcribe && voiceFile != null && voiceFile.exists()) processRecordedVoiceFile(voiceFile);
    }

    private void processRecordedVoiceFile(File file) {
        if (voiceFullMode && recorderSpeechFrames < 4) { handleVoiceMiss(); return; }
        vibrateInputEnded(); fadeVoiceWaves();
        if (shouldUseSingleMultimodalVoice()) sendSingleMultimodalVoice(file);
        else transcribeVoiceFile(file);
    }

    private boolean shouldUseSingleMultimodalVoice() {
        if (!voiceMode || !voiceFullMode) return false;
        if (!sameOpenRouterVoiceModelForAllThree()) return false;
        if (!isAllInOneVoiceModel(configuredVoiceAnswerModel())) return false;
        return "openrouter".equals(modelSource(configuredVoiceAnswerModel()));
    }

    private boolean shouldUseOpenRouterChatAudioTranscription() {
        return false;
    }

    private void sendSingleMultimodalVoice(final File file) {
        saveApiKey();
        final String key = savedApiKey();
        final String model = configuredVoiceAnswerModel();
        if (model.length() == 0) { updateVoiceStatus("select a model first"); return; }
        if (key.length() == 0) { updateVoiceStatus("add openrouter key"); return; }
        updateVoiceStatus("thinking");
        startVoiceThinking("thinking");
        final Msg user = new Msg("user", "[voice input]", "", "", "", "", "");
        final Msg assistant = new Msg("assistant", "", "", "", LOADING, shortModel(model));
        assistant.slowVoice = true;
        assistant.voiceSessionId = voiceSession;
        assistant.reasoningCapable = isReasoningModel(model);
        messages.add(user);
        messages.add(assistant);
        saveCurrentChat();
        resetMessageWindowToLatest();
        forceAutoScrollBottom = true;
        renderMessages();
        renderVoiceConversation();
        new Thread(new Runnable() { @Override public void run() { callOpenRouterSingleVoice(key, model, file, assistant); } }).start();
    }

    private void callOpenRouterSingleVoice(String key, String model, File file, final Msg assistant) {
        long start = System.nanoTime();
        try {
            JSONObject body = new JSONObject();
            body.put("model", model);
            body.put("modalities", new JSONArray().put("text").put("audio"));
            body.put("audio", new JSONObject().put("voice", ttsVoiceForModel(model)).put("format", "pcm16"));
            JSONArray arr = new JSONArray();
            String toolMemory = buildToolMemoryContext();
            if (toolMemory.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", toolMemory));
            String folderInstruction = buildFolderInstructionContext();
            if (folderInstruction.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", folderInstruction));
            String userMemory = buildUserMemoryContext();
            if (userMemory.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", userMemory));
            arr.put(new JSONObject().put("role", "system").put("content", "This is a spoken two-way voice conversation. Reply in plain text only. Do not use markdown, headings, bullets, tables, code blocks, or formatting symbols. Keep the response natural for text-to-speech."));
            for (int i = 0; i < messages.size(); i++) {
                Msg m = messages.get(i);
                if (m == assistant || LOADING.equals(m.stats) || "[voice input]".equals(m.text)) continue;
                JSONObject one = new JSONObject(); one.put("role", m.role); one.put("content", requestText(m)); arr.put(one);
            }
            JSONArray content = new JSONArray();
            content.put(new JSONObject().put("type", "text").put("text", "Answer the spoken request from this audio. Keep the response concise and natural for speech."));
            content.put(new JSONObject().put("type", "input_audio").put("input_audio", new JSONObject().put("data", fileBase64(file)).put("format", "wav")));
            arr.put(new JSONObject().put("role", "user").put("content", content));
            body.put("messages", arr);
            body.put("stream", true);
            HttpURLConnection c = (HttpURLConnection) new URL(OPENROUTER_ENDPOINT + "/chat/completions").openConnection();
            c.setRequestMethod("POST"); c.setConnectTimeout(30000); c.setReadTimeout(120000); c.setDoOutput(true);
            c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Content-Type", "application/json");
            c.setRequestProperty("Accept", "text/event-stream, application/json");
            c.setRequestProperty("HTTP-Referer", "https://minimal.chat/android");
            c.setRequestProperty("X-Title", "chat");
            OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes(StandardCharsets.UTF_8)); os.close();
            int code = c.getResponseCode();
            if (code >= 400) throw new RuntimeException(readAll(c.getErrorStream()));
            final StringBuilder answerText = new StringBuilder();
            final StringBuilder transcriptText = new StringBuilder();
            ByteArrayOutputStream audioBytes = new ByteArrayOutputStream();
            BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (!line.startsWith("data:")) continue;
                String data = line.substring(5).trim();
                if ("[DONE]".equals(data)) break;
                JSONObject chunk = new JSONObject(data);
                JSONObject err = chunk.optJSONObject("error");
                if (err != null) throw new RuntimeException(err.optString("message", err.toString()));
                JSONArray choices = chunk.optJSONArray("choices");
                if (choices == null || choices.length() == 0) continue;
                JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
                if (delta == null) continue;
                String contentDelta = cleanJsonString(delta, "content");
                if (contentDelta.length() > 0) answerText.append(contentDelta);
                JSONObject audioObj = delta.optJSONObject("audio");
                if (audioObj != null) {
                    String transcript = audioObj.optString("transcript", "");
                    if (transcript.length() > 0) transcriptText.append(transcript);
                    String audio = stripDataPrefix(audioObj.optString("data", ""));
                    if (audio.length() > 0) audioBytes.write(Base64.decode(audio, Base64.DEFAULT));
                }
            }
            br.close();
            if (audioBytes.size() == 0) throw new RuntimeException("no audio returned");
            final String answer = answerText.length() > 0 ? answerText.toString() : transcriptText.toString();
            if (isModelRefusal(answer)) throw new ModelRefusalException(answer);
            final byte[] spoken = audioBytes.toByteArray();
            final int session = voiceSession;
            int completionTokens = estimateTokens(answer.length() == 0 ? "audio" : answer);
            final String stats = String.format(Locale.US, "%.1f tok/s", completionTokens / Math.max(0.001, (System.nanoTime() - start) / 1e9));
            runOnUiThread(new Runnable() { @Override public void run() {
                if (!voiceSessionActive(session)) return;
                stopVoiceThinking();
                assistant.text = answer.length() == 0 ? "[voice response]" : answer;
                assistant.stats = stats;
                assistant.streamDone = true;
                assistant.ttsStarted = true;
                assistant.ttsPlaying = true;
                activeTtsOwner = assistant;
                forceAutoScrollBottom = true;
                renderMessages();
                saveCurrentChat();
                renderVoiceConversation();
                updateVoiceStatus("speaking");
                playPcm16Audio(spoken, assistant);
            } });
        } catch (ModelRefusalException e) {
            final String msg = detailedError(e);
            final int session = voiceSession;
            runOnUiThread(new Runnable() { @Override public void run() { if (!voiceSessionActive(session)) return; assistant.stats = ""; assistant.text = "model refused\n" + msg; pauseVoiceAfterProviderFailure("model refused", assistant.text); renderMessages(); saveCurrentChat(); } });
        } catch (Exception e) {
            final String msg = detailedError(e);
            final int session = voiceSession;
            runOnUiThread(new Runnable() { @Override public void run() { if (!voiceSessionActive(session)) return; assistant.stats = ""; assistant.text = "multimodal failed\n" + msg; pauseVoiceAfterProviderFailure("failed", assistant.text); renderMessages(); saveCurrentChat(); } });
        }
    }

    private String fileBase64(File file) throws Exception {
        FileInputStream in = new FileInputStream(file);
        byte[] data = bytes(in);
        in.close();
        return Base64.encodeToString(data, Base64.NO_WRAP);
    }

    private String extractMessageText(JSONObject resp) throws Exception {
        JSONArray choices = resp.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";
        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
        if (msg == null) return "";
        Object content = msg.opt("content");
        if (content instanceof String) return ((String) content).trim();
        if (content instanceof JSONArray) {
            StringBuilder b = new StringBuilder();
            JSONArray arr = (JSONArray) content;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject part = arr.optJSONObject(i);
                if (part == null) continue;
                String text = part.optString("text", part.optString("content", ""));
                if (text.length() > 0) { if (b.length() > 0) b.append('\n'); b.append(text); }
            }
            if (b.length() > 0) return b.toString().trim();
        }
        JSONObject audio = msg.optJSONObject("audio");
        return audio == null ? "" : audio.optString("transcript", "").trim();
    }

    private void transcribeVoiceFile(final File file) {
        stopVoiceThinking();
        updateVoiceStatus("transcribing");
        new Thread(new Runnable() { @Override public void run() {
            try {
                final String transcript = transcribeAudio(file);
                runOnUiThread(new Runnable() { @Override public void run() {
                    submitVoiceText(transcript);
                } });
            } catch (ModelRefusalException e) {
                final String msg = detailedError(e);
                runOnUiThread(new Runnable() { @Override public void run() { pauseVoiceAfterProviderFailure("model refused", "model refused\n" + msg); } });
            } catch (Exception e) {
                final String msg = transcriptionError(e);
                runOnUiThread(new Runnable() { @Override public void run() { pauseVoiceAfterProviderFailure("transcribe failed", msg); } });
            }
        } }).start();
    }

    private void pauseVoiceAfterProviderFailure(String status, String detail) {
        stopVoiceThinking();
        updateVoiceStatus(status);
        setVoiceLevel(0f);
        if (voiceReply != null) voiceReply.setVisibility(View.VISIBLE);
        setVoiceText(detail);
    }

    private String transcribeAudio(File file) throws Exception {
        if ("openrouter".equals(voiceInputProvider()) && !isTranscriptionModel(configuredVoiceInputModel())) return transcribeAudioAt(file, OPENROUTER_ENDPOINT + "/audio/transcriptions", "openai/whisper-1");
        ArrayList<String> urls = transcriptionUrls();
        Exception last = null;
        for (String url : urls) {
            try { return transcribeAudioAt(file, url, null); }
            catch (Exception e) { last = e; }
        }
        throw last == null ? new RuntimeException("no transcription endpoint") : last;
    }

    private String transcribeAudioWithChat(File file) throws Exception {
        String key = savedApiKey();
        if (key.length() == 0) throw new RuntimeException("add openrouter key");
        String model = openRouterTranscriptionModel(configuredVoiceInputModel());
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("temperature", 0);
        body.put("max_tokens", 120);
        JSONArray content = new JSONArray();
        content.put(new JSONObject().put("type", "text").put("text", "Transcribe the audio verbatim. Return only the speaker's exact words. Do not answer, explain, apologize, classify, or add commentary. Preserve profanity, insults, slurs, and abusive language exactly as spoken."));
        content.put(new JSONObject().put("type", "input_audio").put("input_audio", new JSONObject().put("data", fileBase64(file)).put("format", "wav")));
        JSONArray messages = new JSONArray();
        messages.put(new JSONObject().put("role", "system").put("content", "You are an ASR transcription engine, not a conversational assistant. Output only the transcript text."));
        messages.put(new JSONObject().put("role", "user").put("content", content));
        body.put("messages", messages);
        HttpURLConnection c = (HttpURLConnection) new URL(OPENROUTER_ENDPOINT + "/chat/completions").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(30000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "application/json");
        c.setRequestProperty("HTTP-Referer", "https://minimal.chat/android");
        c.setRequestProperty("X-Title", "chat");
        OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes(StandardCharsets.UTF_8)); os.close();
        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new RuntimeException(raw);
        String text = cleanVoiceTranscript(parseTranscriptResponse(raw));
        if (isModelRefusal(text)) throw new ModelRefusalException(text);
        return text.length() == 0 ? raw.trim() : text;
    }

    private String transcribeAudioAt(File file, String url, String modelOverride) throws Exception {
        String boundary = "----minimalchat" + System.currentTimeMillis();
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(30000);
        c.setReadTimeout(120000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
        if (url.toLowerCase(Locale.US).contains("openrouter.ai") && savedApiKey().length() > 0) c.setRequestProperty("Authorization", "Bearer " + savedApiKey());
        OutputStream out = c.getOutputStream();
        if ("openrouter".equals(voiceInputProvider()) || url.toLowerCase(Locale.US).contains("openrouter.ai")) writePart(out, boundary, "model", modelOverride == null ? openRouterTranscriptionModel(prefs.getString("voiceTranscribeModel", "whisper-1")) : modelOverride);
        String fileField = url.contains("/asr") ? "audio_file" : "file";
        String fileName = file == null ? "voice.m4a" : file.getName();
        String contentType = fileName.toLowerCase(Locale.US).endsWith(".wav") ? "audio/wav" : "audio/mp4";
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + fileField + "\"; filename=\"" + fileName + "\"\r\nContent-Type: " + contentType + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        FileInputStream in = new FileInputStream(file);
        byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) >= 0) out.write(buf, 0, n); in.close();
        out.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        out.close();
        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new RuntimeException(raw);
        String text = cleanVoiceTranscript(parseTranscriptResponse(raw));
        if (isModelRefusal(text)) throw new ModelRefusalException(text);
        if (text.length() > 0) return text;
        throw new RuntimeException("empty transcription response");
    }

    private String openRouterTranscriptionModel(String model) {
        String m = model == null ? "" : model.trim();
        if ("whisper-1".equalsIgnoreCase(m)) return "openai/whisper-1";
        return m;
    }

    private String parseTranscriptResponse(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.length() == 0) return "";
        try {
            if (s.startsWith("data:")) {
                StringBuilder b = new StringBuilder();
                String[] lines = s.split("\\r?\\n");
                for (String line : lines) {
                    line = line.trim();
                    if (!line.startsWith("data:")) continue;
                    String data = line.substring(5).trim();
                    if ("[DONE]".equals(data)) continue;
                    String part = parseTranscriptResponse(data);
                    if (part.length() > 0) { if (b.length() > 0) b.append(' '); b.append(part); }
                }
                return b.toString().trim();
            }
            if (s.startsWith("{")) {
                JSONObject o = new JSONObject(s);
                String direct = o.optString("text", o.optString("transcript", o.optString("content", "")));
                if (direct.length() > 0) return cleanVoiceTranscript(direct);
                String msg = extractMessageText(o);
                if (msg.length() > 0) return cleanVoiceTranscript(msg);
            }
        } catch (Exception ignored) { }
        if (s.startsWith("--") || s.startsWith("<") || s.startsWith("[")) return "";
        return cleanVoiceTranscript(s);
    }

    private String cleanVoiceTranscript(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.length() == 0) return "";
        s = s.replace('\n', ' ').replaceAll("\\s+", " ").trim();
        String extracted = extractQuotedAfterMarker(s);
        if (extracted.length() > 0) s = extracted;
        String lower = s.toLowerCase(Locale.US);
        String[] prefixes = new String[]{"the audio says", "audio says", "the recording says", "recording says", "the speaker says", "speaker says", "transcript:", "transcription:"};
        for (String p : prefixes) {
            if (lower.startsWith(p)) {
                s = s.substring(p.length()).trim();
                if (s.startsWith(":")) s = s.substring(1).trim();
                break;
            }
        }
        while ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) s = s.substring(1, s.length() - 1).trim();
        return s;
    }

    private boolean isModelRefusal(String text) {
        String t = (text == null ? "" : text).toLowerCase(Locale.US).replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
        if (t.length() == 0) return false;
        if (t.contains("i m sorry") && (t.contains("can t assist") || t.contains("cannot assist") || t.contains("can t help") || t.contains("cannot help") || t.contains("not able to help"))) return true;
        if (t.contains("i can t assist with that") || t.contains("i cannot assist with that") || t.contains("i can t comply") || t.contains("i cannot comply")) return true;
        if (t.contains("i m unable to") && (t.contains("assist") || t.contains("help") || t.contains("comply"))) return true;
        if (t.contains("i can t provide") || t.contains("i cannot provide") || t.contains("i won t provide")) return true;
        return t.contains("let me know if there s something else") || t.contains("what else can i help you with");
    }

    private static class ModelRefusalException extends Exception { ModelRefusalException(String msg) { super(msg); } }

    private String extractQuotedAfterMarker(String s) {
        String lower = s.toLowerCase(Locale.US);
        String[] markers = new String[]{"audio says", "recording says", "speaker says", "transcript", "transcription"};
        for (String marker : markers) {
            int at = lower.indexOf(marker);
            if (at < 0) continue;
            int start = s.indexOf('"', at);
            if (start < 0) start = s.indexOf('\'', at);
            if (start < 0) continue;
            char quote = s.charAt(start);
            int end = s.indexOf(quote, start + 1);
            if (end > start) return s.substring(start + 1, end).trim();
        }
        return "";
    }

    private String transcriptionError(Exception e) {
        String msg = detailedError(e);
        String lower = msg.toLowerCase(Locale.US);
        if (lower.contains("no number after minus sign") || lower.contains("not a valid json")) return "transcription provider returned a non-json response. If using OpenRouter Whisper, refresh models and use openai/whisper-1.";
        return msg;
    }

    private ArrayList<String> transcriptionUrls() {
        ArrayList<String> urls = new ArrayList<String>();
        String endpoint = "openrouter".equals(voiceInputProvider()) ? OPENROUTER_ENDPOINT : voiceTranscriptionEndpoint();
        if (endpoint.length() == 0) return urls;
        String lower = endpoint.toLowerCase(Locale.US);
        if (lower.endsWith("/audio/transcriptions") || lower.endsWith("/transcriptions") || lower.endsWith("/transcribe") || lower.contains("/asr")) urls.add(endpoint);
        else if (lower.endsWith("/v1")) urls.add(endpoint + "/audio/transcriptions");
        else {
            urls.add(endpoint + "/v1/audio/transcriptions");
            urls.add(endpoint + "/audio/transcriptions");
            urls.add(endpoint + "/asr?task=transcribe&output=json");
            urls.add(endpoint + "/transcribe");
        }
        return urls;
    }

    private String voiceTranscriptionEndpoint() { String configured = normalizeEndpoint(prefs.getString("voiceTranscribeEndpoint", "")); return configured.length() > 0 ? configured : customEndpointBase(); }
    private String voiceTtsEndpoint() { String configured = normalizeEndpoint(prefs.getString("voiceTtsEndpoint", "")); return configured.length() > 0 ? configured : ("openrouter".equals(voiceOutputProvider()) ? OPENROUTER_ENDPOINT : customEndpointBase()); }

    private void writePart(OutputStream out, String boundary, String name, String value) throws Exception {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n" + value + "\r\n").getBytes(StandardCharsets.UTF_8));
    }

    private void showVoiceOverlay() {
        if (voiceOverlay != null) screen.removeView(voiceOverlay);
        final boolean full = voiceFullMode || hookVoiceMode;
        voiceOverlay = new FrameLayout(this);
        voiceTextScroll = null;
        voiceBorder = null;
        voiceOverlay.setBackgroundColor(full ? (hookVoiceMode ? Color.argb(150, 0, 0, 0) : Color.argb(232, 0, 0, 0)) : Color.TRANSPARENT);
        if (full) applyVoiceWindowBlur(true);
        if (full) {
            voiceBorder = new BorderWaveView(this);
            voiceOverlay.addView(voiceBorder, new FrameLayout.LayoutParams(-1, -1));
        }
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(full ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL) : Gravity.CENTER_HORIZONTAL);
        box.setPadding(dp(28), full ? dp(28) : dp(12), dp(28), full ? dp(28) : dp(10));
        if (hookVoiceMode) box.setBackgroundColor(Color.argb(138, 0, 0, 0));
        else if (!full) box.setBackground(grayBorder());
        voiceStatus = text("listening", full ? 16 : 14, Color.WHITE);
        voiceStatus.setGravity(Gravity.CENTER);
        if (full) voiceStatus.setPadding(0, 0, 0, dp(5));
        if (full) voiceStatus.setBackgroundColor(Color.TRANSPARENT);
        voiceWaves = new WaveView(this);
        if (full) voiceWaves.setBackgroundColor(Color.TRANSPARENT);
        voiceWaves.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (recordingFallback) stopRecorder(true); } });
        voiceText = text("", full ? 15 : 12, Color.LTGRAY);
        voiceText.setGravity(full ? (Gravity.TOP | Gravity.CENTER_HORIZONTAL) : Gravity.CENTER);
        if (full) voiceText.setPadding(0, dp(8), 0, dp(24));
        voiceText.setLineSpacing(dp(2), 1.0f);
        if (full) voiceText.setBackgroundColor(Color.TRANSPARENT);
        TextView close = text("close", 13, Color.LTGRAY);
        close.setGravity(Gravity.CENTER);
        if (full) close.setBackgroundColor(Color.TRANSPARENT);
        close.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { stopVoiceMode(); } });
        voiceReply = text("reply", 13, Color.WHITE);
        voiceReply.setGravity(Gravity.CENTER);
        voiceReply.setVisibility(View.GONE);
        if (full) voiceReply.setBackgroundColor(Color.TRANSPARENT);
        voiceReply.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { voiceReply.setVisibility(View.GONE); beginListening(); } });
        box.addView(voiceStatus, new LinearLayout.LayoutParams(-1, full ? dp(62) : dp(28)));
        if (!full) box.addView(voiceWaves, new LinearLayout.LayoutParams(-1, dp(64)));
        if (full) {
            voiceTextScroll = new ScrollView(this);
            voiceTextScroll.setVerticalScrollBarEnabled(false);
            voiceTextScroll.setBackgroundColor(Color.TRANSPARENT);
            voiceTextScroll.addView(voiceText, new ScrollView.LayoutParams(-1, -2));
            box.addView(voiceTextScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        } else box.addView(voiceText, new LinearLayout.LayoutParams(-1, dp(44)));
        if (full) box.addView(voiceReply, new LinearLayout.LayoutParams(-1, dp(36)));
        box.addView(close, new LinearLayout.LayoutParams(-1, full ? dp(40) : dp(28)));
        FrameLayout.LayoutParams boxLp = full ? new FrameLayout.LayoutParams(-1, -1, Gravity.CENTER) : new FrameLayout.LayoutParams(-1, dp(178), Gravity.BOTTOM);
        if (!full) boxLp.setMargins(dp(26), 0, dp(26), dp(76));
        voiceOverlay.addView(box, boxLp);
        screen.addView(voiceOverlay, new FrameLayout.LayoutParams(-1, -1));
    }

    private void stopVoiceMode() {
        if (hookVoiceMode) saveCurrentChat();
        voiceSession++;
        voiceAwaitingSpeechResult = false;
        voiceMode = false;
        voiceFullMode = false;
        abandonVoiceAudioFocus();
        applyVoiceWindowBlur(false);
        if (!prefs.getBoolean("keepScreenAwake", false)) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        stopVoiceThinking();
        if (speechRecognizer != null) speechRecognizer.stopListening();
        stopRecorder(false);
        stopAllVoiceAudio();
        if (voiceOverlay != null) { screen.removeView(voiceOverlay); voiceOverlay = null; }
        voiceTextScroll = null;
        voiceBorder = null;
        voiceReply = null;
        if (hookVoiceMode && !isFinishing()) finish();
    }

    private void stopAllVoiceAudio() {
        activeTtsOwner = null;
        ttsReadyFiles.clear();
        pcm16AudioFiles.clear();
        for (Msg m : messages) {
            m.ttsRequested = false;
            m.ttsPlaying = false;
            m.ttsQueue.clear();
        }
        try { if (ttsPlayer != null) { ttsPlayer.setOnCompletionListener(null); ttsPlayer.setOnErrorListener(null); ttsPlayer.stop(); ttsPlayer.release(); } } catch (Exception ignored) { }
        ttsPlayer = null;
        try { if (currentAudioTrack != null) { currentAudioTrack.pause(); currentAudioTrack.flush(); currentAudioTrack.stop(); currentAudioTrack.release(); } } catch (Exception ignored) { }
        currentAudioTrack = null;
        try { if (tts != null) tts.stop(); } catch (Exception ignored) { }
    }

    private void applyVoiceWindowBlur(boolean enabled) {
        if (Build.VERSION.SDK_INT < 31) return;
        try {
            Window w = getWindow();
            if (enabled) {
                w.addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.setBlurBehindRadius(dp(22));
                lp.dimAmount = 0f;
                w.setAttributes(lp);
                w.setBackgroundBlurRadius(dp(16));
            } else {
                w.clearFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND | WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                WindowManager.LayoutParams lp = w.getAttributes();
                lp.setBlurBehindRadius(0);
                lp.dimAmount = 0f;
                w.setAttributes(lp);
                w.setBackgroundBlurRadius(0);
            }
        } catch (Exception ignored) { }
    }

    private boolean voiceSessionActive(int session) { return voiceMode && session == voiceSession; }

    private void requestVoiceAudioFocus() {
        try {
            if (audioManager == null) return;
            if (Build.VERSION.SDK_INT >= 26) {
                AudioAttributes attrs = new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ASSISTANT).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build();
                audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT).setAudioAttributes(attrs).setOnAudioFocusChangeListener(new AudioManager.OnAudioFocusChangeListener() { @Override public void onAudioFocusChange(int focusChange) { } }).build();
                audioManager.requestAudioFocus(audioFocusRequest);
            } else audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
        } catch (Exception ignored) { }
    }

    private void abandonVoiceAudioFocus() {
        try {
            if (audioManager == null) return;
            if (Build.VERSION.SDK_INT >= 26 && audioFocusRequest != null) audioManager.abandonAudioFocusRequest(audioFocusRequest);
            else audioManager.abandonAudioFocus(null);
            audioFocusRequest = null;
        } catch (Exception ignored) { }
    }

    private void closeCompactVoiceOverlay() {
        if (voiceFullMode || hookVoiceMode) return;
        voiceMode = false;
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        if (voiceOverlay != null) { screen.removeView(voiceOverlay); voiceOverlay = null; }
        voiceTextScroll = null;
        voiceBorder = null;
        voiceReply = null;
    }

    private void updateVoiceStatus(String s) { if (voiceStatus != null) voiceStatus.setText(s); }
    private void setVoiceLevel(float level) { if (voiceWaves != null) { voiceWaves.level = level; voiceWaves.invalidate(); } if (voiceBorder != null) { voiceBorder.level = level; voiceBorder.invalidate(); } }
    private void fadeVoiceWaves() { if (voiceWaves != null) voiceWaves.animate().alpha(0f).setDuration(260).start(); }

    private void setVoiceText(String s) {
        if (voiceText == null) return;
        voiceText.setText(markdownText(s == null ? "" : s));
        if (voiceTextScroll != null && !voiceChunkFollowActive()) voiceTextScroll.post(new Runnable() { @Override public void run() { voiceTextScroll.fullScroll(View.FOCUS_DOWN); } });
    }

    private boolean voiceChunkFollowActive() {
        return voiceMode && voiceFullMode && activeTtsOwner != null && (activeTtsOwner.ttsPlaying || activeTtsOwner.ttsRequested || activeTtsOwner.ttsQueue.size() > 0);
    }

    private void followVoiceChunk(final Msg owner, final String chunk) {
        if (!voiceMode || !voiceFullMode || voiceTextScroll == null || voiceText == null || owner == null || chunk == null || chunk.trim().length() == 0) return;
        if (owner.voiceSessionId != 0 && owner.voiceSessionId != voiceSession) return;
        final int session = voiceSession;
        voiceTextScroll.post(new Runnable() { @Override public void run() {
            if (voiceTextScroll == null || voiceText == null || !voiceSessionActive(session)) return;
            String all = voiceText.getText().toString();
            int at = voiceChunkIndex(all, chunk);
            if (at < 0) return;
            android.text.Layout layout = voiceText.getLayout();
            if (layout == null) { voiceText.post(new Runnable() { @Override public void run() { followVoiceChunk(owner, chunk); } }); return; }
            int line = layout.getLineForOffset(Math.max(0, Math.min(at, all.length())));
            View child = voiceTextScroll.getChildAt(0);
            int max = child == null ? 0 : Math.max(0, child.getHeight() - voiceTextScroll.getHeight());
            int y = Math.max(0, layout.getLineTop(line) - voiceTextScroll.getHeight() / 3);
            voiceTextScroll.smoothScrollTo(0, Math.min(max, y));
        } });
    }

    private int voiceChunkIndex(String all, String chunk) {
        if (all == null || chunk == null) return -1;
        String clean = chunk.trim();
        int at = all.indexOf(clean);
        if (at >= 0) return at;
        String compact = clean.replaceAll("\\s+", " ").trim();
        if (compact.length() > 42) compact = compact.substring(0, 42).trim();
        return compact.length() == 0 ? -1 : all.replaceAll("\\s+", " ").indexOf(compact);
    }

    private void renderVoiceConversation() {
        if (voiceText == null) return;
        StringBuilder b = new StringBuilder();
        int start = Math.max(0, messages.size() - 8);
        for (int i = start; i < messages.size(); i++) {
            Msg m = messages.get(i);
            if (m.text.length() == 0 || LOADING.equals(m.stats) && ".".equals(m.text)) continue;
            if (b.length() > 0) b.append("\n\n");
            b.append(m.role.equals("user") ? "you" : "assistant");
            if (m.role.equals("assistant") && m.streamDone && m.memorySaved) b.append("\nmemory saved");
            b.append("\n").append(m.text);
        }
        setVoiceText(b.toString());
    }

    private void startVoiceThinking() { startVoiceThinking("thinking"); }

    private void startVoiceThinking(String word) {
        if (!voiceFullMode || voiceStatus == null) return;
        voiceThinkingWord = word == null || word.length() == 0 ? "thinking" : word;
        voiceThinking = true;
        voiceThinkingRun++;
        animateVoiceThinking(voiceThinkingRun, 0);
    }

    private void stopVoiceThinking() {
        voiceThinking = false;
        voiceThinkingRun++;
        if (voiceStatus != null) voiceStatus.animate().cancel();
        if (voiceStatus != null) voiceStatus.setAlpha(1f);
    }

    private void animateVoiceThinking(final int run, final int step) {
        if (!voiceThinking || run != voiceThinkingRun || voiceStatus == null) return;
        String word = voiceThinkingWord;
        String text = shortModel(activeAnswerModel()) + "\n" + word;
        SpannableString span = new SpannableString(text);
        int start = text.length() - word.length();
        int cycle = word.length() + 4;
        int pos = step % cycle;
        int peak = Math.min(pos, word.length() - 1);
        for (int i = 0; i < word.length(); i++) {
            int dist = Math.abs(i - peak);
            int color = pos >= word.length() ? Color.WHITE : dist == 0 ? Color.WHITE : dist == 1 ? Color.rgb(190,190,190) : Color.rgb(120,120,120);
            span.setSpan(new ForegroundColorSpan(color), start + i, start + i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        voiceStatus.setAlpha(1f);
        voiceStatus.setText(span);
        ui.postDelayed(new Runnable() { @Override public void run() { animateVoiceThinking(run, step + 1); } }, pos >= word.length() ? 260 : 135);
    }

    private void vibrateInputEnded() {
        vibrateShort(24);
    }

    private void vibrateListenStarted() {
        vibrateShort(14);
    }

    private void vibrateShort(long ms) {
        try {
            Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
            if (v == null) return;
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE));
            else v.vibrate(ms);
        } catch (Exception ignored) { }
    }

    private void speakResponse(String text) {
        speakResponse(text, null);
    }

    private void speakResponse(String text, final Msg owner) {
        if (!voiceMode) return;
        setVoiceText(text);
        fadeVoiceWaves();
        if (!voiceFullMode) { updateVoiceStatus("done"); return; }
        if (!prefs.getBoolean("voiceSpeak", true)) { finishVoiceResponse(); return; }
        if (("openrouter".equals(voiceOutputProvider()) || "endpoint".equals(voiceOutputProvider())) && voiceTtsEndpoint().length() > 0) {
            speakEndpointResponse(text, owner);
            return;
        }
        if (!prefs.getBoolean("voiceSpeak", true) || !ttsReady || tts == null) {
            finishVoiceResponse();
            return;
        }
        updateVoiceStatus("speaking");
        setVoiceText(text);
        if (owner != null) owner.ttsStarted = true;
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "assistant-response");
        if (owner != null) { owner.ttsPlaying = true; activeTtsOwner = owner; followVoiceChunk(owner, text); }
    }

    private void speakEndpointResponse(final String text) { speakEndpointResponse(text, null); }

    private void speakEndpointResponse(final String text, final Msg owner) {
        final int session = voiceSession;
        updateVoiceStatus("speaking");
        setVoiceText(text);
        new Thread(new Runnable() { @Override public void run() {
            try {
                final File audio = requestSpeechAudio(text);
                runOnUiThread(new Runnable() { @Override public void run() { if (!voiceSessionActive(session)) return; if (owner != null) { owner.ttsStarted = true; owner.ttsPlaying = true; } playSpeechAudio(audio, text, owner); } });
            } catch (Exception e) {
                final String msg = friendlyError(e);
                runOnUiThread(new Runnable() { @Override public void run() { if (!voiceSessionActive(session)) return; if (owner != null) owner.ttsRequested = false; updateVoiceStatus(isModelRefusal(msg) ? "model refused" : "tts: " + msg); if (voiceText != null) setVoiceText(voiceText.getText().toString() + "\n\n" + (isModelRefusal(msg) ? "model refused: " : "tts: ") + msg); ui.postDelayed(new Runnable() { @Override public void run() { finishVoiceResponse(); } }, 1200); } });
            }
        } }).start();
    }

    private File requestSpeechAudio(String text) throws Exception {
        String endpoint = voiceTtsEndpoint();
        if ("openrouter".equals(voiceOutputProvider())) return requestOpenRouterSpeechAudio(text);
        String lower = endpoint.toLowerCase(Locale.US);
        String url = lower.endsWith("/audio/speech") ? endpoint : (lower.endsWith("/v1") ? endpoint + "/audio/speech" : endpoint + "/v1/audio/speech");
        JSONObject body = new JSONObject();
        String model = prefs.getString("voiceEndpointTtsModel", "").trim();
        if (model.length() == 0) model = "tts-1";
        body.put("model", model);
        String voice = ttsVoiceForModel(model);
        if (model.toLowerCase(Locale.US).contains("voxtral")) body.put("voice_id", voice);
        else body.put("voice", voice);
        body.put("input", text);
        putVoiceSpeedIfSupported(body, model);
        body.put("response_format", "mp3");
        body.put("format", "mp3");
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(30000);
        c.setReadTimeout(90000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "audio/mpeg, audio/wav, audio/*;q=0.9, */*;q=0.1");
        String key = savedApiKey();
        if (endpoint.toLowerCase(Locale.US).contains("openrouter.ai") && key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes(StandardCharsets.UTF_8)); os.close();
        int code = c.getResponseCode();
        if (code >= 400) throw new RuntimeException(readAll(c.getErrorStream()));
        return saveAudioResponse(c, "tts-audio");
    }

    private File requestOpenRouterSpeechAudio(String text) throws Exception {
        String model = openRouterTtsModel(prefs.getString("voiceTtsModel", "openai/gpt-4o-mini-tts"));
        if (model.length() == 0) throw new RuntimeException("select tts model");
        if (!isTtsModel(model)) throw new RuntimeException("selected output model is not audio-output/TTS: " + model);
        ArrayList<String> failures = new ArrayList<String>();
        if (isOpenRouterChatAudioModel(model)) {
            try { return requestOpenRouterChatAudio(text, model); }
            catch (Exception e) { failures.add("chat-stream: " + friendlyError(e)); }
        }
        try { return requestOpenRouterSpeechEndpoint(text, model); }
        catch (Exception e) { failures.add("speech: " + friendlyError(e)); }
        if (!isOpenRouterChatAudioModel(model)) {
            try { return requestOpenRouterChatAudio(text, model); }
            catch (Exception e) { failures.add("chat: " + friendlyError(e)); }
        }
        throw new RuntimeException("all TTS methods failed for " + model + "\n" + join(failures));
    }

    private boolean isOpenRouterChatAudioModel(String model) {
        String lower = model == null ? "" : model.toLowerCase(Locale.US);
        return audioOutputModels.contains(model) && !lower.contains("tts") && !lower.contains("orpheus");
    }

    private File requestOpenRouterChatAudio(String text, String model) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("modalities", new JSONArray().put("text").put("audio"));
        body.put("audio", new JSONObject().put("voice", ttsVoiceForModel(model)).put("format", isOpenRouterChatAudioModel(model) ? "pcm16" : "mp3"));
        JSONArray ttsMessages = new JSONArray();
        ttsMessages.put(new JSONObject().put("role", "system").put("content", "You are a text-to-speech renderer only. Speak the user's text verbatim. Do not answer questions, do not paraphrase, do not summarize, and do not add or remove words."));
        ttsMessages.put(new JSONObject().put("role", "user").put("content", "Speak exactly this text and nothing else:\n<<<TEXT_TO_SPEAK\n" + text + "\nTEXT_TO_SPEAK"));
        body.put("messages", ttsMessages);
        if (isOpenRouterChatAudioModel(model)) body.put("stream", true);
        HttpURLConnection c = (HttpURLConnection) new URL(OPENROUTER_ENDPOINT + "/chat/completions").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(30000);
        c.setReadTimeout(90000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", isOpenRouterChatAudioModel(model) ? "text/event-stream, application/json" : "application/json");
        String key = savedApiKey();
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("HTTP-Referer", "https://minimal.chat/android");
        c.setRequestProperty("X-Title", "chat");
        OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes(StandardCharsets.UTF_8)); os.close();
        int code = c.getResponseCode();
        if (code >= 400) throw new RuntimeException(readAll(c.getErrorStream()));
        if (isOpenRouterChatAudioModel(model)) return saveStreamingPcmAudio(c, text);
        String raw = readAll(c.getInputStream());
        String audio = extractAudioBase64(new JSONObject(raw));
        if (audio.length() == 0) throw new RuntimeException("no audio returned");
        audio = stripDataPrefix(audio);
        File out = uniqueTtsFile(".mp3");
        FileOutputStream fos = new FileOutputStream(out);
        fos.write(Base64.decode(audio, Base64.DEFAULT));
        fos.close();
        return out;
    }

    private File saveStreamingPcmAudio(HttpURLConnection c, String expectedText) throws Exception {
        ByteArrayOutputStream audioBytes = new ByteArrayOutputStream();
        StringBuilder transcript = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.startsWith("data:")) continue;
            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) break;
            JSONObject chunk = new JSONObject(data);
            JSONObject err = chunk.optJSONObject("error");
            if (err != null) throw new RuntimeException(err.optString("message", err.toString()));
            JSONArray choices = chunk.optJSONArray("choices");
            if (choices == null || choices.length() == 0) continue;
            JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
            if (delta == null) continue;
            JSONObject audioObj = delta.optJSONObject("audio");
            if (audioObj == null) continue;
            String t = audioObj.optString("transcript", "");
            if (t.length() > 0) transcript.append(t);
            String audio = stripDataPrefix(audioObj.optString("data", ""));
            if (audio.length() > 0) audioBytes.write(Base64.decode(audio, Base64.DEFAULT));
        }
        br.close();
        if (audioBytes.size() == 0) throw new RuntimeException("no audio returned");
        if (transcript.length() > 0 && textSimilarity(expectedText, transcript.toString()) < 0.55f) throw new RuntimeException("chat-audio output did not read answer verbatim");
        File out = uniqueTtsFile(".pcm");
        FileOutputStream fos = new FileOutputStream(out);
        fos.write(audioBytes.toByteArray());
        fos.close();
        pcm16AudioFiles.add(out.getAbsolutePath());
        return out;
    }

    private String stripDataPrefix(String data) {
        String s = data == null ? "" : data.trim();
        int comma = s.indexOf(',');
        return s.startsWith("data:") && comma >= 0 ? s.substring(comma + 1) : s;
    }

    private float textSimilarity(String a, String b) {
        String[] aw = normalizeCompareText(a).split(" ");
        String nb = " " + normalizeCompareText(b) + " ";
        int total = 0, hit = 0;
        for (String w : aw) {
            if (w.length() < 3) continue;
            total++;
            if (nb.contains(" " + w + " ")) hit++;
        }
        return total == 0 ? 1f : (float) hit / (float) total;
    }

    private String normalizeCompareText(String s) { return (s == null ? "" : s.toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ")).trim(); }

    private File requestOpenRouterSpeechEndpoint(String text, String model) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        if (model.toLowerCase(Locale.US).contains("voxtral")) body.put("voice_id", ttsVoiceForModel(model));
        else body.put("voice", ttsVoiceForModel(model));
        body.put("input", text);
        putVoiceSpeedIfSupported(body, model);
        body.put("response_format", "mp3");
        body.put("format", "mp3");
        HttpURLConnection c = (HttpURLConnection) new URL(OPENROUTER_ENDPOINT + "/audio/speech").openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(30000);
        c.setReadTimeout(90000);
        c.setDoOutput(true);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "audio/mpeg, audio/wav, audio/*;q=0.9, */*;q=0.1");
        String key = savedApiKey();
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("HTTP-Referer", "https://minimal.chat/android");
        c.setRequestProperty("X-Title", "chat");
        OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes(StandardCharsets.UTF_8)); os.close();
        int code = c.getResponseCode();
        if (code >= 400) throw new RuntimeException(readAll(c.getErrorStream()));
        return saveAudioResponse(c, "tts-audio");
    }

    private void putVoiceSpeedIfSupported(JSONObject body, String model) throws Exception {
        if (!supportsVoiceSpeed(model)) return;
        body.put("speed", voiceSpeed());
    }

    private File saveAudioResponse(HttpURLConnection c, String baseName) throws Exception {
        String type = c.getContentType();
        byte[] data = bytes(c.getInputStream());
        if (data.length < 32) throw new RuntimeException("audio response too small: " + data.length + " bytes");
        String lower = type == null ? "" : type.toLowerCase(Locale.US);
        boolean textLike = lower.contains("json") || lower.contains("text") || lower.contains("html");
        if (textLike || looksLikeText(data)) {
            byte[] decoded = decodeAudioText(data);
            if (decoded.length > 32) data = decoded;
            else throw new RuntimeException("tts returned " + (type == null ? "text" : type) + ": " + firstText(data));
        }
        String ext = lower.contains("wav") ? ".wav" : lower.contains("mpeg") || lower.contains("mp3") ? ".mp3" : lower.contains("ogg") || lower.contains("opus") ? ".ogg" : ".mp3";
        File out = uniqueTtsFile(ext);
        FileOutputStream fos = new FileOutputStream(out); fos.write(data); fos.close();
        return out;
    }

    private File uniqueTtsFile(String ext) {
        return new File(getCacheDir(), "tts-audio-" + System.nanoTime() + (ext == null || ext.length() == 0 ? ".mp3" : ext));
    }

    private byte[] decodeAudioText(byte[] data) {
        try {
            String s = new String(data, StandardCharsets.UTF_8).trim();
            String[] formKeys = new String[]{"audio", "data", "base64", "b64_json", "file"};
            for (String key : formKeys) {
                String form = formValue(s, key);
                if (form.length() == 0) continue;
                byte[] decoded = decodeAudioString(form);
                if (decoded.length > 32) return decoded;
            }
            if (s.startsWith("{")) {
                JSONObject o = new JSONObject(s);
                String a = o.optString("audio_data", o.optString("audio", o.optString("data", o.optString("b64_json", ""))));
                if (a.length() == 0) a = extractAudioBase64(o);
                return decodeAudioString(a);
            }
            int dataAt = s.indexOf("data:audio");
            if (dataAt >= 0) return decodeAudioString(s.substring(dataAt));
            int eq = s.indexOf("base64=");
            if (eq >= 0) return decodeAudioString(s.substring(eq + 7));
            eq = s.indexOf("audio=");
            if (eq >= 0) return decodeAudioString(s.substring(eq + 6));
            return decodeAudioString(s);
        } catch (Exception ignored) { return new byte[0]; }
    }

    private String formValue(String s, String key) {
        String[] parts = s.split("&");
        for (String part : parts) {
            int eq = part.indexOf('=');
            if (eq <= 0) continue;
            String k = part.substring(0, eq).trim();
            if (!key.equalsIgnoreCase(k)) continue;
            String v = part.substring(eq + 1).trim();
            try { return URLDecoder.decode(v.replace("+", "%2B"), "UTF-8"); } catch (Exception ignored) { return v; }
        }
        return "";
    }

    private byte[] decodeAudioString(String s) {
        if (s == null) return new byte[0];
        String clean = stripDataPrefix(s).trim();
        try { clean = URLDecoder.decode(clean.replace("+", "%2B"), "UTF-8"); } catch (Exception ignored) { }
        int amp = clean.indexOf('&');
        if (amp > 0) clean = clean.substring(0, amp);
        int comma = clean.indexOf(',');
        if (clean.toLowerCase(Locale.US).startsWith("mp3") && comma > 0) clean = clean.substring(comma + 1);
        if (clean.toLowerCase(Locale.US).startsWith("mp3:")) clean = clean.substring(4);
        if (clean.toLowerCase(Locale.US).startsWith("mp3;")) clean = clean.substring(4);
        clean = clean.replace("\\n", "").replace("\n", "").replace("\r", "").replace(" ", "");
        try { return Base64.decode(clean, Base64.DEFAULT); } catch (Exception ignored) { return new byte[0]; }
    }

    private boolean looksLikeText(byte[] data) {
        int n = Math.min(data.length, 24);
        int printable = 0;
        for (int i = 0; i < n; i++) { byte b = data[i]; if (b == '{' || b == '[' || b == '<') return true; if (b >= 32 && b <= 126) printable++; else if (b < 9) return false; }
        return printable >= n - 1;
    }

    private String firstText(byte[] data) {
        String s = new String(data, 0, Math.min(data.length, 160), StandardCharsets.UTF_8).replace('\n', ' ').trim();
        return s.length() > 120 ? s.substring(0, 120) : s;
    }

    private String ttsVoiceForModel(String model) {
        String voice = prefs.getString("voiceTtsVoice", "alloy");
        String[] valid = voiceNamesForModel(model);
        for (String v : valid) if (v.equals(voice)) return voice;
        return valid.length == 0 ? voice : valid[0];
    }

    private String extractAudioBase64(JSONObject resp) throws Exception {
        JSONArray choices = resp.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";
        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
        if (msg == null) return "";
        JSONObject audio = msg.optJSONObject("audio");
        if (audio != null) {
            String data = audio.optString("data", "");
            if (data.length() > 0) return data;
        }
        JSONArray content = msg.optJSONArray("content");
        if (content != null) for (int i = 0; i < content.length(); i++) {
            JSONObject part = content.optJSONObject(i);
            if (part == null) continue;
            audio = part.optJSONObject("audio");
            if (audio != null && audio.optString("data", "").length() > 0) return audio.optString("data", "");
            String data = part.optString("data", "");
            if (data.length() > 0 && part.optString("type", "").toLowerCase(Locale.US).contains("audio")) return data;
        }
        return "";
    }

    private void playSpeechAudio(File file, final String fallbackText) { playSpeechAudio(file, fallbackText, null); }

    private void playSpeechAudio(File file, final String fallbackText, final Msg owner) {
        try {
            if (owner != null && owner.voiceSessionId != 0 && owner.voiceSessionId != voiceSession) return;
            if (owner == null && !voiceMode) return;
            if (file != null && pcm16AudioFiles.remove(file.getAbsolutePath())) {
                FileInputStream in = new FileInputStream(file);
                byte[] pcm = bytes(in);
                in.close();
                playPcm16Audio(pcm, owner);
                return;
            }
            if (ttsPlayer != null) { ttsPlayer.release(); ttsPlayer = null; }
            ttsPlayer = new MediaPlayer();
            ttsPlayer.setOnErrorListener(new MediaPlayer.OnErrorListener() { @Override public boolean onError(MediaPlayer mp, int what, int extra) { if (owner != null) { if (owner.voiceSessionId != 0 && owner.voiceSessionId != voiceSession) return true; owner.ttsPlaying = false; owner.ttsRequested = false; if (owner.ttsPlaybackFailures++ == 0 && fallbackText != null && fallbackText.trim().length() > 0) owner.ttsQueue.add(0, fallbackText); showTtsPlaybackError("media error " + what + "/" + extra, false); playNextQueuedSpeech(owner); } else showTtsPlaybackError("media error " + what + "/" + extra); return true; } });
            ttsPlayer.setDataSource(file.getAbsolutePath());
            ttsPlayer.setOnCompletionListener(new MediaPlayer.OnCompletionListener() { @Override public void onCompletion(MediaPlayer mp) { mp.release(); ttsPlayer = null; if (owner != null) { if (owner.voiceSessionId != 0 && owner.voiceSessionId != voiceSession) return; owner.ttsPlaying = false; playNextQueuedSpeech(owner); prefetchNextQueuedSpeech(owner); maybeFinishVoiceAfterTts(owner); } else finishVoiceResponse(); } });
            ttsPlayer.prepare();
            if (owner != null && owner.voiceSessionId != 0 && owner.voiceSessionId != voiceSession) { ttsPlayer.release(); ttsPlayer = null; return; }
            if (owner == null && !voiceMode) { ttsPlayer.release(); ttsPlayer = null; return; }
            ttsPlayer.start();
            if (owner != null) followVoiceChunk(owner, fallbackText);
            if (owner != null) prefetchNextQueuedSpeech(owner);
        } catch (Exception e) {
            if (owner != null) { owner.ttsPlaying = false; owner.ttsRequested = false; if (owner.ttsPlaybackFailures++ == 0 && fallbackText != null && fallbackText.trim().length() > 0) owner.ttsQueue.add(0, fallbackText); }
            showTtsPlaybackError(friendlyError(e) + " " + audioFileSummary(file), owner == null);
            if (owner != null) playNextQueuedSpeech(owner);
        }
    }

    private void playPcm16Audio(final byte[] pcm, final Msg owner) {
        if (pcm == null || pcm.length == 0) { finishVoiceResponse(); return; }
        final int session = owner == null ? voiceSession : owner.voiceSessionId;
        new Thread(new Runnable() { @Override public void run() {
            AudioTrack track = null;
            try {
                if (!voiceSessionActive(session)) return;
                int sampleRate = 24000;
                int min = AudioTrack.getMinBufferSize(sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT);
                int buffer = Math.max(min, Math.min(Math.max(pcm.length, min), sampleRate * 2));
                track = new AudioTrack(AudioManager.STREAM_MUSIC, sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, buffer, AudioTrack.MODE_STREAM);
                currentAudioTrack = track;
                if (!voiceSessionActive(session)) return;
                track.play();
                if (owner != null) runOnUiThread(new Runnable() { @Override public void run() { if (voiceSessionActive(session)) followVoiceChunk(owner, owner.text); } });
                int off = 0;
                while (off < pcm.length && voiceSessionActive(session)) {
                    int n = track.write(pcm, off, Math.min(buffer, pcm.length - off));
                    if (n <= 0) break;
                    off += n;
                }
                int frames = pcm.length / 2;
                long deadline = System.currentTimeMillis() + Math.max(500, frames * 1000L / sampleRate + 500);
                while (voiceSessionActive(session) && track.getPlaybackHeadPosition() < frames && System.currentTimeMillis() < deadline) {
                    try { Thread.sleep(20); } catch (Exception ignored) { }
                }
                try { Thread.sleep(120); } catch (Exception ignored) { }
                track.stop();
            } catch (Exception e) {
                final String msg = friendlyError(e);
                runOnUiThread(new Runnable() { @Override public void run() { updateVoiceStatus("audio: " + msg); } });
            } finally {
                if (track != null) try { track.release(); } catch (Exception ignored) { }
                if (currentAudioTrack == track) currentAudioTrack = null;
                runOnUiThread(new Runnable() { @Override public void run() { if (!voiceSessionActive(session)) return; if (owner != null) { owner.ttsPlaying = false; owner.ttsRequested = false; maybeFinishVoiceAfterTts(owner); } else finishVoiceResponse(); } });
            }
        } }).start();
    }

    private void showTtsPlaybackError(String detail) { showTtsPlaybackError(detail, true); }

    private void showTtsPlaybackError(String detail, boolean finish) {
        updateVoiceStatus("tts playback failed");
        if (voiceText != null) setVoiceText(voiceText.getText().toString() + "\n\ntts playback failed: " + detail);
        if (finish) ui.postDelayed(new Runnable() { @Override public void run() { finishVoiceResponse(); } }, 1800);
    }

    private String audioFileSummary(File file) {
        try {
            if (file == null || !file.exists()) return "no file";
            FileInputStream in = new FileInputStream(file);
            byte[] b = new byte[(int)Math.min(8, file.length())];
            int n = in.read(b); in.close();
            StringBuilder hex = new StringBuilder();
            for (int i = 0; i < n; i++) hex.append(String.format(Locale.US, "%02x", b[i] & 255));
            return file.getName() + " " + file.length() + "b " + hex;
        } catch (Exception e) { return "file unreadable"; }
    }

    private void finishVoiceResponse() {
        if (!voiceMode || !voiceFullMode) return;
        if (activeTtsOwner != null && (activeTtsOwner.ttsRequested || activeTtsOwner.ttsPlaying || activeTtsOwner.ttsQueue.size() > 0)) {
            ui.postDelayed(new Runnable() { @Override public void run() { finishVoiceResponse(); } }, 250);
            return;
        }
        activeTtsOwner = null;
        if (voiceMode && voiceFullMode && prefs.getBoolean("voiceLoop", true)) beginListening();
        else updateVoiceStatus("done");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res == RESULT_OK && req == VOICE && data != null) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && r.size() > 0 && input != null) input.setText(r.get(0));
        }
    }

    private void addFolder() {
        showProjectEditor("", true);
    }

    private void folderActions(final String folder) {
        final Dialog d = panel(folder);
        LinearLayout box = panelBox();
        LinearLayout head = row();
        TextView title = text(folder, 22, Color.WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        ImageButton edit = iconButton(R.drawable.ic_pencil, new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); editFolder(folder); } }, 8);
        head.addView(title, new LinearLayout.LayoutParams(0, dp(50), 1));
        head.addView(edit, new LinearLayout.LayoutParams(dp(44), dp(50)));
        box.addView(head);
        TextView delete = panelItem("delete", "");
        TextView cancel = panelAction("cancel");
        delete.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); confirmDeleteFolder(folder); } });
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(delete); box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(52)));
        showPanel(d, box);
    }

    private void editFolder(final String folder) {
        showProjectEditor(folder, false);
    }

    private void showProjectEditor(final String folder, final boolean creating) {
        projectEditorOpen = true;
        pane = 0;
        hideKeyboard();
        clearPaneViews();
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN);
        root.setPadding(dp(26), dp(18), dp(26), dp(10));
        root.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { clearFocusedTextField(); } });
        TextView title = text(creating ? "new project" : "project instructions", 23, Color.WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        root.addView(title, new LinearLayout.LayoutParams(-1, dp(42)));
        root.addView(fieldLabel("name"));
        final EditText name = panelEdit("project name");
        name.setText(folder);
        root.addView(name, new LinearLayout.LayoutParams(-1, dp(44)));
        root.addView(space(4));
        root.addView(fieldLabel("instructions"));
        final EditText instructions = panelMemo("(optional)");
        instructions.setText(creating ? "" : folderInstruction(folder));
        instructions.setMinLines(6);
        root.addView(instructions, new LinearLayout.LayoutParams(-1, dp(80)));
        root.addView(space(8));
        LinearLayout actions = row();
        TextView cancel = panelAction("cancel");
        TextView save = panelAction(creating ? "create" : "save");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { hideKeyboardFrom(v); showChatsPane(); } });
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            String next = name.getText().toString().trim();
            if (next.length() > 0 && (creating || next.equals(folder) || !folders.contains(next))) {
                if (creating) {
                    folders.add(next);
                    selectedFolder = next;
                } else if (!next.equals(folder)) {
                    folders.remove(folder); folders.add(next);
                    String oldInstruction = folderInstruction(folder);
                    folderInstructions.remove(folder);
                    for (Chat c : chats) if (folder.equals(c.folder)) c.folder = next;
                    if (folder.equals(selectedFolder)) selectedFolder = next;
                    if (folder.equals(projectView)) projectView = next;
                    if (folder.equals(expandedFolder)) expandedFolder = next;
                    if (oldInstruction.length() > 0) folderInstructions.put(next, oldInstruction);
                }
                putFolderInstruction(next, instructions.getText().toString());
                saveState(); hideKeyboardFrom(v); showChatsPane();
            }
        } });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(52), 1));
        root.addView(actions);
    }

    private void showProjectOverlay(Dialog d, FrameLayout overlay, LinearLayout box) {
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(-1, -1, Gravity.TOP);
        boxLp.setMargins(dp(26), dp(58), dp(26), dp(26));
        overlay.addView(box, boxLp);
        attachKeyboardAwareProjectOverlay(overlay, box);
        enablePanelSwipeDismiss(d, overlay);
        d.setContentView(overlay);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            enablePanelSwipeDismiss(d, w.getDecorView());
        }
    }

    private void attachKeyboardAwareProjectOverlay(final FrameLayout overlay, final LinearLayout box) {
        overlay.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override public void onGlobalLayout() {
                Rect visible = new Rect();
                overlay.getWindowVisibleDisplayFrame(visible);
                int keyboard = Math.max(0, overlay.getRootView().getHeight() - visible.bottom);
                FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) box.getLayoutParams();
                int bottom = dp(26) + (keyboard > dp(90) ? keyboard : 0);
                if (lp.bottomMargin != bottom) {
                    lp.setMargins(dp(26), dp(58), dp(26), bottom);
                    box.setLayoutParams(lp);
                }
            }
        });
    }

    private void attachProjectKeyboardBehavior(final FrameLayout overlay, final LinearLayout box, final EditText name, final EditText instructions) {
        final Runnable nameMode = new Runnable() { @Override public void run() { box.animate().translationY(-dp(80)).setDuration(90).start(); } };
        final Runnable instructionsMode = new Runnable() { @Override public void run() { box.animate().translationY(-dp(245)).setDuration(90).start(); } };
        View.OnClickListener clear = new View.OnClickListener() { @Override public void onClick(View v) {
            hideKeyboardFrom(v);
            name.clearFocus();
            instructions.clearFocus();
            box.animate().translationY(0).setDuration(120).start();
        } };
        overlay.setOnClickListener(clear);
        box.setOnClickListener(clear);
        name.setOnTouchListener(new View.OnTouchListener() { @Override public boolean onTouch(View v, MotionEvent e) { if (e.getAction() == MotionEvent.ACTION_DOWN) nameMode.run(); return false; } });
        instructions.setOnTouchListener(new View.OnTouchListener() { @Override public boolean onTouch(View v, MotionEvent e) { if (e.getAction() == MotionEvent.ACTION_DOWN) instructionsMode.run(); return false; } });
        instructions.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { instructionsMode.run(); } });
        name.setOnFocusChangeListener(new View.OnFocusChangeListener() { @Override public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) nameMode.run();
            else if (!instructions.hasFocus()) box.animate().translationY(0).setDuration(120).start();
        } });
        instructions.setOnFocusChangeListener(new View.OnFocusChangeListener() { @Override public void onFocusChange(View v, boolean hasFocus) {
            if (hasFocus) instructionsMode.run();
            else if (!name.hasFocus()) box.animate().translationY(0).setDuration(120).start();
        } });
    }

    private void confirmDeleteFolder(final String folder) {
        final Dialog d = panel("delete");
        LinearLayout box = panelBox();
        box.addView(panelTitle("are you sure?"));
        TextView msg = text("all chats will be moved to general.", 13, Color.LTGRAY);
        msg.setGravity(Gravity.CENTER_VERTICAL);
        box.addView(msg, new LinearLayout.LayoutParams(-1, dp(48)));
        LinearLayout actions = row();
        TextView yes = panelAction("delete");
        TextView cancel = panelAction("cancel");
        yes.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            folders.remove(folder);
            folderInstructions.remove(folder);
            for (Chat c : chats) if (folder.equals(c.folder)) c.folder = "Inbox";
            if (folder.equals(selectedFolder)) selectedFolder = "Inbox";
            if (folder.equals(projectView)) projectView = "";
            if (folder.equals(expandedFolder)) expandedFolder = "";
            saveState(); d.dismiss(); showChatsPane();
        } });
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        actions.addView(yes, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        box.addView(actions);
        showPanel(d, box);
    }

    private void chatActions(final Chat c) {
        final Dialog d = panel(c.title);
        LinearLayout box = panelBox();
        TextView title = text(c.title.length() == 0 ? "chat" : c.title, 18, Color.WHITE);
        title.setPadding(0, 0, 0, dp(14));
        box.addView(title);
        LinearLayout actions = row();
        TextView rename = panelAction("rename");
        TextView move = panelAction("move");
        TextView remove = panelAction("unfile");
        TextView delete = panelAction("delete");
        TextView cancel = panelAction("cancel");
        rename.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); renameChat(c); } });
        move.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); moveChat(c); } });
        remove.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { c.folder = "Inbox"; saveState(); d.dismiss(); showChatsPane(); } });
        delete.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); confirmDeleteChat(c); } });
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        actions.addView(rename, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(move, new LinearLayout.LayoutParams(0, dp(52), 1));
        if (!"Inbox".equals(c.folder)) actions.addView(remove, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(52), 1));
        box.addView(actions);
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(46)));
        showPanel(d, box);
    }

    private void confirmDeleteChat(final Chat c) {
        final Dialog d = panel("delete");
        LinearLayout box = panelBox();
        box.addView(panelTitle("delete chat?"));
        LinearLayout actions = row();
        TextView yes = panelAction("yes");
        TextView cancel = panelAction("cancel");
        yes.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chats.remove(c); if (currentChatId.equals(c.id)) newChat(); saveState(); d.dismiss(); showChatsPane(); } });
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        actions.addView(yes, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        box.addView(actions);
        showPanel(d, box);
    }

    private void showBulkActions() {
        final Dialog d = panel("selected");
        LinearLayout box = panelBox();
        TextView title = text(selectedChats.size() + " selected", 16, Color.LTGRAY);
        title.setPadding(0, 0, 0, dp(12));
        box.addView(title);
        box.addView(separator());
        TextView move = panelItem("move to project", "");
        TextView remove = panelItem("remove from project", "");
        TextView delete = panelItem("delete", "");
        TextView cancel = panelAction("cancel");
        move.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); moveSelectedChats(); } });
        remove.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { for (Chat c : chats) if (selectedChats.contains(c.id)) c.folder = "Inbox"; selectedChats.clear(); saveState(); d.dismiss(); showChatsPane(); } });
        delete.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); confirmDeleteSelectedChats(); } });
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(move); box.addView(remove); box.addView(delete); box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(52)));
        showPanel(d, box);
    }

    private void moveSelectedChats() {
        final Dialog d = panel("move");
        LinearLayout box = panelBox();
        box.addView(panelTitle("move"));
        for (int i = 0; i < folders.size(); i++) {
            final String f = folders.get(i);
            if ("Inbox".equals(f)) continue;
            TextView item = panelItem(f, "");
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
                for (Chat c : chats) if (selectedChats.contains(c.id)) c.folder = f;
                selectedChats.clear(); saveState(); d.dismiss(); showChatsPane();
            } });
            box.addView(item);
        }
        TextView cancel = panelAction("cancel");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(52)));
        showPanel(d, box);
    }

    private void confirmDeleteSelectedChats() {
        final Dialog d = panel("delete");
        LinearLayout box = panelBox();
        box.addView(panelTitle("delete " + selectedChats.size() + " chats?"));
        LinearLayout actions = row();
        TextView delete = panelAction("yes");
        TextView cancel = panelAction("cancel");
        delete.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            for (int i = chats.size() - 1; i >= 0; i--) if (selectedChats.contains(chats.get(i).id)) chats.remove(i);
            if (selectedChats.contains(currentChatId)) { currentChatId = ""; messages.clear(); resetMessageWindowToLatest(); }
            selectedChats.clear(); saveState(); d.dismiss(); showChatsPane();
        } });
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        actions.addView(delete, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        box.addView(actions);
        showPanel(d, box);
    }

    private void renameChat(final Chat c) {
        final Dialog d = panel("rename");
        LinearLayout box = panelBox();
        box.addView(panelTitle("rename"));
        final EditText e = panelEdit("name"); e.setText(c.title);
        box.addView(e, new LinearLayout.LayoutParams(-1, dp(52)));
        LinearLayout actions = row();
        TextView cancel = panelAction("cancel");
        TextView save = panelAction("save");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { c.title = e.getText().toString().trim(); saveState(); d.dismiss(); showChatsPane(); } });
        actions.addView(cancel, new LinearLayout.LayoutParams(0, dp(52), 1));
        actions.addView(save, new LinearLayout.LayoutParams(0, dp(52), 1));
        box.addView(actions);
        showPanel(d, box);
    }

    private void moveChat(final Chat c) {
        final Dialog d = panel("move");
        LinearLayout box = panelBox();
        box.addView(panelTitle("move"));
        for (int i = 0; i < folders.size(); i++) {
            final String f = folders.get(i);
            if ("Inbox".equals(f)) continue;
            TextView item = panelItem(f, "");
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { c.folder = f; saveState(); d.dismiss(); showChatsPane(); } });
            box.addView(item);
        }
        TextView cancel = panelAction("cancel");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(52)));
        showPanel(d, box);
    }

    private void newChat() { saveCurrentChat(); currentChatId = ""; messages.clear(); selectedFolder = projectView.length() > 0 ? projectView : "Inbox"; webSearchChat = false; savedChatScrollKnown = false; forceAutoScrollBottom = false; resetMessageWindowToLatest(); if (messageList != null) renderMessages(); }
    private void loadChat(Chat c) { currentChatId = c.id; selectedFolder = c.folder; if (!"Inbox".equals(c.folder)) projectView = c.folder; messages.clear(); messages.addAll(c.messages); webSearchChat = c.webSearch; if (c.model.length() > 0) prefs.edit().putString("model", c.model).putBoolean("modelSelected", true).apply(); savedChatScrollKnown = false; forceAutoScrollBottom = true; resetMessageWindowToLatest(); }
    private void saveCurrentChat() { if (messages.size() == 0) return; if (currentChatId.length() == 0) currentChatId = String.valueOf(System.currentTimeMillis()); Chat t = null; for (Chat c : chats) if (c.id.equals(currentChatId)) t = c; if (t == null) { t = new Chat(); t.id = currentChatId; chats.add(0, t); } t.folder = selectedFolder; t.title = firstUserText(); t.webSearch = webSearchChat; t.model = chatModelForSave(); t.messages.clear(); t.messages.addAll(messages); saveState(); }
    private String chatModelForSave() { for (int i = messages.size() - 1; i >= 0; i--) if (messages.get(i).role.equals("assistant") && messages.get(i).model.length() > 0) return expandShortModel(messages.get(i).model); return activeAnswerModel(); }
    private String expandShortModel(String label) { for (String m : models) if (shortModel(m).equals(label) || m.equals(label)) return m; return label; }

    private void attachClipboardImageIfPresent() {
        if (imageBase64.length() > 0) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return;
        ClipData clip = cm.getPrimaryClip(); if (clip == null || clip.getItemCount() == 0) return;
        Uri u = clip.getItemAt(0).getUri(); if (u != null) attachUri(u);
    }
    private String clipboardText() { try { ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); if (cm == null || !cm.hasPrimaryClip()) return ""; ClipData clip = cm.getPrimaryClip(); if (clip == null || clip.getItemCount() == 0) return ""; CharSequence text = clip.getItemAt(0).coerceToText(this); return text == null ? "" : text.toString().trim(); } catch (Exception e) { return ""; } }
    private void copyText(String s) { ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("message", s)); }
    private void attachUri(Uri uri) {
        try {
            String type = getContentResolver().getType(uri);
            if (type == null || !type.toLowerCase(Locale.US).startsWith("image/")) { toast("image only"); return; }
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new RuntimeException("unreadable image");
            byte[] b;
            try { b = bytesLimited(in, 8 * 1024 * 1024); }
            finally { try { in.close(); } catch (Exception ignored) { } }
            imageBase64 = Base64.encodeToString(b, Base64.NO_WRAP);
            imageMime = type;
        } catch (TooLargeException e) { toast("image too large"); }
        catch (Exception e) { toast("attach failed"); }
    }

    private void loadState() {
        if (!prefs.getBoolean("modelSelected", false)) prefs.edit().remove("model").apply();
        loadModelState();
        folders.add("Inbox"); String savedFolders = prefs.getString("folders", ""); if (savedFolders.length() > 0) for (String f : savedFolders.split("\\n")) { String clean = f.trim(); if (clean.length() > 0 && !folders.contains(clean)) folders.add(clean); }
        loadFolderInstructions();
        try { JSONArray arr = new JSONArray(prefs.getString("chats", "[]")); for (int i = 0; i < arr.length(); i++) chats.add(Chat.fromJson(arr.getJSONObject(i))); } catch (Exception ignored) { }
    }

    private void reloadChatsFromPrefs() {
        chats.clear();
        folders.clear();
        folderInstructions.clear();
        folders.add("Inbox");
        String savedFolders = prefs.getString("folders", "");
        if (savedFolders.length() > 0) for (String f : savedFolders.split("\\n")) { String clean = f.trim(); if (clean.length() > 0 && !folders.contains(clean)) folders.add(clean); }
        loadFolderInstructions();
        try { JSONArray arr = new JSONArray(prefs.getString("chats", "[]")); for (int i = 0; i < arr.length(); i++) chats.add(Chat.fromJson(arr.getJSONObject(i))); } catch (Exception ignored) { }
    }

    private void loadModelState() {
        String savedEndpoints = prefs.getString("customEndpoints", ""); if (savedEndpoints.length() > 0) for (String e : savedEndpoints.split("\\n")) { String clean = normalizeEndpoint(e); if (clean.length() > 0 && !customEndpoints.contains(clean)) customEndpoints.add(clean); }
        String legacyEndpoint = normalizeEndpoint(prefs.getString("endpointBase", "")); if (legacyEndpoint.length() > 0 && !customEndpoints.contains(legacyEndpoint)) customEndpoints.add(legacyEndpoint);
        String savedModels = prefs.getString("modelCatalog", prefs.getString("models", "")); if (savedModels.length() > 0) for (String m : savedModels.split("\\n")) { String clean = m.trim(); if (clean.length() > 0 && !models.contains(clean)) models.add(clean); }
        String savedAudioInput = prefs.getString("audioInputModelsV2", ""); if (savedAudioInput.length() > 0) for (String m : savedAudioInput.split("\\n")) { String clean = m.trim(); if (clean.length() > 0) audioInputModels.add(clean); }
        String savedAudio = prefs.getString("audioOutputModelsV2", ""); if (savedAudio.length() > 0) for (String m : savedAudio.split("\\n")) { String clean = m.trim(); if (clean.length() > 0) audioOutputModels.add(clean); }
        String savedReasoning = prefs.getString("reasoningModels", ""); if (savedReasoning.length() > 0) for (String m : savedReasoning.split("\\n")) { String clean = m.trim(); if (clean.length() > 0) reasoningModels.add(clean); }
        String savedSpeed = prefs.getString("speedModels", ""); if (savedSpeed.length() > 0) for (String m : savedSpeed.split("\\n")) { String clean = m.trim(); if (clean.length() > 0) speedModels.add(clean); }
        if (models.size() == 0) { models.add("openai/gpt-4o-mini"); models.add("anthropic/claude-3.5-haiku"); models.add("google/gemini-2.0-flash-001"); }
        try {
            JSONObject savedContexts = new JSONObject(prefs.getString("modelContexts", "{}"));
            JSONArray names = savedContexts.names();
            if (names != null) for (int i = 0; i < names.length(); i++) {
                String key = names.getString(i);
                int value = savedContexts.optInt(key, 0);
                if (value > 0) modelContexts.put(key, value);
            }
        } catch (Exception ignored) { }
        try {
            JSONObject savedSources = new JSONObject(prefs.getString("modelSources", "{}"));
            JSONArray sourceNames = savedSources.names();
            if (sourceNames != null) for (int i = 0; i < sourceNames.length(); i++) {
                String key = sourceNames.getString(i);
                String value = savedSources.optString(key, "openrouter");
                if (value.length() > 0) modelSources.put(key, value);
            }
        } catch (Exception ignored) { }
        try {
            JSONObject savedEndpointsByModel = new JSONObject(prefs.getString("modelEndpoints", "{}"));
            JSONArray endpointNames = savedEndpointsByModel.names();
            if (endpointNames != null) for (int i = 0; i < endpointNames.length(); i++) {
                String key = endpointNames.getString(i);
                String value = normalizeEndpoint(savedEndpointsByModel.optString(key, ""));
                if (value.length() > 0) modelEndpoints.put(key, value);
            }
        } catch (Exception ignored) { }
        try {
            JSONObject savedVoices = new JSONObject(prefs.getString("discoveredVoices", "{}"));
            JSONArray voiceKeys = savedVoices.names();
            if (voiceKeys != null) for (int i = 0; i < voiceKeys.length(); i++) {
                String key = voiceKeys.getString(i);
                JSONArray arr = savedVoices.optJSONArray(key);
                ArrayList<String> list = new ArrayList<String>();
                if (arr != null) for (int j = 0; j < arr.length(); j++) addVoice(list, arr.optString(j, ""));
                if (list.size() > 0) discoveredVoices.put(key, list);
            }
        } catch (Exception ignored) { }
        for (String m : models) if (!modelSources.containsKey(m)) modelSources.put(m, "openrouter");
        for (String m : models) if ("custom".equals(modelSources.get(m)) && !modelEndpoints.containsKey(m) && customEndpointBase().length() > 0) modelEndpoints.put(m, customEndpointBase());
        String savedMyModels = prefs.getString("myModels", ""); if (savedMyModels.length() > 0) for (String m : savedMyModels.split("\\n")) { String clean = m.trim(); if (clean.length() > 0 && !myModels.contains(clean)) myModels.add(clean); }
        String selected = prefs.getString("model", "").trim();
        if (prefs.getBoolean("modelSelected", false) && selected.length() > 0 && !myModels.contains(selected)) myModels.add(selected);
        saveModels();
        saveMyModels();
    }
    private void loadFolderInstructions() { try { JSONObject o = new JSONObject(prefs.getString("folderInstructions", "{}")); JSONArray names = o.names(); if (names != null) for (int i = 0; i < names.length(); i++) { String folder = names.getString(i); String instruction = o.optString(folder, "").trim(); if (instruction.length() > 0) folderInstructions.put(folder, instruction); } } catch (Exception ignored) { } }
    private JSONObject folderInstructionsJson() { JSONObject o = new JSONObject(); try { for (String folder : folderInstructions.keySet()) { String instruction = folderInstructions.get(folder); if (instruction != null && instruction.trim().length() > 0 && folders.contains(folder)) o.put(folder, instruction.trim()); } } catch (Exception ignored) { } return o; }
    private String folderInstruction(String folder) { String s = folderInstructions.get(folder == null ? "" : folder); return s == null ? "" : s.trim(); }
    private void putFolderInstruction(String folder, String instruction) { String clean = instruction == null ? "" : instruction.trim(); if (clean.length() == 0) folderInstructions.remove(folder); else folderInstructions.put(folder, clean); }
    private void saveState() { JSONArray arr = new JSONArray(); try { for (Chat c : chats) arr.put(c.toJson()); } catch (Exception ignored) { } prefs.edit().putString("folders", join(folders)).putString("folderInstructions", folderInstructionsJson().toString()).putString("chats", arr.toString()).apply(); }
    private void saveModels() { prefs.edit().putString("modelCatalog", join(models)).apply(); }
    private void saveModelContexts() { JSONObject o = new JSONObject(); try { for (String m : modelContexts.keySet()) o.put(m, modelContexts.get(m)); } catch (Exception ignored) { } prefs.edit().putString("modelContexts", o.toString()).apply(); }
    private void saveModelSources() { JSONObject o = new JSONObject(); try { for (String m : modelSources.keySet()) o.put(m, modelSources.get(m)); } catch (Exception ignored) { } prefs.edit().putString("modelSources", o.toString()).apply(); }
    private void saveModelEndpoints() { JSONObject o = new JSONObject(); try { for (String m : modelEndpoints.keySet()) o.put(m, modelEndpoints.get(m)); } catch (Exception ignored) { } prefs.edit().putString("modelEndpoints", o.toString()).apply(); }
    private void saveDiscoveredVoices() { JSONObject o = new JSONObject(); try { for (String key : discoveredVoices.keySet()) { JSONArray arr = new JSONArray(); for (String voice : discoveredVoices.get(key)) arr.put(voice); o.put(key, arr); } } catch (Exception ignored) { } prefs.edit().putString("discoveredVoices", o.toString()).apply(); }
    private void saveAudioInputModels() { prefs.edit().putString("audioInputModelsV2", join(new ArrayList<String>(audioInputModels))).remove("audioInputModels").apply(); }
    private void saveAudioOutputModels() { prefs.edit().putString("audioOutputModelsV2", join(new ArrayList<String>(audioOutputModels))).remove("audioOutputModels").apply(); }
    private void saveReasoningModels() { prefs.edit().putString("reasoningModels", join(new ArrayList<String>(reasoningModels))).apply(); }
    private void saveSpeedModels() { prefs.edit().putString("speedModels", join(new ArrayList<String>(speedModels))).apply(); }
    private void saveCustomEndpoints() { prefs.edit().putString("customEndpoints", join(customEndpoints)).remove("endpointBase").apply(); }
    private void saveMyModels() { prefs.edit().putString("myModels", join(myModels)).apply(); }
    private String savedApiKey() { return prefs.getString("apiKey", ""); }
    private void saveApiKey() {
        SharedPreferences.Editor e = prefs.edit();
        if (apiKey != null) e.putString("apiKey", apiKey.getText().toString().trim());
        e.apply();
    }
    private void saveJinaSettings() { if (jinaKeyInput != null) prefs.edit().putString("jinaApiKey", jinaKeyInput.getText().toString().trim()).apply(); }
    private void removeCustomModels() {
        for (int i = models.size() - 1; i >= 0; i--) {
            String m = models.get(i);
            if ("custom".equals(modelSources.get(m))) { models.remove(i); myModels.remove(m); modelContexts.remove(m); modelSources.remove(m); modelEndpoints.remove(m); }
        }
    }
    private String selectedModel() { return prefs.getBoolean("modelSelected", false) ? prefs.getString("model", "") : ""; }
    private String selectedModelSource() { return modelSources.containsKey(selectedModel()) ? modelSources.get(selectedModel()) : "openrouter"; }
    private String selectedModelEndpoint() { String endpoint = modelEndpoints.get(selectedModel()); return endpoint == null ? customEndpointBase() : endpoint; }
    private String activeAnswerModel() { return voiceMode && voiceFullMode && voiceAnswerModel().length() > 0 ? voiceAnswerModel() : selectedModel(); }
    private String modelSource(String model) { return modelSources.containsKey(model) ? modelSources.get(model) : "openrouter"; }
    private String modelEndpoint(String model) { String endpoint = modelEndpoints.get(model); return endpoint == null ? customEndpointBase() : endpoint; }
    private String modelSourceLabel(String m) { return "custom".equals(modelSources.get(m)) ? "endpoint" : "openrouter"; }
    private String modelLabel() { String m = selectedModel(); return m.length() == 0 ? "model" : shortModel(m); }
    private String messageModelLabel(Msg m) { return m.model.length() == 0 ? "assistant" : m.model; }
    private String shortModel(String m) { int slash = m.lastIndexOf('/'); return slash >= 0 ? m.substring(slash + 1) : m; }
    private String thoughtDuration(long ms) { long s = Math.max(1, Math.round(ms / 1000f)); long m = s / 60; long r = s % 60; return m > 0 ? m + " minutes and " + r + " seconds" : s + " seconds"; }
    private int estimateTokens(String s) {
        String text = s == null ? "" : s.trim();
        if (text.length() == 0) return 0;
        int chars = text.length();
        int words = text.split("\\s+").length;
        int byChars = (int)Math.ceil(chars / 3.15);
        int byWords = (int)Math.ceil(words * 1.35);
        return Math.max(1, Math.max(byChars, byWords));
    }
    private String requestText(Msg m) { return m.role.equals("user") && m.replyQuote.length() > 0 ? "In reply to: \"" + m.replyQuote + "\"\n\n" + m.text : m.text; }
    private int contextTokens() { int t = 0; for (Msg m : messages) if (!LOADING.equals(m.stats)) t += 14 + estimateTokens(m.role) + estimateTokens(requestText(m)) + estimateTokens(m.replyQuote) + (m.reasoning.length() == 0 ? 0 : estimateTokens(m.reasoning)) + (m.imageBase64.length() == 0 ? 0 : 1200); return t + 24; }
    private int contextMaxTokens() { Integer max = modelContexts.get(selectedModel()); return max == null ? 0 : max; }
    private float contextPercent() { int max = contextMaxTokens(); return max <= 0 ? 0f : Math.min(100f, (float) (contextTokens() * 100.0 / max)); }
    private String contextPercentText() { return String.format(Locale.US, "%.1f%%", contextPercent()); }
    private void showContext() { int max = contextMaxTokens(); toast(max <= 0 ? shortTokens(contextTokens()) + "/? - refresh model catalog" : shortTokens(contextTokens()) + "/" + shortTokens(max) + " - " + contextPercentText()); }
    private String shortTokens(int n) { return n >= 1000 ? Math.round(n / 1000.0) + "k" : String.valueOf(n); }
    private String friendlyError(Exception e) { String msg = e.getMessage(); if (msg == null || msg.length() == 0) msg = e.getClass().getSimpleName(); msg = msg.replace('\n', ' ').trim(); return msg.length() > 120 ? msg.substring(0, 120) : msg; }
    private String detailedError(Exception e) { String msg = e.getMessage(); if (msg == null || msg.length() == 0) msg = e.getClass().getSimpleName(); msg = msg.replace('\n', ' ').trim(); return msg.length() > 700 ? msg.substring(0, 700) : msg; }
    private String firstUserText() { for (Msg m : messages) if (m.role.equals("user") && m.text.length() > 0) return m.text.length() > 36 ? m.text.substring(0, 36) : m.text; return "Image chat"; }
    private String join(ArrayList<String> xs) { StringBuilder b = new StringBuilder(); for (String x : xs) { String clean = x == null ? "" : x.trim(); if (clean.length() > 0) b.append(clean).append('\n'); } return b.toString(); }
    private String readAll(InputStream in) throws Exception { if (in == null) return ""; return new String(bytes(in), StandardCharsets.UTF_8); }
    private byte[] bytes(InputStream in) throws Exception { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) >= 0) out.write(buf, 0, n); return out.toByteArray(); }
    private byte[] bytesLimited(InputStream in, int max) throws Exception { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n, total = 0; while ((n = in.read(buf)) >= 0) { total += n; if (total > max) throw new TooLargeException(); out.write(buf, 0, n); } return out.toByteArray(); }
    private static class TooLargeException extends Exception { }
    private int dp(int v) { return Math.max(1, Math.round(v * uiScale())); }
    private float uiScale() { return getResources().getDisplayMetrics().widthPixels / BASE_WIDTH_DP; }
    private int fontOffset() { return prefs == null ? -1 : prefs.getInt("fontOffset", -1); }
    private void setFontOffset(int offset) { prefs.edit().putInt("fontOffset", Math.max(-4, Math.min(4, offset))).apply(); }
    private void applyKeepScreenAwake() { if (prefs != null && prefs.getBoolean("keepScreenAwake", false)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); else if (!voiceMode || !voiceFullMode) getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON); }
    private void setTextPx(TextView v, int sp) { v.setTextSize(TypedValue.COMPLEX_UNIT_PX, dp(Math.max(8, sp + fontOffset()))); }
    private void toast(String s) { showNotice(s); }
    private void showNotice(String s) {
        if (screen == null) return;
        removeScreenChild(notice);
        notice = text(s, 14, Color.WHITE);
        notice.setGravity(Gravity.CENTER);
        notice.setPadding(dp(18), dp(10), dp(18), dp(10));
        notice.setBackground(grayBorder());
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-2, -2, Gravity.TOP | Gravity.RIGHT);
        lp.setMargins(dp(26), dp(58), dp(26), 0);
        screen.addView(notice, lp);
        final TextView shown = notice;
        shown.postDelayed(new Runnable() { @Override public void run() { if (notice == shown) { removeScreenChild(shown); notice = null; } } }, 1800);
    }
    private void hideKeyboard() { try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(root.getWindowToken(), 0); } catch (Exception ignored) { } }
    private void hideKeyboardFrom(View v) { try { ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(v.getWindowToken(), 0); } catch (Exception ignored) { } }

    private void bindScrollIndicator(ScrollView target, int topMargin, int bottomMargin) {
        target.setVerticalScrollBarEnabled(false);
        removeScreenChild(scrollIndicator);
        scrollIndicator = new ScrollIndicator(this);
        scrollIndicator.target = target;
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(dp(3), -1, Gravity.RIGHT);
        lp.setMargins(0, topMargin, dp(6), bottomMargin);
        screen.addView(scrollIndicator, lp);
        target.post(new Runnable() { @Override public void run() { if (scrollIndicator != null) scrollIndicator.invalidate(); } });
    }

    private void addChatFade() {
        removeScreenChild(chatFade);
        chatFade = new FadeView(this);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(-1, dp(54), Gravity.BOTTOM);
        lp.setMargins(dp(26), 0, dp(26), dp(61));
        screen.addView(chatFade, lp);
    }

    private Dialog panel(String title) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return d;
    }

    private LinearLayout panelBox() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(28), dp(24), dp(28), dp(18));
        box.setBackgroundColor(Color.BLACK);
        return box;
    }

    private void showPanel(Dialog d, LinearLayout box) {
        d.setContentView(box);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            w.setLayout(getResources().getDisplayMetrics().widthPixels - dp(52), ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private void showFullPanel(Dialog d, LinearLayout box) {
        enablePanelSwipeDismiss(d, box);
        d.setContentView(box);
        d.show();
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.BLACK));
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
            enablePanelSwipeDismiss(d, w.getDecorView());
        }
    }

    private void enablePanelSwipeDismiss(final Dialog d, View target) {
        final float[] start = new float[2];
        target.setOnTouchListener(new View.OnTouchListener() { @Override public boolean onTouch(View v, MotionEvent e) {
            if (e.getAction() == MotionEvent.ACTION_DOWN) { start[0] = e.getRawX(); start[1] = e.getRawY(); return false; }
            if (e.getAction() == MotionEvent.ACTION_UP) {
                float dx = e.getRawX() - start[0], dy = e.getRawY() - start[1];
                if (dx > dp(78) && Math.abs(dx) > Math.abs(dy) * 1.15f) { d.dismiss(); return true; }
            }
            return false;
        } });
    }

    private TextView panelTitle(String s) {
        TextView v = text(s.toLowerCase(Locale.US), 24, Color.WHITE);
        v.setPadding(0, 0, 0, dp(18));
        return v;
    }

    private EditText panelEdit(String hint) {
        EditText e = plainEdit(hint);
        setTextPx(e, 17);
        e.setSingleLine(true);
        e.setPadding(dp(14), 0, dp(14), 0);
        e.setBackground(grayBorder());
        return e;
    }

    private EditText panelMemo(String hint) {
        EditText e = plainEdit(hint);
        setTextPx(e, 13);
        e.setSingleLine(false);
        e.setGravity(Gravity.TOP | Gravity.LEFT);
        e.setMinLines(4);
        e.setHorizontallyScrolling(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        e.setPadding(dp(14), dp(10), dp(14), dp(10));
        e.setBackground(grayBorder());
        return e;
    }

    private TextView panelItem(String primary, String secondary) {
        TextView v = text(secondary.length() == 0 ? primary : primary + "\n" + secondary, 17, Color.WHITE);
        v.setPadding(0, dp(12), 0, dp(12));
        return v;
    }

    private TextView fieldLabel(String s) {
        TextView v = text(s, 11, Color.rgb(145,145,145));
        v.setPadding(0, 0, 0, dp(5));
        return v;
    }

    private String shortPanelText(String s) { String clean = (s == null ? "" : s).replace('\n', ' ').trim(); return clean.length() > 52 ? clean.substring(0, 52) + "..." : clean; }

    private TextView searchResultItem(String primary, String secondary) {
        TextView v = text(primary == null ? "" : primary.toLowerCase(Locale.US), 18, Color.WHITE);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(26), 0, dp(18), 0);
        v.setMinHeight(dp(64));
        v.setSingleLine(false);
        v.setBackground(grayBorder());
        return v;
    }

    private TextView panelAction(String s) {
        TextView v = text(s, 16, Color.LTGRAY);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private LinearLayout row() { LinearLayout l = new LinearLayout(this); l.setOrientation(LinearLayout.HORIZONTAL); l.setGravity(Gravity.CENTER_VERTICAL); l.setBackgroundColor(Color.BLACK); return l; }
    private TextView text(String s, int sp, int color) { TextView v = new TextView(this); v.setText(s); v.setTextColor(color); setTextPx(v, sp); v.setBackgroundColor(Color.BLACK); return v; }
    private View separator() { View v = new View(this); v.setBackgroundColor(Color.rgb(95,95,95)); v.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1, dp(1) / 2))); return v; }
    private TextView cardText(String s, int sp, int color) { TextView v = text(s, sp, color); v.setBackgroundColor(Color.TRANSPARENT); return v; }
    private TextView actionText(String s) { TextView v = text(s, 13, Color.WHITE); v.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL); return v; }
    private ImageButton iconButton(int res, View.OnClickListener l, int pad) { ImageButton b = new ImageButton(this); b.setImageResource(res); b.setColorFilter(Color.WHITE); b.setBackgroundColor(Color.BLACK); b.setScaleType(ImageView.ScaleType.CENTER); b.setPadding(dp(pad), dp(pad), dp(pad), dp(pad)); b.setOnClickListener(l); return b; }
    private TextView compactTitle(String s) { TextView v = text(s.toLowerCase(Locale.US), 23, Color.WHITE); v.setPadding(0, dp(8), 0, dp(18)); return v; }
    private TextView title(String s) { TextView v = text(s.toLowerCase(Locale.US), 28, Color.WHITE); v.setPadding(0, dp(16), 0, dp(24)); return v; }
    private TextView menuLine(String s) { TextView v = text(s, 18, Color.WHITE); v.setPadding(0, dp(15), 0, dp(15)); return v; }
    private TextView smallPill(String s) { TextView v = text(s, 14, Color.WHITE); v.setPadding(0, 0, dp(24), 0); return v; }
    private TextView navHint(String s) { TextView v = text(s, 10, Color.rgb(120,120,120)); v.setGravity(Gravity.CENTER); return v; }
    private View space(int h) { View v = new View(this); v.setBackgroundColor(Color.BLACK); v.setLayoutParams(new LinearLayout.LayoutParams(1, dp(h))); return v; }
    private View weightSpace() { View v = new View(this); v.setBackgroundColor(Color.BLACK); v.setLayoutParams(new LinearLayout.LayoutParams(1, 0, 1)); return v; }
    private EditText plainEdit(String hint) { EditText e = new EditText(this); e.setTextColor(Color.WHITE); e.setHintTextColor(Color.rgb(120,120,120)); e.setHint(hint); setTextPx(e, 17); e.setBackgroundColor(Color.BLACK); e.setPadding(0, 0, 0, 0); return e; }
    private android.graphics.drawable.Drawable grayBorder() { android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setColor(Color.BLACK); g.setStroke(dp(1), Color.rgb(130,130,130)); return g; }
    private android.graphics.drawable.Drawable cardBorder() { android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setColor(Color.BLACK); g.setStroke(dp(1), Color.rgb(92,92,92)); return g; }
    private android.graphics.drawable.Drawable selectedBorder() { android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(); g.setColor(Color.BLACK); g.setStroke(dp(2), Color.WHITE); return g; }

    public class MicButton extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); public MicButton(Context c) { super(c); setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startVoice(); } }); } @Override protected void onDraw(Canvas c) { p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setStrokeCap(Paint.Cap.ROUND); float cx=getWidth()/2f, cy=getHeight()/2f; c.drawRoundRect(cx-dp(4), cy-dp(10), cx+dp(4), cy+dp(4), dp(4), dp(4), p); c.drawLine(cx-dp(10), cy-dp(2), cx-dp(10), cy+dp(3), p); c.drawArc(cx-dp(10), cy-dp(2), cx+dp(10), cy+dp(16), 0, 180, false, p); c.drawLine(cx+dp(10), cy-dp(2), cx+dp(10), cy+dp(3), p); c.drawLine(cx, cy+dp(15), cx, cy+dp(20), p); c.drawLine(cx-dp(6), cy+dp(20), cx+dp(6), cy+dp(20), p); } }
    public class SendButton extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); public SendButton(Context c) { super(c); setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { send(); } }); } @Override protected void onDraw(Canvas c) { p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); float cy=getHeight()/2f; c.drawLine(dp(12), cy, getWidth()-dp(12), cy, p); c.drawLine(getWidth()-dp(12), cy, getWidth()-dp(22), cy-dp(10), p); c.drawLine(getWidth()-dp(12), cy, getWidth()-dp(22), cy+dp(10), p); } }
    public class TogglePill extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); boolean checked = false; public TogglePill(Context c) { super(c); } @Override protected void onDraw(Canvas c) { int w=getWidth(), h=getHeight(); p.setStrokeWidth(dp(1)); p.setStyle(checked ? Paint.Style.FILL : Paint.Style.STROKE); p.setColor(Color.WHITE); c.drawRoundRect(dp(1), dp(1), w-dp(1), h-dp(1), h/2f, h/2f, p); } }
    public class ScrollIndicator extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); ScrollView target; public ScrollIndicator(Context c) { super(c); } @Override protected void onDraw(Canvas c) { if (target == null || target.getChildCount() == 0) return; int thumb; int top; if (target == scroll && messages.size() > MESSAGE_WINDOW) { thumb = Math.max(dp(28), Math.round(getHeight() * (MESSAGE_WINDOW / (float) messages.size()))); int maxTop = Math.max(0, getHeight() - thumb); int denominator = Math.max(1, messages.size() - MESSAGE_WINDOW); top = messageEnd >= messages.size() ? maxTop : Math.round(maxTop * (messageStart / (float) denominator)); } else { int content = target.getChildAt(0).getHeight(); int view = target.getHeight(); if (content <= view || view <= 0) return; float ratio = view / (float) content; thumb = Math.max(dp(28), Math.round(getHeight() * ratio)); int maxScroll = content - view; int maxTop = Math.max(0, getHeight() - thumb); top = Math.round(maxTop * (target.getScrollY() / (float) maxScroll)); } p.setColor(Color.rgb(125,125,125)); p.setStyle(Paint.Style.FILL); c.drawRect(0, top, getWidth(), top + thumb, p); } }
    public class FadeView extends View { Paint p = new Paint(); public FadeView(Context c) { super(c); } @Override protected void onDraw(Canvas c) { p.setShader(new LinearGradient(0, 0, 0, getHeight(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)); c.drawRect(0, 0, getWidth(), getHeight(), p); p.setShader(null); } }
    public class WaveView extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float level = 0.05f; public WaveView(Context c) { super(c); } @Override protected void onDraw(Canvas c) { int bars = 9; int gap = dp(8); int barW = dp(3); int total = bars * barW + (bars - 1) * gap; int start = (getWidth() - total) / 2; int mid = getHeight() / 2; p.setColor(Color.WHITE); p.setStyle(Paint.Style.FILL); for (int i = 0; i < bars; i++) { float distance = Math.abs(i - (bars - 1) / 2f); float scale = Math.max(0.15f, 1f - distance * 0.14f); int h = Math.max(dp(8), Math.round(dp(78) * level * scale)); int x = start + i * (barW + gap); c.drawRect(x, mid - h / 2f, x + barW, mid + h / 2f, p); } } }
    public class BorderWaveView extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float level = 0.05f; public BorderWaveView(Context c) { super(c); } @Override protected void onDraw(Canvas c) { p.setStyle(Paint.Style.STROKE); p.setStrokeCap(Paint.Cap.SQUARE); p.setStrokeJoin(Paint.Join.MITER); p.setColor(Color.argb(145,255,255,255)); p.setStrokeWidth(dp(2)); float h = dp(1); c.drawRect(h, h, getWidth() - h, getHeight() - h, p); if (level > 0.12f) { p.setColor(Color.argb(Math.min(210, 90 + Math.round(level * 120)),255,255,255)); p.setStrokeWidth(dp(1)); float in = dp(7); c.drawRect(in, in, getWidth() - in, getHeight() - in, p); } } }
    public class GlobeButton extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); boolean active = false; public GlobeButton(Context c) { super(c); } @Override protected void onDraw(Canvas c) { if (!active) return; int w=getWidth(), h=getHeight(); float r=Math.min(w,h)*0.25f, cx=w/2f, cy=h/2f; p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, dp(1))); p.setStrokeCap(Paint.Cap.ROUND); c.drawCircle(cx, cy, r, p); c.drawOval(cx-r*0.45f, cy-r, cx+r*0.45f, cy+r, p); c.drawArc(cx-r, cy-r*0.55f, cx+r, cy+r*0.55f, 0, 360, false, p); c.drawLine(cx-r*0.94f, cy, cx+r*0.94f, cy, p); } }
    public class JumpTextView extends TextView { String word = "thinking"; int step = 0; Runnable tick = new Runnable() { @Override public void run() { animateJump(); } }; public JumpTextView(Context c) { super(c); setIncludeFontPadding(true); setGravity(Gravity.CENTER_VERTICAL); } @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); animateJump(); } @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); } private void animateJump() { String w = word == null || word.length() == 0 ? "thinking" : word; SpannableString span = new SpannableString(w); int cycle = w.length() + 4, pos = step++ % cycle, peak = Math.min(pos, w.length() - 1); for (int i = 0; i < w.length(); i++) { int dist = Math.abs(i - peak); float size = pos >= w.length() ? 1.0f : dist == 0 ? 1.14f : dist == 1 ? 1.06f : 1.0f; span.setSpan(new RelativeSizeSpan(size), i, i + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE); } setText(span); postDelayed(tick, pos >= w.length() ? 260 : 135); } }
    public static class ContextMeter extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); float percent = 0; public ContextMeter(Context c) { super(c); } @Override protected void onDraw(Canvas c) { int w=getWidth(), h=getHeight(), r=Math.min(w,h)/2-2; p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(2); p.setColor(Color.WHITE); c.drawCircle(w/2f,h/2f,r,p); p.setStyle(Paint.Style.FILL); c.drawArc(w/2f-r,h/2f-r,w/2f+r,h/2f+r,-90,percent*3.6f,true,p); p.setColor(Color.BLACK); c.drawCircle(w/2f,h/2f,Math.max(1,r-5),p); } }
    public static class Msg { String role, text, imageBase64, imageMime, stats, model, replyQuote, reasoning = "", memorySavedText = ""; boolean slowVoice = false, reasoningCapable = false, thinkingExpanded = false, searchExpanded = false, memorySaved = false, memoryExpanded = false, ttsRequested = false, ttsStarted = false, ttsPlaying = false, ttsStartDelayDone = false, streamDone = false; int spokenChars = 0, ttsPlaybackFailures = 0, voiceSessionId = 0; long startedAt = System.currentTimeMillis(), thoughtMs = 0; ArrayList<String> ttsQueue = new ArrayList<String>(); ArrayList<String> searchSources = new ArrayList<String>(); Msg(String r, String t, String i, String m, String s) { this(r,t,i,m,s,"",""); } Msg(String r, String t, String i, String m, String s, String modelName) { this(r,t,i,m,s,modelName,""); } Msg(String r, String t, String i, String m, String s, String modelName, String reply) { role=r; text=t==null?"":t; imageBase64=i==null?"":i; imageMime=m==null?"":m; stats=s==null?"":s; model=modelName==null?"":modelName; replyQuote=reply==null?"":reply; } JSONObject toJson() throws Exception { JSONArray src = new JSONArray(); for (String s : searchSources) src.put(s); return new JSONObject().put("role",role).put("text",text).put("image",imageBase64).put("mime",imageMime).put("stats",stats).put("model",model).put("replyQuote",replyQuote).put("reasoning",reasoning).put("thoughtMs",thoughtMs).put("memorySaved",memorySaved).put("memorySavedText",memorySavedText).put("searchSources",src); } static Msg fromJson(JSONObject o) { Msg m = new Msg(o.optString("role"),o.optString("text"),o.optString("image"),o.optString("mime"),o.optString("stats"),o.optString("model"),o.optString("replyQuote")); m.reasoning = o.optString("reasoning", ""); m.thoughtMs = o.optLong("thoughtMs", 0); m.memorySaved = o.optBoolean("memorySaved", false); m.memorySavedText = o.optString("memorySavedText", ""); JSONArray src = o.optJSONArray("searchSources"); if (src != null) for (int i = 0; i < src.length(); i++) { String s = src.optString(i, ""); if (s.length() > 0) m.searchSources.add(s); } m.streamDone = !LOADING.equals(m.stats); return m; } }
    public static class Chat { String id="", title="", folder="Inbox", model=""; boolean webSearch=false; ArrayList<Msg> messages=new ArrayList<Msg>(); JSONObject toJson() throws Exception { JSONArray a=new JSONArray(); for(Msg m:messages)a.put(m.toJson()); return new JSONObject().put("id",id).put("title",title).put("folder",folder).put("model",model).put("webSearch",webSearch).put("messages",a); } static Chat fromJson(JSONObject o) { Chat c=new Chat(); c.id=o.optString("id"); c.title=o.optString("title"); c.folder=o.optString("folder","Inbox"); c.model=o.optString("model", ""); c.webSearch=o.optBoolean("webSearch", false); JSONArray a=o.optJSONArray("messages"); if(a!=null) for(int i=0;i<a.length();i++) c.messages.add(Msg.fromJson(a.optJSONObject(i))); return c; } }
}
