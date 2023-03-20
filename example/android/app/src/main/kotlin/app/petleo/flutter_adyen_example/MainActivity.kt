package app.adyen.flutter_adyen_example

import android.content.Intent
import android.os.Bundle

//import io.flutter.app.FlutterActivity
//import io.flutter.plugins.GeneratedPluginRegistrant
import io.flutter.embedding.android.FlutterActivity;
import io.flutter.embedding.engine.FlutterEngine;

class MainActivity: FlutterActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    // FlutterMain.startInitialization(this) //Added line
    super.onCreate(savedInstanceState)
    // GeneratedPluginRegistrant.registerWith(this)
  }
}
