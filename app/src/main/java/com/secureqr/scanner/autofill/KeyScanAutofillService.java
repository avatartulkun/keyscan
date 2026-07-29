package com.secureqr.scanner.autofill;

import android.app.PendingIntent;
import android.app.assist.AssistStructure;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.os.Build;
import android.os.CancellationSignal;
import android.service.autofill.AutofillService;
import android.service.autofill.Dataset;
import android.service.autofill.FillCallback;
import android.service.autofill.FillContext;
import android.service.autofill.FillRequest;
import android.service.autofill.FillResponse;
import android.service.autofill.SaveCallback;
import android.service.autofill.SaveInfo;
import android.service.autofill.SaveRequest;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Pair;
import android.view.View;
import android.view.autofill.AutofillId;
import android.view.autofill.AutofillValue;
import android.view.ViewStructure;
import android.widget.RemoteViews;

import com.secureqr.scanner.R;
import com.secureqr.scanner.data.model.PasswordEntry;
import com.secureqr.scanner.security.VaultAccessManager;
import com.secureqr.scanner.utils.PasswordGeneratorEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class KeyScanAutofillService extends AutofillService {
    public static final String EXTRA_CREDENTIAL_ID = "com.secureqr.scanner.autofill.CREDENTIAL_ID";
    public static final String EXTRA_USERNAME_ID = "com.secureqr.scanner.autofill.USERNAME_ID";
    public static final String EXTRA_PASSWORD_ID = "com.secureqr.scanner.autofill.PASSWORD_ID";
    public static final String EXTRA_TITLE = "com.secureqr.scanner.autofill.TITLE";
    public static final String EXTRA_USERNAME = "com.secureqr.scanner.autofill.USERNAME";
    public static final String EXTRA_DOMAIN = "com.secureqr.scanner.autofill.DOMAIN";
    public static final String EXTRA_SCHEME = "com.secureqr.scanner.autofill.SCHEME";
    public static final String EXTRA_PACKAGE_NAME = "com.secureqr.scanner.autofill.PACKAGE";
    public static final String EXTRA_MODE = "com.secureqr.scanner.autofill.MODE";
    public static final String EXTRA_NEW_PASSWORD_ID = "com.secureqr.scanner.autofill.NEW_PASSWORD_ID";
    public static final String EXTRA_CONFIRM_PASSWORD_ID = "com.secureqr.scanner.autofill.CONFIRM_PASSWORD_ID";
    public static final String EXTRA_MAX_LENGTH = "com.secureqr.scanner.autofill.MAX_LENGTH";
    public static final String MODE_FILL = "fill";
    public static final String MODE_SEARCH = "search";
    public static final String MODE_GENERATE = "generate";

    private static final String CLIENT_DOMAIN = "client_domain";
    private static final String CLIENT_PACKAGE = "client_package";
    private static final String CLIENT_SCENARIO = "client_scenario";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override
    public void onFillRequest(FillRequest request, CancellationSignal cancellationSignal, FillCallback callback) {
        executor.execute(() -> {
            try {
                List<FillContext> contexts = request.getFillContexts();
                if (contexts == null || contexts.isEmpty()) {
                    callback.onSuccess(null);
                    return;
                }
                AssistStructure structure = contexts.get(contexts.size() - 1).getStructure();
                FieldSnapshot fields = extractFields(structure, true);
                if (getPackageName().equals(fields.packageName)) {
                    AutofillDiagnostics.record(this, fields.packageName, fields.webDomain, "SELF", fields.textFieldCount, fields.passwordFieldCount, 0, 0, "ignored", "KeyScan self request");
                    callback.onSuccess(null);
                    return;
                }
                if (!fields.hasAutofillTarget()) {
                    AutofillDiagnostics.record(this, fields.packageName, fields.webDomain, "UNKNOWN", fields.textFieldCount, fields.passwordFieldCount, 0, 0, "no_target", "No username/password field");
                    callback.onSuccess(null);
                    return;
                }
                if (cancellationSignal.isCanceled()) return;

                AutofillScenario scenario = detectScenario(fields);
                InlineAutofillSupport.Session inlineSession = InlineAutofillSupport.forRequest(this, request);
                boolean unlocked = VaultAccessManager.canAccessSensitiveData(this);
                List<PasswordEntry> matches = unlocked
                        ? new PasswordAutofillRepository(this).findMatches(fields.packageName, fields.webDomain)
                        : new ArrayList<>();

                FillResponse.Builder response = new FillResponse.Builder();
                int datasetCount = 0;
                Bundle clientState = new Bundle();
                clientState.putString(CLIENT_DOMAIN, fields.webDomain);
                clientState.putString(CLIENT_PACKAGE, fields.packageName);
                clientState.putString(CLIENT_SCENARIO, scenario.name());
                response.setClientState(clientState);

                if (!unlocked) {
                    response.addDataset(createLockedDataset(fields, inlineSession));
                    datasetCount++;
                } else if (scenario == AutofillScenario.CHANGE_PASSWORD) {
                    int count = Math.min(matches.size(), 8);
                    for (int i = 0; i < count; i++) {
                        response.addDataset(createCredentialDataset(matches.get(i), fields, inlineSession));
                        datasetCount++;
                    }
                    Dataset generated = createGeneratedPasswordDataset(fields, inlineSession);
                    if (generated != null) { response.addDataset(generated); datasetCount++; }
                } else if (scenario == AutofillScenario.SIGN_UP) {
                    Dataset generated = createGeneratedPasswordDataset(fields, inlineSession);
                    if (generated != null) { response.addDataset(generated); datasetCount++; }
                } else if (scenario == AutofillScenario.LOGIN && !matches.isEmpty()) {
                    int count = Math.min(matches.size(), 8);
                    for (int i = 0; i < count; i++) {
                        response.addDataset(createCredentialDataset(matches.get(i), fields, inlineSession));
                        datasetCount++;
                    }
                } else {
                    response.addDataset(createSearchDataset(fields, inlineSession));
                    datasetCount++;
                }

                SaveInfo saveInfo = createSaveInfo(fields, scenario);
                if (saveInfo != null) {
                    response.setSaveInfo(saveInfo);
                }
                AutofillDiagnostics.record(this, fields.packageName, fields.webDomain, scenario.name(),
                        fields.textFieldCount, fields.passwordFieldCount, matches.size(), datasetCount,
                        !unlocked ? "locked" : matches.isEmpty() ? "no_match" : "matched",
                        !unlocked ? "VaultSession locked; auth dataset returned" : matches.isEmpty() ? "Search/bind dataset returned" : "Credential datasets returned");
                callback.onSuccess(response.build());
            } catch (Exception e) {
                callback.onFailure(getString(R.string.autofill_failed));
            }
        });
    }

    @Override
    public void onSaveRequest(SaveRequest request, SaveCallback callback) {
        executor.execute(() -> {
            try {
                List<FillContext> contexts = request.getFillContexts();
                if (contexts == null || contexts.isEmpty()) {
                    callback.onSuccess();
                    return;
                }
                AssistStructure structure = contexts.get(contexts.size() - 1).getStructure();
                FieldSnapshot fields = extractFields(structure, true);
                if (!VaultAccessManager.canAccessSensitiveData(this)) {
                    callback.onFailure(getString(R.string.autofill_unlock_keyscan_first));
                    return;
                }
                Bundle clientState = request.getClientState();
                AutofillScenario scenario = scenarioFromClientState(clientState, detectScenario(fields));
                String passwordToSave = fields.passwordValueForSave(scenario);
                if (scenario == AutofillScenario.UNKNOWN
                        && !TextUtils.isEmpty(fields.usernameValue)
                        && !TextUtils.isEmpty(passwordToSave)) {
                    scenario = AutofillScenario.SIGN_UP;
                }
                if (scenario != AutofillScenario.CHANGE_PASSWORD
                        && (TextUtils.isEmpty(fields.usernameValue) || TextUtils.isEmpty(passwordToSave))) {
                    callback.onSuccess();
                    return;
                }

                String webDomain = AutofillCredentialMatcher.normalizeDomain(clientState == null
                        ? fields.webDomain
                        : clientState.getString(CLIENT_DOMAIN, fields.webDomain));
                String packageName = AutofillCredentialMatcher.normalizePackage(clientState == null
                        ? fields.packageName
                        : clientState.getString(CLIENT_PACKAGE, fields.packageName));
                PasswordAutofillRepository repository = new PasswordAutofillRepository(this);
                if (scenario == AutofillScenario.CHANGE_PASSWORD) {
                    repository.updatePasswordWithHistory(
                            webDomain,
                            packageName,
                            fields.usernameValue,
                            fields.currentPasswordValue,
                            passwordToSave
                    );
                } else if (scenario == AutofillScenario.SIGN_UP || scenario == AutofillScenario.LOGIN) {
                    long savedId = repository.saveFromAutofill(webDomain, packageName, fields.usernameValue, passwordToSave);
                    com.secureqr.scanner.data.repository.PasswordGenerationRepository.getInstance(this)
                            .linkSavedEntry(passwordToSave, savedId, TextUtils.isEmpty(webDomain) ? packageName : webDomain, fields.usernameValue);
                }
                GeneratedPasswordSessionStore.clear(fields.generatedPasswordSessionKey());
                callback.onSuccess();
            } catch (Exception e) {
                callback.onFailure(getString(R.string.autofill_save_failed));
            }
        });
    }

    @Override
    public void onDestroy() {
        executor.shutdownNow();
        GeneratedPasswordSessionStore.clearAll();
        super.onDestroy();
    }

    private Dataset createCredentialDataset(PasswordEntry entry, FieldSnapshot fields,
                                            InlineAutofillSupport.Session inlineSession) {
        RemoteViews presentation = createPresentation(
                AutofillCredentialMatcher.displayTitle(entry),
                AutofillCredentialMatcher.displayUsername(entry),
                getString(R.string.autofill_candidate_badge)
        );
        Intent intent = baseAuthIntent(fields)
                .putExtra(EXTRA_MODE, MODE_FILL)
                .putExtra(EXTRA_CREDENTIAL_ID, entry.id)
                .putExtra(EXTRA_TITLE, AutofillCredentialMatcher.displayTitle(entry))
                .putExtra(EXTRA_USERNAME, AutofillCredentialMatcher.displayUsername(entry));
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                (int) (entry.id % Integer.MAX_VALUE),
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Dataset.Builder builder = new Dataset.Builder(presentation)
                .setAuthentication(pendingIntent.getIntentSender());
        InlineAutofillSupport.Presentation inline = inlineSession.next(
                AutofillCredentialMatcher.displayTitle(entry), AutofillCredentialMatcher.displayUsername(entry));
        InlineAutofillSupport.setValue(builder, fields.usernameId, presentation, inline);
        AutofillId passwordTarget = fields.currentPasswordId != null ? fields.currentPasswordId : fields.passwordId;
        InlineAutofillSupport.setValue(builder, passwordTarget, presentation, inline);
        return builder.build();
    }

    private Dataset createGeneratedPasswordDataset(FieldSnapshot fields,
                                                   InlineAutofillSupport.Session inlineSession) {
        AutofillId primaryTarget = fields.newPasswordId != null ? fields.newPasswordId : fields.passwordId;
        String primaryValue = fields.newPasswordId != null ? fields.newPasswordValue : fields.passwordValue;
        if (primaryTarget == null || !TextUtils.isEmpty(primaryValue)) return null;
        RemoteViews presentation = createPresentation(
                getString(R.string.autofill_generate_password),
                fields.hasWeakLengthLimit()
                        ? getString(R.string.autofill_weak_length_limit_hint)
                        : getString(R.string.autofill_generate_password_hint),
                getString(R.string.autofill_candidate_badge)
        );
        Intent intent = baseAuthIntent(fields)
                .putExtra(EXTRA_MODE, MODE_GENERATE)
                .putExtra(EXTRA_NEW_PASSWORD_ID, fields.newPasswordId != null ? fields.newPasswordId : fields.passwordId)
                .putExtra(EXTRA_CONFIRM_PASSWORD_ID, fields.confirmNewPasswordId)
                .putExtra(EXTRA_MAX_LENGTH, fields.passwordMaxLength());
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                fields.generatedPasswordSessionKey().hashCode(),
                intent,
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Dataset.Builder builder = new Dataset.Builder(presentation)
                .setAuthentication(pendingIntent.getIntentSender());
        CharSequence subtitle = fields.hasWeakLengthLimit()
                ? getString(R.string.autofill_weak_length_limit_hint)
                : getString(R.string.autofill_generate_password_hint);
        InlineAutofillSupport.Presentation inline = inlineSession.next(
                getString(R.string.autofill_generate_password), subtitle);
        InlineAutofillSupport.setValue(builder,
                fields.newPasswordId != null ? fields.newPasswordId : fields.passwordId,
                presentation, inline);
        InlineAutofillSupport.setValue(builder, fields.confirmNewPasswordId, presentation, inline);
        return builder.build();
    }

    private Dataset createSearchDataset(FieldSnapshot fields, InlineAutofillSupport.Session inlineSession) {
        RemoteViews presentation = createPresentation(
                getString(R.string.autofill_search_vault),
                getString(R.string.autofill_search_vault_hint),
                getString(R.string.autofill_candidate_badge)
        );
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                17391,
                baseAuthIntent(fields).putExtra(EXTRA_MODE, MODE_SEARCH),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Dataset.Builder builder = new Dataset.Builder(presentation)
                .setAuthentication(pendingIntent.getIntentSender());
        InlineAutofillSupport.Presentation inline = inlineSession.next(
                getString(R.string.autofill_search_vault), getString(R.string.autofill_search_vault_hint));
        InlineAutofillSupport.setValue(builder, fields.usernameId, presentation, inline);
        InlineAutofillSupport.setValue(builder, fields.passwordId, presentation, inline);
        InlineAutofillSupport.setValue(builder, fields.currentPasswordId, presentation, inline);
        InlineAutofillSupport.setValue(builder, fields.newPasswordId, presentation, inline);
        InlineAutofillSupport.setValue(builder, fields.confirmNewPasswordId, presentation, inline);
        return builder.build();
    }

    private Dataset createLockedDataset(FieldSnapshot fields, InlineAutofillSupport.Session inlineSession) {
        RemoteViews presentation = createPresentation(
                getString(R.string.autofill_unlock_keyscan),
                getString(R.string.autofill_authentication_description),
                getString(R.string.autofill_candidate_badge)
        );
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this,
                17392,
                baseAuthIntent(fields).putExtra(EXTRA_MODE, MODE_SEARCH),
                PendingIntent.FLAG_CANCEL_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        Dataset.Builder builder = new Dataset.Builder(presentation)
                .setAuthentication(pendingIntent.getIntentSender());
        InlineAutofillSupport.Presentation inline = inlineSession.next(
                getString(R.string.autofill_unlock_keyscan), getString(R.string.autofill_authentication_description));
        InlineAutofillSupport.setValue(builder, fields.usernameId, presentation, inline);
        InlineAutofillSupport.setValue(builder, fields.passwordId, presentation, inline);
        InlineAutofillSupport.setValue(builder, fields.currentPasswordId, presentation, inline);
        InlineAutofillSupport.setValue(builder, fields.newPasswordId, presentation, inline);
        InlineAutofillSupport.setValue(builder, fields.confirmNewPasswordId, presentation, inline);
        return builder.build();
    }

    private Intent baseAuthIntent(FieldSnapshot fields) {
        Intent intent = new Intent(this, AutofillAuthActivity.class);
        intent.setPackage(getPackageName());
        intent.putExtra(EXTRA_USERNAME_ID, fields.usernameId);
        intent.putExtra(EXTRA_PASSWORD_ID, fields.passwordId);
        intent.putExtra(EXTRA_DOMAIN, fields.webDomain);
        intent.putExtra(EXTRA_SCHEME, fields.webScheme);
        intent.putExtra(EXTRA_PACKAGE_NAME, fields.packageName);
        intent.putExtra(EXTRA_USERNAME, fields.usernameValue);
        return intent;
    }

    private RemoteViews createPresentation(String title, String subtitle, String badge) {
        RemoteViews views = new RemoteViews(getPackageName(), R.layout.item_autofill_credential);
        views.setTextViewText(R.id.tv_autofill_title, title);
        views.setTextViewText(R.id.tv_autofill_subtitle, subtitle);
        views.setTextViewText(R.id.tv_autofill_badge, badge);
        return views;
    }

    private SaveInfo createSaveInfo(FieldSnapshot fields, AutofillScenario scenario) {
        List<AutofillId> required = new ArrayList<>();
        AutofillId primaryPasswordId = primaryPasswordIdForSave(fields, scenario);
        if (primaryPasswordId == null) return null;
        required.add(primaryPasswordId);

        List<AutofillId> optional = new ArrayList<>();
        addOptionalId(optional, fields.usernameId, required);
        addOptionalId(optional, fields.passwordId, required);
        addOptionalId(optional, fields.currentPasswordId, required);
        addOptionalId(optional, fields.newPasswordId, required);
        addOptionalId(optional, fields.confirmNewPasswordId, required);

        int dataTypes = SaveInfo.SAVE_DATA_TYPE_PASSWORD;
        if (fields.usernameId != null) {
            dataTypes |= SaveInfo.SAVE_DATA_TYPE_USERNAME | SaveInfo.SAVE_DATA_TYPE_EMAIL_ADDRESS;
        }
        SaveInfo.Builder saveInfo = new SaveInfo.Builder(dataTypes, required.toArray(new AutofillId[0]));
        if (!optional.isEmpty()) {
            saveInfo.setOptionalIds(optional.toArray(new AutofillId[0]));
        }
        if (scenario == AutofillScenario.CHANGE_PASSWORD) {
            saveInfo.setDescription(getString(R.string.autofill_update_saved_password_prompt));
        }
        return saveInfo.build();
    }

    private AutofillId primaryPasswordIdForSave(FieldSnapshot fields, AutofillScenario scenario) {
        if (scenario == AutofillScenario.CHANGE_PASSWORD) {
            if (fields.newPasswordId != null) return fields.newPasswordId;
            if (fields.confirmNewPasswordId != null) return fields.confirmNewPasswordId;
        }
        if (fields.passwordId != null) return fields.passwordId;
        if (fields.newPasswordId != null) return fields.newPasswordId;
        if (fields.confirmNewPasswordId != null) return fields.confirmNewPasswordId;
        return fields.currentPasswordId;
    }

    private void addOptionalId(List<AutofillId> optional, AutofillId id, List<AutofillId> required) {
        if (id == null || required.contains(id) || optional.contains(id)) return;
        optional.add(id);
    }

    private AutofillScenario scenarioFromClientState(Bundle clientState, AutofillScenario fallback) {
        if (clientState == null) return fallback;
        String value = clientState.getString(CLIENT_SCENARIO, "");
        try {
            return AutofillScenario.valueOf(value);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private FieldSnapshot extractFields(AssistStructure structure, boolean includeValues) {
        FieldSnapshot fields = new FieldSnapshot();
        ComponentName componentName = structure.getActivityComponent();
        fields.packageName = componentName == null ? "" : componentName.getPackageName();
        fields.activityName = componentName == null ? "" : componentName.flattenToShortString();
        for (int i = 0; i < structure.getWindowNodeCount(); i++) {
            traverseNode(structure.getWindowNodeAt(i).getRootViewNode(), fields, includeValues);
        }
        fields.normalizePasswordRoles();
        if (fields.passwordId == null) {
            fields.passwordId = fields.currentPasswordId != null ? fields.currentPasswordId : fields.newPasswordId;
        }
        if (fields.usernameId == null && fields.passwordId != null) {
            fields.usernameId = fields.lastTextId;
            fields.usernameValue = fields.lastTextValue;
        }
        fields.webDomain = AutofillCredentialMatcher.normalizeDomain(fields.webDomain);
        fields.packageName = AutofillCredentialMatcher.normalizePackage(fields.packageName);
        if (!fields.hasAutofillTarget() && !fields.webDomain.isEmpty()) {
            fields.usernameId = fields.focusedTextId != null ? fields.focusedTextId : fields.lastTextId;
            fields.usernameValue = fields.focusedTextId != null ? fields.focusedTextValue : fields.lastTextValue;
        }
        return fields;
    }

    private void traverseNode(AssistStructure.ViewNode node, FieldSnapshot fields, boolean includeValues) {
        if (node == null) return;
        if (!TextUtils.isEmpty(node.getWebDomain())) {
            fields.webDomain = node.getWebDomain();
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !TextUtils.isEmpty(node.getWebScheme())) {
            fields.webScheme = node.getWebScheme();
        }
        AutofillId autofillId = node.getAutofillId();
        String text = nodeText(node);
        // Registration intent is often exposed by a heading or submit button, not by an
        // autofillable input. Keep all visible/semantic node text so those signals are not lost.
        fields.pageText.append(' ').append(text);
        boolean passwordNode = isPasswordNode(node, text);
        boolean usernameNode = isUsernameNode(node, text);
        boolean textNode = autofillId != null && (node.getAutofillType() == View.AUTOFILL_TYPE_TEXT
                || node.getAutofillType() == View.AUTOFILL_TYPE_NONE);
        if (textNode) {
            fields.textFieldCount++;
            AutofillValue value = node.getAutofillValue();
            String currentValue = value != null && value.isText() ? String.valueOf(value.getTextValue()) : "";
            if (passwordNode) {
                fields.addPasswordField(passwordRole(node, text), autofillId, includeValues ? currentValue : "", maxTextLength(node));
            } else if (usernameNode && fields.usernameId == null) {
                fields.usernameId = autofillId;
                fields.hasNewUsername = isNewUsernameNode(node, text);
                if (includeValues) fields.usernameValue = currentValue;
            } else if (!passwordNode) {
                fields.lastTextId = autofillId;
                if (includeValues) fields.lastTextValue = currentValue;
            }
            if (node.isFocused()) {
                fields.focusedTextId = autofillId;
                if (includeValues) fields.focusedTextValue = currentValue;
            }
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            traverseNode(node.getChildAt(i), fields, includeValues);
        }
    }

    private boolean isPasswordNode(AssistStructure.ViewNode node, String nodeText) {
        String[] hints = node.getAutofillHints();
        if (AutofillHintCompat.isPassword(hints)) return true;
        int variation = node.getInputType() & InputType.TYPE_MASK_VARIATION;
        if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                || variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
                || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
            return true;
        }
        return nodeText.contains("password")
                || nodeText.contains("passwd")
                || nodeText.contains("pwd")
                || nodeText.contains("密码");
    }

    private boolean isUsernameNode(AssistStructure.ViewNode node, String nodeText) {
        String[] hints = node.getAutofillHints();
        if (AutofillHintCompat.isUsername(hints)) {
            return true;
        }
        return nodeText.contains("username")
                || nodeText.contains("user")
                || nodeText.contains("login")
                || nodeText.contains("email")
                || nodeText.contains("account")
                || nodeText.contains("phone")
                || nodeText.contains("账号")
                || nodeText.contains("邮箱")
                || nodeText.contains("手机");
    }

    private boolean isNewUsernameNode(AssistStructure.ViewNode node, String nodeText) {
        return AutofillHintCompat.isNewUsername(node.getAutofillHints())
                || AutofillHintCompat.textHasNewUsername(nodeText);
    }

    private int maxTextLength(AssistStructure.ViewNode node) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return -1;
        try {
            return node.getMaxTextLength();
        } catch (Throwable ignored) {
            return -1;
        }
    }

    private boolean containsHint(String[] hints, String target) {
        return AutofillHintCompat.contains(hints, target);
    }

    private PasswordFieldRole passwordRole(AssistStructure.ViewNode node, String nodeText) {
        String[] hints = node.getAutofillHints();
        if (AutofillHintCompat.isNewPassword(hints) || AutofillHintCompat.textHasNewPassword(nodeText)) {
            return PasswordFieldRole.NEW_PASSWORD;
        }
        if (containsAny(nodeText, "current-password", "current password", "old-password", "old password", "旧密码", "当前密码", "原密码")) {
            return PasswordFieldRole.CURRENT_PASSWORD;
        }
        if (containsAny(nodeText, "confirm", "confirmation", "repeat", "again", "verify", "确认密码", "重复密码", "再次输入")) {
            return PasswordFieldRole.CONFIRM_NEW_PASSWORD;
        }
        if (containsAny(nodeText, "new-password", "new password", "create password", "set password", "新密码", "设置密码")) {
            return PasswordFieldRole.NEW_PASSWORD;
        }
        return PasswordFieldRole.UNKNOWN_PASSWORD;
    }

    private AutofillScenario detectScenario(FieldSnapshot fields) {
        String text = fields.pageText.toString();
        if (fields.currentPasswordId != null && (fields.newPasswordId != null || fields.confirmNewPasswordId != null)) {
            return AutofillScenario.CHANGE_PASSWORD;
        }
        if (containsAny(text, "change password", "update password", "reset password", "修改密码", "更改密码", "重置密码")) {
            if (fields.passwordFieldCount >= 2) return AutofillScenario.CHANGE_PASSWORD;
        }
        if (fields.hasNewUsername || fields.newPasswordId != null || fields.confirmNewPasswordId != null
                || isSignUpText(text)) {
            return AutofillScenario.SIGN_UP;
        }
        if (fields.usernameId != null && (fields.passwordId != null || fields.currentPasswordId != null)
                && !containsAny(text, "feedback", "comment", "opinion", "意见反馈", "验证码", "verification code")) {
            return AutofillScenario.LOGIN;
        }
        return AutofillScenario.UNKNOWN;
    }

    private boolean isSignUpText(String text) {
        return containsAny(text,
                "register", "registration", "sign up", "signup", "create account",
                "create an account", "new account", "join now",
                "注册", "註冊", "创建账号", "创建账户", "建立帳號", "建立帳戶",
                "新規登録", "アカウント作成", "会員登録",
                "회원가입", "계정 만들기",
                "registrieren", "konto erstellen",
                "inscription", "s'inscrire", "s’inscrire", "créer un compte",
                "registrarse", "crear una cuenta",
                "registrati", "crea un account",
                "registreren", "account aanmaken",
                "cadastre-se", "criar uma conta",
                "регистрация", "зарегистрироваться", "создать аккаунт");
    }

    private boolean containsAny(String text, String... needles) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (needle != null && lower.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private String generateStrongPassword(int maxLength) {
        PasswordGeneratorEngine.Options options = new PasswordGeneratorEngine.Options();
        options.length = maxLength > 0 ? Math.min(16, maxLength) : 16;
        options.includeUpper = true;
        options.includeLower = true;
        options.includeDigits = true;
        options.includeSymbols = true;
        options.excludeZeroO = true;
        options.excludeLowerO = true;
        options.excludeOneI = true;
        options.excludeLowerL = true;
        return PasswordGeneratorEngine.generate(options);
    }

    private String nodeText(AssistStructure.ViewNode node) {
        StringBuilder builder = new StringBuilder();
        append(builder, node.getIdEntry());
        append(builder, node.getIdType());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            append(builder, node.getTextIdEntry());
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            append(builder, node.getHintIdEntry());
        }
        append(builder, node.getHint());
        append(builder, node.getText() == null ? "" : node.getText().toString());
        append(builder, node.getClassName() == null ? "" : node.getClassName().toString());
        String[] hints = node.getAutofillHints();
        if (hints != null) {
            for (String hint : hints) append(builder, hint);
        }
        ViewStructure.HtmlInfo htmlInfo = node.getHtmlInfo();
        if (htmlInfo != null) {
            append(builder, htmlInfo.getTag());
            List<Pair<String, String>> attributes = htmlInfo.getAttributes();
            if (attributes != null) {
                for (Pair<String, String> attribute : attributes) {
                    if (attribute == null) continue;
                    append(builder, attribute.first);
                    append(builder, attribute.second);
                }
            }
        }
        return builder.toString().toLowerCase(Locale.ROOT);
    }

    private void append(StringBuilder builder, String value) {
        if (value == null) return;
        builder.append(' ').append(value);
    }

    private static class FieldSnapshot {
        AutofillId usernameId;
        AutofillId passwordId;
        AutofillId currentPasswordId;
        AutofillId newPasswordId;
        AutofillId confirmNewPasswordId;
        AutofillId lastTextId;
        AutofillId focusedTextId;
        String usernameValue = "";
        String passwordValue = "";
        String currentPasswordValue = "";
        String newPasswordValue = "";
        String confirmNewPasswordValue = "";
        String lastTextValue = "";
        String focusedTextValue = "";
        String webDomain = "";
        String webScheme = "";
        String packageName = "";
        String activityName = "";
        StringBuilder pageText = new StringBuilder();
        int passwordFieldCount;
        int textFieldCount;
        int passwordMaxLength = -1;
        boolean hasNewUsername;

        void addPasswordField(PasswordFieldRole role, AutofillId id, String value) {
            passwordFieldCount++;
            if (role == PasswordFieldRole.CURRENT_PASSWORD && currentPasswordId == null) {
                currentPasswordId = id;
                currentPasswordValue = value;
                return;
            }
            if (role == PasswordFieldRole.NEW_PASSWORD && newPasswordId == null) {
                newPasswordId = id;
                newPasswordValue = value;
                return;
            }
            if (role == PasswordFieldRole.CONFIRM_NEW_PASSWORD && confirmNewPasswordId == null) {
                confirmNewPasswordId = id;
                confirmNewPasswordValue = value;
                return;
            }
            if (passwordId == null) {
                passwordId = id;
                passwordValue = value;
            } else if (newPasswordId == null && passwordFieldCount >= 2) {
                newPasswordId = id;
                newPasswordValue = value;
            } else if (confirmNewPasswordId == null && passwordFieldCount >= 3) {
                confirmNewPasswordId = id;
                confirmNewPasswordValue = value;
            }
        }

        void addPasswordField(PasswordFieldRole role, AutofillId id, String value, int maxLength) {
            if (passwordMaxLength <= 0 || (maxLength > 0 && maxLength < passwordMaxLength)) {
                passwordMaxLength = maxLength;
            }
            addPasswordField(role, id, value);
        }

        boolean hasAutofillTarget() {
            return usernameId != null || passwordId != null || currentPasswordId != null
                    || newPasswordId != null || confirmNewPasswordId != null;
        }

        void normalizePasswordRoles() {
            String text = pageText.toString();
            boolean changePasswordPage = containsAnyStatic(text,
                    "change password", "update password", "reset password",
                    "修改密码", "更改密码", "重置密码");
            if (!changePasswordPage) return;
            if (currentPasswordId == null && passwordId != null && newPasswordId != null) {
                currentPasswordId = passwordId;
                currentPasswordValue = passwordValue;
                passwordId = null;
                passwordValue = "";
            }
        }

        String passwordValueForSave(AutofillScenario scenario) {
            if (scenario == AutofillScenario.CHANGE_PASSWORD) {
                return !TextUtils.isEmpty(newPasswordValue) ? newPasswordValue : confirmNewPasswordValue;
            }
            return !TextUtils.isEmpty(passwordValue) ? passwordValue : newPasswordValue;
        }

        int passwordMaxLength() {
            return passwordMaxLength;
        }

        boolean hasWeakLengthLimit() {
            return passwordMaxLength > 0 && passwordMaxLength < 12;
        }

        String generatedPasswordSessionKey() {
            String primary = newPasswordId != null ? newPasswordId.toString()
                    : passwordId != null ? passwordId.toString()
                    : currentPasswordId != null ? currentPasswordId.toString()
                    : "none";
            String user = usernameId == null ? "nouser" : usernameId.toString();
            return packageName + "|" + webDomain + "|" + activityName + "|" + user + "|" + primary;
        }
    }

    private static boolean containsAnyStatic(String text, String... needles) {
        if (text == null) return false;
        String lower = text.toLowerCase(Locale.ROOT);
        for (String needle : needles) {
            if (needle != null && lower.contains(needle.toLowerCase(Locale.ROOT))) return true;
        }
        return false;
    }

    private enum AutofillScenario {
        LOGIN,
        SIGN_UP,
        CHANGE_PASSWORD,
        UNKNOWN
    }

    private enum PasswordFieldRole {
        CURRENT_PASSWORD,
        NEW_PASSWORD,
        CONFIRM_NEW_PASSWORD,
        UNKNOWN_PASSWORD
    }
}
