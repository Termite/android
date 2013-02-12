package com.twofours.surespot.activities;

import java.util.Set;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

import com.google.android.gcm.GCMRegistrar;
import com.loopj.android.http.AsyncHttpResponseHandler;
import com.twofours.surespot.GCMIntentService;
import com.twofours.surespot.IdentityController;
import com.twofours.surespot.SurespotApplication;
import com.twofours.surespot.chat.ChatActivity;
import com.twofours.surespot.common.SurespotConstants;
import com.twofours.surespot.common.SurespotConstants.IntentFilters;
import com.twofours.surespot.common.SurespotLog;
import com.twofours.surespot.common.Utils;
import com.twofours.surespot.friends.FriendActivity;
import com.twofours.surespot.network.NetworkController;

public class StartupActivity extends Activity {
	private static final String TAG = "StartupActivity";

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		try {
			// device without GCM throws exception
			GCMRegistrar.checkDevice(this);
			GCMRegistrar.checkManifest(this);

			final String regId = GCMRegistrar.getRegistrationId(this);
			// boolean registered = GCMRegistrar.isRegistered(this);
			// boolean registeredOnServer = GCMRegistrar.isRegisteredOnServer(this);
			if (regId.equals("")) {
				SurespotLog.v(TAG, "Registering for GCM.");
				GCMRegistrar.register(this, GCMIntentService.SENDER_ID);
			}
			else {
				SurespotLog.v(TAG, "GCM already registered.");
			}
		}
		catch (Exception e) {
			SurespotLog.w(TAG, "onCreate", e);
		}

		// NetworkController.unregister(this, regId);

		// if we have any users
		if (IdentityController.hasIdentity(this)) {

			Intent intent = getIntent();
			String action = intent.getAction();
			String type = intent.getType();
			Bundle extras = intent.getExtras();
			Set<String> categories = intent.getCategories();

			SurespotLog.v(TAG, "Intent action: " + action);
			SurespotLog.v(TAG, "Intent type: " + type);
			SurespotLog.v(TAG, "Intent categories: " + (categories == null ? "null" : categories.toString()));
			SurespotLog.v(TAG, "Extras: " + (extras == null ? "null" : extras.toString()));

			// if we have a current user we're logged in
			String user = IdentityController.getLoggedInUser();
			if (user != null) {
				SurespotLog.v(TAG, "using cached credentials");
				// make sure the gcm is set
				// use case:
				// user signs-up without google account (unlikely)
				// user creates google account
				// user opens app again, we have session so neither login or add user is called (which would set the gcm)

				// so we need to upload the gcm here if we haven't already

				NetworkController networkController = SurespotApplication.getNetworkController();
				if (networkController != null) {
					networkController.registerGcmId(new AsyncHttpResponseHandler() {

						@Override
						public void onSuccess(int arg0, String arg1) {
							SurespotLog.v(TAG, "GCM registered in surespot server");
						}

						@Override
						public void onFailure(Throwable arg0, String arg1) {
							SurespotLog.e(TAG, arg0.toString(), arg0);
						}

					});
				}

				Intent newIntent = null;
				// if we have a chat intent go to chat
				String intentName = getIntent().getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);

				// if we have an intent name it's coming from a notification so show the chat
				if (intentName != null) {
					SurespotLog.v(TAG, "found chat name, starting chat activity: " + intentName);
					newIntent = new Intent(this, ChatActivity.class);
					newIntent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, intentName);
					newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);

				}
				else {
					// we have a send action so start friend activity so user can pick someone to send to
					if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
						SurespotLog.v(TAG, "send action, starting home activity so user can select recipient");
						newIntent = new Intent(this, FriendActivity.class);
						newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
						newIntent.setAction(action);
						newIntent.setType(type);
						newIntent.putExtras(intent);
					}
					else {
						if (intent.getStringExtra(IntentFilters.INVITE_NOTIFICATION) == null) {

							// we saved a chat name so load the chat activity with that name
							String lastName = Utils.getSharedPrefsString(this, SurespotConstants.PrefNames.LAST_CHAT);
							if (lastName != null) {
								SurespotLog.v(TAG, "starting chat activity based on LAST_CHAT name");
								newIntent = new Intent(this, ChatActivity.class);
								newIntent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, lastName);
								newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
							}
						}

					}

				}

				if (newIntent == null) {
					newIntent = new Intent(this, FriendActivity.class);
					newIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				}
				startActivity(newIntent);
			}
			else {
				SurespotLog.v(TAG, "no cached credentials, starting Login activity");
				// identity but no session, login
				Intent newIntent = new Intent(this, LoginActivity.class);
				String name = intent.getStringExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME);
				if (name != null) {
					SurespotLog.v(TAG, "setting intent chat name: " + name);
					newIntent.putExtra(SurespotConstants.ExtraNames.SHOW_CHAT_NAME, name);
				}
				else {
					if ((Intent.ACTION_SEND.equals(action) || Intent.ACTION_SEND_MULTIPLE.equals(action)) && type != null) {
						SurespotLog.v(TAG, "setting intent action");
						newIntent.setAction(action);
						newIntent.setType(type);
						newIntent.putExtras(intent);
					}

				}

				intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
				startActivity(newIntent);
			}
		}
		// otherwise show the user / key management activity
		else {
			SurespotLog.v(TAG, "starting signup activity");
			Intent intent = new Intent(this, SignupActivity.class);
			intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
			startActivity(intent);
		}

		finish();
	}
}