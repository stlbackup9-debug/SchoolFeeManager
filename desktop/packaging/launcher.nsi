; SchoolFeeManager.exe – starts the app with the bundled Java runtime (no console window).
; Built with NSIS (makensis) on Linux or Windows: see desktop/build.gradle.kts → packageWindows.
Unicode true
ManifestDPIAware true
RequestExecutionLevel user
SilentInstall silent
AutoCloseWindow true
Name "School Fee Manager"
OutFile "${OUT}"
Icon "${ICON}"
VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "School Fee Manager"
VIAddVersionKey "FileDescription" "School Fee Manager"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "School Fee Manager"

Section
  IfFileExists "$EXEDIR\runtime\bin\javaw.exe" +3 0
    MessageBox MB_ICONSTOP "The Java runtime is missing in $EXEDIR\runtime. Please reinstall School Fee Manager."
    Abort
  SetOutPath "$EXEDIR"
  Exec '"$EXEDIR\runtime\bin\javaw.exe" -Xmx1g -Dfile.encoding=UTF-8 --enable-native-access=ALL-UNNAMED --sun-misc-unsafe-memory-access=allow -cp "$EXEDIR\app\*" com.school.feemanager.desktop.MainKt'
SectionEnd
