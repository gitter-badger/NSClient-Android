package com.eveningoutpost.dexdrip.UtilityModels;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;

import com.eveningoutpost.dexdrip.Models.BgReading;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import info.nightscout.client.MainApp;

/**
 * Created by stephenblack on 11/7/14.
 * Adapted by mike
 */
public class XDripEmulator {
    private static Logger log = LoggerFactory.getLogger(XDripEmulator.class);
    private static List<BgReading> latest6bgReadings = new ArrayList<BgReading>();

    public void handleNewBgReading(BgReading bgReading, boolean isFull, Context context) {
        SharedPreferences SP = PreferenceManager.getDefaultSharedPreferences(MainApp.instance().getApplicationContext());
        boolean sendToDanaApp = SP.getBoolean("ns_sendtodanaapp", false);

        PowerManager powerManager = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
        PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "sendQueue");
        wakeLock.acquire();
        try {
            Intent updateIntent = new Intent(Intents.ACTION_NEW_BG_ESTIMATE_NO_DATA);
            context.sendBroadcast(updateIntent);

            Bundle bundle = new Bundle();
            bundle.putDouble(Intents.EXTRA_BG_ESTIMATE, bgReading.value);
            bundle.putDouble(Intents.EXTRA_BG_SLOPE, bgReading.slope);
            bundle.putString(Intents.EXTRA_BG_SLOPE_NAME, "9");
            bundle.putInt(Intents.EXTRA_SENSOR_BATTERY, bgReading.battery_level);
            bundle.putLong(Intents.EXTRA_TIMESTAMP, bgReading.timestamp);

            bundle.putDouble(Intents.EXTRA_RAW, bgReading.raw);
            Intent intent = new Intent(Intents.ACTION_NEW_BG_ESTIMATE);
            intent.putExtras(bundle);
            intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES);
            context.sendBroadcast(intent, Intents.RECEIVER_PERMISSION);
            List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);

            log.debug("XDRIP BG " + bgReading.valInUnit() + " (" + new SimpleDateFormat("H:mm").format(new Date(bgReading.timestamp)) + ") " + x.size() + " receivers");

            // reset array if data are comming from new connection
            if (isFull) latest6bgReadings = new ArrayList<BgReading>();

            // add new reading
            latest6bgReadings.add(bgReading);
            // cut off to 6 records
            if (latest6bgReadings.size() > 7) latest6bgReadings.remove(0);

            if (sendToDanaApp) sendToBroadcastReceiverToDanaApp(context);

        } finally {
            wakeLock.release();
        }
    }

    private static void sendToBroadcastReceiverToDanaApp(Context context) {


        Intent intent = new Intent("danaR.action.BG_DATA");
        Collections.reverse(latest6bgReadings);

        int sizeRecords = latest6bgReadings.size();
        double deltaAvg30min = 0d;
        double deltaAvg15min = 0d;
        double avg30min = 0d;
        double avg15min = 0d;

        boolean notGood = false;

        if (sizeRecords > 6) {
            for (int i = sizeRecords - 6; i < sizeRecords; i++) {
                short glucoseValueBeeingProcessed = (short) latest6bgReadings.get(i).value;
                if (glucoseValueBeeingProcessed < 40) {
                    notGood = true;
                    log.debug("DANAAPP data not good " + latest6bgReadings.get(i).timestamp);
                }
                deltaAvg30min += glucoseValueBeeingProcessed - latest6bgReadings.get(i - 1).value;
                avg30min += glucoseValueBeeingProcessed;
                if (i >= sizeRecords - 3) {
                    avg15min += glucoseValueBeeingProcessed;
                    deltaAvg15min += glucoseValueBeeingProcessed - latest6bgReadings.get(i - 1).value;
                }
            }
            deltaAvg30min /= 6d;
            deltaAvg15min /= 3d;
            avg30min /= 6d;
            avg15min /= 3d;

            if (notGood) return;

            Bundle bundle = new Bundle();
            BgReading timeMatechedRecordCurrent = latest6bgReadings.get(sizeRecords - 1);
            bundle.putLong("time", timeMatechedRecordCurrent.timestamp);
            bundle.putInt("value", (int) timeMatechedRecordCurrent.value);
            bundle.putInt("delta", (int) (timeMatechedRecordCurrent.value - latest6bgReadings.get(sizeRecords - 2).value));
            bundle.putDouble("deltaAvg30min", deltaAvg30min);
            bundle.putDouble("deltaAvg15min", deltaAvg15min);
            bundle.putDouble("avg30min", avg30min);
            bundle.putDouble("avg15min", avg15min);

            intent.putExtras(bundle);

            List<ResolveInfo> x = context.getPackageManager().queryBroadcastReceivers(intent, 0);
            log.debug("DANAAPP  " + x.size() + " receivers");

            context.sendBroadcast(intent);
        }
    }
}
