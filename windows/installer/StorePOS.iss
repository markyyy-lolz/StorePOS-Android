#define MyAppName "StorePOS Windows"
#define MyAppVersion "1.0.1"
#define MyAppPublisher "Azurate Software Solutions"
#define MyAppExeName "StorePOS.Windows.exe"

[Setup]
AppId={{B0967ED5-7BB8-4536-A72A-31A1918D9002}
AppName={#MyAppName}
AppVersion={#MyAppVersion}
AppPublisher={#MyAppPublisher}
DefaultDirName={localappdata}\Programs\StorePOS Windows
DefaultGroupName=StorePOS Windows
PrivilegesRequired=lowest
OutputDir=..\artifacts
OutputBaseFilename=StorePOS-Windows-v1.0.1-Setup
Compression=lzma2
SolidCompression=yes
WizardStyle=modern
ArchitecturesAllowed=x64
ArchitecturesInstallIn64BitMode=x64
DisableProgramGroupPage=yes
CloseApplications=yes
RestartApplications=no

[Files]
Source: "..\publish\*"; DestDir: "{app}"; Flags: recursesubdirs createallsubdirs ignoreversion

[Icons]
Name: "{autoprograms}\StorePOS Windows"; Filename: "{app}\{#MyAppExeName}"
Name: "{autodesktop}\StorePOS Windows"; Filename: "{app}\{#MyAppExeName}"; Tasks: desktopicon

[Tasks]
Name: "desktopicon"; Description: "Create a desktop shortcut"; Flags: unchecked

[Run]
Filename: "{app}\{#MyAppExeName}"; Description: "Launch StorePOS Windows"; Flags: nowait postinstall skipifsilent
