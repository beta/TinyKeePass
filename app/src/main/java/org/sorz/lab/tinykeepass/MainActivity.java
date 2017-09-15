package org.sorz.lab.tinykeepass;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.Snackbar;
import android.support.v7.widget.Toolbar;
import android.util.Log;

import org.sorz.lab.tinykeepass.keepass.KeePassStorage;

import static org.sorz.lab.tinykeepass.keepass.KeePassHelper.getDatabaseFile;
import static org.sorz.lab.tinykeepass.keepass.KeePassHelper.hasDatabaseConfigured;

public class MainActivity extends BaseActivity {
    private final static String TAG = MainActivity.class.getName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState == null) {
            getFragmentManager().beginTransaction()
                    .add(R.id.fragment_container, new DatabaseLockedFragment())
                    .commit();

            if (!hasDatabaseConfigured(this)) {
                doConfigureDatabase();
                finish();
            } else {
                doUnlockDatabase();
            }
        } else if (KeePassStorage.get() != null) {
            // Restarting activity
            KeePassStorage.registerBroadcastReceiver(this);
        }
        setContentView(R.layout.activity_main);
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (KeePassStorage.get() != null)
            showEntryList();
    }

    public void doUnlockDatabase() {
        if (KeePassStorage.get() != null) {
            showEntryList();
        } else {
            getDatabaseKeys(keys ->
                    openDatabase(keys.get(0),
                            db -> {
                                KeePassStorage.set(this, db);
                                showEntryList();
                            },
                            this::showError)
            , this::showError);
        }
    }

    public void doLockDatabase() {
        KeePassStorage.set(this, null);
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new DatabaseLockedFragment())
                .commit();
    }

    public void doConfigureDatabase() {
        KeePassStorage.set(this, null);
        startActivity(new Intent(this, DatabaseSetupActivity.class));
    }

    public void doSyncDatabase() {
        getDatabaseKeys(keys -> {
            String url = getPreferences().getString("db-url", "");
            String masterKey = keys.get(0);
            String username = null;
            String password = null;
            if (getPreferences().getBoolean("db-auth-required", false)) {
                username = getPreferences().getString("db-auth-username", "");
                password = keys.get(1);
            }
            Intent intent = DatabaseSyncingService.getFetchIntent(
                    this, url, masterKey, username, password);
            startService(intent);
        }, this::showError);
    }

    public void doCleanDatabase() {
        KeePassStorage.set(this, null);
        if (!getDatabaseFile(this).delete())
            Log.w(TAG, "fail to delete database file");
        getSecureStringStorage().clear();
        snackbar(getString(R.string.clean_config_ok), Snackbar.LENGTH_SHORT).show();
    }

    private void showEntryList() {
        getFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, EntryFragment.newInstance())
                .commit();
    }

    private void showError(String message) {
        snackbar(message, Snackbar.LENGTH_LONG).show();
    }

    private Snackbar snackbar(CharSequence text, int duration) {
        return Snackbar.make(findViewById(R.id.fragment_container), text, duration);
    }
}
