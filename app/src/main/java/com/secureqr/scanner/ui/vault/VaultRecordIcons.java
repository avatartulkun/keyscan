package com.secureqr.scanner.ui.vault;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.VaultItem;
import com.secureqr.scanner.vault.VaultTypes;

import org.json.JSONObject;

import java.util.Locale;

final class VaultRecordIcons {
    static int iconFor(VaultItem item, VaultTypes.Type type) {
        Provider provider = provider(item);
        if (provider != null) return provider.icon;
        switch (type.key) {
            case "SOFTWARE_LICENSE": return R.drawable.ic_vault_license;
            case "SSH_KEY": return R.drawable.ic_key_line;
            case "API_KEY": return R.drawable.ic_key_gear;
            case "ACCESS_TOKEN": return R.drawable.ic_vault_tag;
            case "CERTIFICATE": return R.drawable.ic_vault_certificate;
            case "BANK_CARD": return R.drawable.ic_vault_card;
            case "CRYPTO_WALLET": return R.drawable.ic_vault_wallet;
            case "PASSPORT": return R.drawable.ic_vault_passport;
            case "DRIVER_LICENSE":
            case "US_DRIVER_LICENSE": return R.drawable.ic_vault_car;
            case "NATIONAL_ID":
            case "RESIDENT_REGISTRATION":
            case "SOCIAL_SECURITY":
            case "MY_NUMBER_CARD":
            case "PERSONALAUSWEIS":
            case "FRANCE_CNI":
            case "KOREA_ID":
            case "CANADA_ID":
            case "AUSTRALIA_ID":
            case "OTHER_ID": return R.drawable.ic_vault_identity;
            case "EMAIL":
            case "MAIL_CONFIG": return R.drawable.ic_vault_mail;
            case "CONTACT_INFO":
            case "OTHER_CONTACT": return R.drawable.ic_vault_contact;
            case "ID_PHOTO": return R.drawable.ic_vault_photo;
            case "CONTRACT":
            case "IMPORTANT_DOCUMENT": return R.drawable.ic_vault_contract;
            default: return R.drawable.ic_vault_file_lock;
        }
    }

    static int colorFor(VaultItem item, VaultTypes.Type type) {
        Provider provider = provider(item);
        if (provider != null) return provider.color;
        if (VaultTypes.IDENTITY.equals(type.category)) return R.color.vault_icon_green;
        if (VaultTypes.FINANCIAL.equals(type.category)) return R.color.vault_icon_orange;
        if (VaultTypes.CONTACT.equals(type.category)) return R.color.vault_icon_purple;
        if (VaultTypes.FILES.equals(type.category)) return R.color.vault_icon_cyan;
        if ("SSH_KEY".equals(type.key)) return R.color.vault_icon_cyan;
        if ("ACCESS_TOKEN".equals(type.key)) return R.color.vault_icon_purple;
        return R.color.vault_icon_blue;
    }

    private static Provider provider(VaultItem item) {
        try {
            JSONObject object = new JSONObject(item.fieldsJson == null ? "{}" : item.fieldsJson);
            String explicit = object.optString("providerIcon", object.optString("icon", ""));
            String provider = object.optString("provider", object.optString("service", ""));
            String key = (explicit.trim().isEmpty() ? provider : explicit).toLowerCase(Locale.ROOT);
            if (key.contains("openai")) return new Provider(R.drawable.ic_vault_code, R.color.vault_icon_blue);
            if (key.contains("aws") || key.contains("amazon")) return new Provider(R.drawable.ic_cloud_sync, R.color.vault_icon_orange);
            if (key.contains("github")) return new Provider(R.drawable.ic_vault_code, R.color.vault_icon_purple);
            if (key.contains("google")) return new Provider(R.drawable.ic_vault_gear, R.color.vault_icon_green);
            if (key.contains("microsoft") || key.contains("azure")) return new Provider(R.drawable.ic_vault_certificate, R.color.vault_icon_blue);
            if (key.contains("cloudflare")) return new Provider(R.drawable.ic_shield, R.color.vault_icon_orange);
            if (key.contains("阿里") || key.contains("aliyun")) return new Provider(R.drawable.ic_cloud_sync, R.color.vault_icon_cyan);
            if (key.contains("腾讯") || key.contains("tencent")) return new Provider(R.drawable.ic_cloud_sync, R.color.vault_icon_green);
        } catch (Exception ignored) {}
        return null;
    }

    private static final class Provider {
        final int icon;
        final int color;
        Provider(int icon, int color) { this.icon = icon; this.color = color; }
    }

    private VaultRecordIcons() {}
}
