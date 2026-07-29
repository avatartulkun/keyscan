package com.secureqr.scanner.autofill;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.service.autofill.Dataset;
import android.service.autofill.FillRequest;
import android.service.autofill.InlinePresentation;
import android.text.TextUtils;
import android.view.autofill.AutofillId;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.RemoteViews;
import android.widget.inline.InlinePresentationSpec;

import androidx.annotation.RequiresApi;
import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.v1.InlineSuggestionUi;

import com.secureqr.scanner.R;

import java.util.List;

/** Android 11+ keyboard-strip presentations, with menu presentations kept as fallback. */
final class InlineAutofillSupport {
    private InlineAutofillSupport() {
    }

    static Session forRequest(Context context, FillRequest request) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return Session.disabled();
        return Api30.createSession(context, request);
    }

    static void setValue(Dataset.Builder builder, AutofillId id, RemoteViews menuPresentation,
                         Presentation inlinePresentation) {
        if (id == null) return;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
            Api30.setValue(builder, id, menuPresentation, inlinePresentation.value);
        } else {
            builder.setValue(id, null);
        }
    }

    static final class Session {
        private final Context context;
        private final InlineSuggestionsRequest request;
        private final PendingIntent attribution;
        private int nextIndex;

        private Session(Context context, InlineSuggestionsRequest request, PendingIntent attribution) {
            this.context = context;
            this.request = request;
            this.attribution = attribution;
        }

        static Session disabled() {
            return new Session(null, null, null);
        }

        Presentation next(CharSequence title, CharSequence subtitle) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R || request == null) return null;
            return Api30.createPresentation(this, title, subtitle);
        }
    }

    static final class Presentation {
        private final InlinePresentation value;

        private Presentation(InlinePresentation value) {
            this.value = value;
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private static final class Api30 {
        static Session createSession(Context context, FillRequest fillRequest) {
            InlineSuggestionsRequest request = fillRequest.getInlineSuggestionsRequest();
            if (request == null || request.getMaxSuggestionCount() == 0
                    || request.getInlinePresentationSpecs().isEmpty()) {
                return Session.disabled();
            }
            Intent attributionIntent = new Intent(context, AutofillGuideActivity.class)
                    .setAction("com.secureqr.scanner.autofill.INLINE_ATTRIBUTION." + fillRequest.getId());
            PendingIntent attribution = PendingIntent.getActivity(
                    context,
                    fillRequest.getId(),
                    attributionIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
            );
            return new Session(context.getApplicationContext(), request, attribution);
        }

        static Presentation createPresentation(Session session, CharSequence title, CharSequence subtitle) {
            int maxCount = session.request.getMaxSuggestionCount();
            if (maxCount != InlineSuggestionsRequest.SUGGESTION_COUNT_UNLIMITED
                    && session.nextIndex >= maxCount) {
                return null;
            }
            List<InlinePresentationSpec> specs = session.request.getInlinePresentationSpecs();
            InlinePresentationSpec spec = specs.get(Math.min(session.nextIndex, specs.size() - 1));
            session.nextIndex++;
            if (!UiVersions.getVersions(spec.getStyle()).contains(UiVersions.INLINE_UI_VERSION_1)) {
                return null;
            }

            InlineSuggestionUi.Content.Builder content = InlineSuggestionUi
                    .newContentBuilder(session.attribution)
                    .setContentDescription(title)
                    .setTitle(title)
                    .setStartIcon(Icon.createWithResource(session.context, R.mipmap.ic_launcher));
            if (!TextUtils.isEmpty(subtitle)) content.setSubtitle(subtitle);
            return new Presentation(new InlinePresentation(content.build().getSlice(), spec, false));
        }

        static void setValue(Dataset.Builder builder, AutofillId id, RemoteViews menuPresentation,
                             InlinePresentation inlinePresentation) {
            builder.setValue(id, null, menuPresentation, inlinePresentation);
        }
    }
}
