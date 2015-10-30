package ru.killer666.sdremap;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.support.v7.app.AppCompatActivity;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    interface PackageChecker {
        boolean check(String packageName);
    }

    SharedPreferences prefs;

    public List<CharSequence> getPackages(PackageChecker checker) {

        final List<CharSequence> data = new ArrayList<>();
        final PackageManager pm = getPackageManager();

        for (ApplicationInfo packageInfo : pm.getInstalledApplications(PackageManager.GET_META_DATA)) {
            if (checker.check(packageInfo.packageName))
                data.add(packageInfo.packageName);
        }

        return data;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        this.prefs = this.getSharedPreferences("xposed_data", MODE_WORLD_READABLE);

        findViewById(R.id.buttonAdd).setOnClickListener(this);
        findViewById(R.id.buttonDelete).setOnClickListener(this);
        findViewById(R.id.buttonAddLog).setOnClickListener(this);
        findViewById(R.id.buttonDeleteLog).setOnClickListener(this);
        findViewById(R.id.buttonAddRewrite).setOnClickListener(this);
        findViewById(R.id.buttonDeleteRewrite).setOnClickListener(this);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonAdd: {
                final List<CharSequence> data = this.getPackages(new PackageChecker() {
                    @Override
                    public boolean check(String packageName) {
                        return !MainActivity.this.prefs.contains("isowndir_" + packageName);
                    }
                });
                AlertDialog.Builder builder = new AlertDialog.Builder(this);

                builder.setTitle("Select app");
                builder.setCancelable(true);
                builder.setSingleChoiceItems(Arrays.copyOf(data.toArray(), data.size(), CharSequence[].class),
                        -1,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                dialog.dismiss();

                                SharedPreferences.Editor edit = MainActivity.this.prefs.edit();

                                edit.putBoolean("isowndir_" + data.get(item), true);
                                edit.apply();
                            }
                        });
                builder.create().show();
                break;
            }
            case R.id.buttonAddLog: {
                final List<CharSequence> data = MainActivity.this.getPackages(new PackageChecker() {
                    @Override
                    public boolean check(String packageName) {
                        return !MainActivity.this.prefs.contains("islogging_" + packageName);
                    }
                });
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                builder.setTitle("Select app to enable logging");
                builder.setCancelable(true);
                builder.setSingleChoiceItems(Arrays.copyOf(data.toArray(), data.size(), CharSequence[].class),
                        -1,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                dialog.dismiss();

                                SharedPreferences prefs = MainActivity.this.getSharedPreferences("xposed_data", MODE_WORLD_READABLE);
                                SharedPreferences.Editor edit = prefs.edit();

                                edit.putBoolean("islogging_" + data.get(item), true);
                                edit.apply();
                            }
                        });
                builder.create().show();
                break;
            }
            case R.id.buttonDelete: {
                final List<CharSequence> data = MainActivity.this.getPackages(new PackageChecker() {
                    @Override
                    public boolean check(String packageName) {
                        return MainActivity.this.prefs.contains("isowndir_" + packageName);
                    }
                });
                AlertDialog.Builder builder = new AlertDialog.Builder(this);

                builder.setTitle("Select app to delete from");
                builder.setCancelable(true);
                builder.setSingleChoiceItems(Arrays.copyOf(data.toArray(), data.size(), CharSequence[].class),
                        -1,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                dialog.dismiss();

                                SharedPreferences prefs = MainActivity.this.getSharedPreferences("xposed_data", MODE_WORLD_READABLE);
                                SharedPreferences.Editor edit = prefs.edit();

                                edit.remove("isowndir_" + data.get(item));
                                edit.apply();
                            }
                        });
                builder.create().show();
                break;
            }
            case R.id.buttonDeleteLog: {
                final List<CharSequence> data = MainActivity.this.getPackages(new PackageChecker() {
                    @Override
                    public boolean check(String packageName) {
                        return MainActivity.this.prefs.contains("islogging_" + packageName);
                    }
                });
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                builder.setTitle("Select app to disable logging");
                builder.setCancelable(true);
                builder.setSingleChoiceItems(Arrays.copyOf(data.toArray(), data.size(), CharSequence[].class),
                        -1,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                dialog.dismiss();

                                SharedPreferences prefs = MainActivity.this.getSharedPreferences("xposed_data", MODE_WORLD_READABLE);
                                SharedPreferences.Editor edit = prefs.edit();

                                edit.remove("islogging_" + data.get(item));
                                edit.apply();
                            }
                        });
                builder.create().show();
                break;
            }
            case R.id.buttonAddRewrite: {

                AlertDialog.Builder builder = new AlertDialog.Builder(this);

                builder.setTitle("Select");
                builder.setCancelable(true);
                builder.setSingleChoiceItems(new CharSequence[]{"File", "Folder"},
                        -1,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                dialog.dismiss();

                                Intent intent = new Intent("com.estrongs.action." + (item == 0 ? "PICK_FILE" : "PICK_DIRECTORY"));
                                intent.putExtra("com.estrongs.intent.extra.TITLE", "Select " + (item == 0 ? "file" : "folder"));

                                MainActivity.this.startActivityForResult(intent, 1);
                            }
                        });
                builder.create().show();
                break;
            }
            case R.id.buttonDeleteRewrite: {
                Gson gson = new Gson();
                final JsonArray jsonRewriteData = gson.fromJson(prefs.getString("rewrite", "[]"), JsonArray.class);
                final List<CharSequence> data = new ArrayList<>();
                final List<JsonObject> dataJson = new ArrayList<>();

                for (JsonElement element : jsonRewriteData) {
                    JsonObject jsonObject = element.getAsJsonObject();

                    dataJson.add(jsonObject);
                    data.add(jsonObject.get("from").getAsString() + " -> " + jsonObject.get("to").getAsString());
                }

                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);

                builder.setTitle("Select rewrite rule to delete");
                builder.setCancelable(true);
                builder.setSingleChoiceItems(Arrays.copyOf(data.toArray(), data.size(), CharSequence[].class),
                        -1,
                        new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                dialog.dismiss();

                                jsonRewriteData.remove(dataJson.get(item));

                                SharedPreferences prefs = MainActivity.this.getSharedPreferences("xposed_data", MODE_WORLD_READABLE);
                                SharedPreferences.Editor edit = prefs.edit();

                                edit.putString("rewrite", jsonRewriteData.toString());
                                edit.apply();
                            }
                        });
                builder.create().show();

                SharedPreferences.Editor edit = MainActivity.this.prefs.edit();
                edit.putString("rewrite", jsonRewriteData.toString());
                edit.apply();
                break;
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == 1) {
            if (data == null)
                return;

            Uri uri = data.getData();

            if (uri != null) {
                Toast.makeText(this, uri.getPath(), Toast.LENGTH_SHORT).show();

                // Ask to other path
                final String path = uri.getPath().substring(Environment.getExternalStorageDirectory().getAbsolutePath().length() + 1);

                AlertDialog.Builder builder = new AlertDialog.Builder(this);
                builder.setTitle("Input target:");

                final EditText input = new EditText(this);
                input.setInputType(InputType.TYPE_CLASS_TEXT);
                input.setText(path);
                builder.setView(input);

                builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String newPath = input.getText().toString();
                        Gson gson = new Gson();

                        JsonObject jsonObject = new JsonObject();
                        jsonObject.addProperty("from", path);
                        jsonObject.addProperty("to", newPath);

                        JsonArray jsonRewriteData = gson.fromJson(prefs.getString("rewrite", "[]"), JsonArray.class);
                        jsonRewriteData.add(jsonObject);

                        SharedPreferences.Editor edit = MainActivity.this.prefs.edit();
                        edit.putString("rewrite", jsonRewriteData.toString());
                        edit.apply();
                    }
                });
                builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.cancel();
                    }
                });
                builder.show();
            }
        }
    }
}