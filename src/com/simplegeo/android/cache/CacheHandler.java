package com.simplegeo.android.cache;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.List;
import java.util.Timer;
import java.util.TimerTask;

import org.json.JSONException;
import org.json.JSONObject;

import android.util.Log;

public class CacheHandler {
	
	private static final String TAG = CacheHandler.class.getCanonicalName();
	
	private Timer flushTimer = null;
	private long flushTimeInterval = 60;
	private String absoluteFile = null;
	private String currentPath = null;
	private JSONObject data = null;
	
	// I miss real pointers
	private JSONObject parentData = null;
	private JSONObject currentData = null;
	
	public static long ttl =  604800;
	
	public CacheHandler(String fileName, String cachePath) {
		File cacheDir = new File(cachePath + File.separator + fileName);
		absoluteFile = cacheDir.getAbsolutePath();
		
		if(!cacheDir.exists() && !cacheDir.mkdir())
			Log.e(TAG, "unable to create " + absoluteFile);
		
		currentPath = absoluteFile;
		parentData = null;
		data = new JSONObject();
		currentData = data;
	
		deleteStaleCacheFiles(absoluteFile);
		reload();
	}
		
	public void startFlushTimer() {
		if(flushTimer == null) {
			Log.d(TAG, "starting the flush timer");
			
			flushTimer = new Timer("FlushTimer");
			flushTimer.schedule(new TimerTask() {
					@Override
					public void run() {
						flush();
					}
				}, 
				flushTimeInterval);
		}
	}
	
	public void stopFlushTimer() {
		if(flushTimer != null) {
			Log.d(TAG, "stoping the flush timer");
			
			flushTimer.cancel();
			flushTimer = null;
		}
	}
	
	public void flush() {
		Log.d(TAG, "flushing the cache handler");
		flushJSONObject(absoluteFile, data, null);
		data = new JSONObject();
		parentData = null;
		currentData = data;
	}
	
	private void flushJSONObject(String path, JSONObject object, String key) {
		try {
			
			if(key == null) {
				
				Iterator<String> keys = object.keys();
				while(keys.hasNext())
					flushJSONObject(path, object, keys.next());
				
			} else {
			
				Object value = object.get(key);
				String newPath = path + File.separator + key;
				if(value instanceof JSONObject) {
					Iterator<String> keys = object.keys();
					while(keys.hasNext())
						flushJSONObject(path, (JSONObject)value, keys.next());
				} else if(value instanceof String) {
					writeStringToPath((String)value, newPath);
				}			
			}
			
		} catch (JSONException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}
	}
	
	private void writeStringToPath(String value, String path) {
		File file = new File(path);
		try {
			if(!file.exists() && !file.createNewFile())
				Log.e(TAG, "unable to create file at " + path);
		
			FileOutputStream fileOutputStream = new FileOutputStream(path);
			fileOutputStream.write(value.getBytes());
			fileOutputStream.close();
			fileOutputStream = null;
			
		} catch (FileNotFoundException e) {
			Log.e(TAG, e.getLocalizedMessage());
		} catch (IOException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}
	}
	
	public void changeToParentDirectory() {
		currentPath = absoluteFile;
		currentData = data;
	}
	
	public void changeDirectory(String directory) {
		String childPath = currentPath + File.separator + directory;
		File file =  new File(childPath);
		if(!file.exists() && !file.mkdir())
			Log.e(TAG, "unable to create director " + childPath);
		
		if(file.exists() && file.isDirectory())
			currentPath = childPath;
		
		try {
			JSONObject jsonObject = (JSONObject)currentData.get(directory);
			parentData = currentData;
			if(jsonObject != null && jsonObject instanceof JSONObject)
				currentData = jsonObject;
			else {
				jsonObject = new JSONObject();
				currentData.put(directory, jsonObject);
				currentData = jsonObject;
			}
			
		} catch (JSONException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}
	}
		
	public void setValue(String key, String value) {
		try {
			currentData.put(key, value);
		} catch (JSONException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}
	}
	
	public String getValue(String key) {
		String value = null;
		try {
			value = (String)currentData.get(key);
		} catch (JSONException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}
		
		return value;
	}
	
	public void deleteAll() {
		data = new JSONObject();
		changeToParentDirectory();
		File file = new File(absoluteFile);
		recursiveDelete(file);
		file.mkdir();
	}
	
	public void recursiveDelete(File file) {
		File[] files = file.listFiles();
		for(File subdir : files) {
			if(subdir.isDirectory())
				recursiveDelete(subdir);
			else
				subdir.delete();
		}
	}
	
	public void delete(String key) {
		data.remove(key);
		File fileToDelete = new File(absoluteFile + File.separator + key);
		if(fileToDelete.exists() && !fileToDelete.delete())
			Log.e(TAG, "unable to delete file at " + fileToDelete.getAbsolutePath());
	}
	
	public void reload() {
		Log.d(TAG, "reloading data from disk");
		
		data = new JSONObject();
		changeToParentDirectory();
		try {
			loadDirectory(data, absoluteFile);
		} catch (JSONException e) {
			Log.e(TAG, e.getLocalizedMessage());
		}
	}
	
	private void loadDirectory(JSONObject jsonObject, String path) throws JSONException {
		Log.d(TAG, "reloading directory " + path);
		File file = new File(path);
		if(file.exists()) {
			File[] children = file.listFiles();
			for(File child : children) {
				if(child.isFile()) {
					try {
						FileInputStream inputStream = new FileInputStream(child);
						byte[] buffer = new byte[inputStream.available()];
						inputStream.read(buffer);
						data.put(child.getName(), new String(buffer));
					} catch (FileNotFoundException e) {
						Log.e(TAG, e.getLocalizedMessage());
					} catch (IOException e) {
						Log.e(TAG, e.getLocalizedMessage());
					}
				} else if(child.isDirectory()) {
					JSONObject newJSONObject = new JSONObject();
					jsonObject.put(file.getName(), newJSONObject);
					loadDirectory(newJSONObject, path + File.separator + child);
				}
			}
		}
	}
	
	public void deleteStaleCacheFiles(String path) {
		Log.d(TAG, "deleting stale files at " + path);
		
		long currentTime = System.currentTimeMillis();
		File file = new File(path);
		if(file.exists()) {
			File[] subdirectories = file.listFiles();
			for(File subdirectory : subdirectories) {
				if(subdirectory.isDirectory()) {
					deleteStaleCacheFiles(subdirectory.getAbsolutePath());
				} else if(subdirectory.isFile()) {
					if(currentTime - subdirectory.lastModified() > ttl &&
							!subdirectory.delete())
						Log.e(TAG, "unable to delete file at " + subdirectory.getAbsolutePath());		
				}
			}
		}
	}
	
	/**
	 * @return the flushTimeInterval
	 */
	public long getFlushTimeInterval() {
		return flushTimeInterval;
	}

	/**
	 * @param flushTimeInterval the flushTimeInterval to set
	 */
	public void setFlushTimeInterval(long flushTimeInterval) {
		this.flushTimeInterval = flushTimeInterval;
	}
}
