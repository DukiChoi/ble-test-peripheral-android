/*
 * Copyright 2017 Google Inc. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.webbluetoothcg.bletestperipheral;

import android.app.Activity;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.os.Bundle;
import android.os.ParcelUuid;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.TextView.OnEditorActionListener;
import android.widget.Toast;

import org.w3c.dom.Text;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.TimerTask;
import java.util.UUID;

public class NordicUartServiceFragment extends ServiceFragment {
  private static final int MIN_UINT = 0;
  private static final int MAX_UINT8 = (int) Math.pow(2, 8) - 1;
  private static final int MAX_UINT16 = (int) Math.pow(2, 16) - 1;
  /**
   * See <a href="https://developer.bluetooth.org/gatt/services/Pages/ServiceViewer.aspx?u=org.bluetooth.service.health_thermometer.xml">
   * Health Thermometer Service</a>
   * This service exposes two characteristics with descriptors:
   *   - Measurement Interval Characteristic:
   *       - Listen to notifications to from which you can subscribe to notifications
   *     - CCCD Descriptor:
   *       - Read/Write to get/set notifications.
   *     - User Description Descriptor:
   *       - Read/Write to get/set the description of the Characteristic.
   *   - Temperature Measurement Characteristic:
   *       - Read value to get the current interval of the temperature measurement timer.
   *       - Write value resets the temperature measurement timer with the new value. This timer
   *         is responsible for triggering value changed events every "Measurement Interval" value.
   *     - CCCD Descriptor:
   *       - Read/Write to get/set notifications.
   *     - User Description Descriptor:
   *       - Read/Write to get/set the description of the Characteristic.
   */
  private static final int INITIAL_SEND = 0;
  private static final int INITIAL_RECEIVE = 0;
  //이게 프라이머리 서비스
  private static final UUID UART_SERVICE_UUID = UUID
          .fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e");

  /**
   * See <a href="https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.temperature_measurement.xml">
   * Temperature Measurement</a>
   */

  private static final UUID SEND_UUID = UUID
          .fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e");  //RxChar UUID
  private static final int SEND_VALUE_FORMAT = BluetoothGattCharacteristic.FORMAT_UINT8;
  private static final String SEND_DESCRIPTION = "This characteristic is used " +
          "as RxChar Nordic Uart device";


  /**
   * See <a href="https://developer.bluetooth.org/gatt/characteristics/Pages/CharacteristicViewer.aspx?u=org.bluetooth.characteristic.measurement_interval.xml">
   * Measurement Interval</a>
   */
  private static final UUID RECIEVE_UUID = UUID
          .fromString("6e400003-b5a3-f393-e0a9-e50e24dcca9e");  //TxChar UUID
  private static final int RECEIVE_VALUE_FORMAT = BluetoothGattCharacteristic.FORMAT_UINT8;


  private static final String RECEIVE_DESCRIPTION = "This characteristic is used " +
          "as TxChar of Nordic Uart device";




  private BluetoothGattService mNordicUartService;
  private BluetoothGattCharacteristic mSendCharacteristic;
  private BluetoothGattCharacteristic mReceiveCharacteristic;
  private BluetoothGattDescriptor mReceiveCCCDescriptor;


  private ServiceFragmentDelegate mDelegate;

  //원래있던 TemperatureMeasurement를 Send로 바꿔줌.
  private EditText mEditTextSendValue;
  private TextView mTextViewReceiveValue;
  //이건 Text Editor에 수정을 할 시에 그걸 가지고 보낼 값(Characteristic Value)을 바꾸는 것.
  private final OnEditorActionListener mOnEditorActionListenerSend = new OnEditorActionListener() {
    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        String newSENDValueString = textView.getText().toString();
        if (isValidCharacteristicValue(newSENDValueString,
                SEND_VALUE_FORMAT)) {
          int newSendValue = Integer.parseInt(newSENDValueString);
          mSendCharacteristic.setValue(newSendValue,
                  SEND_VALUE_FORMAT,
                  /* offset */ 1);
        } else {
          Toast.makeText(getActivity(), R.string.heartRateMeasurementValueInvalid,
                  Toast.LENGTH_SHORT).show();
        }
      }
      return false;
    }
  };
  
  //이건 사실 필요없음
  private final OnEditorActionListener mOnEditorActionListenerReceive = new OnEditorActionListener() {
    @Override
    public boolean onEditorAction(TextView textView, int actionId, KeyEvent event) {
      if (actionId == EditorInfo.IME_ACTION_DONE) {
        String newReceiveValueString = textView.getText().toString();
        if (isValidCharacteristicValue(newReceiveValueString,
                RECEIVE_VALUE_FORMAT)) {
          int newReceiveValue = Integer.parseInt(newReceiveValueString);
          mReceiveCharacteristic.setValue(newReceiveValue,
                  RECEIVE_VALUE_FORMAT,
                  /* offset */ 1);
        } else {
          Toast.makeText(getActivity(), R.string.heartRateMeasurementValueInvalid,
                  Toast.LENGTH_SHORT).show();
        }
      }
      return false;
    }
  };

  private static final String TAG = Peripheral.class.getCanonicalName();
  //이건 Notify 버튼 즉 Send 버튼을 리스닝 해줌
  private final View.OnClickListener mNotifyButtonListener = new View.OnClickListener() {
    @Override
    public void onClick(View v) {
      int newSENDValueString = Integer.parseInt(mEditTextSendValue.getText().toString());
      mSendCharacteristic.setValue(newSENDValueString,
              SEND_VALUE_FORMAT,
              /* offset */ 1);
      mDelegate.sendNotificationToDevices(mSendCharacteristic);
      Log.v(TAG, "sent: " + Arrays.toString(mSendCharacteristic.getValue()));
    }
  };

  private boolean isValidCharacteristicValue(String s, int format) {
    try {
      int value = Integer.parseInt(s);
      if (format == BluetoothGattCharacteristic.FORMAT_UINT8) {
        return (value >= MIN_UINT) && (value <= MAX_UINT8);
      } else if (format == BluetoothGattCharacteristic.FORMAT_UINT16) {
        return (value >= MIN_UINT) && (value <= MAX_UINT16);
      } else {
        throw new IllegalArgumentException(format + " is not a valid argument");
      }
    } catch (NumberFormatException e) {
      return false;
    }
  }


  //원래있던 MeasurementInterval을 ReceiveValue로 바꿔줌



  public NordicUartServiceFragment() {

    //이거는 Send
    mSendCharacteristic =
            new BluetoothGattCharacteristic(SEND_UUID,
                    BluetoothGattCharacteristic.PROPERTY_INDICATE,
                    /* No permissions */ 0);

    mSendCharacteristic.addDescriptor(
            Peripheral.getClientCharacteristicConfigurationDescriptor());

    mSendCharacteristic.addDescriptor(
            Peripheral.getCharacteristicUserDescriptionDescriptor(SEND_DESCRIPTION));

    //이거는 Receive
    mReceiveCharacteristic =
            new BluetoothGattCharacteristic(
                    RECIEVE_UUID,
                    BluetoothGattCharacteristic.PROPERTY_WRITE,
                    BluetoothGattCharacteristic.PERMISSION_WRITE);

    mReceiveCCCDescriptor = Peripheral.getClientCharacteristicConfigurationDescriptor();
    mReceiveCharacteristic.addDescriptor(mReceiveCCCDescriptor);

    mReceiveCharacteristic.addDescriptor(
            Peripheral.getCharacteristicUserDescriptionDescriptor(RECEIVE_DESCRIPTION));

    mNordicUartService = new BluetoothGattService(UART_SERVICE_UUID,
            BluetoothGattService.SERVICE_TYPE_PRIMARY);
    mNordicUartService.addCharacteristic(mSendCharacteristic);
    mNordicUartService.addCharacteristic(mReceiveCharacteristic);
  }

  //////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////
  //        여기는 ON CREATE VIEW
  //////////////////////////////////////////////////////////////////////////////////////////////////
  //////////////////////////////////////////////////////////////////////////////////////////////////
  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {

    View view = inflater.inflate(R.layout.fragment_nordic_uart, container, false);
    mEditTextSendValue= (EditText) view
            .findViewById(R.id.EditText_Sendvalue);
    mEditTextSendValue
            .setOnEditorActionListener(mOnEditorActionListenerSend);
    mTextViewReceiveValue = (TextView) view
            .findViewById(R.id.Textview_Recievevalue);
    mTextViewReceiveValue
            .setOnEditorActionListener(mOnEditorActionListenerReceive);

    Button notifyButton = (Button) view.findViewById(R.id.button_SendDataNotify);
    notifyButton.setOnClickListener(mNotifyButtonListener);
    setSendValue(INITIAL_SEND, INITIAL_RECEIVE);


    return view;
  }

  @Override
  public void onAttach(Activity activity) {
    super.onAttach(activity);
    try {
      mDelegate = (ServiceFragmentDelegate) activity;
    } catch (ClassCastException e) {
      throw new ClassCastException(activity.toString()
              + " must implement ServiceFragmentDelegate");
    }
  }

  @Override
  public void onDetach() {
    super.onDetach();
    mDelegate = null;
  }

  @Override
  public void onStop() {
    super.onStop();
  }

  @Override
  public BluetoothGattService getBluetoothGattService() {
    return mNordicUartService;
  }

  @Override
  public ParcelUuid getServiceUUID() {
    return new ParcelUuid(UART_SERVICE_UUID);
  }
  
  //이건 처음에 onCreate에서 값 세팅 해주는 것
  private void setSendValue(int SendValue, int ReceiveValue) {

    /* Set the org.bluetooth.characteristic.temperature_measurement
     * characteristic to a byte array of size 5 so
     * we can use setValue(value, format, offset);
     *
     * Flags (8bit) + Temperature Measurement Value (float) = 5 bytes
     *
     * Flags:
     *   Temperature Units Flag (0) -> Celsius
     *   Time Stamp Flag (0) -> Time Stamp field not present
     *   Temperature Type Flag (0) -> Temperature Type field not present
     *   Unused (00000)
     */

    //보낼 값이니까 Send Characteristic(TxChar)의 value 값을 변경해준다.
    //이건 앞으로 (uint8형식으로 넣는다는 뜻) flag를 8(uint8)로 맞춰주는 것.
    mSendCharacteristic.setValue(new byte[]{0b00001000, 0});
    mReceiveCharacteristic.setValue(new byte[]{0b00001000, 0});
    // Characteristic Value: [flags, 0, 0, 0]
    mSendCharacteristic.setValue(SendValue,
            SEND_VALUE_FORMAT,
            /* offset */ 1);
    mReceiveCharacteristic.setValue(ReceiveValue,
            RECEIVE_VALUE_FORMAT,
            /* offset */ 1);
    // Characteristic Value: [flags, heart rate value, 0, 0]
    mEditTextSendValue.setText(Integer.toString(SendValue));
    mTextViewReceiveValue.setText(Integer.toString(ReceiveValue));
    //여기서 보낼 값으로 에딧 텍스트 값도 변경해줌
    }



  //이건 Receive 값 받아오는 것
  @Override
  public int writeCharacteristic(BluetoothGattCharacteristic characteristic, int offset, byte[] value) {
    if (offset != 0) {
      return BluetoothGatt.GATT_INVALID_OFFSET;
    }
    // Heart Rate control point is a 8bit characteristic
    if (value.length != 1) {
      return BluetoothGatt.GATT_INVALID_ATTRIBUTE_LENGTH;
    }
    getActivity().runOnUiThread(new Runnable() {
        @Override
        public void run() {
          String converted = new String(value);
          mTextViewReceiveValue.setText(converted);
          Log.v(TAG, "Received: " + Arrays.toString(mReceiveCharacteristic.getValue()));
        }
    });
    return BluetoothGatt.GATT_SUCCESS;
  }

  
  // 노티피케이션을 쓸 수 있게 되면 알림
  @Override
  public void notificationsEnabled(BluetoothGattCharacteristic characteristic, boolean indicate) {
    if (characteristic.getUuid() != SEND_UUID) {
      Log.v(TAG, "UUID가 SendUUID와 다릅니다: " + characteristic.getUuid());
      return;
    }
    if (indicate) {
      return;
    }
    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        int newSENDValueString = Integer.parseInt(mEditTextSendValue.getText().toString());
        mSendCharacteristic.setValue(newSENDValueString,
                  SEND_VALUE_FORMAT,
                  /* offset */ 1);
        Toast.makeText(getActivity(), R.string.notificationsEnabled, Toast.LENGTH_SHORT)
                .show();
      }
    });
  }

  //노티피케이션을 못 쓰게 되면 알림
  @Override
  public void notificationsDisabled(BluetoothGattCharacteristic characteristic) {
    if (characteristic.getUuid() != SEND_UUID) {
      return;
    }

    getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        Toast.makeText(getActivity(), R.string.notificationsNotEnabled, Toast.LENGTH_SHORT)
                .show();
      }
    });
  }
  private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
  public static String bytesToHex(byte[] bytes) {
    char[] hexChars = new char[bytes.length * 2];
    for (int j = 0; j < bytes.length; j++) {
      int v = bytes[j] & 0xFF;
      hexChars[j * 2] = HEX_ARRAY[v >>> 4];
      hexChars[j * 2 + 1] = HEX_ARRAY[v & 0x0F];
    }
    return new String(hexChars);
  }
}
