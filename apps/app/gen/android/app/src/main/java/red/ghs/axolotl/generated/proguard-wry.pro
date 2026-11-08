# THIS FILE IS AUTO-GENERATED. DO NOT MODIFY!!

# Copyright 2020-2023 Tauri Programme within The Commons Conservancy
# SPDX-License-Identifier: Apache-2.0
# SPDX-License-Identifier: MIT

-keep class red.ghs.axolotl.* {
  native <methods>;
}

-keep class red.ghs.axolotl.WryActivity {
  public <init>(...);

  void setWebView(red.ghs.axolotl.RustWebView);
  java.lang.Class getAppClass(...);
  int getId();
  java.lang.String getVersion();
  int startActivity(...);
}

-keep class red.ghs.axolotl.Ipc {
  public <init>(...);

  @android.webkit.JavascriptInterface public <methods>;
}

-keep class red.ghs.axolotl.RustWebView {
  public <init>(...);

  void loadUrlMainThread(...);
  void loadHTMLMainThread(...);
  void evalScript(...);
}

-keep class red.ghs.axolotl.RustWebChromeClient,red.ghs.axolotl.RustWebViewClient {
  public <init>(...);
}
