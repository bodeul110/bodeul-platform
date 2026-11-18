package com.example.bodeul.ui.support;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import com.example.bodeul.R;

public final class FaqActivity extends AppCompatActivity {
    private static final String CATEGORY = "faq.category";
    private static final String QUESTION = "faq.question";
    private FaqSelection selection;

    public static Intent createIntent(Context context) { return new Intent(context, FaqActivity.class); }

    @Override protected void onCreate(@Nullable Bundle saved) {
        super.onCreate(saved);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_faq);
        View root = findViewById(R.id.faqRoot);
        ViewCompat.setOnApplyWindowInsetsListener(root, (view, windowInsets) -> {
            Insets safe = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
            view.setPadding(safe.left, safe.top, safe.right, safe.bottom);
            return windowInsets;
        });
        ViewCompat.requestApplyInsets(root);
        findViewById(R.id.faqBack).setOnClickListener(view -> finish());
        ViewCompat.setAccessibilityHeading(findViewById(R.id.faqTitle), true);
        selection = FaqSelection.restore(saved == null ? null : saved.getString(CATEGORY),
                saved == null ? null : saved.getString(QUESTION));
        new FaqBinder(root, selection).bind();
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle out) {
        super.onSaveInstanceState(out);
        out.putString(CATEGORY, selection.category().name());
        if (selection.expanded() != null) out.putString(QUESTION, selection.expanded().name());
    }
}
