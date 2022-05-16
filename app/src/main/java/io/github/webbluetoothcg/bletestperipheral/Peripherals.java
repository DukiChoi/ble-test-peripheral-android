/*
 * Copyright 2015 Google Inc. All rights reserved.
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

import android.Manifest;
import android.app.ListActivity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;

//제일 먼저 시작하는 액티비티.
//4가지 메뉴를 보여줌
public class Peripherals extends ListActivity {

  private static final String[] PERIPHERALS_NAMES = new String[]{"Battery", "Heart Rate Monitor", "Health Thermometer", "Nordic Uart"};
  public final static String EXTRA_PERIPHERAL_INDEX = "PERIPHERAL_INDEX";

  //시작 시에 블루투스 권한 설정이 없어서 오류가 나길래 추가해주었다.
  private static final int MULTIPLE_PERMISSION = 1004;
  private String[] PERMISSIONS = {Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_ADVERTISE, Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION};
  public boolean runtimeCheckPermission(Context context, String... permissions) {
    if (context != null && permissions != null) {
      for (String permission : permissions) {
        if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
          return false;

        }
      }
    }
    return true;
  }


  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_peripherals_list);

    //블루투스 권한 요청
    if (!runtimeCheckPermission(this, PERMISSIONS)) {
      ActivityCompat.requestPermissions(this, PERMISSIONS, MULTIPLE_PERMISSION);
    } else {
      Log.i("권한 테스트", "권한이 있네요");
    }

    //여기서 Array어뎁터를 한개 만들고 리스트를 띄우는 것.
    ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
        /* layout for the list item */ android.R.layout.simple_list_item_1,
        /* id of the TextView to use */ android.R.id.text1,
        /* values for the list */ PERIPHERALS_NAMES);
    setListAdapter(adapter);
  }

  //리스트에 아이템 클릭시에 Peripheral에다가 position(리스트 아이템 몇번째를 클릭했는지 저장하는 변수)을
  //EXTRA_PERIPHERAL_INDEX 라는 이름으로 집어넣고 Peripheral 클래스를 호출한다.
  @Override
  protected void onListItemClick(ListView l, View v, int position, long id) {
    super.onListItemClick(l, v, position, id);

    Intent intent = new Intent(this, Peripheral.class);
    intent.putExtra(EXTRA_PERIPHERAL_INDEX, position);
    startActivity(intent);
  }

}
