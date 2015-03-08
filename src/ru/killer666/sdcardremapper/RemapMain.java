package ru.killer666.sdcardremapper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import android.annotation.SuppressLint;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage.LoadPackageParam;

@SuppressLint("SdCardPath")
public class RemapMain implements IXposedHookLoadPackage
{
	final static String		whitelistFile		= "/storage/sdcard0/Noremap.txt";
	final static String		blacklistDirectory	= "/storage/sdcard0/Trash";
	final static String[]	detectFiles			= { "/storage/sdcard0/", "/sdcard/" };

	static List<String>		whitelistedFiles;
	static boolean			enableLogging		= false;

	List<String> readLines(String filename) throws IOException
	{
		List<String> lines = new ArrayList<String>();
		BufferedReader bufferedReader;

		try
		{
			FileReader fileReader = new FileReader(filename);
			bufferedReader = new BufferedReader(fileReader);
		}
		catch (FileNotFoundException ex)
		{
			// XposedBridge.log("Remap: Access denied to noremap!");
			return lines;
		}

		String line = null;
		while ((line = bufferedReader.readLine()) != null)
		{
			if (line.length() != 0)
				lines.add(line);
		}

		bufferedReader.close();
		return lines;
	}

	@Override
	public void handleLoadPackage(LoadPackageParam lpparam) throws Throwable
	{
		// Loading whitelist
		whitelistedFiles = readLines(whitelistFile);

		if (whitelistedFiles.size() == 0)
			return;

		Iterator<String> iter = whitelistedFiles.iterator();

		while (iter.hasNext())
		{
			String currentLine = iter.next();

			if (currentLine.startsWith("package:"))
			{
				if (currentLine.equalsIgnoreCase("package:" + lpparam.packageName))
				{
					if (enableLogging)
						XposedBridge.log("Remap: package " + lpparam.packageName + " is whitelisted!");

					return;
				}

				iter.remove();
			}
			else if (currentLine.startsWith("logging:true"))
			{
				enableLogging = true;
				iter.remove();
			}
		}

		XposedHelpers.findAndHookConstructor("java.io.File", lpparam.classLoader, String.class, new XC_MethodHook()
		{
			@Override
			protected void beforeHookedMethod(MethodHookParam param) throws Throwable
			{
				// Checking detect paths
				String targetPath = (String) param.args[0];
				String detectPath = null;

				for (String currentPath : detectFiles)
				{
					if (targetPath.startsWith(currentPath))
					{
						detectPath = currentPath;
						break;
					}
				}

				if (detectPath == null)
					return;

				// Cut base path
				String relativePath = targetPath.substring(detectPath.length());

				// Checking whitelist
				Iterator<String> iter = whitelistedFiles.iterator();
				boolean isRemap = true;

				while (iter.hasNext())
				{
					String currentPath = iter.next();

					if (relativePath.startsWith(currentPath))
					{
						isRemap = false;
						break;
					}
				}

				if (!isRemap)
				{
					if (enableLogging)
						XposedBridge.log("Remap: " + targetPath + " is not remapped!");

					return;
				}

				String remappedPath = blacklistDirectory + File.separator + relativePath;
				param.args[0] = remappedPath;

				if (enableLogging)
					XposedBridge.log("Remap: " + targetPath + " -> " + remappedPath);
			}

			@Override
			protected void afterHookedMethod(MethodHookParam param) throws Throwable
			{
			}
		});
	}
}
