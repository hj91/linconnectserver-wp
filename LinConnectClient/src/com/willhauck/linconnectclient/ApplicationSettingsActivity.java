/*    
 	LinConnect: Mirror Android notifications on Linux Desktop

    Copyright (C) 2013  Will Hauck

    This program is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program.  If not, see <http://www.gnu.org/licenses/>.
*/

package com.willhauck.linconnectclient;

import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;

@SuppressWarnings("deprecation")
public class ApplicationSettingsActivity extends PreferenceActivity {
	
	ProgressDialog progressDialog;
	PreferenceCategory applicationCategory;
	
	private static final boolean ALWAYS_SIMPLE_PREFS = false;

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		setupSimplePreferencesScreen();
	}

	private void setupSimplePreferencesScreen() {
		addPreferencesFromResource(R.xml.pref_application);
		
		applicationCategory = (PreferenceCategory)findPreference("header_application");
		
		// Listen for check/uncheck all tap
		findPreference("pref_all").setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			@Override
			public boolean onPreferenceChange(Preference arg0, Object arg1) {
				for (int i = 0; i < applicationCategory.getPreferenceCount(); i ++) {
					// Uncheck or check all items
					((CheckBoxPreference)(applicationCategory.getPreference(i))).setChecked((Boolean)arg1);
				}	
				return true;
			}			
		});

		class ApplicationTask extends AsyncTask<String, Void, List<ApplicationInfo>> {
			private PackageManager packageManager;
			
			@Override
			protected void onPreExecute() {
				progressDialog = ProgressDialog.show(ApplicationSettingsActivity.this, null, "Loading...", true);
			}
			
			@Override
			protected List<ApplicationInfo> doInBackground(String... notif) {
				
				packageManager = getApplicationContext().getPackageManager();
				
				// Comparator used to sort applications by name
				class CustomComparator implements Comparator<ApplicationInfo> {
					@Override
					public int compare(ApplicationInfo arg0, ApplicationInfo arg1) {
						return arg0.loadLabel(packageManager).toString().compareTo(arg1.loadLabel(packageManager).toString());
					}
				}
				
			    // Get installed applications
			    Intent mainIntent = new Intent(Intent.ACTION_MAIN, null);
			    mainIntent.addCategory(Intent.CATEGORY_LAUNCHER);
			    List<ApplicationInfo> appList = getApplicationContext().getPackageManager().getInstalledApplications(PackageManager.GET_META_DATA);
			    
			    // Sort by application name
			    Collections.sort(appList, new CustomComparator());
			    
			    return appList;
			}

			@Override
			protected void onPostExecute(List<ApplicationInfo> result) {
				// Add each application to screen
			    for (ApplicationInfo appInfo : result) {
			    	CheckBoxPreference c = new CheckBoxPreference(ApplicationSettingsActivity.this);
			    	c.setTitle(appInfo.loadLabel(packageManager).toString());
			    	c.setSummary(appInfo.packageName);
			    	c.setIcon(appInfo.loadIcon(packageManager));
			    	c.setKey(appInfo.packageName);
			    	c.setChecked(true);
			    	
			    	c.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
						@Override
						public boolean onPreferenceChange(Preference arg0,
								Object arg1) {
							// On tap, show an enabled/disabled notification on the desktop
							Object[] notif = new Object[3];
							
							if (arg1.toString().equals("true")) {
								notif[0] = arg0.getTitle().toString() + " notifications enabled";
								notif[1] = "via LinConnect";
								notif[2] = arg0.getIcon();
							}
							else {
								notif[0] = arg0.getTitle().toString() + " notifications disabled";
								notif[1] = "via LinConnect";
								notif[2] = arg0.getIcon();
							}

							new TestTask().execute(notif);
							
							return true;
						}
			    		
			    	});

			    	applicationCategory.addPreference(c);
			    }
				progressDialog.dismiss();
			}
		}
		
		new ApplicationTask().execute();

	}
	
	
	class TestTask extends AsyncTask<Object, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Object... notif) {
			SharedPreferences prefs = PreferenceManager
					.getDefaultSharedPreferences(getApplicationContext());
			String ip = prefs.getString("pref_ip", "0.0.0.0:9090");

			MultipartEntity entity = new MultipartEntity();
			entity.addPart("notificon", new InputStreamBody(ImageUtilities.bitmapToInputStream(ImageUtilities.drawableToBitmap((Drawable) notif[2])), "drawable.png"));

			HttpPost post = new HttpPost("http://" + ip + "/notif");
			post.setEntity(entity);
			post.addHeader("notifheader", (String)notif[0]);
			post.addHeader("notifdescription", (String)notif[1]);
			
			HttpClient client = new DefaultHttpClient();
			HttpResponse response;
			try {
				response = client.execute(post);
				String html = EntityUtils.toString(response.getEntity());
				if (html.contains("true")) {
					return true;
				}
			} catch (ClientProtocolException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return false;
		}

		@Override
		protected void onPostExecute(Boolean result) {
		}
	}
	
	@Override
	public boolean onIsMultiPane() {
		return isXLargeTablet(this) && !isSimplePreferences(this);
	}

	private static boolean isXLargeTablet(Context context) {
		return (context.getResources().getConfiguration().screenLayout & Configuration.SCREENLAYOUT_SIZE_MASK) >= Configuration.SCREENLAYOUT_SIZE_XLARGE;
	}

	private static boolean isSimplePreferences(Context context) {
		return ALWAYS_SIMPLE_PREFS
				|| Build.VERSION.SDK_INT < Build.VERSION_CODES.HONEYCOMB
				|| !isXLargeTablet(context);
	}
}
