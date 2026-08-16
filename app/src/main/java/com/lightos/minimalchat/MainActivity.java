package com.lightos.minimalchat;

import android.Manifest;
import android.app.Activity;
import android.app.Dialog;
import android.app.DownloadManager;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.media.MediaPlayer;
import android.media.MediaRecorder;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.Settings;
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
import android.view.TouchDelegate;
import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
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
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

public class MainActivity extends Activity {
    private static final String OPENROUTER_ENDPOINT = "https://openrouter.ai/api/v1";
    private static final int VOICE = 11;
    private static final int MESSAGE_WINDOW = 40;
    private static final int MESSAGE_PAGE = 20;
    private static final float BASE_WIDTH_DP = 360f;
    private static final String LOADING = "__loading__";
    private static final String SEARCHING = "__searching__";
    private static final String APP_VERSION = "1.0.45";
    private static final String CHATS_STORE = "chats-store.json";
    private static final long PERSIST_DEBOUNCE_MS = 900;
    private static final long STREAM_RENDER_MIN_MS = 48;
    private static final long MODEL_AUTO_REFRESH_MS = 60L * 60L * 1000L;
    private static final long VERSION_CHECK_MS = 3L * 60L * 60L * 1000L;
    private static final int CONTACTS_PERM = 12;
    private static final int CALL_PERM = 13;
    private static final int INSTALL_PERM = 14;
    private static final int PICK_IMAGE = 15;
    private boolean forceDialFallback = false;
    private static final String PHONE_UTTERANCE = "phone-command";
    private static final String GITHUB_RELEASES_LATEST = "https://api.github.com/repos/awpsec/lightui/releases/latest";
    private SharedPreferences prefs;
    private Runnable pendingPersist;
    private Runnable pendingStreamRender;
    private Runnable pendingChatFilter;
    private ValueAnimator chatSearchAnim;
    private boolean chatSearchOpen = false;
    private int chatPinGen;
    private Runnable pendingChatPin;
    private long lastStreamRenderAt = 0;
    private boolean chatsDirty = false;
    private boolean modelsRefreshing = false;
    private boolean updateDialogShowing = false;
    private boolean updateDownloading = false;
    private Intent pendingLaunchIntent;
    private PhoneCommand pendingPhoneCommand;
    private String pendingInstallVersion = "";
    private String pendingInstallApkUrl = "";
    private Dialog updateDialog;
    private FrameLayout screen, paneHost;
    private final LinearLayout[] paneRoots = new LinearLayout[3];
    private final boolean[] paneReady = new boolean[3];
    private String chatsBoundProject = "\u0001";
    private String chatBoundId = "\u0001";
    private String settingsBoundPage = "\u0001";
    private int chatRenderGen;
    private int messageTokKey = Integer.MIN_VALUE;
    private int messageTokCached = 0;
    private int bgTokKey = Integer.MIN_VALUE;
    private int bgTokCached = 0;
    private LinearLayout root, messageList, folderList, chatList;
    private ScrollView scroll, settingsScrollView;
    private ScrollIndicator scrollIndicator;
    private EditText input, apiKey, endpointInput, endpointKeyInput, jinaKeyInput, braveKeyInput, voiceEndpointInput, chatSearch;
    private View chatSearchRow;
    private ImageButton chatSearchBtn, composerAction;
    private TextView chatSearchClear;
    private View photoPreviewOverlay;
    private TextView modelText, contextText, attachText, replyChip, notice, voiceStatus, voiceText, voiceReply, bulkButton, emptyPrompt, bottomButton, chatsSelectCount;
    private LinearLayout slashSuggestRow, chatsSelectBar;
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
    private final ArrayList<String> pinnedModels = new ArrayList<String>();
    private final ArrayList<String> customEndpoints = new ArrayList<String>();
    private final HashMap<String, String> endpointKeys = new HashMap<String, String>();
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
    private final HashSet<String> titleRequests = new HashSet<String>();
    private final ArrayList<String> folders = new ArrayList<String>();
    private final ArrayList<Msg> messages = new ArrayList<Msg>();
    private final ArrayList<Chat> chats = new ArrayList<Chat>();
    private final ArrayList<String> lastSearchSources = new ArrayList<String>();
    private String lastSearchResult = "";
    private String lastSearchQuery = "";
    private final HashSet<String> selectedChats = new HashSet<String>();
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String currentChatId = "", selectedFolder = "Inbox", projectView = "", expandedFolder = "", pendingVoiceText = "", replyQuote = "", voiceThinkingWord = "thinking...", settingsPage = "", chatFilter = "";
    private final ArrayList<AttachedImage> pendingImages = new ArrayList<AttachedImage>();
    private static final int MAX_PENDING_IMAGES = 6;
    private int pane = 1, messageStart = 0, messageEnd = 0, savedChatScrollY = 0, savedSettingsScrollY = 0, emptyPromptRun = 0, voiceThinkingRun = 0, voiceListenRun = 0, recorderSpeechFrames = 0, voiceSession = 0, paneSlide = 0;
    private long recordingStartedAt = 0, quietSince = 0;
    private float downX, downY;
    private boolean messageWindowReady = false, renderingMessages = false, savedChatScrollKnown = false, restoreScrollOnce = false, forceAutoScrollBottom = false, userAtChatBottom = true, voiceMode = false, voiceFullMode = false, voiceThinking = false, voiceAwaitingSpeechResult = false, ttsReady = false, hookVoiceMode = false, projectEditorOpen = false, recordingFallback = false, wavRecording = false, wavSubmitAfterStop = false, webSearchChat = false;
    private boolean forceSearchThisTurn = false, researchThisTurn = false;
    private volatile boolean turnForceSearch = false, turnResearch = false;
    private final HashSet<String> endpointsWithoutNativeTools = new HashSet<String>();
    private final HashMap<String, Bitmap> faviconCache = new HashMap<String, Bitmap>();
    private final HashSet<String> faviconLoading = new HashSet<String>();
    private final HashSet<String> faviconFailed = new HashSet<String>();
    private volatile boolean streamUiQueued = false;
    private volatile Msg streamUiAssistant = null;
    private volatile String streamUiPartial = "";
    private volatile String streamUiReasoning = "";
    private TextView liveStreamBody;
    private Msg liveStreamMsg;
    private String liveStreamKey = "";
    private Runnable faviconUi;
    private String lastSlashPaletteKey = "";

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
        maybeAutoRefreshModels(false);
        if (!hookVoiceMode) {
            maybeShowWelcome();
            maybeCheckLatestVersion(true);
        }
    }

    @Override protected void onResume() {
        super.onResume();
        maybeAutoRefreshModels(false);
        if (!hookVoiceMode) {
            // VoiceHookActivity may have written new chats while we were paused.
            reloadChatStorePreservingCurrent();
            maybeShowUpdateDialog();
            maybeCheckLatestVersion(true);
            if (pendingInstallApkUrl.length() > 0 && canInstallPackages()) {
                String url = pendingInstallApkUrl;
                String ver = pendingInstallVersion;
                pendingInstallApkUrl = "";
                pendingInstallVersion = "";
                downloadAndInstallUpdate(ver, url);
            }
        }
    }

    @Override protected void onNewIntent(Intent intent) { super.onNewIntent(intent); handleIncoming(intent); }

    @Override protected void onDestroy() {
        flushPendingPersist();
        super.onDestroy();
        stopAllVoiceAudio();
        if (speechRecognizer != null) { speechRecognizer.destroy(); speechRecognizer = null; }
        stopRecorder(false);
        if (tts != null) { tts.stop(); tts.shutdown(); tts = null; }
    }

    @Override protected void onPause() {
        super.onPause();
        flushSettingsInputs();
        flushPendingPersist();
        if (voiceMode && voiceFullMode) stopVoiceMode();
    }

    private void buildChrome() {
        screen = new FrameLayout(this);
        screen.setBackgroundColor(hookVoiceMode ? Color.TRANSPARENT : Color.BLACK);
        paneHost = new FrameLayout(this);
        paneHost.setBackgroundColor(hookVoiceMode ? Color.TRANSPARENT : Color.BLACK);
        screen.addView(paneHost, new FrameLayout.LayoutParams(-1, -1));
        setContentView(screen);
    }

    private LinearLayout makePaneRoot() {
        LinearLayout p = new LinearLayout(this);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackgroundColor(hookVoiceMode ? Color.TRANSPARENT : Color.BLACK);
        return p;
    }

    private void switchToPane(int which) {
        if (paneHost == null) return;
        if (paneRoots[which] == null) {
            paneRoots[which] = makePaneRoot();
            paneHost.addView(paneRoots[which], new FrameLayout.LayoutParams(-1, -1));
        }
        for (int i = 0; i < 3; i++) {
            LinearLayout p = paneRoots[i];
            if (p == null) continue;
            boolean on = i == which;
            if (!on) {
                p.animate().cancel();
                p.setAlpha(1f);
                p.setTranslationX(0);
            }
            p.setVisibility(on ? View.VISIBLE : View.GONE);
        }
        root = paneRoots[which];
    }

    private boolean paneIsShowing(int which) {
        return root != null && paneRoots[which] != null && root == paneRoots[which]
                && paneRoots[which].getVisibility() == View.VISIBLE;
    }

    private boolean chatsPaneReusable() {
        return paneReady[0] && paneRoots[0] != null && chatList != null && !projectEditorOpen
                && projectView.equals(chatsBoundProject);
    }

    private boolean chatPaneReusable() {
        return paneReady[1] && paneRoots[1] != null && messageList != null && scroll != null && input != null;
    }

    private boolean settingsPaneReusable() {
        return paneReady[2] && paneRoots[2] != null && settingsScrollView != null
                && settingsPage.equals(settingsBoundPage);
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
                if (dx > 0 && pane == 0 && projectView.length() > 0) { paneSlide = 1; projectView = ""; showChatsPane(); return true; }
                if (dx > 0 && pane == 2 && settingsPage.length() > 0) { paneSlide = 1; settingsPage = ""; showSettingsPane(); return true; }
                if (dx < 0 && pane < 2) { paneSlide = -1; pane++; if (pane == 2) settingsPage = ""; renderPane(); return true; }
                if (dx > 0 && pane > 0) { paneSlide = 1; pane--; renderPane(); return true; }
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
                if (photoPreviewOverlay != null) { dismissImagePreview(); return true; }
                if (voiceMode && voiceFullMode) stopVoiceMode();
                else if (pane == 0 && chatSearchOpen) collapseChatSearch(true);
                else if (projectEditorOpen) showChatsPane();
                else if (pane == 0 && projectView.length() > 0) { paneSlide = 1; projectView = ""; showChatsPane(); }
                else if (pane == 2 && settingsPage.length() > 0) { paneSlide = 1; settingsPage = ""; showSettingsPane(); }
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
        if (pane == 0 && chatSearchOpen && !eventInsideView(chatSearchRow, e) && !eventInsideView(chatSearchBtn, e) && !eventInsideView(chatSearchClear, e)) {
            collapseChatSearch(true);
        }
        View focused = getCurrentFocus();
        if (!(focused instanceof EditText)) return;
        int[] loc = new int[2];
        focused.getLocationOnScreen(loc);
        float x = e.getRawX(), y = e.getRawY();
        boolean inside = x >= loc[0] && x <= loc[0] + focused.getWidth() && y >= loc[1] && y <= loc[1] + focused.getHeight();
        if (!inside) clearFocusedTextField();
    }

    private boolean eventInsideView(View v, MotionEvent e) {
        if (v == null || v.getVisibility() != View.VISIBLE || v.getWidth() <= 0) return false;
        int[] loc = new int[2];
        v.getLocationOnScreen(loc);
        float x = e.getRawX(), y = e.getRawY();
        return x >= loc[0] && x <= loc[0] + v.getWidth() && y >= loc[1] && y <= loc[1] + v.getHeight();
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
        if (pane != 2) flushSettingsInputs();
        removeScreenChild(bulkButton); bulkButton = null;
        if (pane == 0) showChatsPane(); else if (pane == 2) showSettingsPane(); else showChatPane();
    }

    private void clearPaneViews() {
        if (root != null) {
            root.animate().cancel();
            root.setAlpha(1f);
            root.setTranslationX(0);
            root.removeAllViews();
        }
        if (pane == 0) {
            chatSearch = null;
            chatSearchRow = null;
            chatSearchBtn = null;
            chatSearchClear = null;
            chatsSelectBar = null;
            chatsSelectCount = null;
            chatList = null;
            paneReady[0] = false;
            chatsBoundProject = "\u0001";
        } else if (pane == 2) {
            apiKey = null;
            endpointInput = null;
            endpointKeyInput = null;
            jinaKeyInput = null;
            braveKeyInput = null;
            voiceEndpointInput = null;
            settingsScrollView = null;
            paneReady[2] = false;
            settingsBoundPage = "\u0001";
        } else {
            input = null;
            composerAction = null;
            contextText = null;
            attachText = null;
            replyChip = null;
            slashSuggestRow = null;
            modelText = null;
            meter = null;
            emptyPrompt = null;
            webSearchIcon = null;
            scroll = null;
            messageList = null;
            paneReady[1] = false;
            chatBoundId = "\u0001";
        }
        removeScreenChild(notice); notice = null;
        dismissImagePreview();
        removeScreenChild(bulkButton); bulkButton = null;
        removeScreenChild(scrollIndicator); scrollIndicator = null;
        removeScreenChild(chatFade); chatFade = null;
        removeScreenChild(bottomButton); bottomButton = null;
        emptyPromptRun++;
    }

    private void refreshChatChrome() {
        if (modelText != null) modelText.setText(modelLabel() + " >");
        if (meter != null) { meter.percent = contextPercent(); meter.invalidate(); }
        if (contextText != null) contextText.setText(contextPercentText());
        if (webSearchIcon != null) {
            webSearchIcon.active = webSearchChat;
            webSearchIcon.setClickable(webSearchChat);
            webSearchIcon.invalidate();
        }
        updateComposerAction();
        updateAttachChip();
        updateReplyChip();
    }

    private void finishChatPane(boolean messagesReady) {
        paneReady[1] = true;
        chatBoundId = currentChatId == null ? "" : currentChatId;
        if (messagesReady) {
            slidePaneIn();
            return;
        }
        boolean animate = paneSlide != 0;
        if (!animate) {
            renderMessages();
            slidePaneIn();
            return;
        }
        slidePaneIn();
        final int gen = ++chatRenderGen;
        ui.post(new Runnable() { @Override public void run() {
            if (pane != 1 || gen != chatRenderGen || messageList == null) return;
            renderMessages();
        } });
    }

    private void removeScreenChild(View v) {
        try { if (v != null && screen != null && v.getParent() == screen) screen.removeView(v); } catch (Exception ignored) { }
    }

    private void showChatPane() {
        projectEditorOpen = false;
        if (chatSearchOpen) collapseChatSearch(false);
        pane = 1;
        boolean reuse = chatPaneReusable();
        boolean sameChat = reuse && (currentChatId == null ? "" : currentChatId).equals(chatBoundId);
        chatRenderGen++;
        switchToPane(1);
        if (reuse) {
            refreshChatChrome();
            if (sameChat) {
                finishChatPane(true);
                restoreChatScrollAfterShow();
                return;
            }
            restoreScrollOnce = savedChatScrollKnown;
            paneReady[1] = true;
            chatBoundId = currentChatId == null ? "" : currentChatId;
            renderMessages();
            slidePaneIn();
            restoreChatScrollAfterShow();
            return;
        }
        restoreScrollOnce = savedChatScrollKnown;
        clearPaneViews();
        root.setOnClickListener(null);
        root.setPadding(dp(26), dp(14), dp(26), dp(10));
        LinearLayout header = row();
        modelText = text(modelLabel() + " >", 13, Color.WHITE);
        modelText.setGravity(Gravity.CENTER_VERTICAL);
        modelText.setSingleLine(true);
        modelText.setEllipsize(android.text.TextUtils.TruncateAt.END);
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
        messageList.setPadding(0, dp(12), 0, dp(16));
        messageList.setBackgroundColor(Color.BLACK);
        scroll.addView(messageList);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
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
        attachText.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { clearPendingAttachment(); }
        });
        root.addView(attachText, new LinearLayout.LayoutParams(-1, dp(18)));
        replyChip = text("", 11, Color.LTGRAY);
        replyChip.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { replyQuote = ""; updateReplyChip(); } });
        root.addView(replyChip, new LinearLayout.LayoutParams(-1, dp(20)));
        attachText.setVisibility(View.GONE);
        replyChip.setVisibility(View.GONE);
        updateReplyChip();
        root.addView(separator());

        slashSuggestRow = new LinearLayout(this);
        slashSuggestRow.setOrientation(LinearLayout.VERTICAL);
        slashSuggestRow.setVisibility(View.GONE);
        slashSuggestRow.setPadding(0, 0, 0, dp(4));
        root.addView(slashSuggestRow, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout composer = row();
        composer.setGravity(Gravity.BOTTOM);
        input = new EditText(this);
        input.setTextColor(Color.WHITE);
        input.setHintTextColor(Color.rgb(120, 120, 120));
        input.setHint("ask");
        setTextPx(input, 18);
        input.setSingleLine(false);
        input.setMinLines(1);
        input.setMaxLines(3);
        input.setHorizontallyScrolling(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
                | InputType.TYPE_TEXT_FLAG_AUTO_CORRECT);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setVerticalScrollBarEnabled(true);
        input.setBackgroundColor(Color.BLACK);
        input.setMinHeight(dp(50));
        input.setPadding(0, dp(14), 0, dp(14));
        input.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean hasFocus) { input.setHint(hasFocus ? "" : "ask"); }
        });
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                updateComposerAction();
                updateSlashSuggestions(s == null ? "" : s.toString());
            }
            @Override public void afterTextChanged(Editable e) { }
        });
        composer.addView(input, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
        LinearLayout.LayoutParams actionLp = new LinearLayout.LayoutParams(dp(34), dp(34));
        actionLp.gravity = Gravity.BOTTOM;
        actionLp.bottomMargin = dp(8);
        composerAction = iconButton(R.drawable.ic_mic, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (composerHasOutgoing()) send();
                else startVoice(false);
            }
        }, 4);
        composerAction.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showComposerActionMenu(v); return true; }
        });
        composer.addView(composerAction, actionLp);
        root.addView(composer);
        updateComposerAction();
        updateAttachChip();
        updateSlashSuggestions(input.getText() == null ? "" : input.getText().toString());
        finishChatPane(false);
    }

    private void showSettingsPane() {
        boolean arriving = !paneIsShowing(2);
        projectEditorOpen = false;
        if (chatSearchOpen) collapseChatSearch(false);
        flushSettingsInputs();
        if (pane == 2 && settingsPage.length() == 0 && settingsScrollView != null) savedSettingsScrollY = settingsScrollView.getScrollY();
        pane = 2;
        captureChatScroll();
        boolean reuse = arriving && settingsPaneReusable();
        switchToPane(2);
        if (reuse) {
            slidePaneIn();
            return;
        }
        clearPaneViews();
        root.setOnClickListener(null);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        apiKey = null;
        endpointInput = null;
        endpointKeyInput = null;
        jinaKeyInput = null;
        braveKeyInput = null;
        voiceEndpointInput = null;
        root.addView(settingsTitle());
        ScrollView settingsScroll = new ScrollView(this);
        settingsScrollView = settingsScroll;
        LinearLayout settings = new LinearLayout(this);
        settings.setOrientation(LinearLayout.VERTICAL);
        settings.setGravity(Gravity.TOP | Gravity.START);
        settings.setBackgroundColor(Color.BLACK);
        settingsScroll.setFillViewport(true);
        settingsScroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        settingsScroll.addView(settings, new ScrollView.LayoutParams(-1, -2));
        settingsScroll.setVerticalScrollBarEnabled(false);

        if (settingsPage.length() == 0) addSettingsIndex(settings);
        else if ("providers".equals(settingsPage) || "model source".equals(settingsPage)) addModelSourceSettings(settings);
        else if ("search".equals(settingsPage)) addJinaSettings(settings);
        else if ("voice".equals(settingsPage)) addVoiceSettings(settings);
        else if ("display".equals(settingsPage)) addDisplaySettings(settings);
        else if ("memory".equals(settingsPage)) addMemorySettings(settings);
        else if ("models".equals(settingsPage)) addModelsSettings(settings);
        root.addView(settingsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        paneReady[2] = true;
        settingsBoundPage = settingsPage;
        settingsScroll.post(new Runnable() { @Override public void run() { settingsScroll.scrollTo(0, settingsPage.length() == 0 ? savedSettingsScrollY : 0); } });
        slidePaneIn();
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
        title.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (settingsPage.length() > 0) { paneSlide = 1; settingsPage = ""; showSettingsPane(); } } });
        h.addView(title, new LinearLayout.LayoutParams(-2, dp(34)));
        if (settingsPage.length() > 0) {
            TextView sub = text("  " + settingsPageTitle(settingsPage), 13, Color.rgb(150,150,150));
            sub.setGravity(Gravity.CENTER_VERTICAL);
            sub.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { paneSlide = 1; settingsPage = ""; showSettingsPane(); } });
            h.addView(sub, new LinearLayout.LayoutParams(0, dp(34), 1));
        }
        return h;
    }

    private String settingsPageTitle(String page) {
        if ("model source".equals(page) || "providers".equals(page)) return "providers";
        if ("search".equals(page)) return "web search";
        if ("memory".equals(page)) return "memory";
        return page;
    }

    private void addSettingsIndex(LinearLayout settings) {
        settings.addView(settingsNav("models", "models", modelsNavDetail(), 0));
        settings.addView(settingsNav("providers", "providers", providersNavDetail(), 1));
        settings.addView(settingsNav("web search", "search", searchNavDetail(), 2));
        settings.addView(settingsNav("voice", "voice", voiceNavDetail(), 3));
        settings.addView(settingsNav("display", "display", displayNavDetail(), 4));
        settings.addView(settingsNav("memory", "memory", memoryNavDetail(), 5));
        addVersionFooter(settings);
    }

    private View settingsNav(final String title, final String page, String detail, int index) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setGravity(Gravity.TOP | Gravity.START);
        card.setPadding(dp(10), dp(9), dp(10), dp(9));
        card.setBackground(listCardBg(false, false, index));
        LinearLayout top = row();
        top.setBackgroundColor(Color.TRANSPARENT);
        TextView name = text(title, 15, Color.WHITE);
        name.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        name.setBackgroundColor(Color.TRANSPARENT);
        TextView arrow = text(">", 14, Color.rgb(135, 135, 135));
        arrow.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        arrow.setBackgroundColor(Color.TRANSPARENT);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        top.addView(arrow, new LinearLayout.LayoutParams(dp(28), -2));
        card.addView(top, new LinearLayout.LayoutParams(-1, -2));
        if (detail != null && detail.length() > 0) {
            TextView sub = text(detail, 11, Color.rgb(135, 135, 135));
            sub.setGravity(Gravity.START);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setBackgroundColor(Color.TRANSPARENT);
            sub.setPadding(0, dp(2), 0, 0);
            card.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        }
        bindPress(card);
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            if (settingsScrollView != null) savedSettingsScrollY = settingsScrollView.getScrollY();
            paneSlide = -1;
            settingsPage = page;
            showSettingsPane();
        } });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        if (index > 0) lp.topMargin = dp(6);
        card.setLayoutParams(lp);
        return card;
    }

    private String modelsNavDetail() {
        if (myModels.size() == 0) return "none added";
        String m = selectedModel();
        if (m.length() == 0) return myModels.size() + " saved";
        return shortModel(m) + (myModels.size() > 1 ? " · " + myModels.size() : "");
    }

    private String providersNavDetail() {
        boolean or = savedApiKey().length() > 0;
        int n = customEndpoints.size();
        if (or && n > 0) return "openrouter · " + n + " endpoint" + (n == 1 ? "" : "s");
        if (or) return "openrouter";
        if (n > 0) return n + " endpoint" + (n == 1 ? "" : "s");
        return "add a key or endpoint";
    }

    private String searchNavDetail() {
        return searchProvider() + (prefs.getBoolean("autoSearch", false) ? " · auto on" : " · auto off");
    }

    private String voiceNavDetail() {
        return (prefs.getBoolean("voiceSpeak", true) ? "speak on" : "speak off") + " · " + voiceOutputProvider();
    }

    private String displayNavDetail() {
        int n = fontOffset();
        return n == 0 ? "font default" : "font " + (n > 0 ? "+" : "") + n;
    }

    private String memoryNavDetail() {
        if (!memoryEnabled()) return "off";
        return "on · " + memoryEntryCount(normalizeMemoryMd(memoryMd())) + " entries";
    }

    private void maybeShowWelcome() {
        if (hookVoiceMode || prefs == null) return;
        if (prefs.getBoolean("welcomeShown", false)) return;
        // Upgrades / existing installs never had this flag — don't treat them as first launch.
        if (hasPriorAppUse()) {
            prefs.edit().putBoolean("welcomeShown", true).commit();
            return;
        }
        if (updateDialogShowing || voiceMode) return;
        showWelcomeDialog();
    }

    private boolean hasPriorAppUse() {
        if (prefs == null) return false;
        if (savedApiKey().length() > 0) return true;
        if (customEndpoints.size() > 0) return true;
        if (chats.size() > 0) return true;
        if (prefs.getBoolean("modelSelected", false)) return true;
        if (prefs.getLong("modelsRefreshedAt", 0) > 0) return true;
        if (prefs.getString("memoryMd", "").trim().length() > 0) return true;
        if (prefs.contains("latestGitHubVersion")) return true;
        if (prefs.contains("silencedUpdateVersion")) return true;
        if (prefs.contains("latestVersionCheckedAt")) return true;
        File store = new File(getFilesDir(), CHATS_STORE);
        return store.exists() && store.length() > 2;
    }

    private void showWelcomeDialog() {
        if (prefs == null) return;
        // Persist immediately so updates / relaunches never re-show this.
        prefs.edit().putBoolean("welcomeShown", true).commit();
        final Dialog d = panel("welcome");
        d.setCancelable(true);
        d.setCanceledOnTouchOutside(true);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface dialog) {
                maybeShowUpdateDialog();
            }
        });
        LinearLayout box = panelBox();
        box.addView(panelTitle("welcome to lightui"));
        TextView msg = text("configure openrouter or an openai compatible endpoint in settings, add models, and you're good to go.", 13, Color.LTGRAY);
        msg.setPadding(0, 0, 0, dp(16));
        box.addView(msg);
        TextView ok = panelAction("got it");
        ok.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(ok, new LinearLayout.LayoutParams(-1, dp(48)));
        showPanel(d, box);
    }

    private boolean voicePhoneCommandsEnabled() {
        return prefs == null || prefs.getBoolean("voicePhoneCommands", true);
    }

    private void addVersionFooter(LinearLayout settings) {
        maybeCheckLatestVersion(false);
        boolean outdated = hasPendingUpdate();
        TextView v = text(versionFooterText(), 11, outdated ? Color.rgb(190, 190, 190) : Color.rgb(130, 130, 130));
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(0, dp(18), 0, dp(10));
        v.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View view) { onVersionFooterClick(); } });
        settings.addView(v, new LinearLayout.LayoutParams(-1, -2));
    }

    private void onVersionFooterClick() {
        if (updateDownloading) { toast("download already running"); return; }
        if (updateDialogShowing) return;
        String latest = prefs.getString("latestGitHubVersion", "");
        String apk = prefs.getString("latestGitHubApkUrl", "");
        if (latest.length() > 0 && apk.length() > 0 && compareVersions(latest, APP_VERSION) > 0) {
            showUpdateDialog(latest, apk);
            return;
        }
        toast("checking for updates");
        prefs.edit().remove("latestVersionCheckedAt").apply();
        checkLatestVersion(true, true);
    }

    private boolean hasPendingUpdate() {
        if (prefs == null) return false;
        String latest = prefs.getString("latestGitHubVersion", "");
        return latest.length() > 0 && compareVersions(latest, APP_VERSION) > 0;
    }

    private String versionFooterText() {
        return "version " + APP_VERSION + (hasPendingUpdate() ? " - update available" : "");
    }

    private void maybeCheckLatestVersion(boolean prompt) {
        checkLatestVersion(prompt, false);
    }

    private void checkLatestVersion(final boolean prompt, final boolean fromUser) {
        if (prefs == null || hookVoiceMode) return;
        long now = System.currentTimeMillis();
        long last = prefs.getLong("latestVersionCheckedAt", 0);
        if (now - last < VERSION_CHECK_MS) {
            if (fromUser) presentUpdateCheckResult(true);
            else if (prompt) maybeShowUpdateDialog();
            return;
        }
        prefs.edit().putLong("latestVersionCheckedAt", now).apply();
        new Thread(new Runnable() { @Override public void run() {
            try {
                HttpURLConnection c = (HttpURLConnection) new URL(GITHUB_RELEASES_LATEST).openConnection();
                c.setConnectTimeout(10000);
                c.setReadTimeout(15000);
                c.setUseCaches(false);
                c.setRequestProperty("Accept", "application/vnd.github+json");
                c.setRequestProperty("User-Agent", "lightui-android");
                c.setRequestProperty("Connection", "close");
                int code = c.getResponseCode();
                if (code >= 400) {
                    if (fromUser) runOnUiThread(new Runnable() { @Override public void run() { toast("couldn't check for updates"); } });
                    return;
                }
                JSONObject release = new JSONObject(readAll(c.getInputStream()));
                final String tag = release.optString("tag_name", "").replaceFirst("^[vV]", "").trim();
                final String apkUrl = findReleaseApkUrl(release);
                if (tag.length() == 0) {
                    if (fromUser) runOnUiThread(new Runnable() { @Override public void run() { toast("couldn't check for updates"); } });
                    return;
                }
                prefs.edit().putString("latestGitHubVersion", tag).putString("latestGitHubApkUrl", apkUrl).apply();
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (pane == 2 && settingsPage.length() == 0) showSettingsPane();
                    if (fromUser) presentUpdateCheckResult(true);
                    else if (prompt) maybeShowUpdateDialog();
                } });
            } catch (Exception e) {
                if (fromUser) runOnUiThread(new Runnable() { @Override public void run() { toast("couldn't check for updates"); } });
            }
        } }).start();
    }

    private String findReleaseApkUrl(JSONObject release) {
        JSONArray assets = release == null ? null : release.optJSONArray("assets");
        if (assets == null) return "";
        String fallback = "";
        for (int i = 0; i < assets.length(); i++) {
            JSONObject a = assets.optJSONObject(i);
            if (a == null) continue;
            String name = a.optString("name", "").toLowerCase(Locale.US);
            String url = a.optString("browser_download_url", "");
            if (url.length() == 0 || !name.endsWith(".apk")) continue;
            if ("lightui-release.apk".equals(name) || name.contains("lightui")) return url;
            if (fallback.length() == 0) fallback = url;
        }
        return fallback;
    }

    private void maybeShowUpdateDialog() {
        presentUpdateCheckResult(false);
    }

    private void presentUpdateCheckResult(boolean fromUser) {
        if (hookVoiceMode || prefs == null || updateDialogShowing || updateDownloading || (!fromUser && voiceMode)) {
            return;
        }
        if (!fromUser && !prefs.getBoolean("welcomeShown", false)) return;
        String latest = prefs.getString("latestGitHubVersion", "");
        String apkUrl = prefs.getString("latestGitHubApkUrl", "");
        if (latest.length() == 0 || apkUrl.length() == 0) {
            if (fromUser) toast("couldn't check for updates");
            return;
        }
        if (compareVersions(latest, APP_VERSION) <= 0) {
            if (fromUser) toast("you're on the latest version");
            return;
        }
        if (!fromUser && latest.equals(prefs.getString("silencedUpdateVersion", ""))) return;
        showUpdateDialog(latest, apkUrl);
    }

    private void showUpdateDialog(final String version, final String apkUrl) {
        if (updateDialogShowing || version == null || version.length() == 0 || apkUrl == null || apkUrl.length() == 0) return;
        if (updateDialog != null) try { updateDialog.dismiss(); } catch (Exception ignored) { }
        updateDialogShowing = true;
        final Dialog d = panel("update");
        updateDialog = d;
        d.setCancelable(true);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface dialog) {
                updateDialogShowing = false;
                if (updateDialog == d) updateDialog = null;
            }
        });
        LinearLayout box = panelBox();
        box.addView(panelTitle("version " + version + " is available!"));
        TextView msg = text("a newer lightui build is on github. update now, dismiss for later, or silence this version.", 13, Color.LTGRAY);
        msg.setPadding(0, 0, 0, dp(16));
        box.addView(msg);
        TextView update = panelAction("update");
        TextView dismiss = panelAction("dismiss");
        TextView silence = panelAction("silence");
        update.setTextColor(Color.WHITE);
        update.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            d.dismiss();
            beginUpdateInstall(version, apkUrl);
        } });
        dismiss.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        silence.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            prefs.edit().putString("silencedUpdateVersion", version).apply();
            d.dismiss();
            toast("silenced " + version);
        } });
        box.addView(update, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(dismiss, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(silence, new LinearLayout.LayoutParams(-1, dp(48)));
        showPanel(d, box);
    }

    private void beginUpdateInstall(String version, String apkUrl) {
        if (updateDownloading) { toast("download already running"); return; }
        if (!canInstallPackages()) {
            pendingInstallVersion = version == null ? "" : version;
            pendingInstallApkUrl = apkUrl == null ? "" : apkUrl;
            toast("allow app installs");
            try {
                Intent i = new Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()));
                startActivity(i);
            } catch (Exception e) {
                toast("enable install unknown apps in settings");
            }
            return;
        }
        downloadAndInstallUpdate(version, apkUrl);
    }

    private boolean canInstallPackages() {
        if (Build.VERSION.SDK_INT < 26) return true;
        try { return getPackageManager().canRequestPackageInstalls(); } catch (Exception e) { return true; }
    }

    private void downloadAndInstallUpdate(final String version, final String apkUrl) {
        if (apkUrl == null || apkUrl.length() == 0) { toast("no apk url"); return; }
        if (updateDownloading) return;
        updateDownloading = true;
        toast("downloading " + version + "...");
        new Thread(new Runnable() { @Override public void run() {
            File out = new File(getCacheDir(), ToolText.updateApkFileName(version));
            deleteStaleUpdateFiles(out);
            Exception last = null;
            String[] urls = ToolText.updateDownloadUrls(apkUrl, version);
            String ua = "Mozilla/5.0 (Linux; Android 14; Mobile) lightui/" + APP_VERSION;
            try {
                if (apkHasVersion(out, version)) {
                    finishUpdateDownload(version, out);
                    return;
                }
                if (out.exists()) out.delete();
                for (int i = 0; i < urls.length; i++) {
                    try {
                        UpdateDownload.downloadApk(urls[i], out, ua);
                        if (!apkHasVersion(out, version)) {
                            last = new Exception("apk version mismatch");
                            if (out.exists()) out.delete();
                            continue;
                        }
                        finishUpdateDownload(version, out);
                        return;
                    } catch (Exception e) {
                        last = e;
                    }
                }
                try {
                    if (urls.length > 0 && downloadWithManager(urls[0], out)) {
                        if (apkHasVersion(out, version)) {
                            finishUpdateDownload(version, out);
                            return;
                        }
                        last = new Exception("apk version mismatch");
                        if (out.exists()) out.delete();
                    }
                } catch (Exception e) {
                    last = e;
                }
                final Exception fail = last;
                runOnUiThread(new Runnable() { @Override public void run() {
                    updateDownloading = false;
                    toast(ToolText.friendlyDownloadError(fail));
                    showUpdateDownloadFailed(version, apkUrl);
                } });
            } catch (Exception e) {
                final String msg = ToolText.friendlyDownloadError(e);
                runOnUiThread(new Runnable() { @Override public void run() {
                    updateDownloading = false;
                    toast(msg);
                    showUpdateDownloadFailed(version, apkUrl);
                } });
            }
        } }, "lightui-update").start();
    }

    private void finishUpdateDownload(final String version, final File apk) {
        runOnUiThread(new Runnable() { @Override public void run() {
            updateDownloading = false;
            if (!apkHasVersion(apk, version)) {
                toast("downloaded apk was not " + version);
                showUpdateDownloadFailed(version, prefs.getString("latestGitHubApkUrl", ""));
                return;
            }
            toast("installing " + version);
            installUpdateApk(apk);
        } });
    }

    private boolean apkHasVersion(File apk, String version) {
        if (apk == null || !UpdateDownload.isUsableApk(apk, -1)) return false;
        try {
            android.content.pm.PackageInfo info = getPackageManager().getPackageArchiveInfo(apk.getAbsolutePath(), 0);
            if (info == null) return false;
            String name = info.versionName == null ? "" : info.versionName.trim();
            if (name.length() == 0) return false;
            return compareVersions(name, version) >= 0;
        } catch (Exception e) {
            return false;
        }
    }

    private void deleteStaleUpdateFiles(File keep) {
        String keepPath = keep == null ? "" : keep.getAbsolutePath();
        String keepPart = keepPath.length() == 0 ? "" : keepPath + ".part";
        File[] dirs = new File[] { getCacheDir(), getFilesDir(), getExternalFilesDir(null) };
        for (int d = 0; d < dirs.length; d++) {
            File dir = dirs[d];
            if (dir == null) continue;
            File[] files = dir.listFiles();
            if (files == null) continue;
            for (int i = 0; i < files.length; i++) {
                File f = files[i];
                if (f == null) continue;
                String n = f.getName();
                if (!n.startsWith("lightui-update")) continue;
                if (!n.endsWith(".apk") && !n.endsWith(".apk.part")) continue;
                String path = f.getAbsolutePath();
                if (path.equals(keepPath) || path.equals(keepPart)) continue;
                f.delete();
            }
        }
    }

    private boolean downloadWithManager(String url, File dest) throws Exception {
        DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
        if (dm == null) return false;
        File extDir = getExternalFilesDir(null);
        if (extDir == null) return false;
        File ext = new File(extDir, dest.getName());
        if (ext.exists() && !ext.delete()) return false;
        DownloadManager.Request req = new DownloadManager.Request(Uri.parse(url));
        req.setMimeType("application/vnd.android.package-archive");
        req.setTitle("lightui");
        req.setDescription("updating");
        req.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
        req.setAllowedOverMetered(true);
        req.setAllowedOverRoaming(true);
        req.addRequestHeader("User-Agent", "Mozilla/5.0 (Linux; Android 14; Mobile) lightui/" + APP_VERSION);
        req.addRequestHeader("Accept", "application/octet-stream");
        req.setDestinationInExternalFilesDir(this, null, dest.getName());
        long id = dm.enqueue(req);
        long start = System.currentTimeMillis();
        try {
            while (System.currentTimeMillis() - start < 90000L) {
                Cursor c = dm.query(new DownloadManager.Query().setFilterById(id));
                if (c == null) { Thread.sleep(300); continue; }
                try {
                    if (!c.moveToFirst()) { Thread.sleep(300); continue; }
                    int status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS));
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        if (!ext.exists()) {
                            int uriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI);
                            String local = uriIdx >= 0 ? c.getString(uriIdx) : null;
                            if (local != null && local.startsWith("file://")) ext = new File(Uri.parse(local).getPath());
                        }
                        if (!UpdateDownload.isUsableApk(ext, -1)) return false;
                        if (dest.exists()) dest.delete();
                        UpdateDownload.copyFile(ext, dest);
                        return UpdateDownload.isUsableApk(dest, -1);
                    }
                    if (status == DownloadManager.STATUS_FAILED) return false;
                } finally {
                    c.close();
                }
                Thread.sleep(300);
            }
            return false;
        } finally {
            try { dm.remove(id); } catch (Exception ignored) {}
        }
    }

    private void showUpdateDownloadFailed(final String version, final String apkUrl) {
        if (updateDialogShowing) return;
        updateDialogShowing = true;
        final Dialog d = panel("update");
        updateDialog = d;
        d.setCancelable(true);
        d.setOnDismissListener(new android.content.DialogInterface.OnDismissListener() {
            @Override public void onDismiss(android.content.DialogInterface dialog) {
                updateDialogShowing = false;
                if (updateDialog == d) updateDialog = null;
            }
        });
        LinearLayout box = panelBox();
        box.addView(panelTitle("download cut off"));
        TextView msg = text("the apk did not finish. retry keeps going from where it stopped.", 13, Color.LTGRAY);
        msg.setPadding(0, 0, 0, dp(16));
        box.addView(msg);
        TextView retry = panelAction("retry");
        TextView dismiss = panelAction("dismiss");
        retry.setTextColor(Color.WHITE);
        retry.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            d.dismiss();
            beginUpdateInstall(version, apkUrl);
        } });
        dismiss.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(retry, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(dismiss, new LinearLayout.LayoutParams(-1, dp(48)));
        showPanel(d, box);
    }

    private void installUpdateApk(File apk) {
        if (apk == null || !apk.exists()) { toast("apk missing"); return; }
        try {
            Uri uri = ApkProvider.uriForFile(this, apk);
            Intent i = new Intent(Intent.ACTION_VIEW);
            i.setDataAndType(uri, "application/vnd.android.package-archive");
            i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("install failed: " + friendlyError(e));
        }
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
        ArrayList<String> ordered = ToolText.orderedModels(myModels, pinnedModels);
        for (int i = 0; i < ordered.size(); i++) {
            final String m = ordered.get(i).trim();
            if (m.length() == 0) continue;
            LinearLayout wrap = new LinearLayout(this);
            wrap.setOrientation(LinearLayout.HORIZONTAL);
            wrap.setGravity(Gravity.CENTER_VERTICAL);
            View row = modelListRow(m, m.equals(selectedModel()), pinnedModels.contains(m), i, true);
            row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { setModel(m); } });
            row.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
                togglePinnedModel(m);
                showSettingsPane();
                return true;
            } });
            wrap.addView(row, new LinearLayout.LayoutParams(0, -2, 1));
            TextView remove = text("remove", 12, Color.LTGRAY);
            remove.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
            remove.setPadding(dp(10), 0, 0, 0);
            remove.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { removeMyModel(m); } });
            wrap.addView(remove, new LinearLayout.LayoutParams(-2, -2));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            if (i > 0) lp.topMargin = dp(6);
            list.addView(wrap, lp);
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
        refresh.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { refreshModels(true); } });
        actions.addView(save);
        actions.addView(refresh);
        settings.addView(actions, new LinearLayout.LayoutParams(-1, dp(34)));
        settings.addView(space(8));
    }

    private void addEndpointSettings(LinearLayout settings) {
        settings.addView(text("saved endpoints", 11, Color.LTGRAY));
        if (customEndpoints.size() == 0) {
            TextView none = text("none", 17, Color.rgb(120, 120, 120));
            none.setGravity(Gravity.CENTER_VERTICAL);
            settings.addView(none, new LinearLayout.LayoutParams(-1, dp(42)));
        }
        for (int i = 0; i < customEndpoints.size(); i++) {
            final String endpoint = customEndpoints.get(i);
            LinearLayout line = row();
            line.setGravity(Gravity.CENTER_VERTICAL);
            TextView name = text(endpointLabelWithKey(endpoint), 17, Color.WHITE);
            name.setSingleLine(true);
            name.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
            name.setGravity(Gravity.CENTER_VERTICAL);
            TextView more = text("···", 17, Color.LTGRAY);
            more.setGravity(Gravity.CENTER);
            more.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showEndpointActions(endpoint); } });
            line.addView(name, new LinearLayout.LayoutParams(0, dp(42), 1));
            line.addView(more, new LinearLayout.LayoutParams(dp(42), dp(42)));
            settings.addView(line);
        }
        settings.addView(text("endpoint", 11, Color.LTGRAY));
        endpointInput = plainEdit("https://api.example.com/v1");
        endpointInput.setSingleLine(true);
        endpointInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        endpointInput.setText("");
        settings.addView(endpointInput, new LinearLayout.LayoutParams(-1, dp(42)));
        settings.addView(text("api key", 11, Color.LTGRAY));
        endpointKeyInput = plainEdit("optional");
        endpointKeyInput.setSingleLine(true);
        endpointKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        endpointKeyInput.setText("");
        settings.addView(endpointKeyInput, new LinearLayout.LayoutParams(-1, dp(42)));
        LinearLayout actions = row();
        TextView save = smallPill("save");
        TextView test = smallPill("test");
        TextView refresh = smallPill("refresh models");
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { addEndpointFromInput(); } });
        test.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { testCustomEndpoint(); } });
        refresh.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { refreshModels(true); } });
        actions.addView(save);
        actions.addView(test);
        actions.addView(refresh);
        settings.addView(actions, new LinearLayout.LayoutParams(-1, dp(34)));
        settings.addView(space(8));
    }

    private String endpointLabelWithKey(String endpoint) {
        String clean = normalizeEndpoint(endpoint);
        if (clean.length() == 0) return "";
        return endpointKey(clean).length() > 0 ? clean + " · key" : clean;
    }

    private void showEndpointActions(final String endpoint) {
        final Dialog d = panel("endpoint");
        d.setCancelable(true);
        d.setCanceledOnTouchOutside(true);
        LinearLayout box = panelBox();
        box.addView(panelTitle(shortEndpoint(endpoint)));
        TextView edit = panelAction("edit");
        TextView remove = panelAction("remove");
        edit.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); showEditEndpointDialog(endpoint); } });
        remove.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            d.dismiss();
            removeCustomEndpoint(endpoint);
            toast("endpoint removed");
            showSettingsPane();
        } });
        box.addView(edit, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(remove, new LinearLayout.LayoutParams(-1, dp(48)));
        showPanel(d, box);
    }

    private void showEditEndpointDialog(final String originalEndpoint) {
        final String from = normalizeEndpoint(originalEndpoint);
        final Dialog d = panel("edit endpoint");
        d.setCancelable(true);
        d.setCanceledOnTouchOutside(true);
        LinearLayout box = panelBox();
        box.addView(panelTitle("edit endpoint"));
        box.addView(text("endpoint", 11, Color.LTGRAY));
        final EditText urlInput = plainEdit("https://api.example.com/v1");
        urlInput.setSingleLine(true);
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setText(from);
        box.addView(urlInput, new LinearLayout.LayoutParams(-1, dp(42)));
        box.addView(text("api key", 11, Color.LTGRAY));
        final EditText keyInput = plainEdit("optional");
        keyInput.setSingleLine(true);
        keyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        keyInput.setText(endpointKey(from));
        box.addView(keyInput, new LinearLayout.LayoutParams(-1, dp(42)));
        box.addView(space(8));
        TextView test = panelAction("test");
        TextView save = panelAction("save");
        TextView cancel = panelAction("cancel");
        test.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            testEndpointConnection(normalizeEndpoint(urlInput.getText().toString()), keyInput.getText().toString().trim());
        } });
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            String to = normalizeEndpoint(urlInput.getText().toString());
            if (to.length() == 0) { toast("enter endpoint"); return; }
            renameCustomEndpoint(from, to, keyInput.getText().toString().trim());
            d.dismiss();
            toast("endpoint saved");
            showSettingsPane();
        } });
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(test, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(save, new LinearLayout.LayoutParams(-1, dp(48)));
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(48)));
        showPanel(d, box);
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
        braveKeyInput = null;
        if ("brave".equals(searchProvider())) {
            settings.addView(text("brave api key", 11, Color.LTGRAY));
            braveKeyInput = plainEdit("required");
            braveKeyInput.setSingleLine(true);
            braveKeyInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
            braveKeyInput.setText(prefs.getString("braveApiKey", ""));
            settings.addView(braveKeyInput, new LinearLayout.LayoutParams(-1, dp(42)));
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
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { flushSettingsInputs(); toast("saved"); } });
        actions.addView(save);
        settings.addView(actions, new LinearLayout.LayoutParams(-1, dp(34)));
        settings.addView(text("/search <query>   /research <query>", 11, Color.rgb(120,120,120)), new LinearLayout.LayoutParams(-1, dp(24)));
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

        LinearLayout phone = row();
        phone.addView(text("call & text shortcuts", 14, Color.WHITE), new LinearLayout.LayoutParams(0, dp(38), 1));
        final TogglePill phoneToggle = new TogglePill(this);
        phoneToggle.checked = voicePhoneCommandsEnabled();
        phoneToggle.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            boolean next = !voicePhoneCommandsEnabled();
            prefs.edit().putBoolean("voicePhoneCommands", next).apply();
            phoneToggle.checked = next;
            phoneToggle.invalidate();
            toast(next ? "call & text on" : "call & text off");
        } });
        phone.addView(phoneToggle, new LinearLayout.LayoutParams(dp(48), dp(28)));
        settings.addView(phone);

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
        if (voiceAnswerModel().length() > 0) {
            settings.addView(text(voicePipelineLabel(), 10, Color.rgb(120,120,120)), new LinearLayout.LayoutParams(-1, dp(28)));
        }

        settings.addView(collapsibleHeader("input", "voiceInputOpen"));
        if (isExpanded("voiceInputOpen")) {
            settings.addView(settingChoice("provider", voiceInputProvider(), new View.OnClickListener() { @Override public void onClick(View v) { chooseVoiceProvider("voiceInputProvider", new String[]{"auto", "system", "openrouter", "endpoint"}); } }));
            if ("openrouter".equals(voiceInputProvider())) settings.addView(settingChoice("model", prefs.getString("voiceTranscribeModel", "whisper-1"), new View.OnClickListener() { @Override public void onClick(View v) { chooseTranscriptionModel(); } }));
            else if ("endpoint".equals(voiceInputProvider())) settings.addView(settingChoice("model", "endpoint default", null));
            if ("endpoint".equals(voiceInputProvider()) || "auto".equals(voiceInputProvider())) settings.addView(settingChoice("endpoint", endpointLabel(prefs.getString("voiceTranscribeEndpoint", "")), new View.OnClickListener() { @Override public void onClick(View v) { chooseSavedEndpoint("voiceTranscribeEndpoint"); } }));
        } else {
            settings.addView(text(voiceInputSummary(), 11, Color.rgb(135, 135, 135)), new LinearLayout.LayoutParams(-1, dp(24)));
        }

        settings.addView(collapsibleHeader("output", "voiceOutputOpen"));
        if (isExpanded("voiceOutputOpen")) {
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
        } else {
            settings.addView(text(voiceOutputSummary(), 11, Color.rgb(135, 135, 135)), new LinearLayout.LayoutParams(-1, dp(24)));
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
    private String voiceInputSummary() {
        String p = voiceInputProvider();
        if ("openrouter".equals(p)) return p + " · " + shortModel(prefs.getString("voiceTranscribeModel", "whisper-1"));
        if ("endpoint".equals(p)) return "endpoint · " + endpointLabel(prefs.getString("voiceTranscribeEndpoint", ""));
        return p;
    }
    private String voiceOutputSummary() {
        String p = voiceOutputProvider();
        if ("openrouter".equals(p)) return p + " · " + shortModel(prefs.getString("voiceTtsModel", ""));
        if ("endpoint".equals(p)) return "endpoint · " + endpointLabel(prefs.getString("voiceTtsEndpoint", ""));
        return p;
    }
    private String voiceWebSearchMode() { return prefs.getString("voiceWebSearchMode", "auto"); }
    private String searchProvider() { return prefs.getString("searchProvider", "jina"); }
    private String voiceAnswerModel() { return prefs.getString("voiceAnswerModel", "").trim(); }
    private String voiceAnswerModelLabel() { String m = voiceAnswerModel(); return m.length() == 0 ? "same as chat" : shortModel(m); }
    private String voicePipelineLabel() { String m = configuredVoiceAnswerModel(); if (sameOpenRouterVoiceModelForAllThree() && isAllInOneVoiceModel(m)) return "single multimodal request when fullscreen voice records"; if (sameOpenRouterVoiceModelForAllThree()) return "same model set, but catalog does not show audio in + out"; return isAllInOneVoiceModel(m) ? "all-in-one capable model selected" : "two-way voice answers only; normal chats unchanged"; }
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
        String key = authKeyForUrl(url);
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
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
        if ("openrouter".equals(provider)) { for (String m : models) if (!"custom".equals(modelSource(m))) out.add(m); }
        else if ("endpoint".equals(provider)) { for (String model : models) if ("custom".equals(modelSource(model))) out.add(model); }
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
            for (String m : models) if (!"custom".equals(modelSource(m)) && isTtsModel(m) && (q.length() == 0 || modelMatches(m, q) || isAudioSearch(q))) addTtsChoice(list, d, shown, m);
            if (q.length() > 0) for (String m : models) if (!"custom".equals(modelSource(m)) && isTtsModel(m) && modelMatches(m, q)) addTtsChoice(list, d, shown, m);
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
            for (String m : models) if (!"custom".equals(modelSource(m)) && isTranscriptionModel(m) && (q.length() == 0 || modelMatches(m, q) || isTranscriptionSearch(q))) addTranscriptionChoice(list, d, shown, m);
            if (q.length() > 0) for (String m : models) if (!"custom".equals(modelSource(m)) && modelMatches(m, q) && (isTranscriptionModel(m) || audioInputModels.contains(m))) addTranscriptionChoice(list, d, shown, m);
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
            for (final String m : models) if ("custom".equals(modelSource(m)) && (q.length() == 0 || modelMatches(m, q))) { addAnswerChoice(list, d, shownModels, m); shown++; if (shown >= 50) break; }
            for (final String m : myModels) if (shown < 50 && (q.length() == 0 || modelMatches(m, q))) { addAnswerChoice(list, d, shownModels, m); shown = shownModels.size(); }
            for (final String m : models) if (shown < 50 && !"custom".equals(modelSource(m)) && (q.length() == 0 || modelMatches(m, q))) { addAnswerChoice(list, d, shownModels, m); shown = shownModels.size(); }
            final String typed = search.getText().toString().trim();
            if (typed.length() > 1 && !models.contains(typed) && !models.contains(typedModelKey(typed))) {
                TextView item = searchResultItem("use " + shortModel(typed), soleCustomEndpoint().length() > 0 ? "endpoint" : (customEndpoints.size() > 0 ? "pick from list" : "openrouter"));
                item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { String key = typedModelKey(typed); if (!models.contains(key)) { models.add(0, key); modelSources.put(key, ToolText.isCustomModelKey(key) || soleCustomEndpoint().length() > 0 ? "custom" : "openrouter"); String ep = ToolText.customModelEndpoint(key); if (ep.length() == 0) ep = soleCustomEndpoint(); if (ep.length() > 0) modelEndpoints.put(key, ep); saveModels(); saveModelSources(); saveModelEndpoints(); } prefs.edit().putString("voiceAnswerModel", key).apply(); d.dismiss(); showSettingsPane(); } });
                list.addView(item); shown++;
            }
            status.setText((shownModels.size() + (typed.length() > 1 && !models.contains(typed) && !models.contains(typedModelKey(typed)) ? 1 : 0)) + " shown. applies only to two-way voice");
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
        if ("custom".equals(modelSource(model))) {
            String host = ToolText.endpointCardHost(modelEndpoint(model));
            tags.add(host.length() > 0 ? "endpoint · " + host : "endpoint");
        }
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
        syncCurrentChatInMemory();
        persistChatStore(false);
        pane = 0;
        renderingMessages = false;
        forceAutoScrollBottom = false;
        restoreScrollOnce = false;
        captureChatScroll();
        boolean already = paneIsShowing(0);
        boolean reuse = chatsPaneReusable();
        switchToPane(0);
        if (chatList != null) chatList.setEnabled(true);
        if (reuse) {
            renderChatList();
            updateSearchBadge();
            if (!already) slidePaneIn();
            else {
                paneSlide = 0;
                if (root != null) { root.setAlpha(1f); root.setTranslationX(0); }
            }
            return;
        }
        clearPaneViews();
        root.setOnClickListener(null);
        root.setPadding(dp(16), dp(8), dp(16), dp(8));
        LinearLayout top = row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text("chats", 21, Color.WHITE);
        title.setGravity(Gravity.CENTER_VERTICAL);
        title.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { if (projectView.length() > 0) { paneSlide = 1; projectView = ""; showChatsPane(); } } });
        top.addView(title, new LinearLayout.LayoutParams(-2, dp(38)));
        View mag = chatSearchButton();
        if (projectView.length() > 0) {
            TextView sub = text("  " + projectView, 13, Color.rgb(150,150,150));
            sub.setGravity(Gravity.CENTER_VERTICAL);
            sub.setSingleLine(true);
            sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
            sub.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { paneSlide = 1; projectView = ""; showChatsPane(); } });
            top.addView(sub, new LinearLayout.LayoutParams(0, dp(38), 1));
            top.addView(mag, new LinearLayout.LayoutParams(dp(42), dp(34)));
        } else {
            LinearLayout mid = row();
            mid.setGravity(Gravity.CENTER);
            mid.addView(mag, new LinearLayout.LayoutParams(dp(42), dp(34)));
            top.addView(mid, new LinearLayout.LayoutParams(0, dp(38), 1));
        }
        ImageButton fresh = iconButton(R.drawable.ic_chat_plus, new View.OnClickListener() { @Override public void onClick(View v) { paneSlide = -1; newChat(); pane = 1; renderPane(); } }, 6);
        top.addView(fresh, new LinearLayout.LayoutParams(dp(42), dp(34)));
        if (projectView.length() > 0) top.addView(iconButton(R.drawable.ic_pencil, new View.OnClickListener() { @Override public void onClick(View v) { editFolder(projectView); } }, 8), new LinearLayout.LayoutParams(dp(42), dp(34)));
        else top.addView(iconButton(R.drawable.ic_folder_plus, new View.OnClickListener() { @Override public void onClick(View v) { addFolder(); } }, 6), new LinearLayout.LayoutParams(dp(42), dp(34)));
        root.addView(top, new LinearLayout.LayoutParams(-1, dp(40)));

        chatSearchRow = buildChatSearchRow();
        LinearLayout.LayoutParams searchLp = new LinearLayout.LayoutParams(-1, chatSearchOpen ? dp(36) : 0);
        searchLp.topMargin = chatSearchOpen ? dp(4) : 0;
        searchLp.bottomMargin = chatSearchOpen ? dp(6) : 0;
        root.addView(chatSearchRow, searchLp);
        chatSearchRow.setVisibility(chatSearchOpen ? View.VISIBLE : View.GONE);
        updateSearchBadge();
        if (chatSearchOpen && chatSearch != null) {
            chatSearch.post(new Runnable() { @Override public void run() { showKeyboardFrom(chatSearch); } });
        }

        ScrollView s = new ScrollView(this);
        s.setOverScrollMode(View.OVER_SCROLL_NEVER);
        chatList = new LinearLayout(this); chatList.setOrientation(LinearLayout.VERTICAL);
        s.addView(chatList);
        s.setVerticalScrollBarEnabled(false);
        root.addView(s, new LinearLayout.LayoutParams(-1, 0, 1));

        chatsSelectBar = row();
        chatsSelectBar.setPadding(dp(4), 0, dp(4), 0);
        chatsSelectCount = text("", 13, Color.WHITE);
        chatsSelectCount.setGravity(Gravity.CENTER_VERTICAL);
        TextView actions = text("actions", 13, Color.LTGRAY);
        actions.setGravity(Gravity.CENTER);
        TextView done = text("done", 13, Color.LTGRAY);
        done.setGravity(Gravity.CENTER);
        actions.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { showBulkActions(); } });
        done.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { selectedChats.clear(); renderChatList(); } });
        chatsSelectBar.addView(chatsSelectCount, new LinearLayout.LayoutParams(0, dp(36), 1));
        chatsSelectBar.addView(actions, new LinearLayout.LayoutParams(dp(72), dp(36)));
        chatsSelectBar.addView(done, new LinearLayout.LayoutParams(dp(52), dp(36)));
        GradientDrawable barBg = new GradientDrawable();
        barBg.setColor(Color.BLACK);
        barBg.setStroke(1, Color.rgb(52, 52, 52));
        barBg.setCornerRadius(dp(3));
        chatsSelectBar.setBackground(barBg);
        chatsSelectBar.setVisibility(View.GONE);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(-1, dp(40));
        barLp.topMargin = dp(6);
        root.addView(chatsSelectBar, barLp);

        paneReady[0] = true;
        chatsBoundProject = projectView;
        boolean animate = paneSlide != 0;
        if (animate) {
            slidePaneIn();
            ui.post(new Runnable() { @Override public void run() {
                if (pane == 0 && chatList != null) renderChatList();
            } });
        } else {
            renderChatList();
            slidePaneIn();
        }
    }

    private void renderChatList() {
        if (pendingChatFilter != null) {
            ui.removeCallbacks(pendingChatFilter);
            pendingChatFilter = null;
        }
        if (chatList == null) return;
        chatList.removeAllViews();
        chatList.setPadding(0, dp(3), 0, dp(14));
        String q = chatFilter;
        int shown = 0;
        if (projectView.length() > 0) {
            ArrayList<Chat> list = chatsInFolder(projectView);
            for (int i = 0; i < list.size(); i++) {
                Chat c = list.get(i);
                if (!chatMatchesFilter(c)) continue;
                chatList.addView(chatCard(c, false, shown));
                shown++;
            }
            if (shown == 0) chatList.addView(emptyLine(q.trim().length() > 0 ? "no matches" : "no chats yet"));
            updateBulkButton();
            return;
        }
        ArrayList<String> projectFolders = sortedProjectFolders();
        ArrayList<String> visibleFolders = new ArrayList<String>();
        for (int i = 0; i < projectFolders.size(); i++) {
            String f = projectFolders.get(i);
            if (q.trim().length() == 0 || ToolText.textMatchesQuery(q, f, "") || folderHasMatchingChat(f)) visibleFolders.add(f);
        }
        boolean searching = q.trim().length() > 0;
        boolean showProjects = projectFolders.size() > 0 && (!searching || visibleFolders.size() > 0);
        if (showProjects) {
            boolean collapsed = projectsCollapsed();
            chatList.addView(projectsSectionHeader(searching ? visibleFolders.size() : projectFolders.size(), collapsed));
            if (!collapsed) {
                for (int i = 0; i < visibleFolders.size(); i++) chatList.addView(folderCard(visibleFolders.get(i), i));
            }
            chatList.addView(space(collapsed ? 6 : 10));
        }
        ArrayList<Chat> inbox = chatsInFolder("Inbox");
        ArrayList<Chat> visibleInbox = new ArrayList<Chat>();
        for (int i = 0; i < inbox.size(); i++) if (chatMatchesFilter(inbox.get(i))) visibleInbox.add(inbox.get(i));
        chatList.addView(sectionHeader("general  " + visibleInbox.size()));
        if (visibleInbox.size() == 0) chatList.addView(emptyLine(q.trim().length() > 0 ? "no matches" : "no chats yet"));
        for (int i = 0; i < visibleInbox.size(); i++) chatList.addView(chatCard(visibleInbox.get(i), false, i));
        updateBulkButton();
    }

    private void scheduleChatListRender(int delayMs) {
        if (pendingChatFilter != null) ui.removeCallbacks(pendingChatFilter);
        if (delayMs <= 0) {
            pendingChatFilter = null;
            renderChatList();
            return;
        }
        pendingChatFilter = new Runnable() { @Override public void run() {
            pendingChatFilter = null;
            renderChatList();
        } };
        ui.postDelayed(pendingChatFilter, delayMs);
    }

    private View chatSearchButton() {
        FrameLayout wrap = new FrameLayout(this);
        wrap.setBackgroundColor(Color.BLACK);
        chatSearchBtn = iconButton(R.drawable.ic_search, new View.OnClickListener() {
            @Override public void onClick(View v) {
                if (chatSearchOpen) collapseChatSearch(true);
                else expandChatSearch(true);
            }
        }, 6);
        wrap.addView(chatSearchBtn, new FrameLayout.LayoutParams(dp(42), dp(34)));
        chatSearchClear = text("×", 10, Color.rgb(170, 170, 170));
        chatSearchClear.setGravity(Gravity.CENTER);
        chatSearchClear.setIncludeFontPadding(false);
        chatSearchClear.setPadding(0, 0, dp(1), dp(1));
        chatSearchClear.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { clearChatSearch(); }
        });
        FrameLayout.LayoutParams xLp = new FrameLayout.LayoutParams(dp(18), dp(16), Gravity.RIGHT | Gravity.BOTTOM);
        wrap.addView(chatSearchClear, xLp);
        return wrap;
    }

    private View buildChatSearchRow() {
        FrameLayout row = new FrameLayout(this);
        row.setBackgroundColor(Color.BLACK);
        row.setClipChildren(true);
        chatSearch = plainEdit("search");
        chatSearch.setSingleLine(true);
        chatSearch.setGravity(Gravity.CENTER_VERTICAL);
        chatSearch.setHintTextColor(Color.rgb(90, 90, 90));
        setTextPx(chatSearch, 14);
        chatSearch.setPadding(dp(10), 0, dp(10), 0);
        GradientDrawable searchBg = new GradientDrawable();
        searchBg.setColor(Color.BLACK);
        searchBg.setStroke(1, Color.rgb(34, 34, 34));
        searchBg.setCornerRadius(dp(3));
        chatSearch.setBackground(searchBg);
        chatSearch.setImeOptions(EditorInfo.IME_ACTION_DONE);
        chatSearch.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_SEARCH) {
                    collapseChatSearch(true);
                    return true;
                }
                return false;
            }
        });
        if (chatFilter.length() > 0) chatSearch.setText(chatFilter);
        if (chatSearch.getText() != null) chatSearch.setSelection(chatSearch.getText().length());
        chatSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) { }
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                chatFilter = s == null ? "" : s.toString();
                updateSearchBadge();
                scheduleChatListRender(chatFilter.trim().length() == 0 ? 0 : 50);
            }
            @Override public void afterTextChanged(Editable e) { }
        });
        row.addView(chatSearch, new FrameLayout.LayoutParams(-1, -1));
        return row;
    }

    private void expandChatSearch(boolean animate) {
        if (chatSearchRow == null || chatSearch == null) return;
        chatSearchOpen = true;
        updateSearchBadge();
        if (chatSearchAnim != null) { chatSearchAnim.cancel(); chatSearchAnim = null; }
        chatSearchRow.animate().cancel();
        chatSearchRow.setVisibility(View.VISIBLE);
        final int target = dp(36);
        if (!animate) {
            applySearchRowHeight(target, dp(4), dp(6), 1f);
            chatSearchRow.setScaleX(1f);
            chatSearchRow.setScaleY(1f);
            chatSearchRow.setTranslationY(0);
            showKeyboardFrom(chatSearch);
            return;
        }
        applySearchRowHeight(0, 0, 0, 0f);
        chatSearchRow.setScaleX(0.16f);
        chatSearchRow.setScaleY(0.45f);
        chatSearchRow.setTranslationY(-dp(8));
        chatSearchRow.post(new Runnable() { @Override public void run() {
            if (chatSearchRow == null || !chatSearchOpen) return;
            setSearchRowPivotFromIcon();
            animateSearchRow(0, target, 0f, 1f, 0, dp(4), 0, dp(6), 160, new DecelerateInterpolator(), new Runnable() {
                @Override public void run() {
                    if (chatSearchOpen && chatSearch != null) showKeyboardFrom(chatSearch);
                }
            });
            chatSearchRow.animate().scaleX(1f).scaleY(1f).translationY(0).setDuration(160).setInterpolator(new DecelerateInterpolator()).start();
        } });
    }

    private void collapseChatSearch(boolean animate) {
        if (!chatSearchOpen && (chatSearchRow == null || chatSearchRow.getVisibility() != View.VISIBLE)) {
            hideKeyboard();
            return;
        }
        chatSearchOpen = false;
        updateSearchBadge();
        if (chatSearch != null) {
            hideKeyboardFrom(chatSearch);
            chatSearch.clearFocus();
        } else hideKeyboard();
        if (chatSearchRow == null) return;
        if (chatSearchAnim != null) { chatSearchAnim.cancel(); chatSearchAnim = null; }
        chatSearchRow.animate().cancel();
        if (!animate) {
            applySearchRowHeight(0, 0, 0, 1f);
            chatSearchRow.setVisibility(View.GONE);
            chatSearchRow.setScaleX(1f);
            chatSearchRow.setScaleY(1f);
            chatSearchRow.setTranslationY(0);
            return;
        }
        setSearchRowPivotFromIcon();
        final int from = chatSearchRow.getHeight() > 0 ? chatSearchRow.getHeight() : dp(36);
        animateSearchRow(from, 0, 1f, 0f, dp(4), 0, dp(6), 0, 130, new AccelerateInterpolator(), new Runnable() {
            @Override public void run() {
                if (chatSearchRow == null) return;
                chatSearchRow.setVisibility(View.GONE);
                chatSearchRow.setScaleX(1f);
                chatSearchRow.setScaleY(1f);
                chatSearchRow.setTranslationY(0);
                applySearchRowHeight(0, 0, 0, 1f);
            }
        });
        chatSearchRow.animate().scaleX(0.16f).scaleY(0.45f).translationY(-dp(8)).setDuration(130).setInterpolator(new AccelerateInterpolator()).start();
    }

    private void clearChatSearch() {
        chatFilter = "";
        if (chatSearch != null) {
            chatSearch.setText("");
            if (chatSearchOpen) chatSearch.requestFocus();
        }
        updateSearchBadge();
        renderChatList();
    }

    private void updateSearchBadge() {
        if (chatSearchClear == null) return;
        boolean show = chatFilter.trim().length() > 0;
        chatSearchClear.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void setSearchRowPivotFromIcon() {
        if (chatSearchRow == null) return;
        float pivot = chatSearchRow.getWidth() / 2f;
        if (chatSearchBtn != null && chatSearchBtn.getWidth() > 0) {
            int[] icon = new int[2], row = new int[2];
            chatSearchBtn.getLocationOnScreen(icon);
            chatSearchRow.getLocationOnScreen(row);
            pivot = icon[0] + chatSearchBtn.getWidth() / 2f - row[0];
        }
        chatSearchRow.setPivotX(Math.max(0f, pivot));
        chatSearchRow.setPivotY(0f);
    }

    private void applySearchRowHeight(int height, int top, int bottom, float alpha) {
        if (chatSearchRow == null) return;
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) chatSearchRow.getLayoutParams();
        if (lp == null) lp = new LinearLayout.LayoutParams(-1, height);
        lp.height = height;
        lp.topMargin = top;
        lp.bottomMargin = bottom;
        chatSearchRow.setLayoutParams(lp);
        chatSearchRow.setAlpha(alpha);
    }

    private void animateSearchRow(int fromH, final int toH, final float fromA, final float toA, int fromTop, final int toTop, int fromBot, final int toBot, int ms, android.view.animation.Interpolator interp, final Runnable end) {
        if (chatSearchAnim != null) chatSearchAnim.cancel();
        final int startH = fromH, startTop = fromTop, startBot = fromBot;
        chatSearchAnim = ValueAnimator.ofFloat(0f, 1f);
        chatSearchAnim.setDuration(ms);
        chatSearchAnim.setInterpolator(interp);
        chatSearchAnim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override public void onAnimationUpdate(ValueAnimator a) {
                float t = (Float) a.getAnimatedValue();
                applySearchRowHeight(
                        startH + Math.round((toH - startH) * t),
                        startTop + Math.round((toTop - startTop) * t),
                        startBot + Math.round((toBot - startBot) * t),
                        fromA + (toA - fromA) * t);
            }
        });
        chatSearchAnim.addListener(new AnimatorListenerAdapter() {
            boolean cancelled = false;
            @Override public void onAnimationCancel(Animator a) { cancelled = true; }
            @Override public void onAnimationEnd(Animator a) {
                if (chatSearchAnim == a) chatSearchAnim = null;
                if (cancelled) return;
                applySearchRowHeight(toH, toTop, toBot, toA);
                if (end != null) end.run();
            }
        });
        chatSearchAnim.start();
    }

    private void showKeyboardFrom(final View v) {
        if (v == null || !chatSearchOpen) return;
        v.requestFocus();
        v.post(new Runnable() { @Override public void run() {
            try {
                InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                if (imm != null) imm.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT);
            } catch (Exception ignored) { }
        } });
    }

    private boolean projectsCollapsed() {
        return prefs != null && prefs.getBoolean("projectsCollapsed", false);
    }

    private void setProjectsCollapsed(boolean collapsed) {
        if (prefs != null) prefs.edit().putBoolean("projectsCollapsed", collapsed).apply();
        renderChatList();
    }

    private View projectsSectionHeader(int count, final boolean collapsed) {
        LinearLayout h = row();
        h.setPadding(0, dp(4), dp(2), dp(5));
        TextView name = text("projects  " + count, 11, Color.rgb(140,140,140));
        name.setGravity(Gravity.CENTER_VERTICAL);
        TextView carat = text(collapsed ? ">" : "˅", 11, Color.rgb(140,140,140));
        carat.setGravity(Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        h.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        h.addView(carat, new LinearLayout.LayoutParams(dp(18), -2));
        h.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { setProjectsCollapsed(!collapsed); }
        });
        return h;
    }

    private View folderCard(final String folder, int index) {
        LinearLayout card = row();
        card.setPadding(dp(10), 0, dp(6), 0);
        card.setBackground(listCardBg(false, false, index));
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { paneSlide = -1; projectView = folder; showChatsPane(); } });
        bindPress(card);
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
        lp.setMargins(0, 0, 0, dp(6));
        card.setLayoutParams(lp);
        return card;
    }

    private View chatCard(final Chat c, boolean nested, int index) {
        boolean selected = selectedChats.contains(c.id);
        boolean current = c.id.equals(currentChatId);
        LinearLayout wrap = new LinearLayout(this);
        wrap.setOrientation(LinearLayout.HORIZONTAL);
        wrap.setBackgroundColor(Color.BLACK);
        if (nested) wrap.setPadding(dp(10), 0, 0, 0);
        LinearLayout card = row();
        card.setPadding(dp(10), dp(4), dp(2), dp(4));
        card.setBackground(listCardBg(selected, current, index));
        card.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { toggleChatSelection(c); return true; } });
        card.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) {
            if (selectedChats.size() > 0) toggleChatSelection(c);
            else { paneSlide = -1; openChatFromList(c); }
        } });
        bindPress(card);
        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setGravity(Gravity.CENTER_VERTICAL);
        textCol.setBackgroundColor(Color.TRANSPARENT);
        TextView name = cardText((selected ? "✓ " : "") + chatListTitle(c), 13, Color.WHITE);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        String when = ToolText.relativeTime(System.currentTimeMillis(), chatRecency(c));
        String previewLine = chatPreview(c);
        if (when.length() > 0) previewLine = when + "  ·  " + previewLine;
        TextView preview = cardText(previewLine, 10, Color.rgb(145,145,145));
        preview.setSingleLine(true);
        preview.setEllipsize(android.text.TextUtils.TruncateAt.END);
        textCol.addView(name, new LinearLayout.LayoutParams(-1, dp(22)));
        textCol.addView(preview, new LinearLayout.LayoutParams(-1, dp(17)));
        LinearLayout end = new LinearLayout(this);
        end.setOrientation(LinearLayout.HORIZONTAL);
        end.setGravity(Gravity.CENTER_VERTICAL);
        end.setBackgroundColor(Color.TRANSPARENT);
        if (current && !selected) {
            TextView on = cardText("on", 11, Color.rgb(180, 180, 180));
            on.setGravity(Gravity.CENTER);
            end.addView(on, new LinearLayout.LayoutParams(-2, dp(47)));
        }
        TextView dots = cardText("...", 13, Color.LTGRAY);
        dots.setGravity(Gravity.CENTER);
        dots.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { chatActions(c); } });
        end.addView(dots, new LinearLayout.LayoutParams(dp(30), dp(47)));
        card.addView(textCol, new LinearLayout.LayoutParams(0, dp(47), 1));
        card.addView(end, new LinearLayout.LayoutParams(-2, dp(47)));
        wrap.addView(card, new LinearLayout.LayoutParams(-1, dp(53)));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, dp(57));
        lp.setMargins(0, 0, 0, dp(6));
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
        forceAutoScrollBottom = true;
        userAtChatBottom = true;
        savedChatScrollKnown = false;
        if (c != null && c.id.equals(currentChatId) && chatPaneReusable()) {
            pane = 1;
            showChatPane();
            return;
        }
        if (chatList != null) chatList.setEnabled(false);
        ui.post(new Runnable() { @Override public void run() {
            loadChat(c);
            pane = 1;
            renderPane();
        } });
    }

    private String chatPreview(Chat c) {
        if (c == null) return "empty chat";
        if (c.id.equals(currentChatId) && messages.size() > 0) return computeChatPreview(messages);
        if (c.listPreview != null) return c.listPreview;
        c.listPreview = computeChatPreview(c.messages);
        return c.listPreview;
    }

    private String computeChatPreview(ArrayList<Msg> msgs) {
        if (msgs == null) return "empty chat";
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Msg m = msgs.get(i);
            if (m.text == null || m.text.trim().length() == 0 || isBusyStats(m.stats)) continue;
            String text = chatPreviewText(m);
            if (text.length() > 72) text = text.substring(0, 72) + "...";
            return (m.role.equals("user") ? "you: " : "ai: ") + text;
        }
        return "empty chat";
    }

    private String chatPreviewText(Msg m) {
        String s = cleanSearchArtifacts(m.text == null ? "" : m.text).trim();
        int artifacts = 0;
        if (messageImageCount(m) > 0) artifacts++;
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

    private static boolean isBusyStats(String stats) { return LOADING.equals(stats) || SEARCHING.equals(stats); }

    private boolean chatIsLoading(Chat c) {
        if (c != null && messagesBusyTail(c.messages)) return true;
        if (c != null && c.id.equals(currentChatId) && messagesBusyTail(messages)) return true;
        return false;
    }

    private boolean messagesBusyTail(ArrayList<Msg> msgs) {
        if (msgs == null) return false;
        int n = msgs.size();
        for (int i = n - 1; i >= 0 && i >= n - 6; i--) {
            Msg m = msgs.get(i);
            if (m != null && isBusyStats(m.stats)) return true;
        }
        return false;
    }

    private boolean chatMatchesFilter(Chat c) {
        return ToolText.textMatchesQuery(chatFilter, chatListTitle(c), chatPreview(c));
    }

    private boolean folderHasMatchingChat(String folder) {
        for (Chat c : chats) if (folder.equals(c.folder) && chatMatchesFilter(c)) return true;
        return false;
    }

    private long chatRecency(Chat c) {
        if (c == null) return 0;
        if (c.id.equals(currentChatId) && messages.size() > 0) {
            return ToolText.recencyMillis(c.updatedAt, c.id, lastMessageAt(messages));
        }
        if (c.listRecency > 0) return c.listRecency;
        c.listRecency = ToolText.recencyMillis(c.updatedAt, c.id, lastMessageAt(c.messages));
        return c.listRecency;
    }

    private long lastMessageAt(ArrayList<Msg> msgs) {
        if (msgs == null) return 0;
        for (int i = msgs.size() - 1; i >= 0; i--) {
            Msg m = msgs.get(i);
            if (m != null && m.startedAt > 0) return m.startedAt;
        }
        return 0;
    }

    private void touchChat(Chat c) {
        if (c == null) return;
        c.listPreview = null;
        c.listRecency = 0;
        c.updatedAt = System.currentTimeMillis();
        chats.remove(c);
        chats.add(0, c);
    }

    private ArrayList<Chat> chatsInFolder(String folder) {
        ArrayList<Chat> out = new ArrayList<Chat>();
        for (int i = 0; i < chats.size(); i++) {
            Chat c = chats.get(i);
            if (c != null && folder.equals(c.folder)) out.add(c);
        }
        Collections.sort(out, new Comparator<Chat>() {
            @Override public int compare(Chat a, Chat b) { return Long.compare(chatRecency(b), chatRecency(a)); }
        });
        return out;
    }

    private ArrayList<String> sortedProjectFolders() {
        ArrayList<String> out = new ArrayList<String>();
        for (int i = 0; i < folders.size(); i++) {
            String f = folders.get(i);
            if (f != null && !"Inbox".equals(f)) out.add(f);
        }
        Collections.sort(out, new Comparator<String>() {
            @Override public int compare(String a, String b) {
                long da = folderRecency(a), db = folderRecency(b);
                if (db != da) return Long.compare(db, da);
                return a.compareToIgnoreCase(b);
            }
        });
        return out;
    }

    private long folderRecency(String folder) {
        long best = 0;
        for (int i = 0; i < chats.size(); i++) {
            Chat c = chats.get(i);
            if (c != null && folder.equals(c.folder)) {
                long t = chatRecency(c);
                if (t > best) best = t;
            }
        }
        return best;
    }

    private void toggleChatSelection(Chat c) {
        if (selectedChats.contains(c.id)) selectedChats.remove(c.id); else selectedChats.add(c.id);
        renderChatList();
        updateBulkButton();
    }

    private void updateBulkButton() {
        removeScreenChild(bulkButton); bulkButton = null;
        if (chatsSelectBar == null || chatsSelectCount == null) return;
        int n = selectedChats.size();
        boolean show = n > 0 && pane == 0;
        chatsSelectCount.setText(n == 0 ? "" : n + " selected");
        if (show && chatsSelectBar.getVisibility() != View.VISIBLE) {
            chatsSelectBar.setVisibility(View.VISIBLE);
            chatsSelectBar.setAlpha(0f);
            chatsSelectBar.setTranslationY(dp(10));
            chatsSelectBar.animate().alpha(1f).translationY(0).setDuration(90).setInterpolator(new DecelerateInterpolator()).start();
        } else if (!show) {
            chatsSelectBar.animate().cancel();
            chatsSelectBar.setVisibility(View.GONE);
            chatsSelectBar.setAlpha(1f);
            chatsSelectBar.setTranslationY(0);
        }
    }

    private void captureChatScroll() {
        if (scroll != null) {
            savedChatScrollY = scroll.getScrollY();
            savedChatScrollKnown = true;
        }
    }

    private void toggleThinking(Msg m) {
        if (m == null || m.reasoning.length() == 0) return;
        m.thinkingExpanded = !m.thinkingExpanded;
        renderMessages();
    }

    private void expandTouchArea(final View v, final int extra) {
        final View parent = (View) v.getParent();
        if (parent == null || extra <= 0) return;
        parent.post(new Runnable() { @Override public void run() {
            Rect r = new Rect();
            v.getHitRect(r);
            r.top -= extra;
            r.bottom += extra;
            parent.setTouchDelegate(new TouchDelegate(r, v));
        } });
    }

    private boolean chatPaneVisible() {
        return pane == 1 && paneRoots[1] != null && paneRoots[1].getVisibility() == View.VISIBLE && messageList != null;
    }

    private void renderMessages() {
        if (messageList == null) return;
        if (!chatPaneVisible()) {
            chatBoundId = "\u0001";
            return;
        }
        if (!messageWindowReady) resetMessageWindowToLatest();
        clampMessageWindow();
        final boolean showBottom = messageEnd >= messages.size();
        renderingMessages = true;
        liveStreamBody = null;
        liveStreamMsg = null;
        liveStreamKey = "";
        messageList.removeAllViews();
        if (messages.isEmpty()) {
            showEmptyPrompt();
        }
        if (messageStart > 0) messageList.addView(windowMarker("older messages above"));
        for (int i = messageStart; i < messageEnd; i++) {
            Msg m = messages.get(i);
            ArrayList<ToolStep> toolSteps = copyToolSteps(m);
            boolean hasRunningTool = false;
            for (int ti = 0; ti < toolSteps.size(); ti++) {
                ToolStep ts = toolSteps.get(ti);
                if (ts != null && "running".equals(ts.status)) hasRunningTool = true;
            }
            final boolean isSearching = SEARCHING.equals(m.stats);
            boolean thinkingLive = m.role.equals("assistant") && !m.streamDone && LOADING.equals(m.stats)
                    && (m.text == null || m.text.length() == 0) && !hasRunningTool && !isSearching;
            final boolean hasThinkingRow = m.role.equals("assistant") && (
                    thinkingLive
                    || (m.reasoning.length() > 0 && !m.reasoning.trim().equals("null"))
                    || m.thoughtMs > 0);
            final boolean hasSearchRow = m.role.equals("assistant") && m.searchSources.size() > 0;
            final boolean hasMemoryRow = m.role.equals("assistant") && m.streamDone && m.memorySaved;
            TextView body = text("", 16, Color.WHITE);
            String bodyText = "assistant".equals(m.role) ? sanitizeAssistantText(m.text) : (m.text == null ? "" : m.text);
            boolean hasImage = messageImageCount(m) > 0;
            boolean hasText = bodyText.trim().length() > 0;
            boolean streamPatch = "assistant".equals(m.role) && !m.streamDone;
            if (hasText) {
                if (streamPatch) body.setText(bodyText);
                else body.setText(cachedMarkdown(m, bodyText));
            }
            body.setLineSpacing(dp(2), 1.0f);
            final Msg selectedMessage = m;
            body.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) { showMessageActions(selectedMessage); return true; } });
            if (m.role.equals("user")) {
                messageList.addView(userMessageBlock(hasText ? body : null, m), userMessageBlockParams());
            } else {
                TextView role = text(messageModelLabel(m), 11, Color.LTGRAY);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.setMargins(0, dp(8), 0, 0);
                messageList.addView(role, lp);
                if (hasImage) {
                    LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(-1, -2);
                    imageLp.topMargin = dp(4);
                    imageLp.bottomMargin = hasText ? dp(8) : dp(2);
                    messageList.addView(messageImageRow(m), imageLp);
                }
            }
            if (hasThinkingRow) {
                final Msg thinkingMessage = m;
                LinearLayout thinkRow = compactStatusRow();
                TextView thinking;
                if (thinkingLive) {
                    thinking = liveStatus(ToolText.ensureEllipsis("thinking"), m);
                } else {
                    long doneMs = m.thoughtMs > 0 ? m.thoughtMs : Math.max(1, System.currentTimeMillis() - m.startedAt);
                    thinking = statusText("thought for " + thoughtDuration(doneMs));
                }
                thinking.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleThinking(thinkingMessage); } });
                thinkRow.addView(thinking, new LinearLayout.LayoutParams(-1, -2));
                thinkRow.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleThinking(thinkingMessage); } });
                messageList.addView(thinkRow, new LinearLayout.LayoutParams(-1, -2));
                if (m.thinkingExpanded && m.reasoning.length() > 0) {
                    TextView reason = text(privateReasoning(m) ? "private reasoning hidden" : m.reasoning, 13, Color.rgb(160,160,160));
                    reason.setLineSpacing(dp(2), 1.0f);
                    reason.setPadding(0, 0, 0, dp(4));
                    reason.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { toggleThinking(thinkingMessage); } });
                    messageList.addView(reason, new LinearLayout.LayoutParams(-1, -2));
                }
            }
            for (int ti = 0; ti < toolSteps.size(); ti++) {
                ToolStep ts = toolSteps.get(ti);
                if (ts == null) continue;
                LinearLayout stepRow = compactStatusRow();
                boolean running = "running".equals(ts.status) && !m.streamDone;
                if (running) {
                    stepRow.addView(liveStatus(ToolText.toolLiveLabel(ts.name), m), new LinearLayout.LayoutParams(-1, -2));
                } else {
                    stepRow.addView(toolDoneView(ts), new LinearLayout.LayoutParams(-1, -2));
                }
                messageList.addView(stepRow, new LinearLayout.LayoutParams(-1, -2));
            }
            if (isSearching && !hasRunningTool && !m.streamDone && m.searchSources.size() == 0) {
                LinearLayout searchRow = compactStatusRow();
                searchRow.addView(liveStatus(ToolText.ensureEllipsis("searching the web"), m), new LinearLayout.LayoutParams(-1, -2));
                messageList.addView(searchRow, new LinearLayout.LayoutParams(-1, -2));
            }
            if (hasSearchRow) {
                prefetchFavicons(m.searchSources);
                final Msg searchMessage = m;
                LinearLayout srcRow = compactStatusRow();
                TextView gathered = statusText("searched " + m.searchSources.size() + " sources" + (m.searchExpanded ? " ˅" : " ›"));
                gathered.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { searchMessage.searchExpanded = !searchMessage.searchExpanded; renderMessages(); } });
                srcRow.addView(gathered, new LinearLayout.LayoutParams(-2, -2));
                View thumbs = sourceThumbStrip(m.searchSources);
                LinearLayout.LayoutParams thumbLp = new LinearLayout.LayoutParams(0, -2, 1f);
                thumbLp.leftMargin = dp(8);
                srcRow.addView(thumbs, thumbLp);
                srcRow.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { searchMessage.searchExpanded = !searchMessage.searchExpanded; renderMessages(); } });
                messageList.addView(srcRow, new LinearLayout.LayoutParams(-1, -2));
                if (m.searchExpanded) {
                    TextView src = text(join(m.searchSources), 12, Color.rgb(160,160,160));
                    src.setLineSpacing(dp(1), 1.0f);
                    src.setPadding(0, 0, 0, dp(8));
                    messageList.addView(src, new LinearLayout.LayoutParams(-1, -2));
                }
            }
            if (!m.role.equals("user") && !isSearching && hasText && !LOADING.equals(m.stats)) {
                messageList.addView(body, new LinearLayout.LayoutParams(-1, -2));
                if (!m.streamDone) {
                    liveStreamBody = body;
                    liveStreamMsg = m;
                    liveStreamKey = streamUiKey(m);
                }
            }
            if (hasMemoryRow) {
                final Msg memoryMessage = m;
                boolean removedMemory = m.memorySavedText.startsWith("__REMOVED__\n");
                String label = removedMemory ? "memory removed" : "memory saved";
                TextView saved = text(label + (m.memoryExpanded ? " ˅" : " ›"), 11, Color.rgb(135,135,135));
                saved.setGravity(Gravity.CENTER_VERTICAL);
                saved.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { memoryMessage.memoryExpanded = !memoryMessage.memoryExpanded; renderMessages(); } });
                messageList.addView(saved, new LinearLayout.LayoutParams(-1, dp(24)));
                String memoryDetails = removedMemory ? m.memorySavedText.substring("__REMOVED__\n".length()) : m.memorySavedText;
                if (m.memoryExpanded && memoryDetails.length() > 0) {
                    TextView memoryText = text(memoryDetails, 12, Color.rgb(160,160,160));
                    memoryText.setLineSpacing(dp(1), 1.0f);
                    memoryText.setPadding(0, 0, 0, dp(8));
                    messageList.addView(memoryText, new LinearLayout.LayoutParams(-1, -2));
                }
            }
            if (m.role.equals("assistant") && !m.streamDone) {
                liveStreamMsg = m;
                liveStreamKey = streamUiKey(m);
            }
            if (m.role.equals("assistant") && m.stats.length() > 0 && !isBusyStats(m.stats)) messageList.addView(text(m.stats, 10, Color.rgb(130,130,130)));
        }
        if (messageEnd < messages.size()) messageList.addView(windowMarker("newer messages below"));
        if (meter != null) { meter.percent = contextPercent(); meter.invalidate(); }
        if (contextText != null) contextText.setText(contextPercentText());
        updateAttachChip();
        updateComposerAction();
        final ScrollView renderScroll = scroll;
        if (renderScroll != null) renderScroll.post(new Runnable() { @Override public void run() {
            if (renderScroll != scroll || pane != 1) return;
            boolean restoreAwayFromBottom = restoreScrollOnce && !scrollIsNearBottom(savedChatScrollY);
            if (restoreAwayFromBottom) renderScroll.scrollTo(0, savedChatScrollY);
            else if (showBottom && (forceAutoScrollBottom || userAtChatBottom || !savedChatScrollKnown || restoreScrollOnce)) pinChatToLatest(renderScroll);
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
        if (Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) {
            CharSequence text = intent.getCharSequenceExtra(Intent.EXTRA_TEXT);
            Uri u = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (u != null) attachUri(u, true);
            ArrayList<Uri> many = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (many != null) for (Uri cu : many) if (cu != null) attachUri(cu, false);
            ClipData clip = intent.getClipData();
            if (clip != null) for (int i = 0; i < clip.getItemCount(); i++) {
                Uri cu = clip.getItemAt(i).getUri();
                if (cu != null) attachUri(cu, false);
            }
            updateAttachChip();
            updateComposerAction();
            if (text != null && input != null) input.setText(limitIncomingText(text.toString()));
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
        String endpoint = source.equals("custom") ? modelEndpoint(model) : "";
        String key = source.equals("openrouter") ? savedApiKey() : endpointKey(endpoint);
        String text = input == null ? pendingVoiceText.trim() : input.getText().toString().trim();
        forceSearchThisTurn = false;
        researchThisTurn = false;
        if (handleSlashCommandLocal(text)) return true;
        text = applySlashCommandToOutgoing(text);
        if ((forceSearchThisTurn || researchThisTurn) && text.length() == 0 && pendingImages.size() == 0) {
            toast(researchThisTurn ? "add a research query" : "add a search query");
            forceSearchThisTurn = false;
            researchThisTurn = false;
            return false;
        }
        if (handleMemoryRecall(text)) return true;
        String pendingUserMemoryNote = pendingUserMemoryNote(text);
        String pendingUserMemoryRemoval = pendingUserMemoryRemoval(text);
        if (model.length() == 0) { toast("select a model first"); return false; }
        if (source.equals("openrouter") && key.length() == 0) { toast("add openrouter key"); return false; }
        if (source.equals("custom") && endpoint.length() == 0) { toast("add endpoint"); return false; }
        if (text.length() == 0 && pendingImages.size() == 0) return false;
        if (input != null) hideKeyboardFrom(input);
        if (input != null) input.clearFocus();
        hideSlashSuggestions();
        userAtChatBottom = true;
        Msg userMsg = new Msg("user", text, "", "", "", "", replyQuote);
        for (AttachedImage img : pendingImages) userMsg.images.add(new AttachedImage(img.base64, img.mime));
        userMsg.syncLegacyImageFields();
        messages.add(userMsg);
        final Msg assistant = new Msg("assistant", ".", "", "", LOADING, shortModel(model));
        assistant.slowVoice = voiceMode && voiceFullMode;
        assistant.voiceSessionId = assistant.slowVoice ? voiceSession : 0;
        assistant.reasoningCapable = "custom".equals(source) || isReasoningModel(model);
        if (assistant.slowVoice || assistant.reasoningCapable) assistant.text = "";
        messages.add(assistant);
        saveCurrentChat();
        resetMessageWindowToLatest();
        forceAutoScrollBottom = userAtChatBottom;
        if (input != null) input.setText(""); pendingVoiceText = ""; replyQuote = ""; updateReplyChip(); clearPendingAttachment(); renderMessages();
        animateLoading(assistant, 0);
        final String userText = text;
        final String sendKey = key;
        final String sendSource = source;
        final String sendModel = model;
        final String sendPendingUserMemoryNote = pendingUserMemoryNote;
        final String sendPendingUserMemoryRemoval = pendingUserMemoryRemoval;
        // Capture turn flags before the worker starts — avoid cross-thread races on mutable fields.
        turnForceSearch = forceSearchThisTurn;
        turnResearch = researchThisTurn;
        forceSearchThisTurn = false;
        researchThisTurn = false;
        new Thread(new Runnable() { @Override public void run() { callOpenRouter(sendKey, sendSource, sendModel, assistant, userText, sendPendingUserMemoryNote, sendPendingUserMemoryRemoval); } }).start();
        return true;
    }

    private void callOpenRouter(String key, String source, String model, final Msg assistant, String userText, String pendingUserMemoryNote, String pendingUserMemoryRemoval) {
        long start = System.nanoTime();
        try {
            boolean research = turnResearch;
            boolean forceSearch = turnForceSearch || research;
            turnResearch = false;
            JSONArray arr = new JSONArray();
            String searchContext = buildSearchContext(userText);
            if (searchContext.length() > 0) {
                assistant.searchSources.clear();
                assistant.searchSources.addAll(lastSearchSources);
                arr.put(new JSONObject().put("role", "system").put("content", searchContext));
            }
            boolean searchAlready = searchContext.length() > 0;
            addBackgroundSystemContext(arr, true, searchAlready && !research, true, false);
            if (assistant.slowVoice) arr.put(new JSONObject().put("role", "system").put("content", "This is a spoken two-way voice conversation. Reply in plain text only. Do not use markdown, headings, bullets, tables, code blocks, or formatting symbols. Keep the response natural for text-to-speech."));
            for (Msg m : messages) {
                if (isBusyStats(m.stats)) continue;
                JSONObject one = new JSONObject(); one.put("role", m.role);
                ArrayList<AttachedImage> imgs = requestImages(m);
                if (imgs.size() > 0) {
                    JSONArray content = new JSONArray();
                    content.put(new JSONObject().put("type", "text").put("text", requestText(m)));
                    for (AttachedImage img : imgs) {
                        String mime = img.mime == null || img.mime.length() == 0 ? "image/jpeg" : img.mime;
                        content.put(new JSONObject().put("type", "image_url").put("image_url", new JSONObject().put("url", "data:" + mime + ";base64," + img.base64)));
                    }
                    one.put("content", content);
                } else one.put("content", requestText(m));
                arr.put(one);
            }
            String endpointUrl = chatCompletionsUrl(source, model);
            boolean nativeTools = !endpointsWithoutNativeTools.contains(endpointUrl);
            boolean includeSearchTool = webSearchAvailable() && (!searchAlready || research);
            JSONArray tools = AgentTools.openaiTools(includeSearchTool, memoryEnabled());
            if (tools.length() == 0) nativeTools = false;
            int maxRounds = AgentTools.maxRounds(research);
            StringBuilder allReasoning = new StringBuilder();
            String finalAnswer = "";
            String lastRaw = "";
            ArrayList<String> seenSearchQueries = new ArrayList<String>();
            ArrayList<String> seenFetchUrls = new ArrayList<String>();
            long end = start;
            for (int round = 0; round < maxRounds; round++) {
                boolean lastRound = round == maxRounds - 1;
                rememberPromptTokens(assistant, arr, nativeTools && !lastRound ? tools : null);
                JSONObject req = AgentTools.completionBody(requestModelId(model), arr, nativeTools ? tools : null, true, lastRound);
                if (source.equals("openrouter")) req.put("usage", new JSONObject().put("include", true));
                StreamRound sr;
                try {
                    sr = streamChatCompletion(req, key, source, assistant);
                } catch (RuntimeException e) {
                    if (nativeTools && AgentTools.looksLikeToolsUnsupported(e.getMessage())) {
                        nativeTools = false;
                        endpointsWithoutNativeTools.add(endpointUrl);
                        arr.put(new JSONObject().put("role", "system").put("content", AgentTools.textSearchFallbackPrompt()));
                        round--;
                        continue;
                    }
                    throw e;
                }
                end = sr.endAt;
                if (sr.promptTokens > 0) applyPromptTokens(assistant, sr.promptTokens);
                if (sr.reasoning.length() > 0) {
                    if (allReasoning.length() > 0) allReasoning.append('\n');
                    allReasoning.append(sr.reasoning);
                }
                lastRaw = sr.content;
                String visible = sanitizeAssistantText(sr.content);
                boolean usable = ToolText.isUsableFollowupAnswer(visible);
                ArrayList<AgentTools.ToolCall> calls = AgentTools.resolveCalls(sr.tools, sr.content, sr.reasoning, usable);
                if (calls.size() == 0) {
                    boolean hadSearch = lastSearchResult.length() > 0 || assistant.searchSources.size() > 0;
                    if (hadSearch && ToolText.looksLikeSearchPunt(visible) && round + 1 < maxRounds) {
                        String fetchUrl = "";
                        ArrayList<String> candidates = assistant.searchSources.size() > 0
                                ? assistant.searchSources : extractSearchSources(lastSearchResult);
                        ArrayList<String> pick = ToolText.preferReaderUrls(candidates);
                        for (int pi = 0; pi < pick.size(); pi++) {
                            String u = pick.get(pi);
                            if (u == null || u.length() == 0) continue;
                            if (!seenFetchUrls.contains(u.toLowerCase(Locale.US))) { fetchUrl = u; break; }
                        }
                        if (fetchUrl.length() > 0) {
                            seenFetchUrls.add(fetchUrl.toLowerCase(Locale.US));
                            String result = runFetchForTurn(assistant, fetchUrl);
                            if (result.length() > 0) {
                                arr.put(new JSONObject().put("role", "assistant").put("content", visible));
                                arr.put(AgentTools.textResultUserMessage("fetch", fetchUrl, result));
                                rememberPromptTokens(assistant, arr, nativeTools ? tools : null);
                                continue;
                            }
                        }
                        String seed = lastSearchQuery.length() > 0 ? lastSearchQuery : (userText == null ? "" : userText.trim());
                        String refined = ToolText.refineSearchQuery(seed, seenSearchQueries.size());
                        String keyQ = refined.toLowerCase(Locale.US);
                        if (refined.length() > 0 && !seenSearchQueries.contains(keyQ)) {
                            seenSearchQueries.add(keyQ);
                            String result = runWebSearchForTurn(assistant, refined, nativeTools);
                            if (result == null) result = "";
                            arr.put(new JSONObject().put("role", "assistant").put("content", visible));
                            arr.put(AgentTools.textResultUserMessage("web_search", refined, result));
                            rememberPromptTokens(assistant, arr, nativeTools ? tools : null);
                            continue;
                        }
                    }
                    if (maybeForceWebSearch(round, includeSearchTool, seenSearchQueries, forceSearch, userText, assistant, arr, nativeTools, visible)) {
                        rememberPromptTokens(assistant, arr, nativeTools ? tools : null);
                        continue;
                    }
                    finalAnswer = visible;
                    break;
                }
                boolean nativeThisRound = nativeTools && sr.tools.nativeCalls().size() > 0;
                if (nativeThisRound) {
                    arr.put(AgentTools.assistantNativeMessage(sr.content, calls));
                } else {
                    arr.put(new JSONObject().put("role", "assistant").put("content", visible.length() > 0 ? visible : sr.content));
                }
                ArrayList<AgentTools.ToolCall> executed = new ArrayList<AgentTools.ToolCall>();
                for (int i = 0; i < calls.size(); i++) {
                    AgentTools.ToolCall call = calls.get(i);
                    if (call == null) continue;
                    if (call.isSearch()) {
                        String q = call.query();
                        if (q.length() == 0) q = userText == null ? "" : userText.trim();
                        String keyQ = q.toLowerCase(Locale.US);
                        if (seenSearchQueries.contains(keyQ)) continue;
                        seenSearchQueries.add(keyQ);
                        String result = runWebSearchForTurn(assistant, q, nativeTools);
                        if (result == null) result = "";
                        if (nativeThisRound) arr.put(AgentTools.toolResultMessage(call.id, result));
                        else arr.put(AgentTools.textResultUserMessage("web_search", q, result));
                        executed.add(call);
                    } else if (call.isFetch()) {
                        String url = call.url();
                        String keyU = url.toLowerCase(Locale.US);
                        if (url.length() == 0 || seenFetchUrls.contains(keyU)) {
                            String dup = url.length() == 0 ? "No URL provided." : "Already fetched.";
                            if (nativeThisRound) arr.put(AgentTools.toolResultMessage(call.id, dup));
                            else arr.put(AgentTools.textResultUserMessage("fetch", url, dup));
                            executed.add(call);
                        } else {
                            seenFetchUrls.add(keyU);
                            String result = runFetchForTurn(assistant, url);
                            if (nativeThisRound) arr.put(AgentTools.toolResultMessage(call.id, result));
                            else arr.put(AgentTools.textResultUserMessage("fetch", url, result));
                            executed.add(call);
                        }
                    } else if (call.isSaveMemory()) {
                        String note = call.note();
                        ToolStep memStep = beginToolStep(assistant, "save_memory", note);
                        String result;
                        if (!memoryEnabled()) result = "Memory is turned off.";
                        else if (note.length() == 0) result = "No note provided.";
                        else {
                            String saved = appendMemory(note, "model");
                            if (saved.length() > 0) { assistant.memorySaved = true; assistant.memorySavedText = saved; }
                            result = saved.length() > 0 ? "Saved." : "Not saved (duplicate or empty).";
                        }
                        finishToolStep(memStep, result);
                        if (nativeThisRound) arr.put(AgentTools.toolResultMessage(call.id, result));
                        else arr.put(AgentTools.textResultUserMessage("save_memory", note, result));
                        executed.add(call);
                    } else if (call.isRemoveMemory()) {
                        String note = call.note();
                        ToolStep memStep = beginToolStep(assistant, "remove_memory", note);
                        String result;
                        if (!memoryEnabled()) result = "Memory is turned off.";
                        else {
                            String removed = removeMemory(note);
                            if (removed.length() > 0) appendRemovedMemory(assistant, removed);
                            result = removed.length() > 0 ? "Removed." : "Nothing matched.";
                        }
                        finishToolStep(memStep, result);
                        if (nativeThisRound) arr.put(AgentTools.toolResultMessage(call.id, result));
                        else arr.put(AgentTools.textResultUserMessage("remove_memory", note, result));
                        executed.add(call);
                    }
                }
                rememberPromptTokens(assistant, arr, nativeTools ? tools : null);
                if (maybeForceWebSearch(round, includeSearchTool, seenSearchQueries, forceSearch, userText, assistant, arr, nativeTools, "")) {
                    rememberPromptTokens(assistant, arr, nativeTools ? tools : null);
                    continue;
                }
                if (!AgentTools.continueAfter(executed, usable, round, maxRounds)) {
                    finalAnswer = visible;
                    break;
                }
                finalAnswer = visible;
            }
            if (finalAnswer.length() == 0) finalAnswer = sanitizeAssistantText(lastRaw);
            String memoryNote = memoryToolNote(finalAnswer);
            ArrayList<String> removeNotes = memoryRemoveToolNotes(finalAnswer);
            boolean hasRemoveTool = removeNotes.size() > 0 || finalAnswer.toLowerCase(Locale.US).contains("remove_memory");
            if (memoryNote.length() > 0 || hasRemoveTool || finalAnswer.toLowerCase(Locale.US).contains("save_memory")) {
                if (pendingUserMemoryNote.length() == 0 && memoryEnabled() && memoryNote.length() > 0) {
                    String savedMemory = appendMemory(memoryNote, "model");
                    if (savedMemory.length() > 0) { assistant.memorySaved = true; assistant.memorySavedText = savedMemory; }
                }
                if (memoryEnabled()) for (String removeNote : removeNotes) appendRemovedMemory(assistant, removeMemory(removeNote));
                finalAnswer = cleanAfterToolStrip(stripToolCalls(finalAnswer));
                if (finalAnswer.trim().length() == 0) finalAnswer = followupAfterMemoryTool(key, source, model, userText);
                if (finalAnswer.trim().length() == 0) finalAnswer = hasRemoveTool ? "I removed that from memory." : "I saved that to memory.";
            }
            if (pendingUserMemoryRemoval.length() > 0) appendRemovedMemory(assistant, removeMemory(pendingUserMemoryRemoval));
            if (pendingUserMemoryNote.length() > 0) {
                String savedMemory = appendMemory(pendingUserMemoryNote, "user");
                if (savedMemory.length() > 0) { assistant.memorySaved = true; assistant.memorySavedText = savedMemory; }
            }
            finalAnswer = recoverEmptyAssistantReply(key, source, model, userText, lastRaw, finalAnswer, allReasoning.toString(), assistant);
            final String finishedAnswer = finalAnswer;
            int completionTokens = estimateTokens(finishedAnswer) + (allReasoning.length() == 0 ? 0 : estimateTokens(allReasoning.toString()));
            final String stats = String.format(Locale.US, "%.1f tok/s", completionTokens / Math.max(0.001, (end - start) / 1e9));
            final String finishedReasoning = allReasoning.toString();
            runOnUiThread(new Runnable() { @Override public void run() { finishStreamingAssistant(assistant, finishedAnswer, finishedReasoning, stats, key, source, model); } });
        } catch (Exception e) {
            final String msg = friendlyError(e);
            runOnUiThread(new Runnable() { @Override public void run() {
                stopVoiceThinking();
                completeRunningToolSteps(assistant);
                assistant.stats = "";
                assistant.streamDone = true;
                assistant.text = isModelRefusal(msg) ? "model refused\n" + msg : "failed to load model\n" + msg;
                if (isImageInputUnsupported(msg)) markPriorUserImagesSkipped(assistant);
                setVoiceText(assistant.text);
                updateVoiceStatus(isModelRefusal(msg) ? "model refused" : "failed");
                saveCurrentChat();
                renderMessages();
            } });
        }
    }

    private static final class StreamRound {
        String content = "";
        String reasoning = "";
        AgentTools.RoundState tools = new AgentTools.RoundState();
        int promptTokens = 0;
        long endAt;
    }

    private void showSearchingStatus(final Msg assistant, final boolean keepText) {
        runOnUiThread(new Runnable() { @Override public void run() {
            freezeThinking(assistant);
            if (!keepText) assistant.text = "";
            assistant.stats = SEARCHING;
            assistant.jumpAnimStartMs = 0L;
            assistant.jumpAnimWord = "";
            forceAutoScrollBottom = userAtChatBottom;
            renderMessages();
        } });
    }

    private void freezeThinking(Msg assistant) {
        if (assistant == null) return;
        if (assistant.thoughtMs == 0) {
            assistant.thoughtMs = Math.max(1, System.currentTimeMillis() - assistant.startedAt);
        }
    }

    private ArrayList<ToolStep> copyToolSteps(Msg m) {
        ArrayList<ToolStep> out = new ArrayList<ToolStep>();
        if (m == null || m.toolSteps == null) return out;
        synchronized (m.toolSteps) {
            out.addAll(m.toolSteps);
        }
        return out;
    }

    private ToolStep beginToolStep(final Msg m, String name, String detail) {
        final ToolStep s = new ToolStep();
        s.name = name == null ? "" : name;
        s.detail = detail == null ? "" : detail;
        s.status = "running";
        if (m != null) {
            freezeThinking(m);
            synchronized (m.toolSteps) {
                m.toolSteps.add(s);
            }
        }
        runOnUiThread(new Runnable() { @Override public void run() {
            if (m != null) {
                m.jumpAnimStartMs = 0L;
                m.jumpAnimWord = "";
            }
            forceAutoScrollBottom = userAtChatBottom;
            renderMessages();
        } });
        return s;
    }

    private void finishToolStep(final ToolStep s, String preview) {
        if (s == null) return;
        s.status = "done";
        s.preview = preview == null ? "" : preview;
        runOnUiThread(new Runnable() { @Override public void run() {
            forceAutoScrollBottom = userAtChatBottom;
            renderMessages();
        } });
    }

    private void completeRunningToolSteps(Msg m) {
        if (m == null || m.toolSteps == null) return;
        synchronized (m.toolSteps) {
            for (int i = 0; i < m.toolSteps.size(); i++) {
                ToolStep s = m.toolSteps.get(i);
                if (s != null && "running".equals(s.status)) s.status = "done";
            }
        }
    }

    private String runWebSearchForTurn(Msg assistant, String query, boolean nativeTools) {
        showSearchingStatus(assistant, false);
        ToolStep step = beginToolStep(assistant, "web_search", query);
        String result;
        try {
            result = webSearch(query, nativeTools);
            rememberSearchResult(query, result);
        } catch (Exception searchErr) {
            result = lastSearchResult;
            if (result == null || result.length() == 0) {
                result = "Search failed: " + (searchErr.getMessage() == null ? "unknown error" : searchErr.getMessage());
            }
        }
        if (result == null) result = "";
        addToolTokens(assistant, result);
        if (result.length() > 0 && assistant != null) {
            assistant.searchSources.clear();
            assistant.searchSources.addAll(extractSearchSources(result));
            prefetchFavicons(assistant.searchSources);
        }
        int n = assistant == null ? 0 : assistant.searchSources.size();
        finishToolStep(step, n > 0 ? (n + " sources") : "no sources");
        return result;
    }

    private String runFetchForTurn(Msg assistant, String url) {
        showSearchingStatus(assistant, false);
        String host = ToolText.sourceHost(url);
        ToolStep step = beginToolStep(assistant, "fetch", host.length() > 0 ? host : url);
        String result = fetchUrlForTool(url);
        addToolTokens(assistant, result);
        if (assistant != null && url != null && url.length() > 0 && !assistant.searchSources.contains(url)) {
            assistant.searchSources.add(url);
            prefetchFavicons(assistant.searchSources);
        }
        finishToolStep(step, host.length() > 0 ? host : "done");
        return result;
    }

    private boolean maybeForceWebSearch(int round, boolean includeSearchTool, ArrayList<String> seenSearchQueries,
                                        boolean forceSearch, String userText, Msg assistant, JSONArray arr,
                                        boolean nativeTools, String visibleAssistant) {
        if (round != 0 || !includeSearchTool || seenSearchQueries.size() > 0) return false;
        if (!(forceSearch || ToolText.wantsWebSearch(userText))) return false;
        String q = ToolText.extractSearchQuery(userText);
        if (q.length() == 0) q = userText == null ? "" : userText.trim();
        if (q.length() == 0) return false;
        seenSearchQueries.add(q.toLowerCase(Locale.US));
        String result = runWebSearchForTurn(assistant, q, nativeTools);
        try {
            if (visibleAssistant != null && visibleAssistant.length() > 0) {
                arr.put(new JSONObject().put("role", "assistant").put("content", visibleAssistant));
            }
            arr.put(AgentTools.textResultUserMessage("web_search", q, result == null ? "" : result));
        } catch (Exception e) {
            return true;
        }
        return true;
    }

    private LinearLayout compactStatusRow() {
        LinearLayout row = row();
        row.setClipChildren(false);
        row.setClipToPadding(false);
        row.setPadding(0, dp(1), 0, dp(1));
        row.setMinimumHeight(dp(22));
        return row;
    }

    private TextView statusText(String label) {
        TextView v = text(label, 11, Color.rgb(135, 135, 135));
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setIncludeFontPadding(false);
        v.setPadding(0, dp(1), 0, dp(1));
        v.setMinHeight(dp(22));
        return v;
    }

    private JumpTextView liveStatus(String word, Msg m) {
        JumpTextView jump = new JumpTextView(this);
        jump.word = ToolText.ensureEllipsis(word == null || word.length() == 0 ? "thinking" : word);
        jump.bind(m);
        jump.setTextColor(Color.rgb(135, 135, 135));
        jump.setGravity(Gravity.CENTER_VERTICAL);
        setTextPx(jump, 11);
        jump.setIncludeFontPadding(false);
        jump.setPadding(0, dp(1), 0, dp(1));
        jump.setMinHeight(dp(22));
        return jump;
    }

    private TextView toolDoneView(ToolStep step) {
        String name = step == null || step.name == null ? "tool" : step.name;
        String detail = step == null ? "" : step.detail;
        String label = ToolText.toolDoneLabel(name, detail);
        SpannableStringBuilder b = new SpannableStringBuilder(label);
        int nameEnd = name.length();
        if (nameEnd > 0 && nameEnd <= b.length()) {
            b.setSpan(new TypefaceSpan("monospace"), 0, nameEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        TextView v = statusText("");
        v.setText(b);
        return v;
    }

    private View sourceThumbStrip(ArrayList<String> urls) {
        int size = dp(18);
        int step = dp(10);
        FrameLayout stack = new FrameLayout(this);
        stack.setClipChildren(false);
        stack.setClipToPadding(false);
        stack.setBackgroundColor(Color.TRANSPARENT);
        int n = 0;
        if (urls != null) {
            int limit = Math.min(8, urls.size());
            ArrayList<String> shown = new ArrayList<String>();
            for (int i = 0; i < limit; i++) {
                String url = urls.get(i);
                if (url == null || url.length() == 0) continue;
                shown.add(url);
            }
            n = shown.size();
            // Add right-to-left so the first source sits on top and later ones peek out.
            for (int i = n - 1; i >= 0; i--) {
                final String url = shown.get(i);
                View dot = sourceThumb(url);
                FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
                lp.leftMargin = i * step;
                lp.gravity = Gravity.CENTER_VERTICAL | Gravity.LEFT;
                stack.addView(dot, lp);
            }
        }
        int width = n <= 0 ? 0 : size + step * (n - 1);
        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setFillViewport(false);
        scroller.setClipChildren(false);
        scroller.setClipToPadding(false);
        scroller.setBackgroundColor(Color.TRANSPARENT);
        scroller.addView(stack, new FrameLayout.LayoutParams(width, size));
        return scroller;
    }

    private View sourceThumb(final String url) {
        String host = ToolText.sourceHost(url);
        Bitmap icon = null;
        synchronized (faviconCache) {
            icon = host.length() == 0 ? null : faviconCache.get(host);
        }
        SourceDot dot = new SourceDot(this);
        if (icon != null && !icon.isRecycled()) dot.icon = icon;
        else dot.letter = ToolText.sourceLetter(host);
        dot.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { openHttpUrl(url); } });
        return dot;
    }

    private void prefetchFavicons(ArrayList<String> urls) {
        if (urls == null) return;
        for (int i = 0; i < urls.size(); i++) {
            final String host = ToolText.sourceHost(urls.get(i));
            if (host.length() == 0) continue;
            synchronized (faviconCache) {
                if (faviconCache.containsKey(host) || faviconLoading.contains(host) || faviconFailed.contains(host)) continue;
                faviconLoading.add(host);
            }
            new Thread(new Runnable() { @Override public void run() {
                Bitmap b = downloadFavicon(host);
                synchronized (faviconCache) {
                    faviconLoading.remove(host);
                    if (b != null) faviconCache.put(host, b);
                    else faviconFailed.add(host);
                }
                if (b != null) runOnUiThread(new Runnable() { @Override public void run() { scheduleFaviconRender(); } });
            } }, "favicon").start();
        }
    }

    private void scheduleFaviconRender() {
        if (faviconUi != null) return;
        faviconUi = new Runnable() { @Override public void run() {
            faviconUi = null;
            renderMessages();
        } };
        ui.postDelayed(faviconUi, 180);
    }

    private Bitmap downloadFavicon(String host) {
        if (host == null || host.length() == 0) return null;
        String[] urls = new String[]{
                "https://www.google.com/s2/favicons?sz=64&domain_url=" + host,
                "https://www.google.com/s2/favicons?sz=32&domain=" + host,
                "https://icons.duckduckgo.com/ip3/" + host + ".ico"
        };
        for (int i = 0; i < urls.length; i++) {
            HttpURLConnection c = null;
            try {
                c = (HttpURLConnection) new URL(urls[i]).openConnection();
                c.setConnectTimeout(4000);
                c.setReadTimeout(4000);
                c.setInstanceFollowRedirects(true);
                c.setRequestProperty("User-Agent", "lightui/1.0");
                int code = c.getResponseCode();
                if (code < 200 || code >= 300) continue;
                Bitmap b = BitmapFactory.decodeStream(c.getInputStream());
                if (b == null || b.getWidth() <= 0) continue;
                if (b.getWidth() > 64 || b.getHeight() > 64) {
                    Bitmap scaled = Bitmap.createScaledBitmap(b, 32, 32, true);
                    if (scaled != b) b.recycle();
                    b = scaled;
                }
                return b;
            } catch (Exception ignored) {
            } finally {
                if (c != null) c.disconnect();
            }
        }
        return null;
    }

    private void openHttpUrl(String url) {
        if (url == null || url.length() == 0) return;
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception e) {
            toast("can't open link");
        }
    }

    private StreamRound streamChatCompletion(JSONObject body, String key, String source, final Msg assistant) throws Exception {
        StreamRound round = new StreamRound();
        HttpURLConnection c = (HttpURLConnection) new URL(chatCompletionsUrl(source, body.optString("model", ""))).openConnection();
        c.setRequestMethod("POST"); c.setConnectTimeout(30000); c.setReadTimeout(120000); c.setDoOutput(true);
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json");
        c.setRequestProperty("Accept", "text/event-stream, application/json");
        if (source.equals("openrouter")) { c.setRequestProperty("HTTP-Referer", "https://minimal.chat/android"); c.setRequestProperty("X-Title", "chat"); }
        OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes(StandardCharsets.UTF_8)); os.close();
        int code = c.getResponseCode();
        if (code >= 400) {
            InputStream es = c.getErrorStream();
            throw new RuntimeException(es == null ? ("HTTP " + code) : readAll(es));
        }
        final StringBuilder answer = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        BufferedReader br = new BufferedReader(new InputStreamReader(c.getInputStream(), StandardCharsets.UTF_8));
        StringBuilder nonSse = new StringBuilder();
        boolean[] inThinkTag = new boolean[]{false};
        String line;
        while ((line = br.readLine()) != null) {
            line = line.trim();
            if (!line.startsWith("data:")) { if (line.length() > 0) nonSse.append(line); continue; }
            String data = line.substring(5).trim();
            if ("[DONE]".equals(data)) break;
            JSONObject chunk = new JSONObject(data);
            JSONObject err = chunk.optJSONObject("error");
            if (err != null) throw new RuntimeException(err.optString("message", err.toString()));
            AgentTools.absorbChunk(round.tools, chunk);
            int used = ToolText.usagePromptTokens(chunk);
            if (used > round.promptTokens) round.promptTokens = used;
            JSONArray choices = chunk.optJSONArray("choices");
            if (choices == null || choices.length() == 0) continue;
            JSONObject delta = choices.getJSONObject(0).optJSONObject("delta");
            if (delta == null) continue;
            String content = cleanJsonString(delta, "content");
            String thought = reasoningDelta(delta);
            if (content.length() == 0 && thought.length() == 0) continue;
            appendReasoningAwareContent(content, answer, reasoning, inThinkTag);
            reasoning.append(thought);
            final String partial = answer.toString();
            final String partialReasoning = reasoning.toString();
            postStreamingAssistant(assistant, partial, partialReasoning);
        }
        br.close();
        if (answer.length() == 0 && reasoning.length() == 0 && nonSse.length() > 0) {
            JSONObject resp = new JSONObject(nonSse.toString());
            AgentTools.absorbChunk(round.tools, resp);
            answer.append(extractMessageText(resp));
            reasoning.append(extractMessageReasoning(resp));
            int used = ToolText.usagePromptTokens(resp);
            if (used > round.promptTokens) round.promptTokens = used;
        }
        round.content = answer.toString();
        round.reasoning = reasoning.toString();
        round.endAt = System.nanoTime();
        if (round.tools.error.length() > 0) throw new RuntimeException(round.tools.error);
        return round;
    }

    private boolean isImageInputUnsupported(String msg) {
        String l = msg == null ? "" : msg.toLowerCase(Locale.US);
        return l.contains("image input") || l.contains("image_url") || l.contains("does not support image")
                || l.contains("doesn't support image") || l.contains("images are not supported")
                || (l.contains("vision") && (l.contains("not support") || l.contains("unsupported")))
                || (l.contains("image") && (l.contains("not support") || l.contains("unsupported") || l.contains("not supported")));
    }

    private void markPriorUserImagesSkipped(Msg assistant) {
        int idx = messages.indexOf(assistant);
        if (idx <= 0) return;
        for (int i = idx - 1; i >= 0; i--) {
            Msg m = messages.get(i);
            if (!"user".equals(m.role)) continue;
            if (messageImageCount(m) > 0) {
                m.skipImagesInRequest = true;
                toast("photo kept in chat, skipped for this model");
            }
            break;
        }
    }

    private ArrayList<AttachedImage> requestImages(Msg m) {
        ArrayList<AttachedImage> out = new ArrayList<AttachedImage>();
        if (m == null || m.skipImagesInRequest) return out;
        m.ensureImagesFromLegacy();
        for (AttachedImage img : m.images) {
            if (img != null && img.base64 != null && img.base64.length() > 0) out.add(img);
        }
        return out;
    }

    private int messageImageCount(Msg m) {
        if (m == null) return 0;
        m.ensureImagesFromLegacy();
        return m.images.size();
    }

    private String reasoningDelta(JSONObject delta) {
        StringBuilder out = new StringBuilder();
        String[] keys = new String[]{"reasoning", "reasoning_content", "reasoningContent", "reasoning_text", "reasoningText", "reasoning_summary", "reasoningSummary", "thinking", "think", "thought", "analysis"};
        for (String key : keys) {
            if (delta != null && delta.has(key) && !delta.isNull(key)) appendReasoningValue(out, delta.opt(key));
        }
        JSONArray details = delta.optJSONArray("reasoning_details");
        if (details != null) for (int i = 0; i < details.length(); i++) {
            JSONObject d = details.optJSONObject(i);
            if (d != null) appendReasoningValue(out, d);
        }
        return out.toString();
    }

    private void appendReasoningValue(StringBuilder out, Object value) {
        if (value == null || JSONObject.NULL.equals(value)) return;
        if (value instanceof JSONObject) {
            JSONObject o = (JSONObject) value;
            String[] keys = new String[]{"text", "content", "summary", "reasoning", "reasoning_content", "thinking", "thought"};
            for (String key : keys) if (o.has(key) && !o.isNull(key)) appendReasoningValue(out, o.opt(key));
            JSONArray arr = o.optJSONArray("items");
            if (arr == null) arr = o.optJSONArray("details");
            if (arr != null) appendReasoningValue(out, arr);
            return;
        }
        if (value instanceof JSONArray) {
            JSONArray arr = (JSONArray) value;
            for (int i = 0; i < arr.length(); i++) appendReasoningValue(out, arr.opt(i));
            return;
        }
        String v = String.valueOf(value);
        if (v.length() > 0 && !"null".equals(v)) out.append(v);
    }

    private void appendReasoningAwareContent(String content, StringBuilder answer, StringBuilder reasoning, boolean[] inThinkTag) {
        String s = content == null ? "" : content;
        int i = 0;
        while (i < s.length()) {
            String lower = s.substring(i).toLowerCase(Locale.US);
            int open = firstIndex(lower, new String[]{"<think>", "<thinking>"});
            int close = firstIndex(lower, new String[]{"</think>", "</thinking>"});
            if (inThinkTag[0]) {
                if (close < 0) { reasoning.append(s.substring(i)); return; }
                reasoning.append(s.substring(i, i + close));
                i += close + (lower.startsWith("</thinking>", close) ? "</thinking>".length() : "</think>".length());
                inThinkTag[0] = false;
            } else {
                if (open < 0) { answer.append(s.substring(i)); return; }
                answer.append(s.substring(i, i + open));
                i += open + (lower.startsWith("<thinking>", open) ? "<thinking>".length() : "<think>".length());
                inThinkTag[0] = true;
            }
        }
    }

    private int firstIndex(String s, String[] needles) {
        int out = -1;
        for (String needle : needles) {
            int at = s.indexOf(needle);
            if (at >= 0) out = out < 0 ? at : Math.min(out, at);
        }
        return out;
    }

    private String reasoningFromText(String text) {
        StringBuilder answer = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        appendReasoningAwareContent(text, answer, reasoning, new boolean[]{false});
        return reasoning.toString().trim();
    }

    private String stripReasoningTags(String text) {
        StringBuilder answer = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        appendReasoningAwareContent(text, answer, reasoning, new boolean[]{false});
        return answer.toString().trim();
    }

    private String cleanJsonString(JSONObject o, String key) {
        if (o == null || !o.has(key) || o.isNull(key)) return "";
        String v = o.optString(key, "");
        return "null".equals(v) ? "" : v;
    }

    private void updateStreamingAssistant(Msg assistant, String partial, String reasoning) {
        boolean gotReasoning = reasoning.length() > 0;
        if (gotReasoning) {
            assistant.reasoning = reasoning;
            assistant.reasoningCapable = true;
            // Only clear visible text while still in the reasoning-only phase.
            if (partial == null || partial.length() == 0) assistant.text = "";
        }
        assistant.stats = gotReasoning && (partial == null || partial.length() == 0) ? LOADING : "";
        if (gotReasoning && (partial == null || partial.length() == 0)) {
            forceAutoScrollBottom = userAtChatBottom;
            requestStreamingRender();
            return;
        }
        String visiblePartial = visibleStreamingAnswer(partial);
        if (visiblePartial.length() > 0) stopVoiceThinking();
        if (gotReasoning && visiblePartial.length() > 0 && assistant.thoughtMs == 0) assistant.thoughtMs = Math.max(1, System.currentTimeMillis() - assistant.startedAt);
        assistant.text = visiblePartial.length() == 0 ? "" : cleanSearchArtifacts(visiblePartial);
        assistant.bodyDisplay = null;
        assistant.bodyDisplaySrc = "";
        forceAutoScrollBottom = userAtChatBottom;
        if (assistant.slowVoice && voiceMode && voiceFullMode) { updateVoiceStatus("responding"); if (assistant.ttsStarted || !prefs.getBoolean("voiceSpeak", true)) renderVoiceConversation(); }
        maybeSpeakStreamingChunk(assistant, visiblePartial, false);
        requestStreamingRender();
    }

    private void postStreamingAssistant(Msg assistant, String partial, String reasoning) {
        streamUiAssistant = assistant;
        streamUiPartial = partial == null ? "" : partial;
        streamUiReasoning = reasoning == null ? "" : reasoning;
        if (streamUiQueued) return;
        streamUiQueued = true;
        runOnUiThread(new Runnable() { @Override public void run() {
            streamUiQueued = false;
            Msg m = streamUiAssistant;
            String p = streamUiPartial;
            String r = streamUiReasoning;
            if (m == null) return;
            updateStreamingAssistant(m, p, r);
            if (p != streamUiPartial || r != streamUiReasoning) {
                updateStreamingAssistant(streamUiAssistant, streamUiPartial, streamUiReasoning);
            }
        } });
    }

    private String streamUiKey(Msg m) {
        if (m == null) return "";
        int steps = 0;
        String last = "";
        if (m.toolSteps != null) {
            synchronized (m.toolSteps) {
                steps = m.toolSteps.size();
                if (steps > 0) {
                    ToolStep s = m.toolSteps.get(steps - 1);
                    if (s != null) last = (s.status == null ? "" : s.status) + ":" + (s.name == null ? "" : s.name);
                }
            }
        }
        int sources = m.searchSources == null ? 0 : m.searchSources.size();
        boolean hasText = m.text != null && m.text.length() > 0;
        return (m.stats == null ? "" : m.stats) + "|" + steps + "|" + last + "|" + sources + "|"
                + (hasText ? "1" : "0") + "|" + (m.streamDone ? "1" : "0") + "|"
                + (m.thinkingExpanded ? "1" : "0") + "|" + (m.searchExpanded ? "1" : "0");
    }

    private CharSequence cachedMarkdown(Msg m, String bodyText) {
        if (m != null && m.bodyDisplay != null && bodyText.equals(m.bodyDisplaySrc)) return m.bodyDisplay;
        CharSequence rendered = markdownText(bodyText);
        if (m != null) {
            m.bodyDisplay = rendered;
            m.bodyDisplaySrc = bodyText;
        }
        return rendered;
    }

    private void scrollChatToBottom(ScrollView scroller) {
        pinChatScrollBottom(scroller, 0, 1);
    }

    private void pinChatToLatest(final ScrollView scroller) {
        if (scroller == null || messageList == null) return;
        final int gen = ++chatPinGen;
        pinChatScrollBottom(scroller, 0, 6);
        if (pendingChatPin != null) ui.removeCallbacks(pendingChatPin);
        pendingChatPin = new Runnable() { @Override public void run() {
            pendingChatPin = null;
            if (gen != chatPinGen || scroller != scroll || pane != 1) return;
            scroller.scrollTo(0, chatScrollMax(scroller));
            userAtChatBottom = true;
        } };
        ui.postDelayed(pendingChatPin, 260);
    }

    private boolean scrollIsNearBottom(int y) {
        if (scroll == null || messageList == null) return true;
        int max = chatScrollMax(scroll);
        return max <= dp(8) || y >= max - dp(160);
    }

    private int chatScrollContentHeight(ScrollView scroller) {
        View child = scroller != null && scroller.getChildCount() > 0 ? scroller.getChildAt(0) : messageList;
        if (child == null) return 0;
        return Math.max(child.getBottom(), Math.max(child.getHeight(), Math.max(child.getMeasuredHeight(), messageList == null ? 0 : messageList.getHeight())));
    }

    private int chatScrollMax(ScrollView scroller) {
        if (scroller == null) return 0;
        int view = scroller.getHeight() - scroller.getPaddingTop() - scroller.getPaddingBottom();
        return Math.max(0, chatScrollContentHeight(scroller) - view);
    }

    private void pinChatScrollBottom(final ScrollView scroller, final int pass, final int passes) {
        if (scroller == null || messageList == null) return;
        int view = scroller.getHeight();
        if (view <= 0 && pass < passes) {
            scroller.post(new Runnable() { @Override public void run() { pinChatScrollBottom(scroller, pass + 1, passes); } });
            return;
        }
        int max = chatScrollMax(scroller);
        if (scroller.getScrollY() != max) scroller.scrollTo(0, max);
        if (pass < passes) {
            scroller.post(new Runnable() { @Override public void run() { pinChatScrollBottom(scroller, pass + 1, passes); } });
        } else {
            userAtChatBottom = true;
        }
    }

    private void restoreChatScrollAfterShow() {
        if (scroll == null || messages.size() == 0) return;
        final ScrollView scroller = scroll;
        scroller.post(new Runnable() { @Override public void run() {
            if (scroll != scroller || pane != 1) return;
            if (forceAutoScrollBottom || userAtChatBottom || !savedChatScrollKnown || scrollIsNearBottom(savedChatScrollY) || scrollIsNearBottom(scroller.getScrollY())) {
                userAtChatBottom = true;
                pinChatToLatest(scroller);
            } else {
                scroller.scrollTo(0, savedChatScrollY);
            }
        } });
    }

    private boolean patchStreamingIfPossible(Msg assistant) {
        if (assistant == null || liveStreamMsg != assistant) return false;
        if (assistant.streamDone) return false;
        String key = streamUiKey(assistant);
        if (!key.equals(liveStreamKey)) return false;
        if (liveStreamBody != null) {
            String bodyText = sanitizeAssistantText(assistant.text);
            liveStreamBody.setText(bodyText);
        }
        if (forceAutoScrollBottom && userAtChatBottom) scrollChatToBottom(scroll);
        forceAutoScrollBottom = false;
        return true;
    }

    private void requestStreamingRender() {
        if (!chatPaneVisible()) {
            chatBoundId = "\u0001";
            return;
        }
        if (patchStreamingIfPossible(liveStreamMsg)) return;
        long now = System.currentTimeMillis();
        long wait = STREAM_RENDER_MIN_MS - (now - lastStreamRenderAt);
        if (wait <= 0) {
            if (pendingStreamRender != null) { ui.removeCallbacks(pendingStreamRender); pendingStreamRender = null; }
            lastStreamRenderAt = now;
            renderMessages();
            return;
        }
        if (pendingStreamRender != null) return;
        pendingStreamRender = new Runnable() { @Override public void run() {
            pendingStreamRender = null;
            lastStreamRenderAt = System.currentTimeMillis();
            if (patchStreamingIfPossible(liveStreamMsg)) return;
            renderMessages();
        } };
        ui.postDelayed(pendingStreamRender, wait);
    }

    private void flushPendingStreamRender() {
        if (pendingStreamRender != null) {
            ui.removeCallbacks(pendingStreamRender);
            pendingStreamRender = null;
        }
        lastStreamRenderAt = 0;
        if (!chatPaneVisible()) {
            chatBoundId = "\u0001";
            return;
        }
        renderMessages();
    }

    private String visibleStreamingAnswer(String text) { return ToolText.visibleStreamingAnswer(text); }

    private void finishStreamingAssistant(Msg assistant, String finalAnswer, String reasoning, String stats, String key, String source, String model) {
        stopVoiceThinking();
        completeRunningToolSteps(assistant);
        assistant.stats = stats;
        assistant.streamDone = true;
        String cleanedFinal = sanitizeAssistantText(finalAnswer.length() == 0 ? assistant.text : finalAnswer);
        if ("searching...".equals(cleanedFinal.trim()) || looksLikeToolResidue(cleanedFinal) || ToolText.looksLikeSearchPlanning(cleanedFinal)
                || ToolText.looksLikeSourceMetadataOnly(cleanedFinal) || ToolText.looksLikeInternalMonologue(cleanedFinal)
                || ToolText.looksLikeSearchPunt(cleanedFinal)) {
            cleanedFinal = "";
        }
        if (cleanedFinal.length() == 0) cleanedFinal = fallbackWhenNoReply(assistant);
        assistant.text = cleanedFinal;
        assistant.bodyDisplay = null;
        assistant.bodyDisplaySrc = "";
        if (assistant.thoughtMs == 0) assistant.thoughtMs = Math.max(1, System.currentTimeMillis() - assistant.startedAt);
        if (reasoning.length() > 0) assistant.reasoning = reasoning;
        if (assistant.slowVoice && isModelRefusal(assistant.text)) {
            assistant.ttsQueue.clear();
            assistant.ttsRequested = false;
            assistant.ttsPrefetching = false;
            assistant.ttsPlaying = false;
            forceAutoScrollBottom = true;
            flushPendingStreamRender();
            saveCurrentChat();
            pauseVoiceAfterProviderFailure("model refused", "model refused\n" + assistant.text);
            return;
        }
        forceAutoScrollBottom = userAtChatBottom;
        flushPendingStreamRender();
        saveCurrentChat();
        maybeGenerateChatTitle(key, source, model);
        if (assistant.slowVoice) { maybeSpeakStreamingChunk(assistant, assistant.text, true); maybeFinishVoiceAfterTts(assistant); }
    }

    /** Prefer search snippets over a blank hard-fail when sources exist. Never "No reply" if we have sources. */
    private String fallbackWhenNoReply(Msg assistant) {
        String snippet = ToolText.searchAnswerFallback(lastSearchResult);
        if (snippet.length() == 0) snippet = ToolText.searchSnippetFallback(lastSearchResult);
        if (snippet.length() > 0) {
            if (ToolText.containsPriceAmount(snippet) || ToolText.containsConcreteFact(snippet)) {
                return snippet;
            }
            return "I couldn't form a clean summary from the sources. Closest detail I found:\n\n" + snippet;
        }
        boolean hasSources = (assistant != null && assistant.searchSources.size() > 0) || lastSearchResult.length() > 0;
        if (hasSources) {
            return "I gathered sources, but couldn't form a clear answer from them. Try /research, or open one of the gathered links.";
        }
        return "No reply from the model.";
    }

    /**
     * If the model thought but emitted no usable reply, recover.
     * When sources exist, synthesis ALWAYS wins over accepting junk / empty.
     */
    private String recoverEmptyAssistantReply(String key, String source, String model, String userText,
                                              String rawAnswer, String finalAnswer, String reasoning, final Msg assistant) {
        String cleaned = sanitizeAssistantText(finalAnswer);
        if ("searching...".equals(cleaned.trim()) || looksLikeToolResidue(cleaned) || ToolText.looksLikeSearchPlanning(cleaned)
                || ToolText.looksLikeSourceMetadataOnly(cleaned) || ToolText.looksLikeInternalMonologue(cleaned)
                || ToolText.looksLikeSearchPunt(cleaned)) {
            cleaned = "";
        }
        boolean hasSources = lastSearchResult.length() > 0 || (assistant != null && assistant.searchSources.size() > 0);

        // Sources present: only re-synthesize when the kept text is not usable.
        if (hasSources && !ToolText.isUsableFollowupAnswer(cleaned)) {
            runOnUiThread(new Runnable() { @Override public void run() {
                assistant.text = "";
                assistant.stats = SEARCHING;
                assistant.jumpAnimStartMs = 0L;
                assistant.jumpAnimWord = "";
                forceAutoScrollBottom = userAtChatBottom;
                renderMessages();
            } });
            String q = lastSearchQuery.length() > 0 ? lastSearchQuery : (userText == null ? "" : userText.trim());
            String fromSearch = answerAfterWebSearch(key, source, model, q, lastSearchResult, userText, 3);
            if (fromSearch != null && fromSearch.trim().length() > 0) return fromSearch;
            return fallbackWhenNoReply(assistant);
        }

        if (!ToolText.needsEmptyReplyRecovery(rawAnswer, cleaned)) {
            return cleaned.length() > 0 ? cleaned : sanitizeAssistantText(finalAnswer);
        }

        runOnUiThread(new Runnable() { @Override public void run() {
            if (assistant.thoughtMs == 0) {
                assistant.thoughtMs = Math.max(1, System.currentTimeMillis() - assistant.startedAt);
            }
            assistant.text = "";
            assistant.stats = LOADING;
            forceAutoScrollBottom = userAtChatBottom;
            renderMessages();
        } });
        String recovered = "";
        try {
            recovered = sanitizeAssistantText(followupAfterEmptyReply(key, source, model, userText));
        } catch (Exception ignored) { }
        if (ToolText.isUsableFollowupAnswer(recovered)) return recovered;
        try {
            recovered = sanitizeAssistantText(followupAfterEmptyReply(key, source, model, userText));
        } catch (Exception ignored) { }
        if (ToolText.isUsableFollowupAnswer(recovered)) return recovered;
        return fallbackWhenNoReply(assistant);
    }

    private String followupAfterEmptyReply(String key, String source, String model, String userText) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", requestModelId(model));
        body.put("stream", false);
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject().put("role", "system").put("content", ToolText.emptyReplyFollowupSystem() + buildCurrentTimeContext()));
        String folderInstruction = buildFolderInstructionContext();
        if (folderInstruction.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", folderInstruction));
        arr.put(new JSONObject().put("role", "user").put("content", userText == null ? "" : userText));
        body.put("messages", arr);
        HttpURLConnection c = (HttpURLConnection) new URL(chatCompletionsUrl(source, model)).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(60000);
        c.setDoOutput(true);
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json");
        if (source.equals("openrouter")) {
            c.setRequestProperty("HTTP-Referer", "https://minimal.chat/android");
            c.setRequestProperty("X-Title", "empty reply followup");
        }
        OutputStream os = c.getOutputStream();
        os.write(body.toString().getBytes(StandardCharsets.UTF_8));
        os.close();
        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new RuntimeException(raw);
        String out = extractFollowupAnswerText(new JSONObject(raw));
        if (ToolText.isUsableFollowupAnswer(out)) return out;
        out = sanitizeAssistantText(stripReasoningTags(extractMessageText(new JSONObject(raw))));
        return out;
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
            if (speak.length() >= 8 || (finish && speak.length() > 0)) assistant.ttsQueue.add(speak);
            if (!finish) break;
        }
        if (finish && base < fullText.length()) {
            String tail = fullText.substring(base).trim();
            if (tail.length() > 0) assistant.ttsQueue.add(tail);
            base = fullText.length();
        }
        assistant.spokenChars = base;
        playNextQueuedSpeech(assistant);
        prefetchNextQueuedSpeech(assistant);
    }

    private void playNextQueuedSpeech(final Msg owner) {
        if (!voiceMode || !voiceFullMode) return;
        if (owner.voiceSessionId != 0 && owner.voiceSessionId != voiceSession) return;
        if (owner.ttsRequested || owner.ttsPlaying || owner.ttsQueue.size() == 0) return;
        if (owner.ttsPrefetching) {
            ui.postDelayed(new Runnable() { @Override public void run() { playNextQueuedSpeech(owner); } }, 100);
            return;
        }
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
        if (ready != null && ready.exists()) {
            if (owner.ttsQueue.size() > 0 && owner.ttsQueue.get(0).equals(text)) owner.ttsQueue.remove(0);
            owner.ttsStarted = true;
            owner.ttsPlaying = true;
            owner.ttsPlaybackFailures = 0;
            renderVoiceConversation();
            playSpeechAudio(ready, text, owner);
            prefetchNextQueuedSpeech(owner);
            return;
        }
        owner.ttsRequested = true;
        updateVoiceStatus("speaking");
        new Thread(new Runnable() { @Override public void run() {
            try {
                final File audio = requestSpeechAudio(text);
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (!voiceSessionActive(session)) return;
                    owner.ttsRequested = false;
                    if (owner.ttsQueue.size() > 0 && owner.ttsQueue.get(0).equals(text)) owner.ttsQueue.remove(0);
                    owner.ttsStarted = true;
                    owner.ttsPlaying = true;
                    owner.ttsPlaybackFailures = 0;
                    renderVoiceConversation();
                    playSpeechAudio(audio, text, owner);
                    prefetchNextQueuedSpeech(owner);
                } });
            } catch (Exception e) {
                final String msg = friendlyError(e);
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (!voiceSessionActive(session)) return;
                    owner.ttsRequested = false;
                    handleQueuedSpeechFailure(owner, text, msg);
                } });
            }
        } }).start();
    }

    private void prefetchNextQueuedSpeech(final Msg owner) {
        if (!voiceMode || !voiceFullMode) return;
        if (owner.voiceSessionId != 0 && owner.voiceSessionId != voiceSession) return;
        if (owner.ttsRequested || owner.ttsPrefetching || owner.ttsQueue.size() == 0) return;
        final String text = owner.ttsQueue.get(0);
        final int session = voiceSession;
        if (ttsReadyFiles.containsKey(text)) return;
        owner.ttsPrefetching = true;
        new Thread(new Runnable() { @Override public void run() {
            try {
                final File audio = requestSpeechAudio(text);
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (!voiceSessionActive(session)) return;
                    owner.ttsPrefetching = false;
                    ttsReadyFiles.put(text, audio);
                    if (!owner.ttsPlaying && !owner.ttsRequested) playNextQueuedSpeech(owner);
                } });
            } catch (Exception e) {
                runOnUiThread(new Runnable() { @Override public void run() {
                    if (!voiceSessionActive(session)) return;
                    owner.ttsPrefetching = false;
                    if (!owner.ttsPlaying && !owner.ttsRequested) playNextQueuedSpeech(owner);
                    else maybeFinishVoiceAfterTts(owner);
                } });
            }
        } }).start();
    }

    private void handleQueuedSpeechFailure(Msg owner, String failedText, String msg) {
        if (owner == null) return;
        if (owner.ttsQueue.size() > 0 && owner.ttsQueue.get(0).equals(failedText)) owner.ttsQueue.remove(0);
        ttsReadyFiles.remove(failedText);
        if (owner.ttsPlaybackFailures++ < 2 && failedText != null && failedText.trim().length() > 0) {
            owner.ttsQueue.add(0, failedText);
            updateVoiceStatus("retrying speech");
            ui.postDelayed(new Runnable() { @Override public void run() { playNextQueuedSpeech(owner); } }, 350);
            return;
        }
        if (owner.ttsQueue.size() > 0) {
            updateVoiceStatus("continuing");
            if (voiceText != null) setVoiceText(voiceText.getText().toString() + "\n\ntts skip: " + msg);
            playNextQueuedSpeech(owner);
            prefetchNextQueuedSpeech(owner);
            return;
        }
        updateVoiceStatus(isModelRefusal(msg) ? "model refused" : "tts failed");
        if (voiceText != null) setVoiceText(voiceText.getText().toString() + "\n\n" + (isModelRefusal(msg) ? "model refused: " : "tts: ") + msg);
        owner.streamDone = true;
        ui.postDelayed(new Runnable() { @Override public void run() { finishVoiceResponse(); } }, 1200);
    }

    private void maybeFinishVoiceAfterTts(Msg owner) {
        if (!voiceMode || !voiceFullMode) return;
        if (owner.voiceSessionId != 0 && owner.voiceSessionId != voiceSession) return;
        if (!owner.slowVoice) return;
        if (!prefs.getBoolean("voiceSpeak", true)) { finishVoiceResponse(); return; }
        if (owner.streamDone && !owner.ttsRequested && !owner.ttsPrefetching && !owner.ttsPlaying && owner.ttsQueue.size() == 0) finishVoiceResponse();
        else if (owner.streamDone && !owner.ttsRequested && !owner.ttsPlaying && owner.ttsQueue.size() > 0) playNextQueuedSpeech(owner);
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
        return haystack.contains("minimax") || haystack.contains("reasoning") || haystack.contains("thinking") || haystack.contains("deepseek-r1") || haystack.contains("r1-") || haystack.endsWith("/r1") || haystack.contains("qwq") || haystack.contains("qwen3") || haystack.contains("qwen-3") || haystack.contains("qwen/qwen3") || haystack.contains("magistral") || haystack.contains("glm-4.5") || haystack.contains("sonar-reasoning") || haystack.contains("kimi-k2-thinking") || haystack.contains("kimi-k3") || haystack.contains("kimi/k3") || haystack.contains("o1") || haystack.contains("o3") || haystack.contains("o4-");
    }

    private String buildSearchContext(String userText) throws Exception {
        lastSearchSources.clear();
        lastSearchResult = "";
        lastSearchQuery = "";
        // Consume one-shot flags so they don't leak into the next turn. The model
        // calls web_search / fetch itself — do not pre-search and hide tools.
        turnForceSearch = false;
        if (prefs.getBoolean("searchNext", false)) prefs.edit().remove("searchNext").apply();
        return "";
    }

    private void rememberSearchResult(String query, String result) {
        lastSearchQuery = query == null ? "" : query.trim();
        lastSearchResult = result == null ? "" : result;
        lastSearchSources.clear();
        lastSearchSources.addAll(extractSearchSources(lastSearchResult));
    }

    private String datedSearchQuery(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() == 0) return q;
        String lower = q.toLowerCase(Locale.US);
        if (lower.contains("yesterday") || lower.contains("today") || lower.contains("last night") || lower.matches(".*\\b20\\d{2}\\b.*")) {
            return q + " " + currentDateString();
        }
        return q;
    }

    private String cleanSearchArtifacts(String s) { return ToolText.cleanSearchArtifacts(s); }

    private String webSearchToolQuery(String text) { return ToolText.webSearchToolQuery(text); }

    private String answerAfterWebSearch(String key, String source, String model, String query, String result, String userText) {
        return answerAfterWebSearch(key, source, model, query, result, userText, 2);
    }

    private String answerAfterWebSearch(String key, String source, String model, String query, String result, String userText, int maxAttempts) {
        String cleaned = result == null ? "" : cleanSearchArtifacts(result);
        String q = query == null || query.trim().length() == 0 ? (userText == null ? "" : userText.trim()) : query.trim();
        int attempts = Math.max(1, Math.min(3, maxAttempts));
        for (int i = 0; i < attempts; i++) {
            try {
                boolean retry = i > 0;
                String out = sanitizeAssistantText(followupAfterWebSearch(key, source, model, q, cleaned, userText, retry, i));
                if (ToolText.isUsableFollowupAnswer(out)) return out;
            } catch (Exception ignored) { }
        }
        String snippet = ToolText.searchAnswerFallback(cleaned);
        if (snippet.length() == 0) snippet = ToolText.searchSnippetFallback(cleaned);
        if (snippet.length() > 0) {
            if (ToolText.containsPriceAmount(snippet) || ToolText.containsConcreteFact(snippet)) return snippet;
            return "I couldn't form a clean summary from the sources. Closest detail I found:\n\n" + snippet;
        }
        if (cleaned.trim().length() > 0) {
            return "I gathered sources, but couldn't form a clear answer from them. Try /research, or open one of the gathered links.";
        }
        return "Search returned no usable text. Try /research with a more specific query.";
    }

    private String followupAfterWebSearch(String key, String source, String model, String query, String result, String userText, boolean retry) throws Exception {
        return followupAfterWebSearch(key, source, model, query, result, userText, retry, retry ? 1 : 0);
    }

    private String followupAfterWebSearch(String key, String source, String model, String query, String result, String userText, boolean retry, int attemptIndex) throws Exception {
        // Keep sources short — long system dumps kill small/local models.
        if (result.length() > 4000) result = result.substring(0, 4000);
        JSONObject body = new JSONObject();
        body.put("model", requestModelId(model));
        JSONArray arr = new JSONArray();
        String system = ToolText.webSearchFollowupSystem(retry, attemptIndex) + buildCurrentTimeContext();
        arr.put(new JSONObject().put("role", "system").put("content", system));
        String folderInstruction = buildFolderInstructionContext();
        if (folderInstruction.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", folderInstruction));
        // Recent conversation context helps local models resolve pronouns / entities.
        appendRecentChatHistory(arr, 6);
        String ask = userText == null || userText.trim().length() == 0 ? query : userText.trim();
        // Put sources in the USER turn (recency) — weak models attend here far better than system.
        StringBuilder userBlock = new StringBuilder();
        userBlock.append("Question: ").append(ask).append("\n\n");
        userBlock.append("Search query: ").append(query == null ? "" : query).append("\n\n");
        userBlock.append("Sources:\n").append(cleanSearchArtifacts(result)).append("\n\n");
        userBlock.append("Answer the question now in plain text using the sources. Never tell the user to look it up themselves.");
        if (attemptIndex >= 1) {
            userBlock.append(" Respond with concrete facts only (prices/numbers/dates if relevant). Do not return a title or citation header.");
        }
        if (attemptIndex >= 2) {
            userBlock.append(" Final attempt: one short paragraph with the best numbers from the snippets.");
        }
        arr.put(new JSONObject().put("role", "user").put("content", userBlock.toString()));
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
        return extractFollowupAnswerText(new JSONObject(raw));
    }

    /** Append recent non-busy turns (plain text only) for post-tool synthesis context. */
    private void appendRecentChatHistory(JSONArray arr, int maxTurns) throws Exception {
        ArrayList<Msg> recent = new ArrayList<Msg>();
        // Skip the trailing user turn — the synthesis user block restates the question + sources.
        int end = messages.size() - 1;
        while (end >= 0) {
            Msg m = messages.get(end);
            if (m != null && "user".equals(m.role) && !isBusyStats(m.stats)) { end--; break; }
            if (m != null && "assistant".equals(m.role) && (isBusyStats(m.stats) || (m.text == null || m.text.trim().length() == 0))) {
                end--;
                continue;
            }
            break;
        }
        for (int i = end; i >= 0 && recent.size() < maxTurns; i--) {
            Msg m = messages.get(i);
            if (m == null || isBusyStats(m.stats)) continue;
            if (!"user".equals(m.role) && !"assistant".equals(m.role)) continue;
            String t = "assistant".equals(m.role) ? sanitizeAssistantText(m.text) : (m.text == null ? "" : m.text.trim());
            if (t.length() == 0) continue;
            if ("assistant".equals(m.role) && (ToolText.looksLikeToolResidue(t) || ToolText.looksLikeSearchPlanning(t))) continue;
            recent.add(m);
        }
        for (int i = recent.size() - 1; i >= 0; i--) {
            Msg m = recent.get(i);
            String t = "assistant".equals(m.role) ? sanitizeAssistantText(m.text) : (m.text == null ? "" : m.text.trim());
            if (t.length() > 800) t = t.substring(0, 800);
            arr.put(new JSONObject().put("role", m.role).put("content", t));
        }
    }

    private String extractFollowupAnswerText(JSONObject resp) throws Exception {
        String direct = sanitizeAssistantText(stripReasoningTags(extractMessageText(resp)));
        if (ToolText.isUsableFollowupAnswer(direct)) return direct;
        JSONArray choices = resp.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";
        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
        if (msg == null) return "";
        // Local servers often put the whole answer in reasoning_* / thinking / output / text.
        String[] keys = new String[]{"content", "output_text", "output", "text", "response", "reasoning", "reasoning_content", "thinking"};
        String best = "";
        boolean contentEmpty = !ToolText.isUsableFollowupAnswer(sanitizeAssistantText(stripReasoningTags(cleanJsonString(msg, "content"))));
        for (String key : keys) {
            String v = cleanJsonString(msg, key);
            v = sanitizeAssistantText(stripReasoningTags(v));
            if (!ToolText.isUsableFollowupAnswer(v) || v.length() >= 6000) continue;
            boolean reasoningField = "reasoning".equals(key) || "reasoning_content".equals(key) || "thinking".equals(key);
            if (reasoningField) {
                // Accept reasoning of any length when content is empty/unusable — local models do this often.
                if (!contentEmpty && v.length() > 800) continue;
                if (ToolText.looksLikeSearchPlanning(v) && !ToolText.containsConcreteFact(v)) continue;
            }
            if (v.length() > best.length()) best = v;
        }
        return best;
    }

    private String followupAfterMemoryTool(String key, String source, String model, String userText) {
        try {
            JSONObject body = new JSONObject();
            body.put("model", requestModelId(model));
            body.put("stream", false);
            JSONArray arr = new JSONArray();
            arr.put(new JSONObject().put("role", "system").put("content", "Your previous response contained only a private memory tool call. Do not output any tool calls. Answer the user's message naturally and briefly."));
            arr.put(new JSONObject().put("role", "user").put("content", userText == null ? "" : userText));
            body.put("messages", arr);
            HttpURLConnection c = (HttpURLConnection) new URL(chatCompletionsUrl(source, model)).openConnection();
            c.setRequestMethod("POST"); c.setConnectTimeout(20000); c.setReadTimeout(45000); c.setDoOutput(true);
            if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
            c.setRequestProperty("Content-Type", "application/json");
            if (source.equals("openrouter")) { c.setRequestProperty("HTTP-Referer", "https://minimal.chat/android"); c.setRequestProperty("X-Title", "memory followup"); }
            OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes(StandardCharsets.UTF_8)); os.close();
            int code = c.getResponseCode();
            if (code >= 400) return "";
            return stripToolCalls(extractMessageText(new JSONObject(readAll(c.getInputStream()))));
        } catch (Exception ignored) { return ""; }
    }

    private void maybeGenerateChatTitle(final String key, final String source, final String model) {
        if (currentChatId.length() == 0 || key == null || model == null || model.length() == 0) return;
        final String chatId = currentChatId;
        Chat chat = chatById(chatId);
        if (chat == null || !shouldGenerateTitle(chat)) return;
        final ArrayList<Msg> snapshot = new ArrayList<Msg>(chat.messages);
        if (titleRequests.contains(chatId)) return;
        titleRequests.add(chatId);
        String local = localChatTitle(snapshot);
        if (local.length() > 0) {
            chat.title = local;
            chat.titleGenerated = true;
            saveState();
            if (pane == 0 && chatList != null) renderChatList();
        }
        new Thread(new Runnable() { @Override public void run() {
            String title = "";
            try {
                title = generateChatTitle(key, source, model, snapshot);
            } catch (Exception ignored) { }
            final String finalTitle = title;
            if (finalTitle.length() == 0) return;
            runOnUiThread(new Runnable() { @Override public void run() {
                Chat c = chatById(chatId);
                if (c == null) return;
                c.title = finalTitle;
                c.titleGenerated = true;
                saveState();
                if (pane == 0 && chatList != null) renderChatList();
            } });
        } }).start();
    }

    private boolean shouldGenerateTitle(Chat c) {
        int users = 0, assistants = 0;
        for (Msg m : c.messages) { if ("user".equals(m.role) && m.text.length() > 0) users++; else if ("assistant".equals(m.role) && m.text.length() > 0 && !isBusyStats(m.stats)) assistants++; }
        return !c.titleGenerated && users > 0 && assistants > 0;
    }

    private String generateChatTitle(String key, String source, String model, ArrayList<Msg> titleMessages) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", requestModelId(model));
        body.put("stream", false);
        JSONArray arr = new JSONArray();
        arr.put(new JSONObject().put("role", "system").put("content", "Name this chat in 2-5 words. Return only the title. No quotes, punctuation, emoji, or extra text."));
        StringBuilder transcript = new StringBuilder();
        int included = 0;
        for (Msg m : titleMessages) {
            if (included >= 6 || isBusyStats(m.stats)) continue;
            if (m.text.length() == 0 || "[voice input]".equals(m.text)) continue;
            transcript.append(m.role).append(": ").append(m.text.replace('\n', ' ')).append('\n');
            included++;
        }
        arr.put(new JSONObject().put("role", "user").put("content", transcript.toString()));
        body.put("messages", arr);
        HttpURLConnection c = (HttpURLConnection) new URL(chatCompletionsUrl(source, model)).openConnection();
        c.setRequestMethod("POST");
        c.setConnectTimeout(20000);
        c.setReadTimeout(45000);
        c.setDoOutput(true);
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        c.setRequestProperty("Content-Type", "application/json");
        if (source.equals("openrouter")) { c.setRequestProperty("HTTP-Referer", "https://minimal.chat/android"); c.setRequestProperty("X-Title", "chat title"); }
        OutputStream os = c.getOutputStream(); os.write(body.toString().getBytes(StandardCharsets.UTF_8)); os.close();
        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new RuntimeException(raw);
        return cleanChatTitle(extractMessageText(new JSONObject(raw)));
    }

    private String localChatTitle(ArrayList<Msg> titleMessages) {
        for (Msg m : titleMessages) if ("user".equals(m.role) && m.text.length() > 0) return topicTitle(m.text);
        return "Image chat";
    }

    private String topicTitle(String text) {
        String s = cleanSearchArtifacts(text == null ? "" : text).replaceAll("(?i)^(hey|hi|hello|yo)[,\\s]+", "").replaceAll("[^A-Za-z0-9' ]+", " ").replaceAll("\\s+", " ").trim();
        String[] skip = new String[]{"what","whats","what's","how","why","explain","describe","can","could","would","should","you","please","tell","show","give","me","about","with","from","into","the","a","an","is","are","was","were","do","does","did","for","to","in","of","and","or","my","i","im","i'm","it","its","it's","one","sentence","brief","briefly"};
        ArrayList<String> picked = new ArrayList<String>();
        for (String raw : s.split("\\s+")) {
            String w = raw.trim();
            if (w.length() == 0) continue;
            String lower = w.toLowerCase(Locale.US);
            boolean ignore = false;
            for (String k : skip) if (lower.equals(k)) { ignore = true; break; }
            if (!ignore) picked.add(titleWord(w));
            if (picked.size() >= 5) break;
        }
        if (picked.size() == 0) for (String raw : s.split("\\s+")) { if (raw.length() > 0) picked.add(titleWord(raw)); if (picked.size() >= 4) break; }
        String out = join(picked).replace('\n', ' ').trim();
        return out.length() == 0 ? "New chat" : cleanChatTitle(out);
    }

    private String titleWord(String w) { return w.length() <= 1 ? w.toUpperCase(Locale.US) : w.substring(0,1).toUpperCase(Locale.US) + w.substring(1).toLowerCase(Locale.US); }

    private String cleanChatTitle(String title) {
        String s = cleanSearchArtifacts(title == null ? "" : title).replace('\n', ' ').replace("\"", "").replace("'", "").trim();
        s = s.replaceFirst("(?i)^title:\\s*", "").replaceAll("[.!?]+$", "").trim();
        if (s.length() > 38) s = s.substring(0, 38).trim();
        return s;
    }

    private Chat chatById(String id) { for (Chat c : chats) if (c.id.equals(id)) return c; return null; }

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

    private boolean privateReasoning(Msg m) {
        if (m == null) return false;
        String fullModel = expandShortModel(m.model);
        if ("custom".equals(modelSource(fullModel))) return false;
        return privateReasoning(m.reasoning);
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

    private void addBackgroundSystemContext(JSONArray arr, boolean includeMemoryTools) throws Exception {
        addBackgroundSystemContext(arr, includeMemoryTools, false, true, true);
    }

    private void addBackgroundSystemContext(JSONArray arr, boolean includeMemoryTools, boolean searchContextAlreadyInjected) throws Exception {
        addBackgroundSystemContext(arr, includeMemoryTools, searchContextAlreadyInjected, true, true);
    }

    private void addBackgroundSystemContext(JSONArray arr, boolean includeMemoryTools, boolean searchContextAlreadyInjected, boolean allowSearch, boolean textFallback) throws Exception {
        arr.put(new JSONObject().put("role", "system").put("content", buildCurrentTimeContext()));
        String toolMemory = buildToolMemoryContext();
        if (toolMemory.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", toolMemory));
        String folderInstruction = buildFolderInstructionContext();
        if (folderInstruction.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", folderInstruction));
        String userMemory = buildUserMemoryContext();
        if (userMemory.length() > 0) arr.put(new JSONObject().put("role", "system").put("content", userMemory));
        if (!includeMemoryTools) return;
        boolean search = allowSearch && webSearchAvailable();
        boolean memory = memoryEnabled();
        if (search || memory) {
            arr.put(new JSONObject().put("role", "system").put("content",
                    AgentTools.leanToolsPrompt(search, memory, textFallback, searchContextAlreadyInjected)));
        }
    }

    private boolean webSearchAvailable() {
        // Search providers work without a key for Jina (rate-limited); Brave needs a key.
        if ("brave".equals(searchProvider())) {
            String key = prefs == null ? "" : prefs.getString("braveApiKey", "");
            return key.length() > 0;
        }
        return true;
    }

    private String buildCurrentTimeContext() {
        java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("EEEE, MMMM d, yyyy 'at' h:mm a z", Locale.getDefault());
        fmt.setTimeZone(java.util.TimeZone.getDefault());
        String now = fmt.format(new java.util.Date());
        return "Current local date/time from the user's phone: " + now + ". "
                + "Use this silently as ground truth for now/today/yesterday/last night/this week, and to judge whether facts may be stale. "
                + "For scores, news, schedules, prices, weather, or other moving information, prefer fresh web search over training knowledge when search is available. "
                + "Do not open or pad replies by announcing the date or time unless the user asks what day/time it is, or stating it is necessary to answer clearly.";
    }

    private String memoryToolsPrompt() {
        return "Private memory tools: answer the user normally first, then optionally append ONE tool call at the very end. "
                + "Save with <tool_call><function=save_memory><parameter=note>short third-person note about the user</parameter></function></tool_call> only for durable facts that should still matter in future chats: name/nickname, stable preferences, ongoing constraints, relationships, home/base location, important recurring context. "
                + "Do NOT save: one-off requests, temporary plans for today, chit-chat, trivia, secrets/passwords/API keys, account numbers, medical/financial details, content already in memory, or anything not clearly stated about the user. "
                + "Prefer one short note. If unsure whether it is durable, do not save. "
                + "If the user asks to forget/remove something, append <tool_call><function=remove_memory><parameter=note>memory to remove</parameter></function></tool_call> at the end. "
                + "Never reply with only a tool call. Never mention these tools unless asked.";
    }

    private String buildUserMemoryContext() {
        if (!memoryEnabled()) return "";
        String mem = normalizeMemoryMd(memoryMd()).trim();
        if (mem.length() == 0) return "";
        return "Private persistent user memory from MEMORY.md. This is background context, not the user's current message. "
                + "Numbered entries describe the user unless explicitly stated otherwise. Use memory quietly to personalize replies and respect preferences. "
                + "Do not mention memory, quote this block, or say you know something from memory unless the user asks what you remember or it is directly relevant. "
                + "Entries include the date they were saved; use those dates with the phone's current date/time to reason about time-sensitive facts like age. "
                + "If asked what you remember, summarize these entries.\n\n" + mem;
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
        if (isMemoryRemovalRequest(text)) return "";
        String note = explicitMemoryNote(text);
        if (note.length() == 0) note = heuristicMemoryNote(text);
        return note;
    }

    private String pendingUserMemoryRemoval(String text) {
        if (!memoryEnabled() || !isMemoryRemovalRequest(text)) return "";
        String s = text == null ? "" : text.trim();
        String lower = s.toLowerCase(Locale.US);
        if ((lower.contains("clear") || lower.contains("delete") || lower.contains("remove") || lower.contains("forget")) && (lower.contains("all memory") || lower.contains("all memories") || lower.contains("both memories") || lower.contains("everything from memory") || lower.contains("memory.md"))) return "__ALL__";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?is)\\b(?:forget|remove|delete|clear)\\b(?:\\s+(?:this|that|it|from|out of|in|my|your|memory))*\\s+(?:that\\s+)?(.+?)\\s*(?:from|out of|in)?\\s*(?:memory)?\\s*[?.!]*$").matcher(s);
        if (m.find()) {
            String note = m.group(1).trim().replaceFirst("(?i)^(memory\\s+)?(?:about|of)\\s+", "");
            if (note.length() > 0) return note;
        }
        return s.replaceFirst("(?is).*?\\b(?:forget|remove|delete|clear)\\b", "").replaceAll("(?i)\\b(from|out of|in)?\\s*memory\\b", "").trim();
    }

    private boolean isMemoryRemovalRequest(String text) {
        String s = text == null ? "" : text.toLowerCase(Locale.US);
        return (s.contains("memory") || s.contains("remember")) && (s.contains("forget") || s.contains("remove") || s.contains("delete") || s.contains("clear"));
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
        // Client-side auto-save only for strong identity/style cues. Casual likes/opinions
        // are left to the model save_memory tool so one-off chat does not pollute MEMORY.md.
        String s = text == null ? "" : text.trim();
        String lower = s.toLowerCase(Locale.US);
        String extracted = extractMemoryFactClause(s);
        if (extracted.length() > 0) return extracted;
        if (lower.matches(".*\\bmy name is\\b.+")) return s;
        if (lower.matches(".*\\bmy birthday\\b.+")) return s;
        if (lower.matches(".*\\bi (?:always |usually )?(?:prefer)\\b.+")) return s;
        if (lower.startsWith("stop being ") || lower.startsWith("don't be ") || lower.startsWith("do not be ")) return "User preference: " + s;
        if (lower.contains("call me ")) return s;
        return "";
    }

    private String extractMemoryFactClause(String text) {
        String s = text == null ? "" : text.trim();
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("(?is)\\b(my name is|my birthday is|my birthday's|call me|i prefer|i always prefer|i usually prefer)\\b(.+?)\\s*[?.!]*$").matcher(s);
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

    private ArrayList<String> memoryRemoveToolNotes(String text) {
        ArrayList<String> out = new ArrayList<String>();
        String s = text == null ? "" : text;
        String lower = s.toLowerCase(Locale.US);
        if (!lower.contains("remove_memory")) return out;
        java.util.regex.Matcher fn = java.util.regex.Pattern.compile("(?is)<function\\s*=\\s*remove_memory[^>]*>(.*?)</function>").matcher(s);
        while (fn.find()) {
            String note = memoryRemoveToolNoteFromBlock(fn.group(1));
            if (note.length() > 0 && !out.contains(note)) out.add(note);
        }
        if (out.size() > 0) return out;
        String note = memoryRemoveToolNoteFromBlock(s);
        if (note.length() > 0) out.add(note);
        return out;
    }

    private String memoryRemoveToolNoteFromBlock(String s) {
        String lower = s == null ? "" : s.toLowerCase(Locale.US);
        java.util.regex.Matcher param = java.util.regex.Pattern.compile("(?is)<parameter(?:\\s+name\\s*=\\s*[\"']?(?:note|item|memory)[\"']?|\\s*=\\s*(?:note|item|memory))[^>]*>(.*?)</parameter>").matcher(s);
        if (param.find()) return cleanMemoryToolNote(param.group(1));
        String[] markers = new String[]{"<parameter=note>", "note:", "note=", "\"note\":"};
        for (String marker : markers) {
            int start = lower.indexOf(marker.toLowerCase(Locale.US));
            if (start < 0) continue;
            start += marker.length();
            int end = s.indexOf("</parameter>", start);
            if (end < 0) end = s.indexOf("</function>", start);
            if (end < 0) end = s.length();
            String note = cleanMemoryToolNote(s.substring(start, end));
            if (note.length() > 0) return note;
        }
        return "";
    }

    private void appendRemovedMemory(Msg assistant, String removed) {
        if (removed == null || removed.trim().length() == 0) return;
        assistant.memorySaved = true;
        if (!assistant.memorySavedText.startsWith("__REMOVED__\n")) assistant.memorySavedText = "__REMOVED__\n";
        assistant.memorySavedText = assistant.memorySavedText.length() == "__REMOVED__\n".length() ? assistant.memorySavedText + removed.trim() : assistant.memorySavedText + "\n" + removed.trim();
    }

    private String removeMemory(String note) {
        if ("__ALL__".equals(note)) {
            String removedAll = memoryEntriesText(memoryMd());
            writeMemoryMd("");
            return removedAll.length() > 0 ? removedAll : "all";
        }
        String query = memoryDedupeKey(canonicalMemoryNote(note, "user"));
        if (query.length() == 0) return "";
        String mem = normalizeMemoryMd(memoryMd());
        StringBuilder kept = new StringBuilder("# MEMORY.md\n\n");
        StringBuilder removed = new StringBuilder();
        int n = 1;
        for (String line : mem.replaceFirst("(?is)^#\\s*MEMORY\\.md\\s*", "").trim().split("\\n")) {
            String entry = line.trim();
            if (entry.length() == 0) continue;
            entry = entry.replaceFirst("^(?:[-*]|[0-9]+[.)])\\s+", "").trim();
            String key = memoryDedupeKey(entry);
            if (key.contains(query) || query.contains(key) || memoryTokenOverlap(key, query) >= 0.55f) removed.append(entry).append('\n');
            else kept.append(n++).append(". ").append(entry).append('\n');
        }
        if (removed.length() > 0) writeMemoryMd(n == 1 ? "" : kept.toString());
        return removed.toString().trim();
    }

    private String memoryEntriesText(String markdown) {
        String mem = normalizeMemoryMd(markdown);
        StringBuilder out = new StringBuilder();
        for (String line : mem.replaceFirst("(?is)^#\\s*MEMORY\\.md\\s*", "").trim().split("\\n")) {
            String entry = line.trim();
            if (entry.length() == 0) continue;
            entry = entry.replaceFirst("^(?:[-*]|[0-9]+[.)])\\s+", "").trim();
            if (entry.length() > 0) out.append(entry).append('\n');
        }
        return out.toString().trim();
    }

    private float memoryTokenOverlap(String entryKey, String queryKey) {
        String[] q = queryKey == null ? new String[0] : queryKey.split("\\s+");
        if (q.length == 0) return 0f;
        int useful = 0, hits = 0;
        for (String token : q) {
            if (token.length() < 3 || "user".equals(token) || "users".equals(token) || "memory".equals(token)) continue;
            useful++;
            if ((entryKey == null ? "" : entryKey).contains(token)) hits++;
        }
        return useful == 0 ? 0f : hits / (float) useful;
    }

    private String cleanMemoryToolNote(String note) {
        return (note == null ? "" : note).replace("&quot;", "\"").replace("&apos;", "'").replace("\"", "").replace("'", "").trim();
    }

    private String stripToolCalls(String text) { return ToolText.stripToolCalls(text); }

    private boolean looksLikeToolResidue(String text) { return ToolText.looksLikeToolResidue(text); }

    private String sanitizeAssistantText(String text) {
        return ToolText.sanitizeAssistantText(stripReasoningTags(text == null ? "" : text));
    }

    private String cleanAfterToolStrip(String text) { return ToolText.cleanAfterToolStrip(text); }

    private String searchQuery(String text) {
        String trimmed = text == null ? "" : text.trim();
        String lower = trimmed.toLowerCase(Locale.US);
        if (lower.startsWith("/search ")) return trimmed.substring(8).trim();
        if (lower.startsWith("/research ")) return trimmed.substring(10).trim();
        if (lower.equals("/search") || lower.equals("/research")) return "";
        boolean explicitLookup = lower.contains("look up") || lower.contains("lookup") || lower.contains("search for")
                || lower.contains("search the web") || lower.contains("web search") || lower.startsWith("google ")
                || lower.contains(" look up ") || lower.startsWith("lookup ")
                || lower.startsWith("find me ") || lower.startsWith("find the ")
                || lower.contains("average price") || lower.contains("how much") || lower.contains("going for");
        boolean force = turnForceSearch || turnResearch || webSearchChat;
        if (!prefs.getBoolean("autoSearch", false) && !force) return "";
        if (explicitLookup) return trimmed;
        if (force) return trimmed;
        if (!isLikelyQuestion(lower)) return "";
        if (lower.contains("right now") || lower.contains("currently") || lower.contains("at the moment") || lower.contains("as of now") || lower.contains("today") || lower.contains("yesterday") || lower.contains("last night") || lower.contains("latest") || lower.contains("recent") || lower.contains("newest") || lower.contains("current ") || lower.contains("current-") || lower.contains("news") || lower.contains("score") || lower.contains("this week") || lower.contains("this month") || lower.contains("2025") || lower.contains("2026") || lower.contains("price") || lower.contains("cost")) return trimmed;
        return "";
    }

    private boolean shouldVoiceAutoSearch(String text) {
        String lower = text == null ? "" : text.toLowerCase(Locale.US);
        return lower.contains("search for") || lower.contains("search the web") || lower.contains("web search") || lower.contains("look up") || lower.contains("google") || lower.contains("today") || lower.contains("yesterday") || lower.contains("last night") || lower.contains("right now") || lower.contains("current") || lower.contains("latest") || lower.contains("news") || lower.contains("score") || lower.contains("this week") || lower.contains("this month");
    }

    private boolean isLikelyQuestion(String lower) {
        return lower.contains("?") || lower.startsWith("what ") || lower.startsWith("who ") || lower.startsWith("when ") || lower.startsWith("where ") || lower.startsWith("why ") || lower.startsWith("how ") || lower.startsWith("is ") || lower.startsWith("are ") || lower.startsWith("can ") || lower.startsWith("does ") || lower.startsWith("do ") || lower.startsWith("did ") || lower.startsWith("which ");
    }

    private static final String SEARCH_UA = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Mobile Safari/537.36";

    private String webSearch(String query) throws Exception {
        return webSearch(query, false);
    }

    private String webSearch(String query, boolean modelCanFetch) throws Exception {
        String q = datedSearchQuery(query);
        String raw = "";
        Exception last = null;
        if ("brave".equals(searchProvider())) {
            raw = braveSearch(q);
        } else {
            try {
                raw = jinaSearch(q);
            } catch (Exception e) {
                last = e;
                raw = "";
            }
            boolean weak = ToolText.searchResultsLackFacts(raw) || mostlyVideoHits(raw);
            if (ToolText.looksLikePriceQuery(q) && ToolText.searchResultsLackPriceFacts(raw)) weak = true;
            if (weak) {
                try {
                    String ddg = duckDuckGoSearch(q);
                    if (ddg.length() > 0) raw = raw.length() == 0 ? ddg : (raw + "\n\n" + ddg);
                } catch (Exception e2) {
                    if (raw.length() == 0) {
                        if (last != null) throw last;
                        throw e2;
                    }
                }
            }
        }
        String compact = ToolText.compactWebSearch(raw);
        if (compact.length() == 0) compact = raw;
        // When the model can fetch, keep search compact (Pi: search then fetch).
        // Without native fetch, inline page facts so SEARCH: fallback still has numbers.
        if (!modelCanFetch && ToolText.looksLikePriceQuery(q)) {
            compact = enrichWithPageFacts(raw, compact);
        }
        return compact.trim().length() > 0 ? compact.trim() : raw;
    }

    private boolean mostlyVideoHits(String raw) {
        ArrayList<String> urls = extractSearchSources(raw);
        if (urls.size() == 0) {
            String l = raw == null ? "" : raw.toLowerCase(Locale.US);
            return l.contains("youtube.com") || l.contains("youtu.be");
        }
        int bad = 0;
        for (int i = 0; i < urls.size(); i++) if (ToolText.isLowValueSearchUrl(urls.get(i))) bad++;
        return bad * 2 >= urls.size();
    }

    private String enrichWithPageFacts(String raw, String compact) {
        ArrayList<String> urls = extractSearchSources(raw);
        if (urls.size() == 0) urls = extractSearchSources(compact);
        ArrayList<String> pick = ToolText.preferReaderUrls(urls);
        if (pick.size() == 0) return compact;
        StringBuilder extra = new StringBuilder();
        for (int i = 0; i < pick.size(); i++) {
            String url = pick.get(i);
            String facts = readPageFacts(url);
            if (facts.length() == 0) continue;
            if (extra.length() > 0) extra.append("\n\n");
            extra.append("Page facts (").append(url).append("):\n").append(facts);
        }
        if (extra.length() == 0) return compact;
        return AgentTools.clipResult(extra.toString() + "\n\n" + compact);
    }

    private String readPageFacts(String url) {
        String page = "";
        try { page = jinaRead(url); } catch (Exception ignored) { page = ""; }
        String facts = ToolText.extractFactLines(page, 900);
        if (facts.length() > 0) return facts;
        try { page = fetchPage(url); } catch (Exception ignored) { return ""; }
        return ToolText.extractFactLines(page, 900);
    }

    private String fetchUrlForTool(String url) {
        String u = url == null ? "" : url.trim();
        if (u.length() == 0) return "No URL provided.";
        if (ToolText.isLowValueSearchUrl(u)) return "Skipped low-value URL.";
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        String page = "";
        try { page = jinaRead(u); } catch (Exception ignored) { page = ""; }
        if (page.length() == 0) {
            try { page = fetchPage(u); } catch (Exception e) {
                return "Fetch failed: " + (e.getMessage() == null ? "unknown error" : e.getMessage());
            }
        }
        String facts = ToolText.extractFactLines(page, 2000);
        if (facts.length() > 0) return facts;
        String plain = ToolText.looksLikeHtml(page) ? ToolText.htmlToPlainText(page) : page;
        plain = plain.trim();
        if (plain.length() == 0) return "No readable text on that page.";
        return AgentTools.clipResult(plain);
    }

    private String jinaRead(String url) throws Exception {
        String u = url == null ? "" : url.trim();
        if (u.length() == 0) return "";
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        HttpURLConnection c = (HttpURLConnection) new URL("https://r.jina.ai/" + u).openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(12000);
        c.setRequestProperty("User-Agent", SEARCH_UA);
        c.setRequestProperty("Accept", "text/plain");
        String jinaKey = prefs.getString("jinaApiKey", "");
        if (jinaKey.length() > 0) c.setRequestProperty("Authorization", "Bearer " + jinaKey);
        int code = c.getResponseCode();
        String raw = readAllLimited(code >= 400 ? c.getErrorStream() : c.getInputStream(), 250000);
        if (code >= 400) throw new RuntimeException(raw);
        if (ToolText.looksLikeBlockedPage(raw)) throw new RuntimeException("reader blocked");
        return raw.trim();
    }

    private String fetchPage(String url) throws Exception {
        String u = url == null ? "" : url.trim();
        if (u.length() == 0) return "";
        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
        HttpURLConnection c = (HttpURLConnection) new URL(u).openConnection();
        c.setInstanceFollowRedirects(true);
        c.setConnectTimeout(10000);
        c.setReadTimeout(15000);
        c.setRequestProperty("User-Agent", SEARCH_UA);
        c.setRequestProperty("Accept", "text/html,text/plain;q=0.9,*/*;q=0.8");
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        int code = c.getResponseCode();
        String raw = readAllLimited(code >= 400 ? c.getErrorStream() : c.getInputStream(), 250000);
        if (code >= 400) throw new RuntimeException("page http " + code);
        if (ToolText.looksLikeBlockedPage(raw)) throw new RuntimeException("page blocked");
        return raw;
    }

    private String readAllLimited(InputStream in, int max) throws Exception {
        if (in == null) return "";
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        int total = 0;
        while ((n = in.read(buf)) >= 0) {
            int room = max - total;
            if (room <= 0) break;
            int w = n < room ? n : room;
            out.write(buf, 0, w);
            total += w;
            if (total >= max) break;
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8);
    }

    private String duckDuckGoSearch(String query) throws Exception {
        String encoded = URLEncoder.encode(query, "UTF-8");
        String parsed = fetchDuckDuckGo("https://html.duckduckgo.com/html/?q=" + encoded);
        if (parsed.length() == 0) parsed = fetchDuckDuckGo("https://lite.duckduckgo.com/lite/?q=" + encoded);
        if (parsed.length() == 0) throw new RuntimeException("duckduckgo returned no results");
        return parsed;
    }

    private String fetchDuckDuckGo(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        c.setRequestProperty("User-Agent", SEARCH_UA);
        c.setRequestProperty("Accept", "text/html,application/xhtml+xml");
        c.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if (code >= 400) throw new RuntimeException(raw);
        return ToolText.parseDuckDuckGoHtml(raw);
    }

    private String jinaSearch(String query) throws Exception {
        String encoded = URLEncoder.encode(query, "UTF-8").replace("+", "%20");
        HttpURLConnection c = (HttpURLConnection) new URL("https://s.jina.ai/" + encoded).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("User-Agent", SEARCH_UA);
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
        TextView hint = text("hold to pin a favorite", 11, Color.rgb(120, 120, 120));
        hint.setPadding(0, 0, 0, dp(8));
        box.addView(hint);
        ScrollView scroller = new ScrollView(this);
        scroller.setVerticalScrollBarEnabled(false);
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroller.addView(list);
        final Runnable[] render = new Runnable[1];
        render[0] = new Runnable() { @Override public void run() {
            list.removeAllViews();
            ArrayList<String> ordered = ToolText.orderedModels(myModels, pinnedModels);
            for (int i = 0; i < ordered.size(); i++) {
                final String m = ordered.get(i);
                boolean selected = m.equals(selectedModel());
                boolean pinned = pinnedModels.contains(m);
                View row = modelListRow(m, selected, pinned, i, false);
                row.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); setModel(m); } });
                row.setOnLongClickListener(new View.OnLongClickListener() { @Override public boolean onLongClick(View v) {
                    togglePinnedModel(m);
                    render[0].run();
                    return true;
                } });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                if (i > 0) lp.topMargin = dp(6);
                list.addView(row, lp);
            }
        } };
        render[0].run();
        box.addView(scroller, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView cancel = panelAction("cancel");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(48)));
        showPanel(d, box);
    }

    private View modelListRow(String model, boolean selected, boolean pinned, int index, boolean settings) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(10), dp(9), dp(10), dp(9));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(index % 2 == 0 ? Color.BLACK : Color.rgb(16, 16, 16));
        bg.setStroke(1, selected ? Color.rgb(88, 88, 88) : Color.rgb(34, 34, 34));
        bg.setCornerRadius(dp(3));
        row.setBackground(bg);

        LinearLayout top = row();
        top.setBackgroundColor(Color.TRANSPARENT);
        TextView name = text(shortModel(model), 15, Color.WHITE);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        name.setBackgroundColor(Color.TRANSPARENT);
        top.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        if (pinned) {
            TextView pin = text("pin", 11, Color.rgb(135, 135, 135));
            pin.setBackgroundColor(Color.TRANSPARENT);
            pin.setPadding(dp(8), 0, 0, 0);
            top.addView(pin, new LinearLayout.LayoutParams(-2, -2));
        }
        if (selected && !settings) {
            TextView on = text("on", 11, Color.rgb(180, 180, 180));
            on.setBackgroundColor(Color.TRANSPARENT);
            on.setPadding(dp(8), 0, 0, 0);
            top.addView(on, new LinearLayout.LayoutParams(-2, -2));
        }
        row.addView(top, new LinearLayout.LayoutParams(-1, -2));
        TextView sub = text(modelRowSubtitle(model), 11, Color.rgb(135, 135, 135));
        sub.setSingleLine(true);
        sub.setEllipsize(android.text.TextUtils.TruncateAt.END);
        sub.setBackgroundColor(Color.TRANSPARENT);
        sub.setPadding(0, dp(2), 0, 0);
        row.addView(sub, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private String modelRowSubtitle(String model) {
        String src = modelSource(model);
        String label = ToolText.modelProviderLabel(model, src);
        if ("custom".equals(src)) {
            String host = ToolText.endpointCardHost(modelEndpoint(model));
            return host.length() > 0 ? "endpoint · " + host : "endpoint";
        }
        Integer ctx = modelContexts.get(model);
        if (ctx != null && ctx > 0) return label + " · " + shortTokens(ctx);
        return label;
    }

    private void togglePinnedModel(String model) {
        if (model == null || model.length() == 0 || !myModels.contains(model)) return;
        if (pinnedModels.contains(model)) {
            pinnedModels.remove(model);
            toast("unpinned");
        } else {
            pinnedModels.add(model);
            toast("pinned · top of list");
        }
        savePinnedModels();
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
                    TextView item = searchResultItem(shortModel(m), modelRowSubtitle(m));
                    item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); addMyModel(m); } });
                    list.addView(item);
                    shown++;
                    if (shown >= 12) break;
                }
            }
            final String typed = search.getText().toString().trim();
            if (typed.length() > 1 && !myModels.contains(typed) && !myModels.contains(typedModelKey(typed))) {
                TextView item = searchResultItem("use " + shortModel(typed), soleCustomEndpoint().length() > 0 ? "endpoint" : (customEndpoints.size() > 0 ? "pick from list" : "openrouter"));
                item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); addMyModel(typedModelKey(typed)); } });
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
        if (!models.contains(m) && !ToolText.isCustomModelKey(m) && soleCustomEndpoint().length() > 0) {
            m = typedModelKey(m);
        }
        if (!models.contains(m)) {
            models.add(0, m);
            modelSources.put(m, ToolText.isCustomModelKey(m) || soleCustomEndpoint().length() > 0 ? "custom" : "openrouter");
            if ("custom".equals(modelSources.get(m))) {
                String endpoint = ToolText.customModelEndpoint(m);
                modelEndpoints.put(m, endpoint.length() > 0 ? endpoint : soleCustomEndpoint());
            }
            saveModels(); saveModelSources(); saveModelEndpoints();
        }
        if (!myModels.contains(m)) { myModels.add(m); saveMyModels(); }
        toast("added " + shortModel(m));
        if (pane == 2) showSettingsPane();
    }

    private void removeMyModel(String m) {
        myModels.remove(m);
        pinnedModels.remove(m);
        saveMyModels();
        savePinnedModels();
        if (m.equals(selectedModel())) prefs.edit().remove("model").putBoolean("modelSelected", false).apply();
        showSettingsPane();
    }

    private boolean modelMatches(String model, String query) {
        String q = query.toLowerCase(Locale.US).trim();
        String full = model.toLowerCase(Locale.US);
        String api = ToolText.modelApiId(model).toLowerCase(Locale.US);
        String shortName = shortModel(model).toLowerCase(Locale.US);
        String host = ToolText.sourceHost(modelEndpoint(model)).toLowerCase(Locale.US);
        String meta = modelSearchText.containsKey(model) ? modelSearchText.get(model).toLowerCase(Locale.US) : "";
        return full.contains(q) || api.contains(q) || shortName.contains(q) || (host.length() > 0 && host.contains(q)) || meta.contains(q) || normalized(full).contains(normalized(q)) || normalized(meta).contains(normalized(q));
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

    private void refreshModels() { refreshModels(true); }

    private void maybeAutoRefreshModels(boolean force) {
        if (hookVoiceMode || modelsRefreshing) return;
        String key = savedApiKey();
        if (key.length() == 0 && customEndpoints.size() == 0) return;
        long last = prefs.getLong("modelsRefreshedAt", 0);
        if (!force && last > 0 && System.currentTimeMillis() - last < MODEL_AUTO_REFRESH_MS) return;
        refreshModels(false);
    }

    private void refreshModels(final boolean manual) {
        saveApiKey();
        final String key = savedApiKey();
        final ArrayList<String> endpoints = new ArrayList<String>(customEndpoints);
        final String typedEndpoint = endpointInput == null ? "" : normalizeEndpoint(endpointInput.getText().toString());
        final String typedKey = endpointKeyInput == null ? "" : endpointKeyInput.getText().toString().trim();
        if (typedEndpoint.length() > 0 && !endpoints.contains(typedEndpoint)) endpoints.add(typedEndpoint);
        if (key.length() == 0 && endpoints.size() == 0) { if (manual) toast("add key or endpoint"); return; }
        if (modelsRefreshing) { if (manual) toast("refreshing models..."); return; }
        modelsRefreshing = true;
        if (manual) toast("refreshing models...");
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
                Exception lastFetch = null;
                int fetchedOk = 0;
                final ArrayList<String> listedEndpoints = new ArrayList<String>();
                if (key.length() > 0) {
                    try {
                        if (fetchModelsInto(OPENROUTER_ENDPOINT, key, "openrouter", found, foundContexts, foundSources, foundEndpoints, foundAudioOutput, foundAudioInput, foundReasoning, foundSpeed)) fetchedOk++;
                    } catch (Exception e) { lastFetch = e; }
                }
                for (String endpoint : endpoints) {
                    String endpointKey = typedEndpoint.equals(endpoint) && typedKey.length() > 0 ? typedKey : endpointKey(endpoint);
                    try {
                        if (fetchModelsInto(endpoint, endpointKey, "custom", found, foundContexts, foundSources, foundEndpoints, foundAudioOutput, foundAudioInput, foundReasoning, foundSpeed)) {
                            listedEndpoints.add(normalizeEndpoint(endpoint));
                            fetchedOk++;
                        }
                    } catch (Exception e) { lastFetch = e; }
                }
                if (fetchedOk == 0) throw lastFetch == null ? new RuntimeException("no endpoints") : lastFetch;
                runOnUiThread(new Runnable() { @Override public void run() {
                    modelsRefreshing = false;
                    for (String endpoint : endpoints) {
                        if (!customEndpoints.contains(endpoint)) customEndpoints.add(endpoint);
                        if (typedEndpoint.equals(endpoint) && typedKey.length() > 0) setEndpointKey(endpoint, typedKey);
                    }
                    models.clear(); models.addAll(found); modelContexts.clear(); modelContexts.putAll(foundContexts); modelSources.clear(); modelSources.putAll(foundSources); modelEndpoints.clear(); modelEndpoints.putAll(foundEndpoints); audioOutputModels.clear(); audioOutputModels.addAll(foundAudioOutput); audioInputModels.clear(); audioInputModels.addAll(foundAudioInput); reasoningModels.clear(); reasoningModels.addAll(foundReasoning); speedModels.clear(); speedModels.addAll(foundSpeed);
                    applyCatalogToMyModels(listedEndpoints);
                    retargetSelectedModelIfNeeded();
                    saveCustomEndpoints(); saveEndpointKeys(); saveModels(); saveModelContexts(); saveModelSources(); saveModelEndpoints(); saveAudioOutputModels(); saveAudioInputModels(); saveReasoningModels(); saveSpeedModels(); saveMyModels(); savePinnedModels();
                    prefs.edit().putLong("modelsRefreshedAt", System.currentTimeMillis()).apply();
                    if (manual) toast("models updated");
                    if (manual || pane == 2) renderPane();
                } });
            } catch (Exception e) {
                final String msg = friendlyError(e);
                runOnUiThread(new Runnable() { @Override public void run() {
                    modelsRefreshing = false;
                    if (manual) toast("models failed: " + msg);
                } });
            }
        } }).start();
    }

    private boolean fetchModelsInto(String endpoint, String key, String source, ArrayList<String> found, HashMap<String, Integer> foundContexts, HashMap<String, String> foundSources, HashMap<String, String> foundEndpoints, HashSet<String> foundAudioOutput, HashSet<String> foundAudioInput, HashSet<String> foundReasoning, HashSet<String> foundSpeed) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(normalizeEndpoint(endpoint) + "/models").openConnection();
        c.setConnectTimeout(8000);
        c.setReadTimeout(15000);
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
        int code = c.getResponseCode();
        String raw = readAll(code >= 400 ? c.getErrorStream() : c.getInputStream());
        if ("custom".equals(source) && (code == 404 || code == 405)) return false;
        if (code >= 400) throw new RuntimeException(raw);
        JSONObject root = new JSONObject(raw);
        JSONArray data = root.optJSONArray("data");
        if (data == null) data = root.optJSONArray("models");
        if (data == null) {
            if ("custom".equals(source)) return false;
            throw new RuntimeException(raw);
        }
        for (int i = 0; i < data.length(); i++) {
            JSONObject model = data.optJSONObject(i);
            if (model == null) continue;
            String id = model.optString("id", "").trim();
            if (id.length() == 0) id = model.optString("name", "").trim();
            if (id.length() == 0) continue;
            String catalogId = ToolText.catalogModelKey(source, endpoint, id);
            String name = model.optString("name", "");
            String description = model.optString("description", "");
            if (!found.contains(catalogId)) found.add(catalogId);
            foundSources.put(catalogId, source);
            String host = "custom".equals(source) ? ToolText.endpointCardHost(normalizeEndpoint(endpoint)) : "";
            modelSearchText.put(catalogId, (name.length() > 0 ? name : shortModel(catalogId)) + "\n" + id + "\n" + catalogId + "\n" + description + (host.length() > 0 ? "\n" + host : ""));
            if ("custom".equals(source)) foundEndpoints.put(catalogId, normalizeEndpoint(endpoint));
            if (foundAudioOutput != null && hasAudioOutput(model, id, name)) foundAudioOutput.add(catalogId);
            if (foundAudioInput != null && hasAudioInput(model, id, name, description)) foundAudioInput.add(catalogId);
            if (foundReasoning != null && hasReasoningCapability(model, id, name, description)) foundReasoning.add(catalogId);
            if (foundSpeed != null && hasSpeedParameter(model, id, name, description)) foundSpeed.add(catalogId);
            int context = model.optInt("context_length", 0);
            JSONObject top = model.optJSONObject("top_provider");
            if (context <= 0 && top != null) context = top.optInt("context_length", 0);
            if (context > 0) foundContexts.put(catalogId, context);
        }
        return true;
    }

    /**
     * After a live catalog fetch (or a previously saved good catalog), pin stored
     * custom models to the endpoint that actually listed them. Drops leftover
     * first-endpoint keys. Never rewrites the catalog itself.
     */
    private void applyCatalogToMyModels(ArrayList<String> listedEndpoints) {
        ArrayList<String> nextMine = ToolText.reconcileMyModels(myModels, models, listedEndpoints);
        myModels.clear();
        myModels.addAll(nextMine);
        ArrayList<String> nextPins = new ArrayList<String>();
        for (String id : pinnedModels) {
            String mapped = ToolText.retargetStoredModel(id, models, myModels);
            if (myModels.contains(mapped) && !nextPins.contains(mapped)) nextPins.add(mapped);
        }
        pinnedModels.clear();
        pinnedModels.addAll(nextPins);
        retargetPrefModel("model");
        retargetPrefModel("voiceAnswerModel");
        retargetPrefModel("voiceTranscribeModel");
        retargetPrefModel("voiceTtsModel");
        retargetPrefModel("voiceEndpointTtsModel");
        String selected = prefs.getString("model", "").trim();
        if (selected.length() > 0 && !myModels.contains(selected)) {
            String mapped = ToolText.retargetStoredModel(selected, models, myModels);
            if (!myModels.contains(mapped)) mapped = myModels.size() == 0 ? "" : myModels.get(0);
            SharedPreferences.Editor e = prefs.edit();
            if (mapped.length() == 0) e.remove("model").putBoolean("modelSelected", false);
            else e.putString("model", mapped);
            e.apply();
        }
    }

    private void retargetPrefModel(String pref) {
        String cur = prefs.getString(pref, "").trim();
        if (cur.length() == 0) return;
        String mapped = ToolText.retargetStoredModel(cur, models, myModels);
        if (mapped.length() > 0 && !mapped.equals(cur)) prefs.edit().putString(pref, mapped).apply();
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
        return haystack.contains("reasoning") || haystack.contains("thinking") || haystack.contains("deepseek-r1") || haystack.contains("r1-") || haystack.endsWith("/r1") || haystack.contains("qwq") || haystack.contains("qwen3") || haystack.contains("qwen-3") || haystack.contains("qwen/qwen3") || haystack.contains("minimax") || haystack.contains("magistral") || haystack.contains("glm-4.5") || haystack.contains("sonar-reasoning") || haystack.contains("kimi-k2-thinking") || haystack.contains("kimi-k3") || haystack.contains("kimi/k3") || haystack.contains("o1") || haystack.contains("o3") || haystack.contains("o4-");
    }

    private boolean hasSpeedParameter(JSONObject model, String id, String name, String description) {
        JSONArray params = model.optJSONArray("supported_parameters");
        if (params != null) for (int i = 0; i < params.length(); i++) if ("speed".equalsIgnoreCase(params.optString(i))) return true;
        String haystack = (id + " " + name + " " + description).toLowerCase(Locale.US);
        return haystack.contains("kokoro") || haystack.contains("tts-1") || haystack.contains("gpt-4o-mini-tts");
    }

    private void testCustomEndpoint() {
        final String typed = endpointInput == null ? "" : normalizeEndpoint(endpointInput.getText().toString());
        final String endpoint = typed.length() > 0 ? typed : customEndpointBase();
        final String typedKey = endpointKeyInput == null ? "" : endpointKeyInput.getText().toString().trim();
        final String key = typed.length() > 0 ? typedKey : endpointKey(endpoint);
        testEndpointConnection(endpoint, key);
    }

    private void testEndpointConnection(final String endpointRaw, final String key) {
        final String endpoint = normalizeEndpoint(endpointRaw);
        if (endpoint.length() == 0) { toast("add endpoint"); return; }
        toast("testing endpoint");
        new Thread(new Runnable() { @Override public void run() {
            try {
                ArrayList<String> found = new ArrayList<String>();
                HashMap<String, Integer> contexts = new HashMap<String, Integer>();
                HashMap<String, String> sources = new HashMap<String, String>();
                HashMap<String, String> endpoints = new HashMap<String, String>();
                fetchModelsInto(endpoint, key == null ? "" : key.trim(), "custom", found, contexts, sources, endpoints, null, null, null, null);
                final int count = found.size();
                runOnUiThread(new Runnable() { @Override public void run() { toast(count == 0 ? "endpoint connected: audio/no models" : "endpoint connected: " + count + " models"); } });
            } catch (Exception e) {
                final String msg = friendlyError(e);
                runOnUiThread(new Runnable() { @Override public void run() { toast("endpoint failed: " + msg); } });
            }
        } }).start();
    }

    private String customEndpointBase() { return customEndpoints.size() == 0 ? "" : customEndpoints.get(0); }
    private String soleCustomEndpoint() { return customEndpoints.size() == 1 ? customEndpoints.get(0) : ""; }
    private String normalizeEndpoint(String endpoint) { return ToolText.normalizeEndpoint(endpoint); }
    private String chatCompletionsUrl(String source) { return chatCompletionsUrl(source, selectedModel()); }
    private String chatCompletionsUrl(String source, String model) { return (source.equals("custom") ? modelEndpoint(model) : OPENROUTER_ENDPOINT) + "/chat/completions"; }

    private String endpointKey(String endpoint) {
        String e = normalizeEndpoint(endpoint);
        if (e.length() == 0) return "";
        String key = endpointKeys.get(e);
        return key == null ? "" : key;
    }

    private void setEndpointKey(String endpoint, String key) {
        String e = normalizeEndpoint(endpoint);
        if (e.length() == 0) return;
        String clean = key == null ? "" : key.trim();
        if (clean.length() == 0) endpointKeys.remove(e);
        else endpointKeys.put(e, clean);
    }

    private String authKeyForUrl(String url) {
        String u = url == null ? "" : url.trim();
        if (u.length() == 0) return "";
        String lower = u.toLowerCase(Locale.US);
        if (lower.contains("openrouter.ai")) return savedApiKey();
        String best = "";
        String bestKey = "";
        for (String endpoint : customEndpoints) {
            String e = normalizeEndpoint(endpoint);
            if (e.length() == 0) continue;
            if (lower.startsWith(e.toLowerCase(Locale.US)) && e.length() > best.length()) {
                best = e;
                bestKey = endpointKey(e);
            }
        }
        return bestKey;
    }

    private void addEndpointFromInput() {
        if (endpointInput == null) return;
        String endpoint = normalizeEndpoint(endpointInput.getText().toString());
        if (endpoint.length() == 0) { toast("enter endpoint"); return; }
        String key = endpointKeyInput == null ? "" : endpointKeyInput.getText().toString().trim();
        if (!customEndpoints.contains(endpoint)) customEndpoints.add(endpoint);
        setEndpointKey(endpoint, key);
        saveCustomEndpoints();
        saveEndpointKeys();
        if ("endpoint".equals(voiceOutputProvider()) && (prefs.getString("voiceTtsEndpoint", "").trim().length() == 0 || endpoint.equals(voiceTtsEndpoint()))) discoverVoiceEndpoint(endpoint);
        endpointInput.setText("");
        if (endpointKeyInput != null) endpointKeyInput.setText("");
        toast("endpoint added");
        showSettingsPane();
    }

    private void renameCustomEndpoint(String fromRaw, String toRaw, String key) {
        String from = normalizeEndpoint(fromRaw);
        String to = normalizeEndpoint(toRaw);
        if (to.length() == 0) return;
        if (from.length() > 0 && !from.equals(to)) {
            int idx = customEndpoints.indexOf(from);
            if (idx >= 0) {
                if (customEndpoints.contains(to)) customEndpoints.remove(idx);
                else customEndpoints.set(idx, to);
            } else if (!customEndpoints.contains(to)) {
                customEndpoints.add(to);
            }
            endpointKeys.remove(from);
            HashMap<String, String> renamed = new HashMap<String, String>();
            for (String m : new ArrayList<String>(models)) {
                boolean match = from.equals(modelEndpoints.get(m)) || from.equals(ToolText.customModelEndpoint(m));
                if (!match) continue;
                String nk = ToolText.customModelKey(to, ToolText.modelApiId(m));
                if (!nk.equals(m)) renamed.put(m, nk);
            }
            for (String fromId : renamed.keySet()) remapModelIdentity(fromId, renamed.get(fromId));
            for (String m : new ArrayList<String>(modelEndpoints.keySet())) {
                if (from.equals(modelEndpoints.get(m))) modelEndpoints.put(m, to);
            }
            if (from.equals(normalizeEndpoint(prefs.getString("voiceTranscribeEndpoint", "")))) prefs.edit().putString("voiceTranscribeEndpoint", to).apply();
            if (from.equals(normalizeEndpoint(prefs.getString("voiceTtsEndpoint", "")))) prefs.edit().putString("voiceTtsEndpoint", to).apply();
        } else if (!customEndpoints.contains(to)) {
            customEndpoints.add(to);
        }
        setEndpointKey(to, key);
        saveCustomEndpoints();
        saveEndpointKeys();
        saveModelEndpoints();
        if ("endpoint".equals(voiceOutputProvider()) && (prefs.getString("voiceTtsEndpoint", "").trim().length() == 0 || to.equals(voiceTtsEndpoint()))) discoverVoiceEndpoint(to);
    }

    private void removeCustomEndpoint(String endpoint) {
        endpoint = normalizeEndpoint(endpoint);
        boolean removedSelected = false;
        customEndpoints.remove(endpoint);
        endpointKeys.remove(endpoint);
        for (int i = models.size() - 1; i >= 0; i--) {
            String m = models.get(i);
            if (endpoint.equals(modelEndpoints.get(m)) || endpoint.equals(ToolText.customModelEndpoint(m))) {
                if (m.equals(selectedModel())) removedSelected = true;
                models.remove(i); myModels.remove(m); pinnedModels.remove(m); modelContexts.remove(m); modelSources.remove(m); modelEndpoints.remove(m);
            }
        }
        if (removedSelected) prefs.edit().remove("model").putBoolean("modelSelected", false).apply();
        saveCustomEndpoints(); saveEndpointKeys(); saveModels(); saveMyModels(); savePinnedModels(); saveModelContexts(); saveModelSources(); saveModelEndpoints();
    }

    private void initTts() {
        tts = new TextToSpeech(this, new TextToSpeech.OnInitListener() { @Override public void onInit(int status) {
            ttsReady = status == TextToSpeech.SUCCESS;
            if (ttsReady) {
                tts.setLanguage(Locale.getDefault());
                tts.setOnUtteranceProgressListener(new UtteranceProgressListener() {
                    @Override public void onStart(String id) { runOnUiThread(new Runnable() { @Override public void run() { updateVoiceStatus("speaking"); } }); }
                    @Override public void onDone(String id) {
                        final String utteranceId = id;
                        runOnUiThread(new Runnable() { @Override public void run() {
                            if (PHONE_UTTERANCE.equals(utteranceId)) { launchPendingPhoneIntent(); return; }
                            if (activeTtsOwner != null) {
                                Msg owner = activeTtsOwner;
                                owner.ttsPlaying = false;
                                owner.ttsRequested = false;
                                if (owner.ttsQueue.size() > 0) playNextQueuedSpeech(owner);
                                else maybeFinishVoiceAfterTts(owner);
                            } else finishVoiceResponse();
                        } });
                    }
                    @Override public void onError(String id) {
                        final String utteranceId = id;
                        runOnUiThread(new Runnable() { @Override public void run() {
                            if (PHONE_UTTERANCE.equals(utteranceId)) { launchPendingPhoneIntent(); return; }
                            if (activeTtsOwner != null) {
                                Msg owner = activeTtsOwner;
                                owner.ttsPlaying = false;
                                owner.ttsRequested = false;
                                if (owner.ttsQueue.size() > 0) playNextQueuedSpeech(owner);
                                else { updateVoiceStatus("speech failed"); maybeFinishVoiceAfterTts(owner); }
                            } else updateVoiceStatus("speech failed");
                        } });
                    }
                });
            }
        } });
    }

    private void startVoice() { startVoice(false); }

    private void startVoice(boolean fullMode) {
        voiceSession++;
        voiceMode = true;
        voiceFullMode = fullMode || hookVoiceMode;
        if (hookVoiceMode) beginFreshVoiceChat();
        requestVoiceAudioFocus();
        if (voiceFullMode) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        pane = 1;
        if (!hookVoiceMode) renderPane();
        showVoiceOverlay();
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) { requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, VOICE); return; }
        beginListening();
    }

    private void beginFreshVoiceChat() {
        // Assist/voice-command sessions always become their own saved chat.
        if (messages.size() > 0) saveCurrentChat();
        currentChatId = "";
        messages.clear();
        selectedFolder = "Inbox";
        webSearchChat = false;
        savedChatScrollKnown = false;
        forceAutoScrollBottom = true;
        resetMessageWindowToLatest();
    }

    @Override public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == VOICE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) beginListening();
        else if (requestCode == VOICE) updateVoiceStatus("mic permission denied");
        else if (requestCode == CONTACTS_PERM) {
            PhoneCommand cmd = pendingPhoneCommand;
            pendingPhoneCommand = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED && cmd != null) executePhoneCommand(cmd);
            else {
                updateVoiceStatus("contacts permission denied");
                setVoiceText("contacts permission needed to call or text by name");
                if (voiceFullMode && voiceReply != null) voiceReply.setVisibility(View.VISIBLE);
            }
        } else if (requestCode == CALL_PERM) {
            PhoneCommand cmd = pendingPhoneCommand;
            pendingPhoneCommand = null;
            if (cmd == null) return;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                forceDialFallback = false;
                executePhoneCommand(cmd);
            } else {
                forceDialFallback = true;
                executePhoneCommand(cmd);
            }
        }
    }

    private void beginListening() {
        if (voiceReply != null) voiceReply.setVisibility(View.GONE);
        if (voiceWaves != null) {
            voiceWaves.animate().cancel();
            voiceWaves.setAlpha(1f);
        }
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
            @Override public void onRmsChanged(float rmsdB) { setVoiceLevel(ToolText.voiceVisualFromRmsDb(rmsdB)); }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { vibrateInputEnded(); updateVoiceStatus("thinking..."); setVoiceLevel(0.05f); fadeVoiceWaves(); }
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
                updateVoiceStatus("thinking...");
                try { speechRecognizer.stopListening(); } catch (Exception ignored) { }
                ui.postDelayed(new Runnable() { @Override public void run() { if (voiceSessionActive(session) && run == voiceListenRun && voiceAwaitingSpeechResult) handleVoiceMiss(); } }, 1800);
            } else handleVoiceMiss();
        } }, 12000);
    }

    private void finishListeningNow() {
        if (!voiceMode || !voiceFullMode || !voiceAwaitingSpeechResult) return;
        vibrateInputEnded();
        updateVoiceStatus("thinking...");
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
        if (voicePhoneCommandsEnabled()) {
            PhoneCommand phone = parseVoicePhoneCommand(clean);
            if (phone != null) {
                fadeVoiceWaves();
                closeCompactVoiceOverlay();
                handleVoicePhoneCommand(phone);
                return;
            }
        }
        pendingVoiceText = clean;
        if (input != null) input.setText(clean);
        updateVoiceStatus("thinking...");
        startVoiceThinking("thinking...");
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

    private static class PhoneCommand {
        boolean textMessage;
        String contactQuery = "";
        String message = "";
    }

    private void handleVoicePhoneCommand(PhoneCommand cmd) {
        if (cmd == null) return;
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            pendingPhoneCommand = cmd;
            updateVoiceStatus("need contacts access");
            setVoiceText("allow contacts to call or text by name");
            requestPermissions(new String[]{Manifest.permission.READ_CONTACTS}, CONTACTS_PERM);
            return;
        }
        executePhoneCommand(cmd);
    }

    private void executePhoneCommand(PhoneCommand cmd) {
        stopVoiceThinking();
        String query = cmd.contactQuery == null ? "" : cmd.contactQuery.trim();
        if (query.length() == 0) {
            updateVoiceStatus(cmd.textMessage ? "who should i text?" : "who should i call?");
            setVoiceText(cmd.textMessage ? "who should i text?" : "who should i call?");
            if (voiceFullMode && voiceReply != null) voiceReply.setVisibility(View.VISIBLE);
            return;
        }
        if (cmd.textMessage && (cmd.message == null || cmd.message.trim().length() == 0)) {
            updateVoiceStatus("what should i say?");
            setVoiceText("what should the text say?");
            if (voiceFullMode && voiceReply != null) voiceReply.setVisibility(View.VISIBLE);
            return;
        }
        ContactMatch match = resolveContact(query);
        if (match == null || match.number.length() == 0) {
            updateVoiceStatus("no contact found");
            setVoiceText("couldn't find " + query + " in contacts");
            if (voiceFullMode && voiceReply != null) voiceReply.setVisibility(View.VISIBLE);
            return;
        }
        Intent launch;
        String spoken;
        if (cmd.textMessage) {
            launch = new Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:" + Uri.encode(match.number)));
            launch.putExtra("sms_body", cmd.message.trim());
            spoken = "texting " + match.name;
        } else {
            boolean canCall = !forceDialFallback && checkSelfPermission(Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED;
            if (!canCall && !forceDialFallback) {
                pendingPhoneCommand = cmd;
                updateVoiceStatus("need phone access");
                setVoiceText("allow phone permission to place the call");
                requestPermissions(new String[]{Manifest.permission.CALL_PHONE}, CALL_PERM);
                return;
            }
            forceDialFallback = false;
            if (canCall) {
                launch = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + Uri.encode(match.number)));
                spoken = "calling " + match.name;
            } else {
                launch = new Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(match.number)));
                spoken = "dialing " + match.name;
            }
        }
        speakPhoneConfirm(spoken, launch);
    }

    private void speakPhoneConfirm(String spoken, Intent launch) {
        pendingLaunchIntent = launch;
        stopAllVoiceAudio();
        updateVoiceStatus(spoken);
        setVoiceText(spoken);
        fadeVoiceWaves();
        final int session = voiceSession;
        final int safetyMs = Math.max(1100, Math.min(3500, 700 + spoken.length() * 70));
        if (tts != null && ttsReady) {
            try {
                activeTtsOwner = null;
                tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, PHONE_UTTERANCE);
                ui.postDelayed(new Runnable() { @Override public void run() {
                    if (pendingLaunchIntent != null && voiceSessionActive(session)) launchPendingPhoneIntent();
                } }, safetyMs);
                return;
            } catch (Exception ignored) { }
        }
        ui.postDelayed(new Runnable() { @Override public void run() {
            if (pendingLaunchIntent != null && voiceSessionActive(session)) launchPendingPhoneIntent();
        } }, 650);
    }

    private void launchPendingPhoneIntent() {
        Intent launch = pendingLaunchIntent;
        pendingLaunchIntent = null;
        if (launch == null) return;
        try {
            startActivity(launch);
        } catch (Exception e) {
            toast("couldn't open " + (launch.getAction() == null ? "app" : "phone"));
            updateVoiceStatus("couldn't open phone");
            if (voiceFullMode && voiceReply != null) voiceReply.setVisibility(View.VISIBLE);
            return;
        }
        stopVoiceMode();
    }

    private PhoneCommand parseVoicePhoneCommand(String text) {
        String raw = text == null ? "" : text.trim();
        if (raw.length() == 0) return null;
        String s = stripVoiceCommandFiller(raw);
        if (s.length() == 0) return null;
        String lower = s.toLowerCase(Locale.US);

        java.util.regex.Matcher textMarker = java.util.regex.Pattern.compile(
                "(?i)^(?:send\\s+(?:a\\s+)?(?:text|message|sms)(?:\\s+message)?\\s+to|text|message|sms|send)\\s+(.+)$").matcher(s);
        if (textMarker.find() || lower.startsWith("send a text") || lower.startsWith("send text") || lower.startsWith("send message") || lower.startsWith("send an sms")) {
            PhoneCommand textCmd = parseVoiceTextCommand(s);
            if (textCmd != null) return textCmd;
        }

        java.util.regex.Matcher call = java.util.regex.Pattern.compile(
                "(?i)^(?:(?:start|make|place|begin)\\s+(?:a\\s+)?(?:phone\\s+)?call\\s+(?:to\\s+|with\\s+)?|(?:phone\\s+)?call(?:\\s+up)?\\s+(?:to\\s+|with\\s+)?|phone\\s+|dial\\s+|ring\\s+)(.+?)(?:\\s+please)?[.!?]*$").matcher(s);
        if (call.find()) {
            String who = cleanContactQuery(call.group(1));
            if (who.length() == 0) return null;
            if (who.matches("(?i)^(me|you|someone|anybody|anyone)$")) return null;
            PhoneCommand cmd = new PhoneCommand();
            cmd.textMessage = false;
            cmd.contactQuery = who;
            return cmd;
        }
        return null;
    }

    private PhoneCommand parseVoiceTextCommand(String s) {
        String lower = s.toLowerCase(Locale.US);
        java.util.regex.Matcher m;
        m = java.util.regex.Pattern.compile("(?i)^(?:send\\s+(?:a\\s+)?(?:text|message|sms)(?:\\s+message)?\\s+to)\\s+(.+?)\\s+(?:saying|that|about)\\s+(.+)$").matcher(s);
        if (m.find()) return textCommand(m.group(1), m.group(2));
        m = java.util.regex.Pattern.compile("(?i)^(?:text|message|sms)\\s+(.+?)\\s+(?:saying|that|about)\\s+(.+)$").matcher(s);
        if (m.find()) return textCommand(m.group(1), m.group(2));
        m = java.util.regex.Pattern.compile("(?i)^send\\s+(.+?)\\s+a\\s+(?:text|message|sms)(?:\\s+message)?(?:\\s+(?:saying|that))?\\s+(.+)$").matcher(s);
        if (m.find()) return textCommand(m.group(1), m.group(2));
        m = java.util.regex.Pattern.compile("(?i)^(?:text|message|sms)\\s+(.+)$").matcher(s);
        if (m.find()) {
            String rest = m.group(1).trim();
            String[] parts = splitContactAndMessage(rest);
            if (parts != null) return textCommand(parts[0], parts[1]);
        }
        if (lower.startsWith("send a text to ") || lower.startsWith("send text to ") || lower.startsWith("send message to ") || lower.startsWith("send an sms to ")) {
            int to = lower.indexOf(" to ");
            String rest = s.substring(to + 4).trim();
            String[] parts = splitContactAndMessage(rest);
            if (parts != null) return textCommand(parts[0], parts[1]);
            m = java.util.regex.Pattern.compile("(?i)^(.+?)\\s+(?:saying|that|about)\\s+(.+)$").matcher(rest);
            if (m.find()) return textCommand(m.group(1), m.group(2));
        }
        return null;
    }

    private PhoneCommand textCommand(String contact, String message) {
        String who = cleanContactQuery(contact);
        String body = message == null ? "" : message.trim();
        body = body.replaceAll("(?i)^(?:that\\s+|saying\\s+)", "").trim();
        if (who.length() == 0 || body.length() == 0) return null;
        PhoneCommand cmd = new PhoneCommand();
        cmd.textMessage = true;
        cmd.contactQuery = who;
        cmd.message = body;
        return cmd;
    }

    private String[] splitContactAndMessage(String rest) {
        String s = rest == null ? "" : rest.trim();
        if (s.length() == 0) return null;
        java.util.regex.Matcher marked = java.util.regex.Pattern.compile("(?i)^(.+?)\\s+(?:saying|that|about)\\s+(.+)$").matcher(s);
        if (marked.find()) return new String[]{marked.group(1).trim(), marked.group(2).trim()};
        String[] words = s.split("\\s+");
        if (words.length < 2) return null;
        // Prefer longer contact names when contacts are available; otherwise first word + rest.
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
            for (int n = Math.min(4, words.length - 1); n >= 1; n--) {
                StringBuilder name = new StringBuilder();
                for (int i = 0; i < n; i++) {
                    if (i > 0) name.append(' ');
                    name.append(words[i]);
                }
                ContactMatch match = resolveContact(name.toString());
                if (match != null && match.score >= 70) {
                    StringBuilder body = new StringBuilder();
                    for (int i = n; i < words.length; i++) {
                        if (body.length() > 0) body.append(' ');
                        body.append(words[i]);
                    }
                    return new String[]{name.toString(), body.toString()};
                }
            }
        }
        StringBuilder body = new StringBuilder();
        for (int i = 1; i < words.length; i++) {
            if (body.length() > 0) body.append(' ');
            body.append(words[i]);
        }
        return new String[]{words[0], body.toString()};
    }

    private String stripVoiceCommandFiller(String text) {
        String s = text == null ? "" : text.trim();
        String prev;
        do {
            prev = s;
            s = s.replaceFirst("(?i)^(hey|hi|okay|ok|please|yo)[,\\s]+", "").trim();
            s = s.replaceFirst("(?i)^(can you|could you|would you|will you|please)\\s+", "").trim();
            s = s.replaceFirst("(?i)^(i want to|i need to|i'd like to|id like to|let's|lets)\\s+", "").trim();
        } while (!s.equals(prev));
        return s.replaceAll("[.!?]+$", "").trim();
    }

    private String cleanContactQuery(String raw) {
        String s = raw == null ? "" : raw.trim();
        s = s.replaceAll("(?i)^(my\\s+)?(contact\\s+)?", "");
        s = s.replaceAll("(?i)\\s+(please|now|for me)$", "");
        s = s.replaceAll("[\"']", "").trim();
        return s;
    }

    private static class ContactMatch {
        String name = "";
        String number = "";
        int score = 0;
    }

    private ContactMatch resolveContact(String query) {
        String q = query == null ? "" : query.trim();
        if (q.length() == 0) return null;
        String digits = q.replaceAll("[^0-9+]", "");
        if (digits.length() >= 7 && q.matches("(?i)^[\\d\\s()+.-]+$")) {
            ContactMatch direct = new ContactMatch();
            direct.name = q;
            direct.number = digits;
            direct.score = 100;
            return direct;
        }
        if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return null;
        ContactMatch best = null;
        android.database.Cursor c = null;
        try {
            c = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    new String[]{
                            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                            ContactsContract.CommonDataKinds.Phone.NUMBER,
                            ContactsContract.CommonDataKinds.Phone.TYPE
                    },
                    null, null, ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC");
            if (c == null) return null;
            String qKey = normalizeContactKey(q);
            while (c.moveToNext()) {
                String name = c.getString(0);
                String number = c.getString(1);
                int type = c.getInt(2);
                if (name == null || number == null) continue;
                int score = contactScore(q, qKey, name);
                if (score <= 0) continue;
                if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE) score += 3;
                else if (type == ContactsContract.CommonDataKinds.Phone.TYPE_MAIN) score += 1;
                if (best == null || score > best.score) {
                    best = new ContactMatch();
                    best.name = name.trim();
                    best.number = number.replaceAll("[^0-9+]", "");
                    best.score = score;
                }
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (c != null) try { c.close(); } catch (Exception ignored) { }
        }
        return best != null && best.score >= 45 ? best : null;
    }

    private int contactScore(String query, String qKey, String name) {
        String n = name == null ? "" : name.trim();
        if (n.length() == 0) return 0;
        String nKey = normalizeContactKey(n);
        if (nKey.length() == 0 || qKey.length() == 0) return 0;
        if (n.equalsIgnoreCase(query) || nKey.equals(qKey)) return 100;
        if (nKey.startsWith(qKey) || qKey.startsWith(nKey)) return 85;
        String[] qTokens = qKey.split(" ");
        String[] nTokens = nKey.split(" ");
        int hits = 0;
        for (String qt : qTokens) {
            if (qt.length() == 0) continue;
            boolean found = false;
            for (String nt : nTokens) if (nt.equals(qt) || nt.startsWith(qt) || qt.startsWith(nt)) { found = true; break; }
            if (found) hits++;
        }
        if (hits > 0 && hits == qTokens.length) return 75;
        if (nKey.contains(qKey)) return 55;
        if (hits > 0 && hits >= Math.max(1, qTokens.length - 1)) return 50;
        return 0;
    }

    private String normalizeContactKey(String s) {
        return (s == null ? "" : s).toLowerCase(Locale.US).replaceAll("[^a-z0-9]+", " ").replaceAll("\\s+", " ").trim();
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
            int amp = mediaRecorder.getMaxAmplitude();
            float gate = ToolText.voiceGateFromPeak(amp);
            setVoiceLevel(ToolText.voiceVisualFromPeak(amp));
            if (gate > 0.13f) recorderSpeechFrames++;
            long now = System.currentTimeMillis();
            if (now - recordingStartedAt > 12000) { stopRecorder(true); return; }
            if (now - recordingStartedAt > 1400) {
                if (gate < 0.08f) {
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
                final float gate = ToolText.voiceGateFromPeak(max);
                final float visual = ToolText.voiceVisualFromPeak(max);
                if (gate > 0.13f) recorderSpeechFrames++;
                long now = System.currentTimeMillis();
                if (now - recordingStartedAt > 12000) ui.post(new Runnable() { @Override public void run() { stopRecorder(true); } });
                if (now - recordingStartedAt > 1400) {
                    if (gate < 0.08f) {
                        if (quietSince == 0) quietSince = now;
                        if (now - quietSince > 1500) ui.post(new Runnable() { @Override public void run() { stopRecorder(true); } });
                    } else quietSince = 0;
                }
                ui.post(new Runnable() { @Override public void run() { setVoiceLevel(visual); } });
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
        updateVoiceStatus("thinking...");
        startVoiceThinking("thinking...");
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
            body.put("model", requestModelId(model));
            body.put("modalities", new JSONArray().put("text").put("audio"));
            body.put("audio", new JSONObject().put("voice", ttsVoiceForModel(model)).put("format", "pcm16"));
            JSONArray arr = new JSONArray();
            addBackgroundSystemContext(arr, true, false, false, false);
            arr.put(new JSONObject().put("role", "system").put("content", "This is a spoken two-way voice conversation. Reply in plain text only. Do not use markdown, headings, bullets, tables, code blocks, or formatting symbols. Keep the response natural for text-to-speech."));
            for (int i = 0; i < messages.size(); i++) {
                Msg m = messages.get(i);
                if (m == assistant || isBusyStats(m.stats) || "[voice input]".equals(m.text)) continue;
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
                stopVoiceThinking();
                assistant.text = answer.length() == 0 ? "[voice response]" : answer;
                assistant.stats = stats;
                assistant.streamDone = true;
                forceAutoScrollBottom = true;
                // Always persist the transcript/answer even if the voice UI session ended.
                saveCurrentChat();
                if (!voiceSessionActive(session)) return;
                assistant.ttsStarted = true;
                assistant.ttsPlaying = true;
                activeTtsOwner = assistant;
                renderMessages();
                renderVoiceConversation();
                updateVoiceStatus("speaking");
                playPcm16Audio(spoken, assistant);
            } });
        } catch (ModelRefusalException e) {
            final String msg = detailedError(e);
            final int session = voiceSession;
            runOnUiThread(new Runnable() { @Override public void run() {
                assistant.stats = "";
                assistant.text = "model refused\n" + msg;
                assistant.streamDone = true;
                saveCurrentChat();
                if (!voiceSessionActive(session)) return;
                pauseVoiceAfterProviderFailure("model refused", assistant.text);
                renderMessages();
            } });
        } catch (Exception e) {
            final String msg = detailedError(e);
            final int session = voiceSession;
            runOnUiThread(new Runnable() { @Override public void run() {
                assistant.stats = "";
                assistant.text = "multimodal failed\n" + msg;
                assistant.streamDone = true;
                saveCurrentChat();
                if (!voiceSessionActive(session)) return;
                pauseVoiceAfterProviderFailure("failed", assistant.text);
                renderMessages();
            } });
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
        if (content instanceof String) return stripReasoningTags((String) content);
        if (content instanceof JSONArray) {
            StringBuilder b = new StringBuilder();
            JSONArray arr = (JSONArray) content;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject part = arr.optJSONObject(i);
                if (part == null) continue;
                String text = stripReasoningTags(part.optString("text", part.optString("content", "")));
                if (text.length() > 0) { if (b.length() > 0) b.append('\n'); b.append(text); }
            }
            if (b.length() > 0) return b.toString().trim();
        }
        JSONObject audio = msg.optJSONObject("audio");
        return audio == null ? "" : audio.optString("transcript", "").trim();
    }

    private String extractMessageReasoning(JSONObject resp) throws Exception {
        JSONArray choices = resp.optJSONArray("choices");
        if (choices == null || choices.length() == 0) return "";
        JSONObject msg = choices.getJSONObject(0).optJSONObject("message");
        if (msg == null) return "";
        StringBuilder out = new StringBuilder(reasoningDelta(msg));
        Object content = msg.opt("content");
        if (content instanceof String) out.append(reasoningFromText((String) content));
        else if (content instanceof JSONArray) {
            JSONArray arr = (JSONArray) content;
            for (int i = 0; i < arr.length(); i++) {
                JSONObject part = arr.optJSONObject(i);
                if (part != null) out.append(reasoningFromText(part.optString("text", part.optString("content", ""))));
            }
        }
        return out.toString().trim();
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
        body.put("model", requestModelId(model));
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
        String auth = authKeyForUrl(url);
        if (auth.length() > 0) c.setRequestProperty("Authorization", "Bearer " + auth);
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
        box.addView(voiceStatus, new LinearLayout.LayoutParams(-1, full ? dp(48) : dp(28)));
        box.addView(voiceWaves, new LinearLayout.LayoutParams(-1, full ? dp(52) : dp(64)));
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
        saveCurrentChat();
        voiceSession++;
        voiceAwaitingSpeechResult = false;
        voiceMode = false;
        voiceFullMode = false;
        pendingLaunchIntent = null;
        pendingPhoneCommand = null;
        forceDialFallback = false;
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
            m.ttsPrefetching = false;
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
        return voiceMode && voiceFullMode && activeTtsOwner != null && (activeTtsOwner.ttsPlaying || activeTtsOwner.ttsRequested || activeTtsOwner.ttsPrefetching || activeTtsOwner.ttsQueue.size() > 0);
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
            if (m.text.length() == 0 || isBusyStats(m.stats) && ".".equals(m.text)) continue;
            if (b.length() > 0) b.append("\n\n");
            b.append(m.role.equals("user") ? "you" : "assistant");
            if (m.role.equals("assistant") && m.streamDone && m.memorySaved) b.append("\nmemory saved");
            b.append("\n").append(m.text);
        }
        setVoiceText(b.toString());
    }

    private void startVoiceThinking() { startVoiceThinking("thinking..."); }

    private void startVoiceThinking(String word) {
        if (!voiceFullMode || voiceStatus == null) return;
        voiceThinkingWord = ToolText.ensureEllipsis(word == null || word.length() == 0 ? "thinking" : word);
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
        body.put("model", requestModelId(model));
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
        String key = authKeyForUrl(endpoint);
        if (key.length() > 0) c.setRequestProperty("Authorization", "Bearer " + key);
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
        body.put("model", requestModelId(model));
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
        body.put("model", requestModelId(model));
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
        if (activeTtsOwner != null && (activeTtsOwner.ttsRequested || activeTtsOwner.ttsPrefetching || activeTtsOwner.ttsPlaying || activeTtsOwner.ttsQueue.size() > 0)) {
            ui.postDelayed(new Runnable() { @Override public void run() { finishVoiceResponse(); } }, 250);
            return;
        }
        activeTtsOwner = null;
        if (voiceMode && voiceFullMode && prefs.getBoolean("voiceLoop", true)) beginListening();
        else updateVoiceStatus("done");
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null) return;
        if (req == VOICE) {
            ArrayList<String> r = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            if (r != null && r.size() > 0 && input != null) input.setText(r.get(0));
            return;
        }
        if (req == PICK_IMAGE) {
            ClipData clip = data.getClipData();
            if (clip != null && clip.getItemCount() > 0) {
                for (int i = 0; i < clip.getItemCount(); i++) {
                    Uri cu = clip.getItemAt(i).getUri();
                    if (cu != null) attachUri(cu, false);
                }
                updateAttachChip();
                updateComposerAction();
                toast(pendingImages.size() == 1 ? "image attached" : ("image attached (" + pendingImages.size() + ")"));
                return;
            }
            Uri u = data.getData();
            if (u != null) attachUri(u, true);
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
        switchToPane(0);
        paneReady[0] = false;
        chatsBoundProject = "\u0001";
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
        box.addView(panelTitle(c.title.length() == 0 ? "chat" : c.title));
        TextView rename = panelItem("rename", "");
        TextView move = panelItem("move to project", "");
        TextView remove = panelItem("remove from project", "");
        TextView delete = panelItem("delete", "");
        TextView cancel = panelAction("cancel");
        rename.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); renameChat(c); } });
        move.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); moveChat(c); } });
        remove.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { c.folder = "Inbox"; touchChat(c); saveState(); d.dismiss(); showChatsPane(); } });
        delete.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); confirmDeleteChat(c); } });
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(rename);
        box.addView(move);
        if (!"Inbox".equals(c.folder)) box.addView(remove);
        box.addView(delete);
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
        remove.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { for (Chat c : chats) if (selectedChats.contains(c.id)) { c.folder = "Inbox"; touchChat(c); } selectedChats.clear(); saveState(); d.dismiss(); showChatsPane(); } });
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
                for (Chat c : chats) if (selectedChats.contains(c.id)) { c.folder = f; touchChat(c); }
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
        save.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { c.title = e.getText().toString().trim(); c.titleGenerated = true; saveState(); d.dismiss(); showChatsPane(); } });
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
            item.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { c.folder = f; touchChat(c); saveState(); d.dismiss(); showChatsPane(); } });
            box.addView(item);
        }
        TextView cancel = panelAction("cancel");
        cancel.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { d.dismiss(); } });
        box.addView(cancel, new LinearLayout.LayoutParams(-1, dp(52)));
        showPanel(d, box);
    }

    private void newChat() { saveCurrentChat(); currentChatId = ""; messages.clear(); selectedFolder = projectView.length() > 0 ? projectView : "Inbox"; webSearchChat = false; savedChatScrollKnown = false; forceAutoScrollBottom = false; resetMessageWindowToLatest(); chatBoundId = "\u0001"; if (messageList != null) renderMessages(); }
    private void loadChat(Chat c) { currentChatId = c.id; selectedFolder = c.folder; if (!"Inbox".equals(c.folder)) projectView = c.folder; messages.clear(); messages.addAll(c.messages); webSearchChat = c.webSearch; if (c.model.length() > 0) prefs.edit().putString("model", c.model).putBoolean("modelSelected", true).apply(); savedChatScrollKnown = false; forceAutoScrollBottom = true; userAtChatBottom = true; resetMessageWindowToLatest(); }
    private void saveCurrentChat() {
        if (!syncCurrentChatInMemory()) return;
        persistChatStore(true);
    }

    private void saveCurrentChatDeferred() {
        if (!syncCurrentChatInMemory()) return;
        persistChatStore(false);
    }

    private boolean syncCurrentChatInMemory() {
        if (messages.size() == 0) return false;
        if (currentChatId.length() == 0) currentChatId = String.valueOf(System.currentTimeMillis());
        Chat t = null;
        for (Chat c : chats) if (c.id.equals(currentChatId)) t = c;
        if (t == null) { t = new Chat(); t.id = currentChatId; chats.add(0, t); }
        else if (chats.indexOf(t) > 0) { chats.remove(t); chats.add(0, t); }
        t.updatedAt = System.currentTimeMillis();
        t.folder = selectedFolder;
        if (t.title.length() == 0 || "[voice input]".equals(t.title)) t.title = firstUserText();
        t.webSearch = webSearchChat;
        t.model = chatModelForSave();
        t.messages.clear();
        t.messages.addAll(messages);
        t.listPreview = null;
        t.listRecency = 0;
        return true;
    }

    private boolean chatWorkInFlight() {
        if (voiceMode) return true;
        for (Msg m : messages) if (isBusyStats(m.stats)) return true;
        return false;
    }

    private void reloadChatStorePreservingCurrent() {
        if (hookVoiceMode) return;
        String keepId = currentChatId;
        ArrayList<Msg> localCopy = null;
        String localFolder = selectedFolder;
        boolean localWeb = webSearchChat;
        boolean preserveLocal = keepId.length() > 0 && messages.size() > 0;
        if (preserveLocal) {
            localCopy = new ArrayList<Msg>(messages);
        }
        loadChatStore();
        if (keepId.length() == 0) {
            if (localCopy != null) {
                messages.clear();
                messages.addAll(localCopy);
            }
            return;
        }
        Chat keep = null;
        for (Chat c : chats) if (c.id.equals(keepId)) { keep = c; break; }
        if (preserveLocal && localCopy != null) {
            if (keep == null) {
                keep = new Chat();
                keep.id = keepId;
                chats.add(0, keep);
            }
            keep.folder = localFolder;
            keep.webSearch = localWeb;
            keep.messages.clear();
            keep.messages.addAll(localCopy);
            messages.clear();
            messages.addAll(localCopy);
            selectedFolder = localFolder;
            webSearchChat = localWeb;
            if (keep.title.length() == 0 || "[voice input]".equals(keep.title)) keep.title = firstUserText();
        } else if (keep != null) {
            messages.clear();
            messages.addAll(keep.messages);
            selectedFolder = keep.folder;
            webSearchChat = keep.webSearch;
        } else {
            currentChatId = "";
            messages.clear();
        }
        if (pane == 0 && chatList != null) renderChatList();
        else paneReady[0] = false;
        chatBoundId = "\u0001";
        if (!chatWorkInFlight() && pane == 1 && messageList != null) {
            resetMessageWindowToLatest();
            renderMessages();
            chatBoundId = currentChatId == null ? "" : currentChatId;
        }
    }
    private String chatModelForSave() { for (int i = messages.size() - 1; i >= 0; i--) if (messages.get(i).role.equals("assistant") && messages.get(i).model.length() > 0) return expandShortModel(messages.get(i).model); return activeAnswerModel(); }
    private String expandShortModel(String label) {
        return ToolText.resolveModelKey(label, myModels, models, selectedModel(), savedApiKey().length() > 0, soleCustomEndpoint());
    }

    private void attachClipboardImageIfPresent() {
        if (pendingImages.size() > 0) return;
        ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
        if (cm == null || !cm.hasPrimaryClip()) return;
        ClipData clip = cm.getPrimaryClip(); if (clip == null || clip.getItemCount() == 0) return;
        Uri u = clip.getItemAt(0).getUri(); if (u != null) attachUri(u, true);
    }
    private String clipboardText() { try { ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); if (cm == null || !cm.hasPrimaryClip()) return ""; ClipData clip = cm.getPrimaryClip(); if (clip == null || clip.getItemCount() == 0) return ""; CharSequence text = clip.getItemAt(0).coerceToText(this); return text == null ? "" : text.toString().trim(); } catch (Exception e) { return ""; } }
    private void copyText(String s) { ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE); if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("message", s)); }

    private boolean composerHasOutgoing() {
        boolean hasText = input != null && input.getText() != null && input.getText().toString().trim().length() > 0;
        return hasText || pendingImages.size() > 0;
    }

    private void updateComposerAction() {
        if (composerAction == null) return;
        composerAction.setImageResource(composerHasOutgoing() ? R.drawable.ic_send_up : R.drawable.ic_mic);
    }

    private void hideSlashSuggestions() {
        if (slashSuggestRow == null) return;
        slashSuggestRow.removeAllViews();
        slashSuggestRow.setVisibility(View.GONE);
        lastSlashPaletteKey = "";
    }

    private void updateSlashSuggestions(String raw) {
        if (slashSuggestRow == null) return;
        ArrayList<ToolText.SlashCommand> matches = ToolText.filterSlashCommands(raw);
        String key = raw == null ? "" : raw;
        if (matches.size() == 0) {
            if (lastSlashPaletteKey.length() > 0 || slashSuggestRow.getVisibility() == View.VISIBLE) {
                hideSlashSuggestions();
            }
            return;
        }
        StringBuilder kb = new StringBuilder();
        for (int i = 0; i < matches.size(); i++) kb.append(matches.get(i).name).append('|');
        kb.append('#').append(key);
        String paletteKey = kb.toString();
        if (paletteKey.equals(lastSlashPaletteKey) && slashSuggestRow.getVisibility() == View.VISIBLE) return;
        lastSlashPaletteKey = paletteKey;
        slashSuggestRow.removeAllViews();
        slashSuggestRow.setVisibility(View.VISIBLE);
        int shown = Math.min(6, matches.size());
        int nameCol = dp(96);
        for (int i = 0; i < shown; i++) {
            final ToolText.SlashCommand cmd = matches.get(i);
            LinearLayout row = row();
            row.setPadding(0, dp(7), 0, dp(7));
            TextView name = text(cmd.paletteName(), 13, Color.WHITE);
            name.setTypeface(Typeface.MONOSPACE);
            name.setIncludeFontPadding(false);
            name.setMinWidth(nameCol);
            String descText = cmd.description;
            if (cmd.takesArgs && cmd.hint.length() > 0) descText = cmd.description + "  " + cmd.hint;
            TextView desc = text(descText, 12, Color.rgb(135, 135, 135));
            desc.setIncludeFontPadding(false);
            desc.setPadding(dp(8), 0, 0, 0);
            row.addView(name, new LinearLayout.LayoutParams(-2, -2));
            row.addView(desc, new LinearLayout.LayoutParams(0, -2, 1));
            row.setOnClickListener(new View.OnClickListener() {
                @Override public void onClick(View v) {
                    if (input == null) return;
                    if (cmd.takesArgs) {
                        String next = "/" + cmd.name + " ";
                        input.setText(next);
                        input.setSelection(next.length());
                        hideSlashSuggestions();
                    } else {
                        input.setText("/" + cmd.name);
                        input.setSelection(input.getText().length());
                        send();
                    }
                }
            });
            slashSuggestRow.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    /** Handle slash commands that never hit the model. */
    private boolean handleSlashCommandLocal(String text) {
        ToolText.SlashParse parsed = ToolText.parseSlash(text);
        if (parsed == null) return false;
        if ("help".equals(parsed.name)) {
            int col = ToolText.slashNameColumnChars();
            StringBuilder b = new StringBuilder();
            for (ToolText.SlashCommand c : ToolText.SLASH_COMMANDS) {
                String name = c.paletteName();
                while (name.length() < col) name = name + " ";
                b.append(name).append("  ").append(c.description);
                if (c.takesArgs && c.hint.length() > 0) b.append("  ").append(c.hint);
                b.append('\n');
            }
            messages.add(new Msg("user", text, "", "", "", "", replyQuote));
            messages.add(new Msg("assistant", b.toString().trim(), "", "", "", "help"));
            if (input != null) input.setText("");
            pendingVoiceText = ""; replyQuote = ""; updateReplyChip(); hideSlashSuggestions();
            saveCurrentChat(); resetMessageWindowToLatest(); forceAutoScrollBottom = true; renderMessages();
            return true;
        }
        if ("new".equals(parsed.name)) {
            if (input != null) input.setText("");
            pendingVoiceText = ""; replyQuote = ""; updateReplyChip(); hideSlashSuggestions();
            newChat();
            toast("new chat");
            return true;
        }
        if ("web".equals(parsed.name)) {
            webSearchChat = !webSearchChat;
            saveCurrentChat();
            if (webSearchIcon != null) {
                webSearchIcon.active = webSearchChat;
                webSearchIcon.setClickable(webSearchChat);
                webSearchIcon.invalidate();
            }
            if (input != null) input.setText("");
            pendingVoiceText = ""; hideSlashSuggestions();
            toast(webSearchChat ? "web search on" : "web search off");
            return true;
        }
        if ("memory".equals(parsed.name)) {
            return handleMemoryRecall("show memory");
        }
        return false;
    }

    /** Strip /search|/research prefixes and set force/research flags for this turn. */
    private String applySlashCommandToOutgoing(String text) {
        ToolText.SlashParse parsed = ToolText.parseSlash(text);
        if (parsed == null) return text == null ? "" : text;
        if ("search".equals(parsed.name)) {
            forceSearchThisTurn = true;
            return parsed.args.length() > 0 ? parsed.args : "";
        }
        if ("research".equals(parsed.name)) {
            forceSearchThisTurn = true;
            researchThisTurn = true;
            return parsed.args.length() > 0 ? parsed.args : "";
        }
        return text == null ? "" : text;
    }

    private void updateAttachChip() {
        if (attachText == null) return;
        int n = pendingImages.size();
        boolean show = n > 0;
        attachText.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) attachText.setText("");
        else if (n == 1) attachText.setText("image attached  x");
        else attachText.setText("image attached (" + n + ")  x");
    }

    private void clearPendingAttachment() {
        pendingImages.clear();
        updateAttachChip();
        updateComposerAction();
    }

    private void showComposerActionMenu(View anchor) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(cardBorder());
        box.setPadding(dp(14), dp(10), dp(14), dp(10));
        final PopupWindow popup = new PopupWindow(box, LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT, true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(6));

        TextView photo = text("photo", 16, Color.WHITE);
        photo.setPadding(dp(8), dp(10), dp(8), dp(10));
        photo.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { popup.dismiss(); pickImageFromGallery(); }
        });
        box.addView(photo, new LinearLayout.LayoutParams(-1, -2));

        TextView voice = text("voice assistant", 16, Color.WHITE);
        voice.setPadding(dp(8), dp(10), dp(8), dp(10));
        voice.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { popup.dismiss(); startVoice(true); }
        });
        box.addView(voice, new LinearLayout.LayoutParams(-1, -2));

        box.measure(View.MeasureSpec.makeMeasureSpec(dp(200), View.MeasureSpec.AT_MOST), View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int xOff = anchor.getWidth() - box.getMeasuredWidth();
        int yOff = -(anchor.getHeight() + box.getMeasuredHeight() + dp(6));
        popup.showAsDropDown(anchor, xOff, yOff);
    }

    private void pickImageFromGallery() {
        try {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(Intent.createChooser(intent, "photos"), PICK_IMAGE);
        } catch (Exception e) {
            toast("no photo picker");
        }
    }

    private void attachUri(Uri uri, boolean announce) {
        try {
            if (pendingImages.size() >= MAX_PENDING_IMAGES) {
                if (announce) toast("max " + MAX_PENDING_IMAGES + " images");
                return;
            }
            String type = getContentResolver().getType(uri);
            if (type == null || !type.toLowerCase(Locale.US).startsWith("image/")) {
                if (announce) toast("image only");
                return;
            }
            InputStream in = getContentResolver().openInputStream(uri);
            if (in == null) throw new RuntimeException("unreadable image");
            byte[] b;
            try { b = bytesLimited(in, 8 * 1024 * 1024); }
            finally { try { in.close(); } catch (Exception ignored) { } }
            pendingImages.add(new AttachedImage(Base64.encodeToString(b, Base64.NO_WRAP), type));
            updateAttachChip();
            updateComposerAction();
            if (announce) toast(pendingImages.size() == 1 ? "image attached" : ("image attached (" + pendingImages.size() + ")"));
        } catch (TooLargeException e) { if (announce) toast("image too large"); }
        catch (Exception e) { if (announce) toast("attach failed"); }
    }

    private void loadState() {
        if (!prefs.getBoolean("modelSelected", false)) prefs.edit().remove("model").apply();
        loadModelState();
        loadChatStore();
    }

    private void loadChatStore() {
        folders.clear();
        folderInstructions.clear();
        chats.clear();
        folders.add("Inbox");
        String raw = readChatStoreRaw();
        boolean fromFile = raw.length() > 0;
        if (!fromFile) {
            String legacyFolders = prefs.getString("folders", "");
            String legacyInstructions = prefs.getString("folderInstructions", "{}");
            String legacyChats = prefs.getString("chats", "[]");
            try {
                JSONObject migrated = new JSONObject();
                migrated.put("folders", new JSONArray());
                if (legacyFolders.length() > 0) for (String f : legacyFolders.split("\\n")) {
                    String clean = f.trim();
                    if (clean.length() > 0) migrated.getJSONArray("folders").put(clean);
                }
                migrated.put("folderInstructions", new JSONObject(legacyInstructions.length() == 0 ? "{}" : legacyInstructions));
                migrated.put("chats", new JSONArray(legacyChats.length() == 0 ? "[]" : legacyChats));
                raw = migrated.toString();
            } catch (Exception ignored) { raw = ""; }
        }
        if (raw.length() > 0) {
            try {
                JSONObject root = new JSONObject(raw);
                JSONArray savedFolders = root.optJSONArray("folders");
                if (savedFolders != null) for (int i = 0; i < savedFolders.length(); i++) {
                    String clean = savedFolders.optString(i, "").trim();
                    if (clean.length() > 0 && !folders.contains(clean)) folders.add(clean);
                }
                JSONObject instructions = root.optJSONObject("folderInstructions");
                if (instructions != null) {
                    JSONArray names = instructions.names();
                    if (names != null) for (int i = 0; i < names.length(); i++) {
                        String folder = names.getString(i);
                        String instruction = instructions.optString(folder, "").trim();
                        if (instruction.length() > 0) folderInstructions.put(folder, instruction);
                    }
                }
                JSONArray arr = root.optJSONArray("chats");
                if (arr != null) for (int i = 0; i < arr.length(); i++) {
                    JSONObject one = arr.optJSONObject(i);
                    if (one != null) chats.add(Chat.fromJson(one));
                }
            } catch (Exception ignored) { }
        }
        if (!fromFile && (chats.size() > 0 || folders.size() > 1 || folderInstructions.size() > 0)) {
            persistChatStore(true);
            prefs.edit().remove("chats").remove("folders").remove("folderInstructions").apply();
        }
    }

    private String readChatStoreRaw() {
        File file = new File(getFilesDir(), CHATS_STORE);
        if (!file.exists()) return "";
        try {
            FileInputStream in = new FileInputStream(file);
            String raw = readAll(in);
            in.close();
            return raw == null ? "" : raw.trim();
        } catch (Exception e) { return ""; }
    }

    private void loadModelState() {
        String savedEndpoints = prefs.getString("customEndpoints", ""); if (savedEndpoints.length() > 0) for (String e : savedEndpoints.split("\\n")) { String clean = normalizeEndpoint(e); if (clean.length() > 0 && !customEndpoints.contains(clean)) customEndpoints.add(clean); }
        String legacyEndpoint = normalizeEndpoint(prefs.getString("endpointBase", "")); if (legacyEndpoint.length() > 0 && !customEndpoints.contains(legacyEndpoint)) customEndpoints.add(legacyEndpoint);
        try {
            JSONObject savedEndpointKeys = new JSONObject(prefs.getString("endpointKeys", "{}"));
            JSONArray keyNames = savedEndpointKeys.names();
            if (keyNames != null) for (int i = 0; i < keyNames.length(); i++) {
                String key = keyNames.getString(i);
                String value = savedEndpointKeys.optString(key, "").trim();
                String clean = normalizeEndpoint(key);
                if (clean.length() > 0 && value.length() > 0) endpointKeys.put(clean, value);
            }
        } catch (Exception ignored) { }
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
        migrateCustomModelIdentities();
        for (String m : models) {
            if (modelSources.containsKey(m)) continue;
            modelSources.put(m, ToolText.isCustomModelKey(m) ? "custom" : "openrouter");
        }
        for (String m : models) {
            if (!"custom".equals(modelSource(m))) continue;
            String mappedEndpoint = modelEndpoints.get(m);
            if (mappedEndpoint != null && mappedEndpoint.length() > 0) continue;
            String fromKey = ToolText.customModelEndpoint(m);
            if (fromKey.length() > 0) { modelEndpoints.put(m, fromKey); continue; }
            if (soleCustomEndpoint().length() > 0) modelEndpoints.put(m, soleCustomEndpoint());
        }
        String savedMyModels = prefs.getString("myModels", ""); if (savedMyModels.length() > 0) for (String m : savedMyModels.split("\\n")) { String clean = m.trim(); if (clean.length() > 0 && !myModels.contains(clean)) myModels.add(clean); }
        String savedPins = prefs.getString("pinnedModels", ""); if (savedPins.length() > 0) for (String m : savedPins.split("\\n")) { String clean = m.trim(); if (clean.length() > 0 && !pinnedModels.contains(clean)) pinnedModels.add(clean); }
        migrateLoadedMyModelLists();
        String selected = prefs.getString("model", "").trim();
        if (prefs.getBoolean("modelSelected", false) && selected.length() > 0 && !myModels.contains(selected)) myModels.add(selected);
        applyCatalogToMyModels(null);
        for (int i = pinnedModels.size() - 1; i >= 0; i--) if (!myModels.contains(pinnedModels.get(i))) pinnedModels.remove(i);
        retargetSelectedModelIfNeeded();
        if (!prefs.getBoolean("modelIdentityV3", false)) {
            prefs.edit().putBoolean("modelIdentityV3", true).remove("modelsRefreshedAt").apply();
        }
        saveModels();
        saveMyModels();
        savePinnedModels();
        saveModelSources();
        saveModelEndpoints();
        saveModelContexts();
        saveAudioInputModels();
        saveAudioOutputModels();
        saveReasoningModels();
        saveSpeedModels();
    }
    private JSONObject folderInstructionsJson() { JSONObject o = new JSONObject(); try { for (String folder : folderInstructions.keySet()) { String instruction = folderInstructions.get(folder); if (instruction != null && instruction.trim().length() > 0 && folders.contains(folder)) o.put(folder, instruction.trim()); } } catch (Exception ignored) { } return o; }
    private String folderInstruction(String folder) { String s = folderInstructions.get(folder == null ? "" : folder); return s == null ? "" : s.trim(); }
    private void putFolderInstruction(String folder, String instruction) { String clean = instruction == null ? "" : instruction.trim(); if (clean.length() == 0) folderInstructions.remove(folder); else folderInstructions.put(folder, clean); }
    private void saveState() { persistChatStore(true); }

    private JSONObject chatStoreJson() {
        JSONObject root = new JSONObject();
        try {
            JSONArray folderArr = new JSONArray();
            for (String folder : folders) {
                String clean = folder == null ? "" : folder.trim();
                if (clean.length() > 0 && !"Inbox".equals(clean)) folderArr.put(clean);
            }
            JSONArray arr = new JSONArray();
            for (Chat c : chats) arr.put(c.toJson());
            root.put("folders", folderArr);
            root.put("folderInstructions", folderInstructionsJson());
            root.put("chats", arr);
        } catch (Exception ignored) { }
        return root;
    }

    private void persistChatStore(boolean immediate) {
        chatsDirty = true;
        if (immediate) {
            flushPendingPersist();
            return;
        }
        if (pendingPersist != null) return;
        pendingPersist = new Runnable() { @Override public void run() {
            pendingPersist = null;
            writeChatStoreIfDirty();
        } };
        ui.postDelayed(pendingPersist, PERSIST_DEBOUNCE_MS);
    }

    private void flushPendingPersist() {
        if (pendingPersist != null) {
            ui.removeCallbacks(pendingPersist);
            pendingPersist = null;
        }
        writeChatStoreIfDirty();
    }

    private final Object chatStoreWriteLock = new Object();

    private void writeChatStoreIfDirty() {
        if (!chatsDirty) return;
        chatsDirty = false;
        final JSONObject snap;
        try { snap = chatStoreJson(); }
        catch (Exception e) { chatsDirty = true; return; }
        new Thread(new Runnable() { @Override public void run() {
            final String payload;
            try { payload = snap.toString(); }
            catch (Exception e) { chatsDirty = true; return; }
            synchronized (chatStoreWriteLock) {
                try {
                    File dir = getFilesDir();
                    File tmp = new File(dir, CHATS_STORE + ".tmp");
                    File out = new File(dir, CHATS_STORE);
                    FileOutputStream fos = new FileOutputStream(tmp);
                    fos.write(payload.getBytes(StandardCharsets.UTF_8));
                    fos.getFD().sync();
                    fos.close();
                    if (!tmp.renameTo(out)) {
                        FileOutputStream direct = new FileOutputStream(out);
                        direct.write(payload.getBytes(StandardCharsets.UTF_8));
                        direct.getFD().sync();
                        direct.close();
                        tmp.delete();
                    }
                } catch (Exception ignored) {
                    chatsDirty = true;
                }
            }
        } }, "chats-store").start();
    }
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
    private void saveEndpointKeys() {
        JSONObject o = new JSONObject();
        try { for (String e : endpointKeys.keySet()) { String key = endpointKeys.get(e); if (key != null && key.length() > 0) o.put(e, key); } } catch (Exception ignored) { }
        prefs.edit().putString("endpointKeys", o.toString()).apply();
    }
    private void saveMyModels() { prefs.edit().putString("myModels", join(myModels)).apply(); }
    private void savePinnedModels() { prefs.edit().putString("pinnedModels", join(pinnedModels)).apply(); }
    private String savedApiKey() { return prefs.getString("apiKey", ""); }
    private void saveApiKey() {
        SharedPreferences.Editor e = prefs.edit();
        if (apiKey != null) e.putString("apiKey", apiKey.getText().toString().trim());
        e.apply();
    }
    private void saveJinaSettings() { if (jinaKeyInput != null) prefs.edit().putString("jinaApiKey", jinaKeyInput.getText().toString().trim()).apply(); }
    private void flushSettingsInputs() {
        saveApiKey();
        saveJinaSettings();
        if (braveKeyInput != null) prefs.edit().putString("braveApiKey", braveKeyInput.getText().toString().trim()).apply();
    }
    private void removeCustomModels() {
        for (int i = models.size() - 1; i >= 0; i--) {
            String m = models.get(i);
            if ("custom".equals(modelSource(m))) { models.remove(i); myModels.remove(m); pinnedModels.remove(m); modelContexts.remove(m); modelSources.remove(m); modelEndpoints.remove(m); }
        }
    }
    private String selectedModel() { return prefs.getBoolean("modelSelected", false) ? prefs.getString("model", "") : ""; }
    private String selectedModelSource() { return modelSource(selectedModel()); }
    private String selectedModelEndpoint() { return modelEndpoint(selectedModel()); }
    private String activeAnswerModel() { return voiceMode && voiceFullMode && voiceAnswerModel().length() > 0 ? voiceAnswerModel() : selectedModel(); }
    private String modelSource(String model) {
        if (ToolText.isCustomModelKey(model)) return "custom";
        return modelSources.containsKey(model) ? modelSources.get(model) : "openrouter";
    }
    private String modelEndpoint(String model) {
        String fromKey = ToolText.customModelEndpoint(model);
        if (fromKey.length() > 0) return fromKey;
        String endpoint = modelEndpoints.get(model);
        if (endpoint == null || endpoint.length() == 0) endpoint = modelEndpoints.get(ToolText.modelApiId(model));
        if (endpoint != null && endpoint.length() > 0) return endpoint;
        return "custom".equals(modelSource(model)) ? soleCustomEndpoint() : "";
    }
    private String modelSourceLabel(String m) { return ToolText.modelProviderLabel(m, modelSource(m)); }
    private String modelLabel() {
        String m = selectedModel();
        if (m.length() == 0) return "model";
        String name = shortModel(m);
        if ("custom".equals(selectedModelSource())) return name;
        String vendor = ToolText.modelVendor(m);
        if (vendor.length() == 0) return name;
        String lower = name.toLowerCase(Locale.US);
        if (lower.startsWith(vendor) || lower.contains(vendor)) return name;
        return vendor + " · " + name;
    }
    private String messageModelLabel(Msg m) { return m.model.length() == 0 ? "assistant" : m.model; }
    private String shortModel(String m) { return ToolText.shortModel(m); }
    private String requestModelId(String model) { return ToolText.modelApiId(model); }
    private String typedModelKey(String typed) {
        String t = typed == null ? "" : typed.trim();
        if (t.length() == 0) return t;
        if (ToolText.isCustomModelKey(t) || models.contains(t) || myModels.contains(t)) return t;
        if (soleCustomEndpoint().length() > 0) return ToolText.customModelKey(soleCustomEndpoint(), t);
        return t;
    }
    private void migrateCustomModelIdentities() {
        repairCustomIdentities();
        namespaceBareCustomModels(allModelIds());
    }
    private void migrateLoadedMyModelLists() {
        repairCustomIdentities();
        ArrayList<String> extra = new ArrayList<String>(myModels);
        extra.addAll(pinnedModels);
        namespaceBareCustomModels(extra);
    }
    private ArrayList<String> allModelIds() {
        ArrayList<String> ids = new ArrayList<String>();
        for (String m : models) if (!ids.contains(m)) ids.add(m);
        for (String m : myModels) if (!ids.contains(m)) ids.add(m);
        for (String m : pinnedModels) if (!ids.contains(m)) ids.add(m);
        for (String m : modelSources.keySet()) if (!ids.contains(m)) ids.add(m);
        for (String m : modelEndpoints.keySet()) if (!ids.contains(m)) ids.add(m);
        return ids;
    }
    private String mappedEndpointFor(String id) {
        if (id == null || id.length() == 0) return "";
        String mapped = modelEndpoints.get(id);
        if (mapped == null || mapped.length() == 0) mapped = modelEndpoints.get(ToolText.modelApiId(id));
        if (mapped == null) mapped = "";
        if (mapped.length() == 0) mapped = ToolText.customModelEndpoint(id);
        if (mapped.length() == 0 && soleCustomEndpoint().length() > 0) mapped = soleCustomEndpoint();
        return mapped;
    }
    private void repairCustomIdentities() {
        HashMap<String, String> renamed = new HashMap<String, String>();
        ArrayList<String> ids = allModelIds();
        for (String m : ids) {
            if (m == null || m.length() == 0 || renamed.containsKey(m)) continue;
            String nk = ToolText.repairCustomIdentity(m, mappedEndpointFor(m), customEndpoints);
            if (nk.length() > 0 && !nk.equals(m)) renamed.put(m, nk);
        }
        for (String from : renamed.keySet()) remapModelIdentity(from, renamed.get(from));
    }
    private void namespaceBareCustomModels(ArrayList<String> ids) {
        HashMap<String, String> renamed = new HashMap<String, String>();
        for (String m : ids) {
            if (m == null || m.length() == 0 || ToolText.isCustomModelKey(m) || renamed.containsKey(m)) continue;
            String source = modelSources.containsKey(m) ? modelSources.get(m) : modelSource(m);
            String endpoint = mappedEndpointFor(m);
            if (!shouldNamespaceAsCustom(m, source, endpoint)) continue;
            String nk = ToolText.customModelKey(endpoint, m);
            if (nk.length() > 0 && !nk.equals(m)) renamed.put(m, nk);
        }
        for (String from : renamed.keySet()) remapModelIdentity(from, renamed.get(from));
    }
    private boolean shouldNamespaceAsCustom(String id, String source, String endpoint) {
        if (id == null || id.length() == 0 || ToolText.isCustomModelKey(id)) return false;
        if (endpoint == null || endpoint.length() == 0) return false;
        if (!ToolText.isKnownEndpoint(endpoint, customEndpoints)) return false;
        if ("custom".equals(source)) return true;
        String mapped = modelEndpoints.get(id);
        return mapped != null && mapped.length() > 0;
    }
    private void retargetSelectedModelIfNeeded() {
        SharedPreferences.Editor e = prefs.edit();
        boolean dirty = false;
        String selected = prefs.getString("model", "").trim();
        if (selected.length() > 0 && "openrouter".equals(modelSource(selected)) && savedApiKey().length() == 0) {
            String alt = customModelWithSlug(shortModel(selected));
            if (alt.length() > 0) {
                e.putString("model", alt);
                if (!myModels.contains(alt)) myModels.add(alt);
                dirty = true;
            }
        }
        String voiceAns = prefs.getString("voiceAnswerModel", "").trim();
        if (voiceAns.length() > 0 && "openrouter".equals(modelSource(voiceAns)) && savedApiKey().length() == 0) {
            String alt = customModelWithSlug(shortModel(voiceAns));
            if (alt.length() > 0) {
                e.putString("voiceAnswerModel", alt);
                dirty = true;
            }
        }
        if (dirty) e.apply();
    }
    private String customModelWithSlug(String slug) {
        String want = slug == null ? "" : slug.trim();
        if (want.length() == 0) return "";
        for (String m : myModels) if ("custom".equals(modelSource(m)) && shortModel(m).equals(want)) return m;
        for (String m : models) if ("custom".equals(modelSource(m)) && shortModel(m).equals(want)) return m;
        return "";
    }
    private void remapModelIdentity(String from, String to) {
        if (from == null || to == null || from.equals(to) || to.length() == 0) return;
        replaceInModelList(models, from, to);
        replaceInModelList(myModels, from, to);
        replaceInModelList(pinnedModels, from, to);
        remapModelMap(modelContexts, from, to);
        remapModelMap(modelSources, from, to);
        remapModelMap(modelEndpoints, from, to);
        remapModelMap(modelSearchText, from, to);
        if (audioInputModels.remove(from)) audioInputModels.add(to);
        if (audioOutputModels.remove(from)) audioOutputModels.add(to);
        if (reasoningModels.remove(from)) reasoningModels.add(to);
        if (speedModels.remove(from)) speedModels.add(to);
        if (ToolText.isCustomModelKey(to)) {
            modelSources.put(to, "custom");
            String ep = ToolText.customModelEndpoint(to);
            if (ep.length() > 0 && !modelEndpoints.containsKey(to)) modelEndpoints.put(to, ep);
        }
        if (from.equals(prefs.getString("model", ""))) prefs.edit().putString("model", to).apply();
        if (from.equals(prefs.getString("voiceAnswerModel", ""))) prefs.edit().putString("voiceAnswerModel", to).apply();
        if (from.equals(prefs.getString("voiceTranscribeModel", ""))) prefs.edit().putString("voiceTranscribeModel", to).apply();
        if (from.equals(prefs.getString("voiceTtsModel", ""))) prefs.edit().putString("voiceTtsModel", to).apply();
        if (from.equals(prefs.getString("voiceEndpointTtsModel", ""))) prefs.edit().putString("voiceEndpointTtsModel", to).apply();
    }
    private void replaceInModelList(ArrayList<String> list, String from, String to) {
        for (int i = 0; i < list.size(); i++) {
            if (!from.equals(list.get(i))) continue;
            if (list.contains(to)) list.remove(i--);
            else list.set(i, to);
        }
    }
    private <T> void remapModelMap(HashMap<String, T> map, String from, String to) {
        if (!map.containsKey(from)) return;
        T v = map.remove(from);
        if (!map.containsKey(to)) map.put(to, v);
    }
    private String thoughtDuration(long ms) { long s = Math.max(1, Math.round(ms / 1000f)); long m = s / 60; long r = s % 60; return m > 0 ? m + " minutes and " + r + " seconds" : s + " seconds"; }
    private int estimateTokens(String s) { return ToolText.estimateTokens(s); }
    private String requestText(Msg m) {
        String t = m.text == null ? "" : m.text;
        if ("assistant".equals(m.role)) t = sanitizeAssistantText(t);
        if ("user".equals(m.role) && m.replyQuote != null && m.replyQuote.length() > 0) {
            return "In reply to: \"" + m.replyQuote + "\"\n\n" + t;
        }
        return t;
    }
    private int messageTokens() {
        int key = messages.size() * 997;
        if (messages.size() > 0) {
            Msg last = messages.get(messages.size() - 1);
            key += (last.text == null ? 0 : last.text.length()) + last.promptTokens + last.toolTokens;
            if (last.reasoning != null) key += last.reasoning.length();
        }
        if (key == messageTokKey) return messageTokCached;
        int t = 0;
        for (Msg m : messages) {
            if (isBusyStats(m.stats)) continue;
            t += 14 + estimateTokens(m.role) + estimateTokens(requestText(m)) + estimateTokens(m.replyQuote)
                    + (m.reasoning.length() == 0 ? 0 : estimateTokens(m.reasoning))
                    + requestImages(m).size() * 1200;
        }
        messageTokKey = key;
        messageTokCached = t;
        return t;
    }
    private int backgroundContextTokens() {
        String folder = selectedFolder == null ? "" : selectedFolder;
        int key = (webSearchAvailable() ? 1 : 0) + (memoryEnabled() ? 2 : 0);
        key = 31 * key + folder.hashCode();
        key = 31 * key + folderInstruction(folder).length();
        key = 31 * key + memoryMd().length();
        if (key == bgTokKey) return bgTokCached;
        int t = estimateTokens(buildCurrentTimeContext());
        try {
            String toolMemory = buildToolMemoryContext();
            if (toolMemory.length() > 0) t += estimateTokens(toolMemory);
            String folderCtx = buildFolderInstructionContext();
            if (folderCtx.length() > 0) t += estimateTokens(folderCtx);
            String mem = buildUserMemoryContext();
            if (mem.length() > 0) t += estimateTokens(mem);
        } catch (Exception ignored) { }
        boolean search = webSearchAvailable();
        boolean memory = memoryEnabled();
        if (search || memory) t += estimateTokens(AgentTools.leanToolsPrompt(search, memory, false, false));
        if (search) t += 120;
        t += 24;
        bgTokKey = key;
        bgTokCached = t;
        return t;
    }
    private Msg lastAssistantMessage() {
        if (messages.size() == 0) return null;
        Msg last = messages.get(messages.size() - 1);
        return last != null && "assistant".equals(last.role) ? last : null;
    }
    private int lastPromptTokens() {
        Msg m = lastAssistantMessage();
        return m == null ? 0 : m.promptTokens;
    }
    private int lastToolTokens() {
        Msg m = lastAssistantMessage();
        return m == null ? 0 : m.toolTokens;
    }
    private int contextTokens() {
        int live = messageTokens() + backgroundContextTokens() + lastToolTokens();
        int prompt = lastPromptTokens();
        return prompt > live ? prompt : live;
    }
    private int contextMaxTokens() { Integer max = modelContexts.get(selectedModel()); return max == null ? 0 : max; }
    private float contextPercent() { int max = contextMaxTokens(); return max <= 0 ? 0f : Math.min(100f, (float) (contextTokens() * 100.0 / max)); }
    private String contextPercentText() { return String.format(Locale.US, "%.1f%%", contextPercent()); }
    private void showContext() {
        int used = contextTokens();
        int max = contextMaxTokens();
        int chat = messageTokens();
        int sys = backgroundContextTokens();
        int tools = lastToolTokens();
        int prompt = lastPromptTokens();
        String cap = max <= 0 ? "?" : shortTokens(max);
        StringBuilder b = new StringBuilder();
        b.append(shortTokens(used)).append("/").append(cap);
        b.append("  chat ").append(shortTokens(chat));
        if (sys > 0) b.append("  sys ").append(shortTokens(sys));
        if (tools > 0) b.append("  tools ").append(shortTokens(tools));
        if (prompt > chat + sys) b.append("  req ").append(shortTokens(prompt));
        if (max <= 0) b.append("  refresh model catalog");
        toast(b.toString());
    }
    private void applyPromptTokens(final Msg assistant, int n) {
        if (assistant == null || n <= 0) return;
        if (n > assistant.promptTokens) assistant.promptTokens = n;
        refreshContextMeter();
    }
    private void rememberPromptTokens(final Msg assistant, JSONArray arr, JSONArray tools) {
        applyPromptTokens(assistant, ToolText.estimateRequestTokens(arr, tools));
    }
    private void addToolTokens(Msg assistant, String payload) {
        if (assistant == null) return;
        int n = estimateTokens(payload);
        if (n > 0) assistant.toolTokens += n;
        refreshContextMeter();
    }
    private void refreshContextMeter() {
        runOnUiThread(new Runnable() { @Override public void run() {
            if (meter != null) { meter.percent = contextPercent(); meter.invalidate(); }
            if (contextText != null) contextText.setText(contextPercentText());
        } });
    }
    private int estimateRequestTokens(JSONArray arr, JSONArray tools) {
        return ToolText.estimateRequestTokens(arr, tools);
    }
    private String shortTokens(int n) { return n >= 1000 ? Math.round(n / 1000.0) + "k" : String.valueOf(n); }
    private String friendlyError(Exception e) { String msg = e.getMessage(); if (msg == null || msg.length() == 0) msg = e.getClass().getSimpleName(); msg = msg.replace('\n', ' ').trim(); return msg.length() > 120 ? msg.substring(0, 120) : msg; }
    private String detailedError(Exception e) { String msg = e.getMessage(); if (msg == null || msg.length() == 0) msg = e.getClass().getSimpleName(); msg = msg.replace('\n', ' ').trim(); return msg.length() > 700 ? msg.substring(0, 700) : msg; }
    private String firstUserText() {
        for (Msg m : messages) {
            if (!"user".equals(m.role) || m.text == null || m.text.length() == 0) continue;
            if ("[voice input]".equals(m.text)) continue;
            return m.text.length() > 36 ? m.text.substring(0, 36) : m.text;
        }
        for (Msg m : messages) {
            if (!"assistant".equals(m.role) || m.text == null || m.text.length() == 0 || isBusyStats(m.stats)) continue;
            String t = m.text.replace('\n', ' ').trim();
            if (t.length() == 0) continue;
            return t.length() > 36 ? t.substring(0, 36) : t;
        }
        for (Msg m : messages) if ("user".equals(m.role) && "[voice input]".equals(m.text)) return "Voice chat";
        return "Image chat";
    }
    private String join(ArrayList<String> xs) { StringBuilder b = new StringBuilder(); for (String x : xs) { String clean = x == null ? "" : x.trim(); if (clean.length() > 0) b.append(clean).append('\n'); } return b.toString(); }
    private String readAll(InputStream in) throws Exception { if (in == null) return ""; return new String(bytes(in), StandardCharsets.UTF_8); }
    private byte[] bytes(InputStream in) throws Exception { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n; while ((n = in.read(buf)) >= 0) out.write(buf, 0, n); return out.toByteArray(); }
    private byte[] bytesLimited(InputStream in, int max) throws Exception { ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[8192]; int n, total = 0; while ((n = in.read(buf)) >= 0) { total += n; if (total > max) throw new TooLargeException(); out.write(buf, 0, n); } return out.toByteArray(); }
    private static class TooLargeException extends Exception { }
    private int dp(int v) { return Math.max(1, Math.round(v * uiScale())); }
    private float uiScale() { return getResources().getDisplayMetrics().widthPixels / BASE_WIDTH_DP; }
    private int fontOffset() { return prefs == null ? -1 : prefs.getInt("fontOffset", -1); }
    private void setFontOffset(int offset) {
        prefs.edit().putInt("fontOffset", Math.max(-4, Math.min(4, offset))).apply();
        paneReady[0] = false;
        paneReady[1] = false;
        paneReady[2] = false;
    }
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
    private void hideKeyboard() {
        View focused = getCurrentFocus();
        if (!(focused instanceof EditText)) return;
        hideKeyboardFrom(focused);
    }
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
        String p = primary == null ? "" : primary;
        String s = secondary == null ? "" : secondary;
        TextView v = text("", 16, Color.WHITE);
        if (s.length() == 0) v.setText(p);
        else {
            SpannableStringBuilder b = new SpannableStringBuilder();
            b.append(p);
            b.append('\n');
            int start = b.length();
            b.append(s);
            b.setSpan(new RelativeSizeSpan(0.7f), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            b.setSpan(new ForegroundColorSpan(Color.rgb(135, 135, 135)), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            v.setText(b);
        }
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(12), dp(10), dp(12), dp(10));
        v.setMinHeight(dp(52));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(Color.BLACK);
        bg.setStroke(1, Color.rgb(34, 34, 34));
        bg.setCornerRadius(dp(3));
        v.setBackground(bg);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = dp(6);
        v.setLayoutParams(lp);
        return v;
    }

    private TextView panelAction(String s) {
        TextView v = text(s, 16, Color.LTGRAY);
        v.setGravity(Gravity.CENTER);
        return v;
    }

    private void slidePaneIn() {
        if (root == null) return;
        root.animate().cancel();
        if (paneSlide == 0) {
            root.setAlpha(1f);
            root.setTranslationX(0);
            return;
        }
        float from = paneSlide < 0 ? dp(10) : -dp(10);
        paneSlide = 0;
        root.setAlpha(0.82f);
        root.setTranslationX(from);
        root.animate().alpha(1f).translationX(0).setDuration(90).setInterpolator(new DecelerateInterpolator()).withLayer().start();
    }

    private void bindPress(final View v) {
        if (v == null) return;
        v.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View view, MotionEvent e) {
                int action = e.getAction();
                if (action == MotionEvent.ACTION_DOWN) view.animate().alpha(0.55f).setDuration(32).start();
                else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) view.animate().alpha(1f).setDuration(70).start();
                return false;
            }
        });
    }

    private GradientDrawable listCardBg(boolean selected, boolean current, int index) {
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(index % 2 == 0 ? Color.BLACK : Color.rgb(16, 16, 16));
        if (selected) bg.setStroke(2, Color.WHITE);
        else bg.setStroke(1, current ? Color.rgb(88, 88, 88) : Color.rgb(34, 34, 34));
        bg.setCornerRadius(dp(3));
        return bg;
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
    private android.graphics.drawable.Drawable userMessageBorder() {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(Color.BLACK);
        g.setStroke(1, Color.rgb(72, 72, 72));
        return g;
    }

    private LinearLayout.LayoutParams userMessageBlockParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.setMargins(0, dp(10), 0, dp(4));
        return lp;
    }

    private View userMessageBlock(TextView body, Msg m) {
        // Fieldset-style user bubble: light outline with "you" sitting on the top stroke.
        FrameLayout wrap = new FrameLayout(this);
        wrap.setClipChildren(false);
        wrap.setClipToPadding(false);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setBackground(userMessageBorder());
        box.setPadding(dp(10), dp(12), dp(10), dp(8));
        if (messageImageCount(m) > 0) {
            LinearLayout.LayoutParams imageLp = new LinearLayout.LayoutParams(-1, -2);
            if (body != null) imageLp.bottomMargin = dp(8);
            box.addView(messageImageRow(m), imageLp);
        }
        if (body != null) {
            body.setPadding(0, 0, 0, 0);
            box.addView(body, new LinearLayout.LayoutParams(-1, -2));
        }

        TextView you = text("you", 11, Color.LTGRAY);
        you.setBackgroundColor(Color.BLACK);
        you.setIncludeFontPadding(false);
        you.setPadding(dp(6), 0, dp(6), 0);
        you.setSingleLine(true);
        you.setGravity(Gravity.CENTER_VERTICAL);

        // Leave room so the top border runs through the vertical center of "you".
        int labelHalf = dp(7);
        FrameLayout.LayoutParams boxLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT);
        boxLp.topMargin = labelHalf;
        wrap.addView(box, boxLp);

        FrameLayout.LayoutParams youLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.LEFT | Gravity.TOP);
        youLp.leftMargin = dp(10);
        wrap.addView(you, youLp);
        return wrap;
    }

    private LinearLayout messageImageRow(final Msg m) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(Color.TRANSPARENT);
        int count = messageImageCount(m);
        for (int i = 0; i < count; i++) {
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(64), dp(64));
            if (i > 0) lp.leftMargin = dp(8);
            row.addView(messageImageThumb(m, i), lp);
        }
        return row;
    }

    private ImageView messageImageThumb(final Msg m, final int index) {
        int size = dp(64);
        ImageView iv = new ImageView(this);
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setBackgroundColor(Color.rgb(28, 28, 28));
        Bitmap thumb = ensureMessageThumb(m, index, size * 2);
        if (thumb != null) iv.setImageBitmap(thumb);
        GradientDrawable frame = new GradientDrawable();
        frame.setColor(Color.rgb(28, 28, 28));
        frame.setStroke(1, Color.rgb(72, 72, 72));
        iv.setBackground(frame);
        iv.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { showImagePreview(m, index); }
        });
        iv.setOnLongClickListener(new View.OnLongClickListener() {
            @Override public boolean onLongClick(View v) { showMessageActions(m); return true; }
        });
        return iv;
    }

    private Bitmap ensureMessageThumb(Msg m, int index, int maxEdge) {
        AttachedImage img = messageImageAt(m, index);
        if (img == null || img.base64 == null || img.base64.length() == 0) return null;
        if (img.thumb != null && !img.thumb.isRecycled()) return img.thumb;
        img.thumb = decodeMessageBitmap(img.base64, maxEdge);
        return img.thumb;
    }

    private AttachedImage messageImageAt(Msg m, int index) {
        if (m == null) return null;
        m.ensureImagesFromLegacy();
        if (index < 0 || index >= m.images.size()) return null;
        return m.images.get(index);
    }

    private Bitmap decodeMessageBitmap(String b64, int maxEdge) {
        try {
            byte[] data = Base64.decode(b64, Base64.DEFAULT);
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            BitmapFactory.decodeByteArray(data, 0, data.length, bounds);
            int sample = 1;
            int largest = Math.max(bounds.outWidth, bounds.outHeight);
            if (largest <= 0) largest = maxEdge;
            while (largest / sample > maxEdge) sample *= 2;
            BitmapFactory.Options opts = new BitmapFactory.Options();
            opts.inSampleSize = Math.max(1, sample);
            opts.inPreferredConfig = Bitmap.Config.RGB_565;
            return BitmapFactory.decodeByteArray(data, 0, data.length, opts);
        } catch (Exception e) {
            return null;
        }
    }

    private void showImagePreview(Msg m) { showImagePreview(m, 0); }

    private void showImagePreview(Msg m, int index) {
        AttachedImage attached = messageImageAt(m, index);
        if (attached == null || attached.base64 == null || attached.base64.length() == 0 || screen == null) return;
        dismissImagePreview();
        int maxEdge = Math.max(getResources().getDisplayMetrics().widthPixels, getResources().getDisplayMetrics().heightPixels);
        final Bitmap full = decodeMessageBitmap(attached.base64, maxEdge);
        if (full == null) { toast("couldn't open photo"); return; }

        final FrameLayout overlay = new FrameLayout(this);
        overlay.setClickable(true);
        // Frosted dim chrome around the photo; tap outside the image to dismiss.
        overlay.setBackgroundColor(Color.argb(198, 0, 0, 0));

        ImageView photo = new ImageView(this);
        photo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        photo.setImageBitmap(full);
        photo.setClickable(true);
        // Consume taps on the photo itself so only frosted chrome dismisses.
        photo.setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { } });

        int padH = dp(26);
        int padTop = dp(52);
        int padBottom = dp(36);
        FrameLayout.LayoutParams imgLp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT,
                Gravity.CENTER);
        imgLp.setMargins(padH, padTop, padH, padBottom);
        overlay.addView(photo, imgLp);

        overlay.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { dismissImagePreview(); }
        });

        photoPreviewOverlay = overlay;
        screen.addView(overlay, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void dismissImagePreview() {
        if (photoPreviewOverlay == null) return;
        removeScreenChild(photoPreviewOverlay);
        photoPreviewOverlay = null;
    }

    public class MicButton extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); public MicButton(Context c) { super(c); setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { startVoice(); } }); } @Override protected void onDraw(Canvas c) { p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setStrokeCap(Paint.Cap.ROUND); float cx=getWidth()/2f, cy=getHeight()/2f; c.drawRoundRect(cx-dp(4), cy-dp(10), cx+dp(4), cy+dp(4), dp(4), dp(4), p); c.drawLine(cx-dp(10), cy-dp(2), cx-dp(10), cy+dp(3), p); c.drawArc(cx-dp(10), cy-dp(2), cx+dp(10), cy+dp(16), 0, 180, false, p); c.drawLine(cx+dp(10), cy-dp(2), cx+dp(10), cy+dp(3), p); c.drawLine(cx, cy+dp(15), cx, cy+dp(20), p); c.drawLine(cx-dp(6), cy+dp(20), cx+dp(6), cy+dp(20), p); } }
    public class SendButton extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); public SendButton(Context c) { super(c); setOnClickListener(new View.OnClickListener() { @Override public void onClick(View v) { send(); } }); } @Override protected void onDraw(Canvas c) { p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(dp(2)); p.setStrokeCap(Paint.Cap.ROUND); p.setStrokeJoin(Paint.Join.ROUND); float cy=getHeight()/2f; c.drawLine(dp(12), cy, getWidth()-dp(12), cy, p); c.drawLine(getWidth()-dp(12), cy, getWidth()-dp(22), cy-dp(10), p); c.drawLine(getWidth()-dp(12), cy, getWidth()-dp(22), cy+dp(10), p); } }
    public class TogglePill extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); boolean checked = false; public TogglePill(Context c) { super(c); } @Override protected void onDraw(Canvas c) { int w=getWidth(), h=getHeight(); p.setStrokeWidth(dp(1)); p.setStyle(checked ? Paint.Style.FILL : Paint.Style.STROKE); p.setColor(Color.WHITE); c.drawRoundRect(dp(1), dp(1), w-dp(1), h-dp(1), h/2f, h/2f, p); } }
    public class ScrollIndicator extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); ScrollView target; public ScrollIndicator(Context c) { super(c); } @Override protected void onDraw(Canvas c) { if (target == null || target.getChildCount() == 0) return; int thumb; int top; if (target == scroll && messages.size() > MESSAGE_WINDOW) { thumb = Math.max(dp(28), Math.round(getHeight() * (MESSAGE_WINDOW / (float) messages.size()))); int maxTop = Math.max(0, getHeight() - thumb); int denominator = Math.max(1, messages.size() - MESSAGE_WINDOW); top = messageEnd >= messages.size() ? maxTop : Math.round(maxTop * (messageStart / (float) denominator)); } else { int content = target.getChildAt(0).getHeight(); int view = target.getHeight(); if (content <= view || view <= 0) return; float ratio = view / (float) content; thumb = Math.max(dp(28), Math.round(getHeight() * ratio)); int maxScroll = content - view; int maxTop = Math.max(0, getHeight() - thumb); top = Math.round(maxTop * (target.getScrollY() / (float) maxScroll)); } p.setColor(Color.rgb(125,125,125)); p.setStyle(Paint.Style.FILL); c.drawRect(0, top, getWidth(), top + thumb, p); } }
    public class FadeView extends View { Paint p = new Paint(); public FadeView(Context c) { super(c); } @Override protected void onDraw(Canvas c) { p.setShader(new LinearGradient(0, 0, 0, getHeight(), Color.TRANSPARENT, Color.BLACK, Shader.TileMode.CLAMP)); c.drawRect(0, 0, getWidth(), getHeight(), p); p.setShader(null); } }
    public class WaveView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float level = 0.05f;
        float shown = 0.05f;
        final Runnable tick = new Runnable() {
            @Override public void run() {
                shown = ToolText.followVoiceShown(shown, level);
                invalidate();
                postDelayed(this, 40);
            }
        };
        public WaveView(Context c) { super(c); setBackgroundColor(Color.TRANSPARENT); }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); removeCallbacks(tick); post(tick); }
        @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }
        @Override protected void onDraw(Canvas c) {
            int bars = 9; int gap = dp(8); int barW = dp(3); int total = bars * barW + (bars - 1) * gap; int start = (getWidth() - total) / 2; int mid = getHeight() / 2;
            p.setColor(Color.WHITE); p.setStyle(Paint.Style.FILL);
            for (int i = 0; i < bars; i++) {
                float distance = Math.abs(i - (bars - 1) / 2f);
                float scale = Math.max(0.18f, 1f - distance * 0.12f);
                int h = Math.max(dp(4), Math.round(getHeight() * 0.94f * shown * scale));
                int x = start + i * (barW + gap);
                c.drawRect(x, mid - h / 2f, x + barW, mid + h / 2f, p);
            }
        }
    }
    public class BorderWaveView extends View {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        float level = 0.05f;
        float shown = 0.05f;
        long born = android.os.SystemClock.uptimeMillis();
        final Runnable tick = new Runnable() {
            @Override public void run() {
                shown = ToolText.followVoiceShown(shown, level);
                invalidate();
                postDelayed(this, 40);
            }
        };
        public BorderWaveView(Context c) {
            super(c);
            setBackgroundColor(Color.TRANSPARENT);
            setClickable(false);
            setFocusable(false);
        }
        @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); born = android.os.SystemClock.uptimeMillis(); removeCallbacks(tick); post(tick); }
        @Override protected void onDetachedFromWindow() { removeCallbacks(tick); super.onDetachedFromWindow(); }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w < 8 || h < 8) return;
            float breath = 0.5f + 0.5f * (float) Math.sin((android.os.SystemClock.uptimeMillis() - born) / 2400.0 * 2.0 * Math.PI);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.SQUARE);
            p.setStrokeJoin(Paint.Join.MITER);
            p.setStrokeWidth(dp(2));
            p.setColor(Color.argb(Math.round(138 + breath * 22), 255, 255, 255));
            float m = dp(1);
            c.drawRect(m, m, w - m, h - m, p);
            if (shown > 0.08f) {
                p.setStrokeWidth(1);
                p.setColor(Color.argb(Math.min(220, 70 + Math.round(shown * 150)), 255, 255, 255));
                float in = dp(7);
                c.drawRect(in, in, w - in, h - in, p);
            }
        }
    }
    public class GlobeButton extends View { Paint p = new Paint(Paint.ANTI_ALIAS_FLAG); boolean active = false; public GlobeButton(Context c) { super(c); } @Override protected void onDraw(Canvas c) { if (!active) return; int w=getWidth(), h=getHeight(); float r=Math.min(w,h)*0.25f, cx=w/2f, cy=h/2f; p.setColor(Color.WHITE); p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(Math.max(1f, dp(1))); p.setStrokeCap(Paint.Cap.ROUND); c.drawCircle(cx, cy, r, p); c.drawOval(cx-r*0.45f, cy-r, cx+r*0.45f, cy+r, p); c.drawArc(cx-r, cy-r*0.55f, cx+r, cy+r*0.55f, 0, 360, false, p); c.drawLine(cx-r*0.94f, cy, cx+r*0.94f, cy, p); } }
    public class JumpTextView extends TextView {
        String word = "thinking...";
        Msg bound;
        final Paint wavePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Runnable tick = new Runnable() { @Override public void run() { animateJump(); } };
        public JumpTextView(Context c) {
            super(c);
            setSingleLine(true);
            setIncludeFontPadding(false);
            setGravity(Gravity.CENTER_VERTICAL);
            setTextColor(Color.TRANSPARENT);
        }
        void bind(Msg m) {
            bound = m;
            String w = word == null || word.length() == 0 ? "thinking..." : word;
            setText(w);
            if (m != null) {
                if (m.jumpAnimStartMs == 0L || m.jumpAnimWord == null || !w.equals(m.jumpAnimWord)) {
                    m.jumpAnimWord = w;
                    m.jumpAnimStartMs = android.os.SystemClock.uptimeMillis();
                }
            }
        }
        @Override protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            removeCallbacks(tick);
            animateJump();
        }
        @Override protected void onDetachedFromWindow() {
            removeCallbacks(tick);
            super.onDetachedFromWindow();
        }
        private void animateJump() {
            if (!isAttachedToWindow()) return;
            if (!isShown()) {
                postDelayed(tick, 90);
                return;
            }
            invalidate();
            postDelayed(tick, 32);
        }
        @Override protected void onDraw(Canvas c) {
            String w = word == null || word.length() == 0 ? "thinking..." : word;
            long start = bound != null && bound.jumpAnimStartMs > 0L
                    ? bound.jumpAnimStartMs : android.os.SystemClock.uptimeMillis();
            if (bound != null && bound.jumpAnimStartMs == 0L) {
                bound.jumpAnimStartMs = start;
                bound.jumpAnimWord = w;
            }
            final float periodSec = 1.45f;
            long elapsed = Math.max(0L, android.os.SystemClock.uptimeMillis() - start);
            double cycle = (elapsed / 1000.0) / periodSec;
            final float ampPx = 2.6f * uiScale();
            final int baseR = 135, baseG = 135, baseB = 135;
            Paint tp = getPaint();
            wavePaint.set(tp);
            wavePaint.setAntiAlias(true);
            float x = getPaddingLeft();
            Paint.FontMetrics fm = wavePaint.getFontMetrics();
            float y = getPaddingTop() + ((getHeight() - getPaddingTop() - getPaddingBottom()) - (fm.bottom - fm.top)) / 2f - fm.top;
            int n = Math.max(1, w.length());
            for (int i = 0; i < w.length(); i++) {
                float wave = (float) Math.sin((2.0 * Math.PI) * (cycle - i / (double) n));
                float lift = ampPx * (0.55f + 0.45f * wave);
                float bright = 0.62f + 0.38f * ((wave + 1f) * 0.5f);
                int r = Math.min(255, Math.round(baseR + (255 - baseR) * (bright - 0.62f)));
                int g = Math.min(255, Math.round(baseG + (255 - baseG) * (bright - 0.62f)));
                int b = Math.min(255, Math.round(baseB + (255 - baseB) * (bright - 0.62f)));
                wavePaint.setColor(Color.argb(255, r, g, b));
                String ch = w.substring(i, i + 1);
                c.drawText(ch, x, y - lift, wavePaint);
                x += tp.measureText(ch);
            }
        }
    }
    public class SourceDot extends View {
        final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint glyph = new Paint(Paint.ANTI_ALIAS_FLAG);
        final Paint bitmapPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        final Path clip = new Path();
        final RectF dest = new RectF();
        Bitmap icon;
        String letter = "";
        public SourceDot(Context c) {
            super(c);
            setBackgroundColor(Color.TRANSPARENT);
            fill.setStyle(Paint.Style.FILL);
            ring.setStyle(Paint.Style.STROKE);
            ring.setStrokeWidth(1f);
            ring.setColor(Color.rgb(52, 52, 52));
            glyph.setColor(Color.rgb(135, 135, 135));
            glyph.setTextAlign(Paint.Align.CENTER);
        }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            if (w <= 0 || h <= 0) return;
            float cx = w / 2f, cy = h / 2f;
            float r = Math.min(w, h) / 2f - 0.5f;
            fill.setColor(Color.BLACK);
            c.drawCircle(cx, cy, r, fill);
            if (icon != null && !icon.isRecycled()) {
                float inset = 1f;
                dest.set(cx - r + inset, cy - r + inset, cx + r - inset, cy + r - inset);
                clip.reset();
                clip.addCircle(cx, cy, Math.max(1f, r - inset), Path.Direction.CW);
                c.save();
                c.clipPath(clip);
                c.drawBitmap(icon, null, dest, bitmapPaint);
                c.restore();
            } else {
                fill.setColor(Color.rgb(22, 22, 22));
                c.drawCircle(cx, cy, Math.max(1f, r - 1f), fill);
                if (letter != null && letter.length() > 0) {
                    glyph.setTextSize(Math.max(8f, h * 0.48f));
                    Paint.FontMetrics fm = glyph.getFontMetrics();
                    c.drawText(letter, cx, cy - (fm.ascent + fm.descent) / 2f, glyph);
                }
            }
            c.drawCircle(cx, cy, r - 0.5f, ring);
        }
    }
    public static class ContextMeter extends View {
        Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        float percent = 0;
        public ContextMeter(Context c) {
            super(c);
            track.setStyle(Paint.Style.STROKE);
            track.setColor(Color.rgb(55, 55, 55));
            fill.setStyle(Paint.Style.STROKE);
            fill.setStrokeCap(Paint.Cap.BUTT);
            fill.setColor(Color.WHITE);
        }
        @Override protected void onDraw(Canvas c) {
            int w = getWidth(), h = getHeight();
            float stroke = Math.max(1.4f, Math.min(w, h) / 11f);
            float r = Math.min(w, h) / 2f - stroke;
            float cx = w / 2f, cy = h / 2f;
            track.setStrokeWidth(stroke);
            fill.setStrokeWidth(stroke);
            c.drawCircle(cx, cy, r, track);
            float sweep = Math.max(0f, Math.min(360f, percent * 3.6f));
            if (sweep > 0.5f) c.drawArc(cx - r, cy - r, cx + r, cy + r, -90, sweep, false, fill);
        }
    }
    public static class AttachedImage {
        String base64 = "", mime = "image/jpeg";
        transient Bitmap thumb;
        AttachedImage(String b64, String mimeType) {
            base64 = b64 == null ? "" : b64;
            mime = mimeType == null || mimeType.length() == 0 ? "image/jpeg" : mimeType;
        }
    }
    public static class ToolStep {
        String name = "";
        String detail = "";
        String status = "done";
        String preview = "";
        JSONObject toJson() throws Exception {
            return new JSONObject().put("name", name == null ? "" : name)
                    .put("detail", detail == null ? "" : detail)
                    .put("status", "running".equals(status) ? "done" : (status == null ? "done" : status))
                    .put("preview", preview == null ? "" : preview);
        }
        static ToolStep fromJson(JSONObject o) {
            ToolStep s = new ToolStep();
            if (o == null) return s;
            s.name = o.optString("name", "");
            s.detail = o.optString("detail", "");
            s.status = o.optString("status", "done");
            if ("running".equals(s.status)) s.status = "done";
            s.preview = o.optString("preview", "");
            return s;
        }
    }
    public static class Msg {
        String role, text, imageBase64 = "", imageMime = "", stats, model, replyQuote, reasoning = "", memorySavedText = "";
        boolean slowVoice = false, reasoningCapable = false, thinkingExpanded = false, searchExpanded = false, memorySaved = false, memoryExpanded = false, ttsRequested = false, ttsPrefetching = false, ttsStarted = false, ttsPlaying = false, ttsStartDelayDone = false, streamDone = false, skipImagesInRequest = false;
        int spokenChars = 0, ttsPlaybackFailures = 0, voiceSessionId = 0, thinkingAnimStep = 0, promptTokens = 0, toolTokens = 0;
        long startedAt = 0, thoughtMs = 0, jumpAnimStartMs = 0;
        String jumpAnimWord = "";
        transient CharSequence bodyDisplay;
        transient String bodyDisplaySrc = "";
        ArrayList<AttachedImage> images = new ArrayList<AttachedImage>();
        ArrayList<String> ttsQueue = new ArrayList<String>();
        ArrayList<String> searchSources = new ArrayList<String>();
        ArrayList<ToolStep> toolSteps = new ArrayList<ToolStep>();
        Msg(String r, String t, String i, String m, String s) { this(r,t,i,m,s,"",""); }
        Msg(String r, String t, String i, String m, String s, String modelName) { this(r,t,i,m,s,modelName,""); }
        Msg(String r, String t, String i, String m, String s, String modelName, String reply) {
            role=r; text=t==null?"":t; imageBase64=i==null?"":i; imageMime=m==null?"":m; stats=s==null?"":s; model=modelName==null?"":modelName; replyQuote=reply==null?"":reply;
            startedAt = System.currentTimeMillis();
            if (imageBase64.length() > 0) images.add(new AttachedImage(imageBase64, imageMime));
        }
        void ensureImagesFromLegacy() {
            if (images == null) images = new ArrayList<AttachedImage>();
            if (images.size() == 0 && imageBase64 != null && imageBase64.length() > 0) {
                images.add(new AttachedImage(imageBase64, imageMime));
            }
        }
        void syncLegacyImageFields() {
            ensureImagesFromLegacy();
            if (images.size() == 0) { imageBase64 = ""; imageMime = ""; return; }
            AttachedImage first = images.get(0);
            imageBase64 = first.base64 == null ? "" : first.base64;
            imageMime = first.mime == null || first.mime.length() == 0 ? "image/jpeg" : first.mime;
        }
        JSONObject toJson() throws Exception {
            syncLegacyImageFields();
            JSONArray src = new JSONArray();
            for (String s : searchSources) src.put(s);
            JSONArray steps = new JSONArray();
            synchronized (toolSteps) {
                for (ToolStep step : toolSteps) {
                    if (step == null) continue;
                    try { steps.put(step.toJson()); } catch (Exception ignored) {}
                }
            }
            JSONArray imgs = new JSONArray();
            for (AttachedImage img : images) {
                if (img == null || img.base64 == null || img.base64.length() == 0) continue;
                imgs.put(new JSONObject().put("data", img.base64).put("mime", img.mime == null || img.mime.length() == 0 ? "image/jpeg" : img.mime));
            }
            return new JSONObject().put("role",role).put("text",text).put("image",imageBase64).put("mime",imageMime).put("images",imgs).put("skipImagesInRequest",skipImagesInRequest).put("stats",stats).put("model",model).put("replyQuote",replyQuote).put("reasoning",reasoning).put("startedAt",startedAt).put("thoughtMs",thoughtMs).put("memorySaved",memorySaved).put("memorySavedText",memorySavedText).put("searchSources",src).put("toolSteps",steps).put("promptTokens",promptTokens).put("toolTokens",toolTokens);
        }
        static Msg fromJson(JSONObject o) {
            Msg m = new Msg(o.optString("role"),o.optString("text"),o.optString("image"),o.optString("mime"),o.optString("stats"),o.optString("model"),o.optString("replyQuote"));
            m.reasoning = o.optString("reasoning", "");
            m.startedAt = o.optLong("startedAt", 0);
            m.thoughtMs = o.optLong("thoughtMs", 0);
            m.memorySaved = o.optBoolean("memorySaved", false);
            m.memorySavedText = o.optString("memorySavedText", "");
            m.skipImagesInRequest = o.optBoolean("skipImagesInRequest", false);
            m.promptTokens = o.optInt("promptTokens", 0);
            m.toolTokens = o.optInt("toolTokens", 0);
            JSONArray imgs = o.optJSONArray("images");
            if (imgs != null && imgs.length() > 0) {
                m.images.clear();
                for (int i = 0; i < imgs.length(); i++) {
                    JSONObject im = imgs.optJSONObject(i);
                    if (im == null) continue;
                    String data = im.optString("data", im.optString("image", ""));
                    String mime = im.optString("mime", "image/jpeg");
                    if (data.length() > 0) m.images.add(new AttachedImage(data, mime));
                }
                m.syncLegacyImageFields();
            } else {
                m.ensureImagesFromLegacy();
            }
            JSONArray src = o.optJSONArray("searchSources");
            if (src != null) for (int i = 0; i < src.length(); i++) { String s = src.optString(i, ""); if (s.length() > 0) m.searchSources.add(s); }
            JSONArray steps = o.optJSONArray("toolSteps");
            if (steps != null) {
                for (int i = 0; i < steps.length(); i++) {
                    JSONObject so = steps.optJSONObject(i);
                    if (so == null) continue;
                    ToolStep ts = ToolStep.fromJson(so);
                    if (ts.name != null && ts.name.length() > 0) m.toolSteps.add(ts);
                }
            }
            m.streamDone = !isBusyStats(m.stats);
            return m;
        }
    }
    public static class Chat {
        String id="", title="", folder="Inbox", model="";
        boolean webSearch=false, titleGenerated=false;
        long updatedAt = 0;
        transient String listPreview;
        transient long listRecency;
        ArrayList<Msg> messages=new ArrayList<Msg>();
        JSONObject toJson() throws Exception {
            JSONArray a=new JSONArray();
            for (Msg m : messages) a.put(m.toJson());
            return new JSONObject().put("id",id).put("title",title).put("titleGenerated",titleGenerated)
                    .put("folder",folder).put("model",model).put("webSearch",webSearch)
                    .put("updatedAt",updatedAt).put("messages",a);
        }
        static Chat fromJson(JSONObject o) {
            Chat c=new Chat();
            c.id=o.optString("id");
            c.title=o.optString("title");
            c.titleGenerated=o.optBoolean("titleGenerated", false);
            c.folder=o.optString("folder","Inbox");
            c.model=o.optString("model", "");
            c.webSearch=o.optBoolean("webSearch", false);
            c.updatedAt=o.optLong("updatedAt", 0);
            JSONArray a=o.optJSONArray("messages");
            if (a != null) for (int i = 0; i < a.length(); i++) c.messages.add(Msg.fromJson(a.optJSONObject(i)));
            return c;
        }
    }
}
