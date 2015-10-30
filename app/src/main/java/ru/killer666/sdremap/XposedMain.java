package ru.killer666.sdremap;

import android.os.Environment;
import android.util.Pair;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XSharedPreferences;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;
import lombok.AllArgsConstructor;
import lombok.Getter;

public class XposedMain implements IXposedHookLoadPackage {

    private final String[] blacklistApps = new String[]{"android", "com.google.android.backup"};

    @Getter
    private boolean isLogging = false;
    @Getter
    private String packageName;
    @Getter
    private File externalStorageDirectory;
    @Getter
    private Map<String, String> env;

    private void logForce(String text) {
        XposedBridge.log("Rewrite (" + this.packageName + "): " + text);
    }

    private void log(String text) {
        if (this.isLogging)
            this.logForce(text);
    }

    @Override
    public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable {

        if (Arrays.asList(this.blacklistApps).contains(lpparam.packageName) || lpparam.packageName.startsWith("com.android."))
            return;

        XSharedPreferences prefs = new XSharedPreferences("ru.killer666.sdremap", "xposed_data");

        this.isLogging = prefs.getBoolean("islogging_" + lpparam.packageName, false);

        boolean isOwnDirectory = prefs.getBoolean("isowndir_" + lpparam.packageName, false);
        this.packageName = lpparam.packageName;
        this.externalStorageDirectory = Environment.getExternalStorageDirectory();

        if (isOwnDirectory)
            this.externalStorageDirectory = new File(this.externalStorageDirectory, "Android/sdroot/" + lpparam.packageName);

        if (!this.externalStorageDirectory.exists())
            this.externalStorageDirectory.mkdirs();

        this.logForce("SD directory: " + this.externalStorageDirectory.getAbsolutePath());

        this.env = new HashMap<>(System.getenv());
        this.env.put("EXTERNAL_STORAGE", this.externalStorageDirectory.getAbsolutePath());
        this.env.put("SECONDARY_STORAGE", this.externalStorageDirectory.getAbsolutePath());
        this.env = Collections.unmodifiableMap(this.env);

        Gson gson = new Gson();
        JsonArray jsonRewriteData = gson.fromJson(prefs.getString("rewrite", "[]"), JsonArray.class);

        List<Pair<String, String>> remapPaths = new ArrayList<>();

        for (JsonElement element : jsonRewriteData) {
            JsonObject jsonObject = element.getAsJsonObject();
            remapPaths.add(new Pair<>(
                    this.externalStorageDirectory + File.separator + jsonObject.get("from").getAsString(),
                    this.externalStorageDirectory + File.separator + jsonObject.get("to").getAsString()));
        }

        /* java.io.File */
        Class<?> javaIoFile = XposedHelpers.findClass("java.io.File", lpparam.classLoader);

        // public File(String path)
        XposedHelpers.findAndHookConstructor(javaIoFile, String.class, new FileConstructorHook(this, remapPaths));

        // public File(File dir, String name)
        XposedHelpers.findAndHookConstructor(javaIoFile, File.class, String.class, new FileConstructorHook(this, remapPaths));

        // public File(String dirPath, String name)
        XposedHelpers.findAndHookConstructor(javaIoFile, String.class, String.class, new FileConstructorHook(this, remapPaths));

        // public File(URI uri)
        XposedHelpers.findAndHookConstructor(javaIoFile, URI.class, new FileConstructorHook(this, remapPaths));

        /* System.getenv */
        Class<?> system = XposedHelpers.findClass("java.lang.System", lpparam.classLoader);

        // public static Map<String, String> getenv()
        XposedHelpers.findAndHookMethod(system, "getenv", new GetEnvHook(this));

        // public static String getenv(String name)
        XposedHelpers.findAndHookMethod(system, "getenv", String.class, new GetEnvHook(this));

        /* Environment.getExternalStorageDirectory() */
        XposedHelpers.findAndHookMethod(Environment.class, "getExternalStorageDirectory", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                param.setResult(XposedMain.this.externalStorageDirectory);
            }
        });
    }

    @AllArgsConstructor
    private static class GetEnvHook extends XC_MethodHook {
        XposedMain module;

        @Override
        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
            if (param.args.length != 0) {
                param.setResult(this.module.getEnv().get((String) param.args[0]));
            } else {
                param.setResult(this.module.getEnv());
            }
        }
    }

    @AllArgsConstructor
    private static class FileConstructorHook extends XC_MethodHook {

        XposedMain module;
        List<Pair<String, String>> remapPaths;

        @Override
        protected void afterHookedMethod(MethodHookParam param) throws Throwable {

            Field pathField = XposedHelpers.findField(param.thisObject.getClass(), "path");
            pathField.setAccessible(true);

            String path = (String) pathField.get(param.thisObject);

            if (!path.startsWith(this.module.getExternalStorageDirectory().getAbsolutePath()))
                return;

            this.module.log("new: " + path);

            for (Pair<String, String> currentMap : this.remapPaths) {
                if (path.startsWith(currentMap.first)) {
                    this.module.log("using filter " + currentMap.first + " -> " + currentMap.second);
                    String newPath = currentMap.second + path.substring(currentMap.first.length());
                    pathField.set(param.thisObject, newPath);

                    this.module.log("Path " + path + " -> " + newPath);
                    return;
                }
            }
        }
    }
}
