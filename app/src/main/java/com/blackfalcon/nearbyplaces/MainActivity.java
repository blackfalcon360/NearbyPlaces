package com.blackfalcon.nearbyplaces;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Locale;

public class MainActivity extends Activity implements Tracker.Listener {

    static final int BLUE = 0xFF1E88E5;
    static final String CACHE_FILE = "places.json";
    static final int MAX_SHOWN = 40;

    static class Hit {
        Place p;
        double d, b;
    }

    Store st;
    Tracker tracker;
    final ArrayList<Place> builtin = Gazetteer.all();
    ArrayList<Place> osm = new ArrayList<Place>();
    final ArrayList<Place> db = new ArrayList<Place>();
    Location cur;
    float heading = -1f;
    boolean busy = false, autoTried = false;
    long lastBuild = 0;
    Location lastBuildLoc;

    int cBg, cCard, cText, cSub;
    TextView[] tabViews;
    ScrollView[] scrolls;
    LinearLayout listBox, setBox;
    TextView tvCoord, tvInfo, tvHead, tvState;
    Button btnGps;
    final ArrayList<TextView> arrows = new ArrayList<TextView>();
    final ArrayList<Double> brgs = new ArrayList<Double>();
    RadioGroup rgLang, rgTheme;
    int[] langIds = new int[3];
    int[] themeIds = new int[2];
    CheckBox cbAuto;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        st = new Store(this);
        osm = Cache.load(this, CACHE_FILE);
        recompose();
        tracker = new Tracker(this, this);
        buildUI();
        showTab(0);
        requestPermissions(new String[] {"android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION"}, 21);
    }

    boolean hasPerm() {
        return checkSelfPermission("android.permission.ACCESS_FINE_LOCATION")
                == PackageManager.PERMISSION_GRANTED
            || checkSelfPermission("android.permission.ACCESS_COARSE_LOCATION")
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (hasPerm()) tracker.start();
        updateState();
    }

    @Override
    protected void onPause() {
        super.onPause();
        tracker.stop();
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (hasPerm()) tracker.start();
        updateState();
    }

    void recompose() {
        db.clear();
        db.addAll(builtin);
        db.addAll(osm);
    }

    // ---------- helpers ----------
    String tr(String ur, String en, String ar) { return st.tr(ur, en, ar); }

    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    GradientDrawable round(int color, int r) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(r));
        return g;
    }

    TextView tv(String s, int size, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(size);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    LinearLayout cardBox() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(round(cCard, 14));
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(dp(10), dp(5), dp(10), dp(5));
        c.setLayoutParams(lp);
        return c;
    }

    Button btn(String s, int color, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(s);
        b.setTextColor(Color.WHITE);
        b.setBackground(round(color, 10));
        b.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, dp(8), 0, 0);
        b.setLayoutParams(lp);
        return b;
    }

    String[] windNames() {
        String l = st.lang();
        if (l.equals("en")) return new String[] {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};
        if (l.equals("ar")) return new String[] {"شمال", "شمال شرق", "شرق", "جنوب شرق",
            "جنوب", "جنوب غرب", "غرب", "شمال غرب"};
        return new String[] {"شمال", "شمال مشرق", "مشرق", "جنوب مشرق",
            "جنوب", "جنوب مغرب", "مغرب", "شمال مغرب"};
    }

    String dirText(double deg) {
        return windNames()[Geo.wind(deg)] + " " + Math.round(deg) + "\u00B0";
    }

    // ---------- UI ----------
    void buildUI() {
        boolean dark = st.dark();
        cBg = dark ? 0xFF000000 : 0xFFF2F2F2;
        cCard = dark ? 0xFF1A1A1A : 0xFFFFFFFF;
        cText = dark ? 0xFFFFFFFF : 0xFF111111;
        cSub = dark ? 0xFFAAAAAA : 0xFF555555;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);
        root.setLayoutDirection(st.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);

        TextView title = tv("Nearby Places", 22, cText, true);
        title.setPadding(dp(14), dp(14), dp(14), dp(6));
        root.addView(title);

        String[] names = {tr("قریبی مقامات", "Nearby", "الأماكن القريبة"),
            tr("سیٹنگز", "Settings", "الإعدادات")};
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setPadding(dp(8), dp(4), dp(8), dp(8));
        tabViews = new TextView[2];
        for (int i = 0; i < 2; i++) {
            final int idx = i;
            TextView t = tv(names[i], 15, cText, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(8), dp(9), dp(8), dp(9));
            t.setOnClickListener(v -> showTab(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                    ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            tabs.addView(t, lp);
            tabViews[i] = t;
        }
        root.addView(tabs);

        FrameLayout content = new FrameLayout(this);
        scrolls = new ScrollView[2];
        LinearLayout main = new LinearLayout(this);
        main.setOrientation(LinearLayout.VERTICAL);
        setBox = new LinearLayout(this);
        setBox.setOrientation(LinearLayout.VERTICAL);
        setBox.setPadding(dp(14), dp(4), dp(14), dp(16));
        LinearLayout[] boxes = {main, setBox};
        for (int i = 0; i < 2; i++) {
            scrolls[i] = new ScrollView(this);
            scrolls[i].addView(boxes[i]);
            content.addView(scrolls[i], new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        }
        root.addView(content, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        // header card
        LinearLayout head = cardBox();
        tvHead = tv("", 26, cText, true);
        tvCoord = tv("", 15, cText, false);
        tvInfo = tv("", 12, cSub, false);
        tvState = tv("", 12, cSub, false);
        head.addView(tvHead);
        head.addView(tvCoord);
        head.addView(tvInfo);
        head.addView(tvState);
        btnGps = btn(tr("لوکیشن سیٹنگز کھولیں", "Open location settings", "فتح إعدادات الموقع"),
                0xFF455A64, v -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)));
        head.addView(btnGps);
        head.addView(btn(tr("انٹرنیٹ سے مقامات محفوظ کریں (آف لائن کے لیے)",
                "Download places for offline use", "تنزيل الأماكن للاستخدام دون إنترنت"),
                BLUE, v -> refreshOnline(false)));
        main.addView(head);

        listBox = new LinearLayout(this);
        listBox.setOrientation(LinearLayout.VERTICAL);
        main.addView(listBox);

        TextView credit = tv("By: Black Falcon \uD83E\uDD85", 13, cSub, false);
        credit.setGravity(Gravity.CENTER);
        credit.setPadding(0, dp(8), 0, dp(10));
        root.addView(credit);

        setContentView(root);
        buildSettings();
        updateHeader();
        updateState();
        rebuild();
    }

    void showTab(int i) {
        for (int k = 0; k < 2; k++) {
            boolean sel = k == i;
            tabViews[k].setTextColor(sel ? Color.WHITE : cText);
            tabViews[k].setBackground(round(sel ? BLUE : cCard, 20));
            scrolls[k].setVisibility(sel ? View.VISIBLE : View.GONE);
        }
    }

    void updateState() {
        if (tvState == null) return;
        String s;
        boolean needGps = false;
        if (!hasPerm()) {
            s = tr("لوکیشن کی اجازت درکار ہے۔", "Location permission is needed.", "مطلوب إذن الموقع.");
        } else if (!tracker.gpsOn()) {
            s = tr("GPS بند ہے؛ آن کریں۔", "GPS is off; please turn it on.", "GPS مغلق؛ شغّله.");
            needGps = true;
        } else if (cur == null) {
            s = tr("GPS فکس کا انتظار… کھلی جگہ پر جائیں۔", "Waiting for GPS fix… go outdoors.",
                    "بانتظار تحديد GPS… اخرج إلى مكان مفتوح.");
        } else {
            s = tr("آف لائن موڈ میں بھی کام کرتا ہے: ", "Works offline: ", "يعمل دون إنترنت: ")
                + (builtin.size() + osm.size()) + tr(" مقامات", " places", " مكانًا");
        }
        tvState.setText(s);
        btnGps.setVisibility(needGps ? View.VISIBLE : View.GONE);
    }

    void updateHeader() {
        if (tvHead == null) return;
        if (heading >= 0) {
            tvHead.setText(dirText(heading));
        } else {
            tvHead.setText(tracker.hasCompass() ? "\u2014"
                : tr("کمپاس سینسر نہیں", "No compass sensor", "لا يوجد حساس بوصلة"));
        }
        if (cur == null) {
            tvCoord.setText("\u2014");
            tvInfo.setText("");
            return;
        }
        tvCoord.setText(String.format(Locale.ENGLISH, "%.6f, %.6f", cur.getLatitude(), cur.getLongitude()));
        StringBuilder sb = new StringBuilder();
        if (cur.hasAccuracy()) sb.append("\u00B1").append(Math.round(cur.getAccuracy())).append(" m   ");
        if (cur.hasAltitude()) sb.append(tr("بلندی ", "Alt ", "الارتفاع ")).append(Math.round(cur.getAltitude())).append(" m   ");
        if (cur.hasSpeed()) sb.append(Math.round(cur.getSpeed() * 3.6f)).append(" km/h");
        tvInfo.setText(sb.toString());
    }

    // ---------- tracker callbacks ----------
    @Override
    public void onLoc(Location l) {
        cur = l;
        updateHeader();
        updateState();
        long now = System.currentTimeMillis();
        boolean moved = lastBuildLoc == null || l.distanceTo(lastBuildLoc) > 15f;
        if (lastBuildLoc == null || (moved && now - lastBuild > 3000) || now - lastBuild > 30000) {
            lastBuild = now;
            lastBuildLoc = l;
            rebuild();
        }
        maybeAuto();
    }

    @Override
    public void onHeading(float deg) {
        heading = deg;
        updateHeader();
        for (int i = 0; i < arrows.size(); i++) {
            arrows.get(i).setRotation((float) (brgs.get(i) - heading));
        }
    }

    // ---------- list ----------
    void rebuild() {
        if (listBox == null) return;
        listBox.removeAllViews();
        arrows.clear();
        brgs.clear();
        if (cur == null) {
            TextView t = tv(tr("مقام معلوم ہوتے ہی قریبی جگہیں یہاں آئیں گی۔",
                    "Nearby places will appear here once your position is known.",
                    "ستظهر الأماكن القريبة هنا بعد تحديد موقعك."), 14, cSub, false);
            t.setGravity(Gravity.CENTER);
            t.setPadding(dp(20), dp(30), dp(20), dp(30));
            listBox.addView(t);
            return;
        }
        double la = cur.getLatitude(), lo = cur.getLongitude();
        ArrayList<Hit> hs = new ArrayList<Hit>(db.size());
        for (Place p : db) {
            Hit h = new Hit();
            h.p = p;
            h.d = Geo.dist(la, lo, p.lat, p.lon);
            hs.add(h);
        }
        Collections.sort(hs, new Comparator<Hit>() {
            @Override public int compare(Hit a, Hit b) { return Double.compare(a.d, b.d); }
        });
        String lang = st.lang();
        HashSet<String> seen = new HashSet<String>();
        int n = 0;
        for (Hit h : hs) {
            String nm = h.p.name(lang);
            if (nm.length() == 0) continue;
            if (!seen.add(nm.toLowerCase(Locale.ROOT))) continue;
            h.b = Geo.bearing(la, lo, h.p.lat, h.p.lon);
            listBox.addView(row(h, nm));
            if (++n >= MAX_SHOWN) break;
        }
    }

    View row(Hit h, String name) {
        LinearLayout c = cardBox();
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        TextView ar = tv("\u2191", 30, BLUE, true);
        ar.setGravity(Gravity.CENTER);
        float rot = (float) (heading >= 0 ? h.b - heading : h.b);
        ar.setRotation(rot);
        arrows.add(ar);
        brgs.add(h.b);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(dp(44), dp(44));
        ap.setMargins(0, 0, dp(12), 0);
        c.addView(ar, ap);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.addView(tv(name, 17, cText, true));
        String src = h.p.osm ? "OSM" : tr("بلٹ اِن", "built-in", "مدمج");
        col.addView(tv(Geo.fmtDist(h.d) + "   •   " + dirText(h.b) + "   •   " + src, 12, cSub, false));
        c.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        
        // Tap Listener for opening maps
        c.setOnClickListener(v -> openMaps(h.p, name));
        
        return c;
    }

    /** Tap on a place: lets the user pick any installed maps app to see the place / get directions. */
    void openMaps(Place p, String name) {
        try {
            Uri u = Uri.parse("geo:" + p.lat + "," + p.lon + "?q=" + p.lat + "," + p.lon + "(" + Uri.encode(name) + ")");
            startActivity(Intent.createChooser(new Intent(Intent.ACTION_VIEW, u),
                    tr("نقشے کی ایپ منتخب کریں", "Choose a maps app", "اختر تطبيق خرائط")));
        } catch (Exception e) {
            Toast.makeText(this, tr("نقشے کی ایپ نہیں ملی", "No maps app found", "لا يوجد تطبيق خرائط"),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ---------- online refresh (saved for offline) ----------
    void maybeAuto() {
        if (autoTried || cur == null || !st.auto()) return;
        autoTried = true;
        if (!st.nearTile(cur.getLatitude(), cur.getLongitude(), 15000)) refreshOnline(true);
    }

    void refreshOnline(final boolean silent) {
        if (cur == null) {
            if (!silent) Toast.makeText(this, tr("پہلے مقام معلوم ہونے دیں", "Wait for a position first",
                    "انتظر تحديد الموقع أولًا"), Toast.LENGTH_SHORT).show();
            return;
        }
        if (busy) return;
        busy = true;
        if (!silent) Toast.makeText(this, tr("ڈاؤن لوڈ ہو رہا ہے…", "Downloading…", "جارٍ التنزيل…"),
                Toast.LENGTH_SHORT).show();
        final double la = cur.getLatitude(), lo = cur.getLongitude();
        final ArrayList<Place> snapshot = new ArrayList<Place>(osm);
        new Thread(() -> {
            ArrayList<Place> got = Net.places(la, lo);
            ArrayList<Place> merged = null;
            int added = 0;
            if (got != null && got.size() > 0) {
                merged = snapshot;
                HashSet<String> keys = new HashSet<String>();
                for (Place p : merged) keys.add(p.key());
                for (Place p : got) {
                    if (merged.size() >= 8000) break;
                    if (keys.add(p.key())) { merged.add(p); added++; }
                }
                Cache.save(this, CACHE_FILE, merged);
            }
            final ArrayList<Place> fm = merged;
            final int fa = added;
            final boolean failed = got == null;
            runOnUiThread(() -> {
                busy = false;
                if (fm != null) {
                    osm = fm;
                    st.addTile(la, lo);
                    recompose();
                    rebuild();
                    updateState();
                    if (!silent) Toast.makeText(this, tr("محفوظ ہوئے: ", "Saved: ", "تم الحفظ: ") + fa
                            + tr(" نئے مقامات", " new places", " أماكن جديدة"), Toast.LENGTH_LONG).show();
                } else if (!silent) {
                    Toast.makeText(this, failed
                        ? tr("انٹرنیٹ نہیں ملا", "No internet connection", "لا يوجد اتصال بالإنترنت")
                        : tr("اس علاقے میں نئے مقامات نہیں ملے", "No new places found here",
                            "لا أماكن جديدة هنا"), Toast.LENGTH_LONG).show();
                }
            });
        }).start();
    }

    // ---------- settings ----------
    TextView label(String s) {
        TextView t = tv(s, 16, cText, true);
        t.setPadding(0, dp(14), 0, dp(6));
        return t;
    }

    RadioGroup radios(String[] labels, int checked, int[] ids) {
        RadioGroup g = new RadioGroup(this);
        for (int i = 0; i < labels.length; i++) {
            RadioButton r = new RadioButton(this);
            r.setText(labels[i]);
            r.setTextColor(cText);
            r.setTextSize(15);
            r.setId(View.generateViewId());
            ids[i] = r.getId();
            g.addView(r);
        }
        g.check(ids[checked]);
        return g;
    }

    void
