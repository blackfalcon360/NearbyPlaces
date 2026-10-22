package com.blackfalcon.nearbyplaces;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;

/** Downloads named places around a point from OpenStreetMap (Overpass API). */
public class Net {
    static final String[] SERVERS = {
        "https://overpass-api.de/api/interpreter",
        "https://overpass.kumi.systems/api/interpreter"
    };

    static String get(String server, String query) {
        HttpURLConnection c = null;
        try {
            String u = server + "?data=" + URLEncoder.encode(query, "UTF-8");
            c = (HttpURLConnection) new URL(u).openConnection();
            c.setConnectTimeout(15000);
            c.setReadTimeout(40000);
            c.setRequestProperty("User-Agent", "BlackFalconNearbyPlaces/1.0");
            if (c.getResponseCode() != 200) return null;
            BufferedReader r = new BufferedReader(new InputStreamReader(c.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            char[] buf = new char[8192];
            int n;
            while ((n = r.read(buf)) > 0) sb.append(buf, 0, n);
            r.close();
            return sb.toString();
        } catch (Exception e) {
            return null;
        } finally {
            if (c != null) c.disconnect();
        }
    }

    /** Returns null if the network failed, otherwise the (possibly empty) list. */
    public static ArrayList<Place> places(double lat, double lon) {
        String ll = lat + "," + lon;
        String q = "[out:json][timeout:30];("
            + "node[\"place\"~\"^(city|town)$\"](around:60000," + ll + ");"
            + "node[\"place\"~\"^(village|suburb|hamlet|neighbourhood|locality)$\"](around:15000," + ll + ");"
            + ");out 600;";
        String body = null;
        for (String s : SERVERS) {
            body = get(s, q);
            if (body != null) break;
        }
        if (body == null) return null;
        ArrayList<Place> out = new ArrayList<Place>();
        try {
            JSONArray el = new JSONObject(body).getJSONArray("elements");
            for (int i = 0; i < el.length(); i++) {
                JSONObject o = el.getJSONObject(i);
                JSONObject t = o.optJSONObject("tags");
                if (t == null || !o.has("lat") || !o.has("lon")) continue;
                Place p = new Place();
                String n = t.optString("name", "");
                p.en = t.optString("name:en", "");
                if (p.en.length() == 0) p.en = n;
                p.ur = t.optString("name:ur", "");
                p.ar = t.optString("name:ar", "");
                if (p.en.length() == 0 && p.ur.length() == 0 && p.ar.length() == 0) continue;
                p.kind = t.optString("place", "");
                p.lat = o.getDouble("lat");
                p.lon = o.getDouble("lon");
                p.id = "osm" + o.optLong("id", 0);
                p.osm = true;
                out.add(p);
            }
        } catch (Exception e) {
            return null;
        }
        return out;
    }
}
