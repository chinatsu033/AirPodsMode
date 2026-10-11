-keep class org.lsposed.hiddenapibypass.** { *; }
-dontwarn org.lsposed.hiddenapibypass.**
-keepclassmembers class android.bluetooth.BluetoothDevice {
    *** createL2capSocket(...);
    *** createInsecureL2capSocket(...);
}
-keepclassmembers class android.bluetooth.BluetoothSocket {
    <init>(...);
}
