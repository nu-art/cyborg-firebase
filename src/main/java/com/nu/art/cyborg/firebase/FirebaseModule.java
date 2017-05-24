package com.nu.art.cyborg.firebase;

import com.firebase.client.DataSnapshot;
import com.firebase.client.Firebase;
import com.firebase.client.FirebaseError;
import com.firebase.client.FirebaseException;
import com.firebase.client.ValueEventListener;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.RemoteMessage;
import com.nu.art.core.generics.Processor;
import com.nu.art.cyborg.annotations.ModuleDescriptor;
import com.nu.art.cyborg.core.CyborgBuilder;
import com.nu.art.cyborg.core.CyborgModule;
import com.nu.art.cyborg.core.modules.PreferencesModule;
import com.nu.art.cyborg.core.modules.PreferencesModule.StringPreference;

@ModuleDescriptor
public class FirebaseModule
		extends CyborgModule {

	private StringPreference token;

	public static class FirebaseKeyDB<Value> {

		String dbName;

		String pathToResource;

		Class<Value> valueClass;

		public FirebaseKeyDB(String dbName, String pathToResource, Class<Value> valueClass) {
			this.dbName = dbName;
			this.pathToResource = pathToResource;
			this.valueClass = valueClass;
		}

		public final String composeUrl() {
			return "https://" + dbName + ".firebaseio.com/" + pathToResource;
		}
	}

	public interface FirebaseResponseListener<Value> {

		void onResponse(Value value);

		void onError(FirebaseException error);
	}

	@Override
	protected void init() {
		PreferencesModule preferences = getModule(PreferencesModule.class);
		token = preferences.new StringPreference("firebase-token", null);
		Firebase.setAndroidContext(getApplication());
	}

	public <Value> void getValueOneshot(final FirebaseKeyDB<Value> key, final FirebaseResponseListener<Value> listener) {
		String url = key.composeUrl();
		Firebase firebase = new Firebase(url);
		logDebug("Getting value from firebase: " + url);

		firebase.addListenerForSingleValueEvent(new ValueEventListener() {
			@Override
			public void onDataChange(DataSnapshot dataSnapshot) {
				if (!dataSnapshot.exists()) {
					listener.onResponse(null);
					return;
				}

				listener.onResponse(dataSnapshot.getValue(key.valueClass));
			}

			@Override
			public void onCancelled(FirebaseError firebaseError) {
				logError("Error: ", firebaseError.toException());
				listener.onError(firebaseError.toException());
			}
		});
	}

	@Override
	protected void printModuleDetails() {
		logInfo("Registration token: " + token.get());
	}

	private void dispatchFirebaseMessageReceived(final RemoteMessage message) {
		dispatchEvent("Dispatched firebase message", FirebaseNotificationListener.class, new Processor<FirebaseNotificationListener>() {
			@Override
			public void process(FirebaseNotificationListener listener) {
				listener.onPushMessageReceived(message);
			}
		});
	}

	private void onTokenUpdated(String token) {
		this.token.set(token);
		dispatchModuleEvent("Firebase token updated", FirebaseTokenListener.class, new Processor<FirebaseTokenListener>() {
			@Override
			public void process(FirebaseTokenListener listener) {
				listener.onFirebaseTokenUpdated();
			}
		});
	}

	public String getFirebaseToken() { return token.get(); }

	public interface FirebaseNotificationListener {

		void onPushMessageReceived(RemoteMessage message);
	}

	public interface FirebaseTokenListener {

		void onFirebaseTokenUpdated();
	}

	public static class StupidService
			extends com.google.firebase.messaging.FirebaseMessagingService {

		@Override
		public void onMessageReceived(RemoteMessage remoteMessage) {
			CyborgBuilder.getInstance().getModule(FirebaseModule.class).dispatchFirebaseMessageReceived(remoteMessage);
		}
	}

	public static class AnotherStupidService
			extends com.google.firebase.iid.FirebaseInstanceIdService {

		@Override
		public void onTokenRefresh() {
			CyborgBuilder.getInstance().getModule(FirebaseModule.class).onTokenUpdated(FirebaseInstanceId.getInstance().getToken());
		}
	}
}


