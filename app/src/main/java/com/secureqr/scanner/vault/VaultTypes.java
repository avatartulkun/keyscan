package com.secureqr.scanner.vault;

import com.secureqr.scanner.R;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;

public final class VaultTypes {
    public static final String KEYS = "KEYS_LICENSES", IDENTITY = "IDENTITY", FINANCIAL = "FINANCIAL";
    public static final String CONTACT = "CONTACT", FILES = "SECURE_FILES", CUSTOM = "CUSTOM";

    public static final class Field {
        public final String key; public final int labelRes; public final boolean secret; public final boolean date;
        Field(String key, int labelRes) { this(key, labelRes, false, false); }
        Field(String key, int labelRes, boolean secret, boolean date) { this.key=key; this.labelRes=labelRes; this.secret=secret; this.date=date; }
    }
    public static final class Type {
        public final String key, category; public final int labelRes; public final List<Field> fields;
        Type(String key, String category, int labelRes, Field... fields) { this.key=key; this.category=category; this.labelRes=labelRes; this.fields=Arrays.asList(fields); }
    }
    public static final class Category {
        public final String key; public final int labelRes; public final List<Type> types;
        Category(String key, int labelRes, Type... types) { this.key=key; this.labelRes=labelRes; this.types=Arrays.asList(types); }
    }

    private static Field f(String key, int label) { return new Field(key,label); }
    private static Field s(String key, int label) { return new Field(key,label,true,false); }
    private static Field d(String key, int label) { return new Field(key,label,false,true); }

    public static final List<Category> CATEGORIES = Arrays.asList(
      new Category(KEYS, R.string.vault_category_keys,
        new Type("SOFTWARE_LICENSE",KEYS,R.string.vault_type_software_license,f("softwareName",R.string.vault_field_software_name),s("licenseNumber",R.string.vault_field_license_number),d("purchaseDate",R.string.vault_field_purchase_date),f("email",R.string.vault_field_email)),
        new Type("SSH_KEY",KEYS,R.string.vault_type_ssh_key,f("name",R.string.vault_field_name),f("server",R.string.vault_field_server),f("port",R.string.vault_field_port),f("username",R.string.vault_field_username),s("publicKey",R.string.vault_field_public_key),s("passphrase",R.string.vault_field_passphrase)),
        new Type("API_KEY",KEYS,R.string.vault_type_api_key,f("service",R.string.vault_field_service),s("apiKey",R.string.vault_field_api_key),s("secret",R.string.vault_field_secret),f("endpoint",R.string.vault_field_endpoint),d("createdDate",R.string.vault_field_created_date),d("expiryDate",R.string.vault_field_expiry_date)),
        new Type("ACCESS_TOKEN",KEYS,R.string.vault_type_access_token,f("service",R.string.vault_field_service),s("token",R.string.vault_field_token),f("scope",R.string.vault_field_scope),d("expiryDate",R.string.vault_field_expiry_date)),
        new Type("CERTIFICATE",KEYS,R.string.vault_type_certificate,f("name",R.string.vault_field_name),d("expiryDate",R.string.vault_field_expiry_date))),
      new Category(IDENTITY, R.string.vault_category_identity,
        new Type("NATIONAL_ID",IDENTITY,R.string.vault_type_national_id,f("documentType",R.string.vault_field_document_type),f("fullName",R.string.vault_field_full_name),s("documentNumber",R.string.vault_field_document_number),f("gender",R.string.vault_field_gender),f("ethnicity",R.string.vault_field_ethnicity),d("birthDate",R.string.vault_field_birth_date),f("address",R.string.vault_field_address),f("authority",R.string.vault_field_authority),d("validFrom",R.string.vault_field_valid_from),d("expiryDate",R.string.vault_field_expiry_date)),
        new Type("PASSPORT",IDENTITY,R.string.vault_type_passport,f("documentType",R.string.vault_field_document_type),f("fullName",R.string.vault_field_full_name),f("surname",R.string.vault_field_surname),f("givenName",R.string.vault_field_given_name),f("nationality",R.string.vault_field_nationality),s("passportNumber",R.string.vault_field_passport_number),d("birthDate",R.string.vault_field_birth_date),d("expiryDate",R.string.vault_field_expiry_date),f("authority",R.string.vault_field_authority)),
        new Type("DRIVER_LICENSE",IDENTITY,R.string.vault_type_driver_license,f("fullName",R.string.vault_field_full_name),s("licenseNumber",R.string.vault_field_license_number),f("licenseClass",R.string.vault_field_license_class),d("expiryDate",R.string.vault_field_expiry_date),f("authority",R.string.vault_field_authority)),
        new Type("SOCIAL_SECURITY",IDENTITY,R.string.vault_type_social_security_card,f("fullName",R.string.vault_field_full_name),s("number",R.string.vault_field_number),f("authority",R.string.vault_field_authority),d("expiryDate",R.string.vault_field_expiry_date)),
        new Type("RESIDENT_REGISTRATION",IDENTITY,R.string.vault_type_resident_registration,f("fullName",R.string.vault_field_full_name),s("documentNumber",R.string.vault_field_document_number),f("authority",R.string.vault_field_authority),d("expiryDate",R.string.vault_field_expiry_date)),
        new Type("OTHER_ID",IDENTITY,R.string.vault_type_other_id,f("documentType",R.string.vault_field_document_type),f("fullName",R.string.vault_field_full_name),s("documentNumber",R.string.vault_field_document_number),f("authority",R.string.vault_field_authority),d("expiryDate",R.string.vault_field_expiry_date))),
      new Category(FINANCIAL, R.string.vault_category_financial,
        new Type("BANK_CARD",FINANCIAL,R.string.vault_type_bank_card,f("bank",R.string.vault_field_bank),f("cardType",R.string.vault_field_card_type),f("cardholder",R.string.vault_field_cardholder),s("cardNumber",R.string.vault_field_card_number),d("expiryDate",R.string.vault_field_expiry_date),s("cvv",R.string.vault_field_cvv),s("pin",R.string.vault_field_pin),s("securityPassword",R.string.vault_field_security_password)),
        new Type("CRYPTO_WALLET",FINANCIAL,R.string.vault_type_crypto_wallet,f("network",R.string.vault_field_network),f("address",R.string.vault_field_wallet_address),s("seed",R.string.vault_field_seed)),
        new Type("PAYMENT",FINANCIAL,R.string.vault_type_payment,f("provider",R.string.vault_field_provider),f("account",R.string.vault_field_account))),
      new Category(CONTACT, R.string.vault_category_contact,
        new Type("EMAIL",CONTACT,R.string.vault_type_email,f("email",R.string.vault_field_email),f("provider",R.string.vault_field_provider)),
        new Type("CONTACT_INFO",CONTACT,R.string.vault_type_contact_info,f("fullName",R.string.vault_field_full_name),f("phone",R.string.vault_field_phone),f("email",R.string.vault_field_email),f("address",R.string.vault_field_address)),
        new Type("OTHER_CONTACT",CONTACT,R.string.vault_type_other_contact,f("provider",R.string.vault_field_provider),f("account",R.string.vault_field_account))),
      new Category(FILES, R.string.vault_category_files,
        new Type("ID_PHOTO",FILES,R.string.vault_type_id_photo),new Type("CONTRACT",FILES,R.string.vault_type_contract),new Type("IMPORTANT_DOCUMENT",FILES,R.string.vault_type_important_document),new Type("OTHER_ATTACHMENT",FILES,R.string.vault_type_other_attachment)),
      new Category(CUSTOM, R.string.vault_category_custom,new Type("CUSTOM",CUSTOM,R.string.vault_type_custom,f("content",R.string.vault_field_content)))
    );

    public static Type find(String key) {
        for (Category category : CATEGORIES) for (Type type : category.types) if (type.key.equals(key)) return type;
        return CATEGORIES.get(5).types.get(0);
    }
    public static String storageType(Type type) {
        return IDENTITY.equals(type.category) ? "IDENTITY_DOCUMENT" : type.key;
    }
    public static Type resolveStored(String storedType, String fieldsJson) {
        if (!"IDENTITY_DOCUMENT".equals(storedType)) return find(storedType);
        try {
            String type = new JSONObject(fieldsJson).optString("documentType", "OTHER_ID");
            if ("US_DRIVER_LICENSE".equals(type) || "MY_NUMBER_CARD".equals(type) || "PERSONALAUSWEIS".equals(type)
                    || "FRANCE_CNI".equals(type) || "KOREA_ID".equals(type) || "CANADA_ID".equals(type) || "AUSTRALIA_ID".equals(type)) type = "OTHER_ID";
            return find(type);
        }
        catch (Exception ignored) { return find("OTHER_ID"); }
    }
    public static boolean matches(Type visibleType, String storedType, String fieldsJson) {
        if (!IDENTITY.equals(visibleType.category)) return visibleType.key.equals(storedType);
        if (!"IDENTITY_DOCUMENT".equals(storedType)) return false;
        try { return visibleType.key.equals(new JSONObject(fieldsJson).optString("documentType")); }
        catch (Exception ignored) { return false; }
    }
    public static Category category(String key) { for(Category c:CATEGORIES) if(c.key.equals(key)) return c; return CATEGORIES.get(5); }
    private VaultTypes() {}
}
