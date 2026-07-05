package com.secureqr.scanner.utils;

import android.net.Uri;
import android.util.Log;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import okhttp3.Credentials;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class WebDAVClient {
    private final String baseUrl;
    private final String username;
    private final String password;
    private final OkHttpClient client = new OkHttpClient();

    public WebDAVClient(String baseUrl, String username, String password) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.username = username;
        this.password = password;
    }

    public boolean upload(String remotePath, String data) {
        RequestBody body = RequestBody.create(data, MediaType.parse("text/plain; charset=utf-8"));
        Request request;
        try {
            request = authorizedBuilder(remotePath).put(body).build();
        } catch (IllegalArgumentException e) {
            Log.e("WebDAV", "Invalid upload url", e);
            return false;
        }
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            Log.e("WebDAV", "Upload error", e);
            return false;
        }
    }

    public String download(String remotePath) {
        Request request;
        try {
            request = authorizedBuilder(remotePath).get().build();
        } catch (IllegalArgumentException e) {
            Log.e("WebDAV", "Invalid download url", e);
            return null;
        }
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return null;
            return response.body().string();
        } catch (IOException e) {
            Log.e("WebDAV", "Download error", e);
            return null;
        }
    }

    public boolean delete(String remotePath) {
        Request request;
        try {
            request = authorizedBuilder(remotePath).delete().build();
        } catch (IllegalArgumentException e) {
            Log.e("WebDAV", "Invalid delete url", e);
            return false;
        }
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            Log.e("WebDAV", "Delete error", e);
            return false;
        }
    }

    public boolean testConnection() {
        RequestBody body = RequestBody.create(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><propfind xmlns=\"DAV:\"><prop><displayname/></prop></propfind>",
                MediaType.parse("application/xml; charset=utf-8"));
        Request request;
        try {
            request = new Request.Builder()
                    .url(baseUrl + "/")
                    .method("PROPFIND", body)
                    .header("Depth", "0")
                    .header("Authorization", Credentials.basic(username, password))
                    .build();
        } catch (IllegalArgumentException e) {
            Log.e("WebDAV", "Invalid test url", e);
            return false;
        }
        try (Response response = client.newCall(request).execute()) {
            return response.isSuccessful();
        } catch (IOException e) {
            Log.e("WebDAV", "Test error", e);
            return false;
        }
    }

    public List<String> listBackups() {
        List<BackupFile> files = listBackupFiles();
        List<String> paths = new ArrayList<>();
        for (BackupFile file : files) paths.add(file.path);
        return paths;
    }

    public List<BackupFile> listBackupFiles() {
        RequestBody body = RequestBody.create(
                "<?xml version=\"1.0\" encoding=\"utf-8\"?><propfind xmlns=\"DAV:\"><prop><displayname/><getcontentlength/><getlastmodified/></prop></propfind>",
                MediaType.parse("application/xml; charset=utf-8"));
        Request request;
        try {
            request = new Request.Builder()
                    .url(baseUrl + "/")
                    .method("PROPFIND", body)
                    .header("Depth", "1")
                    .header("Authorization", Credentials.basic(username, password))
                    .build();
        } catch (IllegalArgumentException e) {
            Log.e("WebDAV", "Invalid list url", e);
            return Collections.emptyList();
        }
        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) return Collections.emptyList();
            return parseBackupFiles(response.body().string());
        } catch (IOException e) {
            Log.e("WebDAV", "List error", e);
            return Collections.emptyList();
        }
    }

    private List<String> parseBackupPaths(String xml) {
        List<String> paths = new ArrayList<>();
        for (BackupFile file : parseBackupFiles(xml)) paths.add(file.path);
        Collections.sort(paths, (a, b) -> b.compareTo(a));
        return paths;
    }

    private List<BackupFile> parseBackupFiles(String xml) {
        List<BackupFile> files = new ArrayList<>();
        int index = 0;
        String lowerXml = xml.toLowerCase();
        while (true) {
            int start = nextResponseStart(lowerXml, index);
            if (start < 0) break;
            int end = nextResponseEnd(lowerXml, start);
            if (end < 0) break;
            String block = xml.substring(start, end);
            String href = firstTagValue(block, "href");
            String decoded = Uri.decode(href == null ? "" : href);
            int slash = decoded.lastIndexOf('/');
            String name = slash >= 0 ? decoded.substring(slash + 1) : decoded;
            if (isBackupName(name)) {
                long size = parseLong(firstTagValue(block, "getcontentlength"));
                String lastModified = firstTagValue(block, "getlastmodified");
                files.add(new BackupFile("/" + name, name, size, lastModified == null ? "" : lastModified));
            }
            index = end;
        }
        Collections.sort(files, (a, b) -> b.name.compareTo(a.name));
        return files;
    }

    private int nextResponseStart(String xml, int from) {
        int prefixed = xml.indexOf("<d:response", from);
        int plain = xml.indexOf("<response", from);
        if (prefixed < 0) return plain;
        if (plain < 0) return prefixed;
        return Math.min(prefixed, plain);
    }

    private int nextResponseEnd(String xml, int from) {
        int prefixed = xml.indexOf("</d:response>", from);
        int plain = xml.indexOf("</response>", from);
        if (prefixed < 0) return plain < 0 ? -1 : plain + "</response>".length();
        if (plain < 0) return prefixed + "</d:response>".length();
        return Math.min(prefixed + "</d:response>".length(), plain + "</response>".length());
    }

    private String firstTagValue(String xml, String tagName) {
        String lower = xml.toLowerCase();
        String lowerTag = tagName.toLowerCase();
        String[] openTags = {"<d:" + lowerTag, "<D:" + tagName, "<" + lowerTag};
        for (String openTag : openTags) {
            int open = lower.indexOf(openTag.toLowerCase());
            if (open < 0) continue;
            int contentStart = lower.indexOf('>', open);
            if (contentStart < 0) continue;
            String closeTag;
            if (openTag.toLowerCase().startsWith("<d:")) closeTag = "</d:" + lowerTag + ">";
            else closeTag = "</" + lowerTag + ">";
            int close = lower.indexOf(closeTag, contentStart + 1);
            if (close < 0) continue;
            return xml.substring(contentStart + 1, close).trim();
        }
        return null;
    }

    private boolean isBackupName(String name) {
        String lower = name == null ? "" : name.toLowerCase();
        return lower.endsWith(".dat") && (lower.contains("backup") || lower.equals("secure_backup.dat"));
    }

    private long parseLong(String value) {
        if (value == null || value.isEmpty()) return 0L;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static class BackupFile {
        public final String path;
        public final String name;
        public final long size;
        public final String lastModified;

        public BackupFile(String path, String name, long size, String lastModified) {
            this.path = path;
            this.name = name;
            this.size = size;
            this.lastModified = lastModified;
        }
    }

    private Request.Builder authorizedBuilder(String remotePath) {
        String path = remotePath.startsWith("/") ? remotePath : "/" + remotePath;
        return new Request.Builder()
                .url(baseUrl + path)
                .header("Authorization", Credentials.basic(username, password));
    }

    private String normalizeBaseUrl(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}

