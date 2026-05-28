package com.takpack.smsforwarder;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

public class SmsReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (!DataStore.isEnabled(context)) return;

        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            SmsMessage[] msgs = Telephony.Sms.Intents.getMessagesFromIntent(intent);
            if (msgs == null || msgs.length == 0) return;

            String sender = "";
            StringBuilder body = new StringBuilder();
            long time = System.currentTimeMillis();

            for (SmsMessage m : msgs) {
                if (m == null) continue;
                sender = m.getDisplayOriginatingAddress();
                body.append(m.getMessageBody());
                time = m.getTimestampMillis();
            }
            DataStore.addSms(context, sender, body.toString(), time);
        }
    }
}
