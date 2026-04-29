# 🏠 Smart Home Gesture – Bài 3

## Mô tả
Ứng dụng Android điều khiển thiết bị thông minh (IoT) bằng **cử chỉ tay**,
sử dụng **Accelerometer** và **Gyroscope** của điện thoại.

---

## 🧩 Cấu trúc project

```
SmartHomeGesture/
├── app/src/main/java/com/example/smarthomegesture/
│   ├── MainActivity.kt           ← Màn hình chính + sensor loop
│   ├── GestureDetectorHelper.kt  ← Nhận diện cử chỉ (Accel + Gyro)
│   ├── BluetoothController.kt    ← Kết nối & gửi lệnh Bluetooth (SPP)
│   ├── WiFiController.kt         ← Gửi lệnh HTTP đến ESP8266/ESP32
│   ├── DeviceRepository.kt       ← Lưu trữ danh sách thiết bị (SharedPrefs)
│   ├── DeviceAdapter.kt          ← RecyclerView adapter
│   ├── SmartDevice.kt            ← Data model + Enums
│   ├── DeviceControlActivity.kt  ← Chi tiết & cấu hình thiết bị
│   └── BluetoothScanActivity.kt  ← Quét thiết bị Bluetooth
└── app/src/main/res/
    ├── layout/
    │   ├── activity_main.xml
    │   ├── activity_device_control.xml
    │   ├── activity_bluetooth_scan.xml
    │   ├── item_device.xml
    │   └── item_bluetooth_device.xml
    └── values/
        ├── colors.xml, strings.xml, themes.xml
```

---

## 📱 Tính năng

| Cử chỉ | Cảm biến | Hành động |
|--------|---------|-----------|
| **Vẫy tay ngang** | Accelerometer (X-axis) | Bật/Tắt đèn thông minh |
| **Nghiêng lên** | Accelerometer (Y-axis) | Tăng âm lượng +5% |
| **Nghiêng xuống** | Accelerometer (Y-axis) | Giảm âm lượng -5% |
| **Xoay phải** | Gyroscope (Z-axis) | Chuyển bài tiếp theo |
| **Xoay trái** | Gyroscope (Z-axis) | Quay lại bài trước |

---

## ⚙️ Cách mở project trong Android Studio

1. Giải nén file `SmartHomeGesture.zip`
2. Mở **Android Studio** → `File > Open` → chọn thư mục `SmartHomeGesture`
3. Chờ Gradle sync hoàn tất (cần kết nối Internet lần đầu)
4. Cắm điện thoại Android (API 26+) hoặc dùng **Emulator**
5. Nhấn ▶️ **Run**

> **Lưu ý Emulator:** Emulator không có sensor vật lý thật.
> Dùng **Extended Controls > Virtual sensors** để giả lập Accelerometer/Gyroscope.

---

## 🔌 Kết nối thiết bị IoT thật

### Bluetooth (Arduino/HC-05)
```
Arduino nhận chuỗi qua Serial:
  "TOGGLE:ON\n"   → bật relay
  "TOGGLE:OFF\n"  → tắt relay
  "VOLUME:75\n"   → đặt âm lượng 75
  "NEXT\n"        → chuyển bài
  "PREV\n"        → bài trước
```

### WiFi (ESP8266/ESP32)
```
HTTP GET đến địa chỉ IP của ESP:
  http://192.168.x.x/toggle
  http://192.168.x.x/volume?v=75
  http://192.168.x.x/next
  http://192.168.x.x/prev
```

---

## 🛠 Yêu cầu môi trường

- Android Studio Hedgehog (2023.1.1) trở lên
- Android SDK API 26 (Android 8.0) trở lên
- Kotlin 1.9.0
- Gradle 8.0

---

## 📦 Dependencies chính

```gradle
implementation 'com.google.android.material:material:1.11.0'
implementation 'androidx.recyclerview:recyclerview:1.3.2'
implementation 'androidx.lifecycle:lifecycle-runtime-ktx:2.7.0'
implementation 'org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3'
implementation 'com.google.code.gson:gson:2.10.1'
```

---

## 📐 Thuật toán nhận diện cử chỉ

### Wave (Vẫy tay)
- Đọc gia tốc trục X từ Accelerometer
- Phát hiện **≥ 2 lần đổi chiều** trong vòng **1000ms**
- Ngưỡng: |X| > 12 m/s²

### Tilt (Nghiêng)
- Đọc gia tốc trục Y
- Y < -5 m/s² → nghiêng lên (tăng âm lượng)
- Y > +5 m/s² → nghiêng xuống (giảm âm lượng)
- Cooldown: 300ms

### Rotate (Xoay)
- Đọc vận tốc góc trục Z từ Gyroscope
- rotZ > 2.5 rad/s → xoay trái → bài trước
- rotZ < -2.5 rad/s → xoay phải → bài tiếp
- Cooldown: 600ms
