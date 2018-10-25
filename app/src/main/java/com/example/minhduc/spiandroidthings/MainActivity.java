package com.example.minhduc.spiandroidthings;

import android.app.Activity;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.galarzaa.androidthings.Rc522;
import com.google.android.things.pio.Gpio;
import com.google.android.things.pio.PeripheralManager;
import com.google.android.things.pio.SpiDevice;

import java.io.IOException;

public class MainActivity extends Activity {
    private static final String SPI_PORT = "SPI0.0";
    private static final String PIN_RESET = "BCM25";
    private static final String PIN_LED = "BCM2";
    private static final String PIN_GREEN = "BCM3";
    private static final String PIN_BLUE = "BCM4";

    private static final String TAG_GLOB = "RFID";

    RfidWriteTask mRfidWriteTask;
    RfidReadTask mRfidReadTask;
    String resultsText = "";

    private Rc522 mRc522;

    private TextView mTagDetectedView;
    private TextView mTagUidView;
    private TextView mTagResultsView;
    private TextView mStatus;

    private Button button_write;
    private Button button_read;
    private Button button_stop;

    private SpiDevice spiDevice;

    private Gpio gpioReset;
    private Gpio mLedGpioRed;
    private Gpio mLedGpioGreen;
    private Gpio mLedGpioBlue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTagDetectedView = (TextView) findViewById(R.id.tag_read);
        mTagUidView = (TextView) findViewById(R.id.tag_uid);
        mTagResultsView = (TextView) findViewById(R.id.tag_results);
        mStatus = (TextView) findViewById(R.id.status);

        button_write = (Button) findViewById(R.id.button_write);
        button_read = (Button) findViewById(R.id.button_read);
        button_stop = (Button) findViewById(R.id.button_stop);

        mStatus.setVisibility(View.VISIBLE);

        button_write.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRfidWriteTask = new RfidWriteTask(mRc522);
                mRfidWriteTask.execute();
                mStatus.setText(R.string.waiting);
            }
        });

        button_read.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mRfidReadTask = new RfidReadTask(mRc522);
                mRfidReadTask.execute();
                mStatus.setText(R.string.waiting);
            }
        });

        PeripheralManager pioService = PeripheralManager.getInstance();
        try {
            spiDevice = pioService.openSpiDevice(SPI_PORT);
            gpioReset = pioService.openGpio(PIN_RESET);
            mLedGpioRed = pioService.openGpio(PIN_LED);
            mLedGpioGreen = pioService.openGpio(PIN_GREEN);
            mLedGpioBlue = pioService.openGpio(PIN_BLUE);

            mLedGpioRed.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
            mLedGpioGreen.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);
            mLedGpioBlue.setDirection(Gpio.DIRECTION_OUT_INITIALLY_HIGH);

            mLedGpioRed.setActiveType(Gpio.ACTIVE_LOW);
            mLedGpioGreen.setDirection(Gpio.ACTIVE_LOW);
            mLedGpioBlue.setDirection(Gpio.ACTIVE_LOW);

            mRc522 = new Rc522(spiDevice, gpioReset);
            mRc522.setDebugging(true);
        } catch (IOException e) {
            Log.e(TAG_GLOB, "Error on opening GPIO ports.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            if (spiDevice != null) {
                spiDevice.close();
            }
            if (gpioReset != null) {
                gpioReset.close();
            }
            if (mLedGpioRed != null) {
                mLedGpioRed.close();
            }
            if (mLedGpioGreen != null) {
                mLedGpioGreen.close();
            }
            if (mLedGpioBlue != null) {
                mLedGpioBlue.close();
            }
        } catch (IOException e) {
            Log.e(TAG_GLOB, "Error on closing GPIO ports.");
        } finally {
            spiDevice = null;
            gpioReset = null;
            mLedGpioRed = null;
            mLedGpioGreen = null;
            mLedGpioBlue = null;
        }
    }

    private class RfidWriteTask extends AsyncTask<Object, Object, Boolean> {
        private static final String TAG = "RfidWriteTask";
        private Rc522 rc522;

        RfidWriteTask(Rc522 rc522) {
            this.rc522 = rc522;
        }

        @Override
        protected void onPreExecute() {
            button_write.setEnabled(false);
            button_read.setEnabled(false);
            mTagResultsView.setVisibility(View.GONE);
            mTagDetectedView.setVisibility(View.GONE);
            mTagUidView.setVisibility(View.GONE);
            resultsText = "";
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            rc522.stopCrypto();
            while (true) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
                //Check if a RFID tag has been found
                if (!rc522.request()) {
                    continue;
                }
                //Check for collision errors
                if (!rc522.antiCollisionDetect()) {
                    continue;
                }
                byte[] uuid = rc522.getUid();
                return rc522.selectTag(uuid);
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (!success) {
                mTagResultsView.setText(R.string.unknown_error);
                mStatus.setText(R.string.fail);
                return;
            }

            byte address_name = Rc522.getBlockAddress(2, 1);
            byte address_dob = Rc522.getBlockAddress(2, 2);
            byte address_id = Rc522.getBlockAddress(2, 3);

            writeToRFID("Tran Minh Duc", "08/06/1998", "1610800", address_name, address_dob, address_id);

        }
    }

    private class RfidReadTask extends AsyncTask<Object, Object, Boolean> {
        private static final String TAG = "RfidReadTask";
        private Rc522 rc522;

        RfidReadTask(Rc522 rc522) {
            this.rc522 = rc522;
        }

        @Override
        protected void onPreExecute() {
            button_write.setEnabled(false);
            button_read.setEnabled(false);
            mTagResultsView.setVisibility(View.GONE);
            mTagDetectedView.setVisibility(View.GONE);
            mTagUidView.setVisibility(View.GONE);
            resultsText = "";
        }

        @Override
        protected Boolean doInBackground(Object... params) {
            rc522.stopCrypto();
            while (true) {
                try {
                    Thread.sleep(50);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                    return null;
                }
                //Check if a RFID tag has been found
                if (!rc522.request()) {
                    continue;
                }
                //Check for collision errors
                if (!rc522.antiCollisionDetect()) {
                    continue;
                }
                byte[] uuid = rc522.getUid();
                return rc522.selectTag(uuid);
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (!success) {
                mTagResultsView.setText(R.string.unknown_error);
                mStatus.setText(R.string.fail);
                return;
            }

            byte address_name = Rc522.getBlockAddress(2, 1);
            byte address_dob = Rc522.getBlockAddress(2, 2);
            byte address_id = Rc522.getBlockAddress(2, 3);

            readFromRFID(address_name);
//            readFromRFID(address_dob);
//            readFromRFID(address_id);

        }
    }

    private void writeToRFID(String Name, String DOB, String ID, byte blockName, byte blockDOB, byte blockID){
        String tmp = Name;
        for (int i = Name.length(); i < 16; i++) {
            tmp = tmp + " ";
        }
        byte[] name = tmp.getBytes();

        tmp = DOB;
        for (int i = DOB.length(); i < 16; i++) {
            tmp = tmp + " ";
        }
        byte[] dob = tmp.getBytes();

        tmp = ID;
        for (int i = ID.length(); i < 16; i++) {
            tmp = tmp + " ";
        }
        byte[] id = tmp.getBytes();

        try {
            byte[] key = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

            boolean result = mRc522.authenticateCard(Rc522.AUTH_A, blockName, key);
            if (!result) {
                mTagResultsView.setText(R.string.authetication_error);
                mStatus.setText(R.string.fail);
                return;
            }
            result = mRc522.writeBlock(blockName, name);
            if (!result) {
                mTagResultsView.setText(R.string.write_error);
                mStatus.setText(R.string.fail);
                return;
            }

            result = mRc522.authenticateCard(Rc522.AUTH_A, blockDOB, key);
            if (!result) {
                mTagResultsView.setText(R.string.authetication_error);
                mStatus.setText(R.string.fail);
                return;
            }
            result = mRc522.writeBlock(blockDOB, dob);
            if (!result) {
                mTagResultsView.setText(R.string.write_error);
                mStatus.setText(R.string.fail);
                return;
            }

            result = mRc522.authenticateCard(Rc522.AUTH_A, blockID, key);
            if (!result) {
                mTagResultsView.setText(R.string.authetication_error);
                mStatus.setText(R.string.fail);
                return;
            }
            result = mRc522.writeBlock(blockID, id);
            if (!result) {
                mTagResultsView.setText(R.string.write_error);
                mStatus.setText(R.string.fail);
                return;
            }
            resultsText += "Sector written successfully";

            mRc522.stopCrypto();
            mTagResultsView.setText(resultsText);
            mStatus.setText(R.string.success);
        } finally {
            button_write.setEnabled(true);
            button_read.setEnabled(true);

            mTagUidView.setText(getString(R.string.tag_uid, mRc522.getUidString()));
            mTagResultsView.setVisibility(View.VISIBLE);
            mTagDetectedView.setVisibility(View.VISIBLE);
            mTagUidView.setVisibility(View.VISIBLE);
        }
    }

    private void readFromRFID(byte Block){

        try {
            byte[] key = {(byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF};

            boolean result = mRc522.authenticateCard(Rc522.AUTH_A, Block, key);
            if (!result) {
                mTagResultsView.setText(R.string.authetication_error);
                mStatus.setText(R.string.fail);
                return;
            }
            byte[] buffer = new byte[16];

            result = mRc522.readBlock(Block, buffer);
            if (!result) {
                mTagResultsView.setText(R.string.read_error);
                return;
            }
            resultsText += "Sector read successfully: " + buffer.toString();

            mRc522.stopCrypto();
            mTagResultsView.setText(resultsText);
            mStatus.setText(R.string.success);
        } finally {
            button_write.setEnabled(true);
            button_read.setEnabled(true);

            mTagUidView.setText(getString(R.string.tag_uid, mRc522.getUidString()));
            mTagResultsView.setVisibility(View.VISIBLE);
            mTagDetectedView.setVisibility(View.VISIBLE);
            mTagUidView.setVisibility(View.VISIBLE);
        }
    }
}
