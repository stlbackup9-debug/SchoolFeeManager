School Fee Manager – Windows
============================

Start:   double-click SchoolFeeManager.exe (or the "School Fee Manager" shortcut).
First login: admin / admin123 – you must set a new password.

Your data:   %APPDATA%\SchoolFeeManager\school_fees.db
             (kept when you update or uninstall the program)
Backup:      Settings -> Data -> Back up now. A backup made on the Android phone app
             can be restored here, and a backup made here can be restored on the phone.
Printing:    receipts and reports open in your web browser with the print dialog;
             choose a printer or "Microsoft Print to PDF" / "Save as PDF".

This folder contains:
  SchoolFeeManager.exe   the program launcher
  app\                   the program (Java/Kotlin)
  runtime\               a Java 25 runtime (OpenJDK, GPLv2 with the Classpath Exception –
                         licences in runtime\legal), taken from the jdk4py package
