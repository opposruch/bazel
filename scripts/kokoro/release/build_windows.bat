
if not defined RELEASE_NAME (
  set RELEASE_NAME=unknown
)

mkdir T:\tmp\tool
set BAZELISK=T:\tmp\tool\bazelisk.exe
powershell /c "[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12; (New-Object Net.WebClient).DownloadFile('https://github.com/bazelbuild/bazelisk/releases/download/v1.2.1/bazelisk-windows-amd64.exe', '%BAZELISK%')"

set PATH=C:\python37;%PATH%

rem %BAZELISK% build //src:bazel.exe
rem mkdir output
rem copy bazel-bin\src\bazel.exe output\bazel.exe

rem output\bazel build -c opt --copt=-w --host_copt=-w --stamp --embed_label %RELEASE_NAME% src/bazel scripts/packages/bazel.zip

mkdir artifacts
move %BAZELISK% artifacts\bazel-%RELEASE_NAME%-windows-x86_64.exe
rem move bazel-bin\src\bazel artifacts\bazel-%RELEASE_NAME%-windows-x86_64.exe
rem move bazel-bin\scripts\packages\bazel.zip artifacts\bazel-%RELEASE_NAME%-windows-x86_64.zip
