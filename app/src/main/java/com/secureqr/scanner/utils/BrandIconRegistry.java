package com.secureqr.scanner.utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Offline-only brand matcher. User domains and issuer names never leave the device. */
public final class BrandIconRegistry {
    private static volatile BrandIconRegistry instance;
    private final Context appContext;
    private final Map<String, String> domains = new HashMap<>();
    private final Map<String, String> issuers = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();
    private final Map<String, String> surfaces = new HashMap<>();
    private final Map<String, Bitmap> bitmaps = new HashMap<>();

    private BrandIconRegistry(Context context) {
        appContext = context.getApplicationContext();
        loadIndex();
    }

    public static BrandIconRegistry get(Context context) {
        if (instance == null) {
            synchronized (BrandIconRegistry.class) {
                if (instance == null) instance = new BrandIconRegistry(context);
            }
        }
        return instance;
    }

    public BrandIcon websiteBrand(String website) {
        String host = normalizedHost(website);
        if (host.isEmpty()) return null;
        String matched = null;
        int matchedLength = -1;
        for (Map.Entry<String, String> entry : domains.entrySet()) {
            String domain = entry.getKey();
            if ((host.equals(domain) || host.endsWith("." + domain)) && domain.length() > matchedLength) {
                matched = entry.getValue();
                matchedLength = domain.length();
            }
        }
        return brandIcon(matched);
    }

    public BrandIcon issuerBrand(String issuer) {
        return brandIcon(issuers.get(normalize(issuer)));
    }

    public BrandIcon namedBrand(String name) {
        return brandIcon(names.get(normalize(name)));
    }

    private void loadIndex() {
        try (InputStream input = appContext.getAssets().open("brand_icons/index.json")) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) >= 0) output.write(buffer, 0, read);
            JSONObject root = new JSONObject(new String(output.toByteArray(), StandardCharsets.UTF_8));
            JSONArray brands = root.getJSONArray("brands");
            for (int i = 0; i < brands.length(); i++) {
                JSONObject brand = brands.getJSONObject(i);
                String id = brand.getString("id");
                surfaces.put(id, brand.optString("surface", "none"));
                addAliases(domains, brand.optJSONArray("domains"), id);
                addAliases(issuers, brand.optJSONArray("issuerAliases"), id);
                addAliases(names, brand.optJSONArray("nameAliases"), id);
            }
        } catch (Exception ignored) {
            domains.clear();
            issuers.clear();
            names.clear();
        }
    }

    private void addAliases(Map<String, String> target, JSONArray values, String id) {
        if (values == null) return;
        for (int i = 0; i < values.length(); i++) {
            String value = normalize(values.optString(i));
            if (!value.isEmpty()) target.put(value, id);
        }
    }

    private Bitmap bitmap(String id) {
        if (id == null || id.isEmpty()) return null;
        synchronized (bitmaps) {
            if (bitmaps.containsKey(id)) return bitmaps.get(id);
            Bitmap result = null;
            try (InputStream input = appContext.getAssets().open("brand_icons/" + id + ".webp")) {
                result = BitmapFactory.decodeStream(input);
            } catch (Exception ignored) { }
            bitmaps.put(id, result);
            return result;
        }
    }

    private BrandIcon brandIcon(String id) {
        Bitmap bitmap = bitmap(id);
        return bitmap == null ? null : new BrandIcon(bitmap, surfaces.getOrDefault(id, "none"));
    }

    private String normalizedHost(String value) {
        if (value == null) return "";
        String clean = value.trim();
        if (clean.isEmpty()) return "";
        try {
            Uri uri = Uri.parse(clean.contains("://") ? clean : "https://" + clean);
            String host = uri.getHost();
            if (host == null) return "";
            host = host.toLowerCase(Locale.ROOT);
            while (host.startsWith("www.")) host = host.substring(4);
            return host;
        } catch (Exception ignored) {
            return "";
        }
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public static final class BrandIcon {
        public final Bitmap bitmap;
        public final String surface;
        BrandIcon(Bitmap bitmap, String surface) {
            this.bitmap = bitmap;
            this.surface = surface;
        }
    }
}
