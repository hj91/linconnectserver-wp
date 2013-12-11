package com.willhauck.linconnectclient;

import android.accessibilityservice.AccessibilityService;
import android.app.Notification;
import android.os.AsyncTask;
import android.view.accessibility.AccessibilityEvent;

public class OldNotificationService extends AccessibilityService {

	public OldNotificationService() {
	}

	@Override
	public void onAccessibilityEvent(AccessibilityEvent arg0) {
		if (!(arg0.getParcelableData() instanceof Notification)) {
			return;
		}
		final Notification notification = (Notification) arg0
				.getParcelableData();
		NotifyTask task = new NotifyTask();
		task.execute(notification, arg0.getPackageName().toString());
	}

	@Override
	public void onInterrupt() {
	}

	private class NotifyTask extends AsyncTask<Object, Void, Boolean> {
		@Override
		protected Boolean doInBackground(Object... notif) {
			return NotificationUtilities.sendData(getApplicationContext(),
					(Notification) notif[0], (String) notif[1]);
		}

		@Override
		protected void onPostExecute(Boolean result) {
		}
	}

}
