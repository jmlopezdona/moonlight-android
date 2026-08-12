package com.limelight.profiles;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;

import androidx.test.core.app.ApplicationProvider;

import com.limelight.R;
import com.limelight.TestLogSuppressor;
import com.limelight.preferences.StreamSettings;

import org.junit.After;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * The settings screen edits the global preferences, which an active profile shadows. It has
 * to say so, or a change made there looks like it did nothing.
 */
@Config(sdk = {33}, shadows = {com.limelight.shadows.ShadowMoonBridge.class, com.limelight.shadows.ShadowGameManager.class})
@RunWith(RobolectricTestRunner.class)
public class SettingsProfileNoticeTest {
    private Context context;
    private ProfilesManager pm;
    private File profilesDir;

    @BeforeClass
    public static void suppressLogs() {
        TestLogSuppressor.install();
    }

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        profilesDir = new File(context.getFilesDir(), "profiles");
        deleteRecursively(profilesDir);
        ProfilesManager.instance = null;
        pm = ProfilesManager.getInstance();
        pm.load(context);
    }

    @After
    public void tearDown() {
        deleteRecursively(profilesDir);
    }

    /**
     * The shared LayoutInflationTest stops at the first layout that fails, and one already
     * does, so it never reaches this one.
     */
    @Test
    public void settingsLayout_inflatesWithBothViews() {
        Context themed = new androidx.appcompat.view.ContextThemeWrapper(
                context, androidx.appcompat.R.style.Theme_AppCompat);
        View root = LayoutInflater.from(themed).inflate(R.layout.activity_stream_settings, null);

        assertNotNull("the notice view must exist", root.findViewById(R.id.profileOverrideNotice));
        assertNotNull("the fragment container id must survive the restructure",
                root.findViewById(R.id.stream_settings));
    }

    @Test
    public void withActiveProfile_theNoticeNamesIt() {
        Map<String, Object> options = new HashMap<>();
        options.put("list_resolution", "2560x1440");
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "seat 1440p", 0L, 0L, options);
        pm.add(p);
        pm.setActive(p.getUuid());

        StreamSettings activity = Robolectric.buildActivity(StreamSettings.class).create().get();

        TextView notice = activity.findViewById(R.id.profileOverrideNotice);
        assertNotNull(notice);
        assertEquals(View.VISIBLE, notice.getVisibility());
        assertTrue("the notice should name the profile doing the shadowing",
                notice.getText().toString().contains("seat 1440p"));
    }

    @Test
    public void withoutActiveProfile_thereIsNoNotice() {
        StreamSettings activity = Robolectric.buildActivity(StreamSettings.class).create().get();

        TextView notice = activity.findViewById(R.id.profileOverrideNotice);
        assertNotNull(notice);
        assertEquals(View.GONE, notice.getVisibility());
    }

    @Test
    public void pcBoundToNone_leavesNoNotice() {
        SettingsProfile p = new SettingsProfile(UUID.randomUUID(), "seat", 0L, 0L, null);
        pm.add(p);
        pm.setActive(p.getUuid());

        // Entering a PC bound to "none" clears the active profile
        pm.bindPc("pc-hijo-uuid", null);
        assertTrue(pm.applyProfileForPc("pc-hijo-uuid"));

        StreamSettings activity = Robolectric.buildActivity(StreamSettings.class).create().get();

        TextView notice = activity.findViewById(R.id.profileOverrideNotice);
        assertEquals(View.GONE, notice.getVisibility());
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        File[] children = f.listFiles();
        if (children != null) {
            for (File c : children) {
                deleteRecursively(c);
            }
        }
        f.delete();
    }
}
