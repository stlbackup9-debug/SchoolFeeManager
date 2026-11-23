; Installer for School Fee Manager (Windows 10/11, 64-bit). Installs for the current user – no admin rights needed.
; Data (database) is stored in %APPDATA%\SchoolFeeManager and is NOT removed by updates or uninstalling.
Unicode true
ManifestDPIAware true
SetCompressor /SOLID lzma
RequestExecutionLevel user

!define APP "School Fee Manager"
!define KEY "Software\Microsoft\Windows\CurrentVersion\Uninstall\SchoolFeeManager"

Name "${APP} ${VERSION}"
OutFile "${OUT}"
InstallDir "$LOCALAPPDATA\Programs\School Fee Manager"
InstallDirRegKey HKCU "${KEY}" "InstallLocation"
BrandingText "${APP} ${VERSION}"
VIProductVersion "${VERSION}.0"
VIAddVersionKey "ProductName" "${APP}"
VIAddVersionKey "FileDescription" "${APP} Setup"
VIAddVersionKey "ProductVersion" "${VERSION}"
VIAddVersionKey "FileVersion" "${VERSION}"
VIAddVersionKey "LegalCopyright" "${APP}"

!include "MUI2.nsh"
!include "x64.nsh"
!define MUI_ICON "${ICON}"
!define MUI_UNICON "${ICON}"
!define MUI_ABORTWARNING
!define MUI_WELCOMEPAGE_TEXT "This will install ${APP} ${VERSION} on this computer.$\r$\n$\r$\nYour data is kept in %APPDATA%\SchoolFeeManager. Installing a newer version over this one keeps all data.$\r$\n$\r$\nClick Next to continue."
!define MUI_FINISHPAGE_RUN "$INSTDIR\SchoolFeeManager.exe"
!define MUI_FINISHPAGE_RUN_TEXT "Start ${APP}"
!insertmacro MUI_PAGE_WELCOME
!insertmacro MUI_PAGE_DIRECTORY
!insertmacro MUI_PAGE_INSTFILES
!insertmacro MUI_PAGE_FINISH
!insertmacro MUI_UNPAGE_CONFIRM
!insertmacro MUI_UNPAGE_INSTFILES
!insertmacro MUI_LANGUAGE "English"

Function .onInit
  ${IfNot} ${RunningX64}
    MessageBox MB_ICONSTOP "${APP} needs 64-bit Windows 10 or 11."
    Abort
  ${EndIf}
FunctionEnd

Section "Install"
  ; replace the program files of an older version (data is elsewhere and is kept)
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\runtime"
  SetOutPath "$INSTDIR"
  File /r "${SRC}\*.*"
  WriteUninstaller "$INSTDIR\Uninstall.exe"
  CreateShortcut "$SMPROGRAMS\${APP}.lnk" "$INSTDIR\SchoolFeeManager.exe" "" "$INSTDIR\SchoolFeeManager.exe" 0
  CreateShortcut "$DESKTOP\${APP}.lnk" "$INSTDIR\SchoolFeeManager.exe" "" "$INSTDIR\SchoolFeeManager.exe" 0
  WriteRegStr HKCU "${KEY}" "DisplayName" "${APP}"
  WriteRegStr HKCU "${KEY}" "DisplayVersion" "${VERSION}"
  WriteRegStr HKCU "${KEY}" "Publisher" "${APP}"
  WriteRegStr HKCU "${KEY}" "DisplayIcon" "$INSTDIR\SchoolFeeManager.exe"
  WriteRegStr HKCU "${KEY}" "InstallLocation" "$INSTDIR"
  WriteRegStr HKCU "${KEY}" "UninstallString" '"$INSTDIR\Uninstall.exe"'
  WriteRegDWORD HKCU "${KEY}" "NoModify" 1
  WriteRegDWORD HKCU "${KEY}" "NoRepair" 1
  WriteRegDWORD HKCU "${KEY}" "EstimatedSize" ${SIZE_KB}
SectionEnd

Section "Uninstall"
  Delete "$SMPROGRAMS\${APP}.lnk"
  Delete "$DESKTOP\${APP}.lnk"
  RMDir /r "$INSTDIR\app"
  RMDir /r "$INSTDIR\runtime"
  Delete "$INSTDIR\SchoolFeeManager.exe"
  Delete "$INSTDIR\README.txt"
  Delete "$INSTDIR\Uninstall.exe"
  RMDir "$INSTDIR"
  DeleteRegKey HKCU "${KEY}"
  MessageBox MB_ICONINFORMATION "${APP} was removed. Your data in %APPDATA%\SchoolFeeManager was kept – delete that folder yourself if you no longer need it."
SectionEnd
