# PS2 UDPFS Server for Android

[Русский](#русский) · [English](#english)

Android-приложение, превращающее телефон или планшет в UDPFS-сервер для PlayStation 2. Проект использует [`udpfsd`](https://github.com/pcm720/udpfsd) и предназначен прежде всего для запуска игр по локальной сети через совместимые PS2-клиенты, включая Neutrino/NHDDL.

> Проект не связан с Sony Interactive Entertainment. PlayStation и PS2 являются товарными знаками их правообладателей.

---

# Русский

## Исправление зависания в 1.0.4

В закреплённый `udpfsd` добавлен патч `android-nack1`: сервер находит запрошенный пакет в середине буфера повторной передачи и продвигает окно по допустимому NACK, если подтверждение предыдущего окна потерялось. Проверки покрывают потерю пакета, потерю ACK, переход номера пакета через 4095 → 0 и устаревшие/недопустимые NACK. В CI те же тесты сначала воспроизводят ошибку на исходном сервере, затем проходят после патча с Go race detector. Проверка на физической PS2 остаётся необходимой.

## Возможности

- Запуск UDPFS-сервера прямо на Android без root.
- Выбор папки с играми через интерфейс приложения.
- Работа с ISO, CSO и ZSO.
- Сервер работает в режиме чтения и записи, чтобы Neutrino/NHDDL мог создавать кэш и сохранять служебные файлы.
- Автоматическое отображение IP-адреса телефона в локальной сети.
- Мониторинг активности PS2 и отображение IP подключившегося клиента.
- Просмотр диагностического лога `udpfsd` прямо в приложении.
- Foreground Service, WakeLock и Wi-Fi HIGH_PERF lock для стабильной работы при выключенном экране.
- Кнопка запроса исключения из оптимизации батареи Android.
- Проверка обновлений через GitHub.
- Нативный ARM64 `udpfsd` собирается автоматически из закреплённого коммита upstream при сборке APK.

## Требования

- Android 8.0 (API 26) или новее.
- ARM64 Android-устройство.
- PlayStation 2 с сетевым подключением и UDPFS-совместимым ПО.
- Телефон и PS2 должны находиться в одной локальной сети.

Типичная схема подключения:

```text
Android-телефон ── Wi-Fi ── Роутер ── Ethernet ── PS2
```

## Быстрый старт

1. Установите APK на Android-устройство.
2. При необходимости разрешите приложению доступ ко всем файлам. Он нужен нативному `udpfsd` для прямого доступа к выбранной папке.
3. Нажмите **Выбрать папку** и укажите каталог, содержащий игры.
4. Нажмите **Запустить сервер**.
5. Убедитесь, что в приложении появился статус работающего сервера и локальный IP телефона.
6. На PS2 запустите Neutrino/NHDDL или другой UDPFS-совместимый клиент.
7. После обращения консоли к серверу приложение покажет активность PS2 и её IP-адрес.

UDPFS discovery использует UDP-порт **62966**. Порт передачи данных назначается `udpfsd` автоматически.

## Форматы

| Формат | Поддержка |
| --- | --- |
| ISO | ✅ |
| CSO | ✅ |
| ZSO | ✅ |
| CHD | ❌ в текущей Android-сборке |

CHD сейчас не поддерживается, поскольку Android-версия `udpfsd` собирается без CGO.

## Разрешения Android

Приложение использует сетевой доступ, WakeLock, Foreground Service и доступ ко всем файлам (`MANAGE_EXTERNAL_STORAGE`). Последний необходим из-за того, что нативному серверу требуется обычный файловый путь, а не только Android SAF URI.

## Сборка

APK автоматически собирается через GitHub Actions. Workflow:

- устанавливает JDK 17 и Go 1.25;
- загружает закреплённый коммит [`pcm720/udpfsd`](https://github.com/pcm720/udpfsd) из `UDPFSD_COMMIT`;
- собирает ARM64 Android PIE-бинарник как нативную библиотеку `libudpfsd.so`;
- собирает debug APK;
- публикует APK как GitHub Actions artifact.

Версия 1.0.4 собирается с нуля: Go и Gradle используют отдельные пустые каталоги кэша для каждого запуска. Gradle выполняет `clean assembleDebug --no-build-cache --rerun-tasks --refresh-dependencies`. После сборки проверяются подпись APK, версия, launcher activity и точное совпадение ARM64 PIE-бинарника внутри APK с только что собранным сервером.

Артефакт `PS2-UDPFS-1.0.4-arm64-clean` содержит APK, SHA-256 и `build-info.json` с коммитами приложения и сервера. Патч и его SHA-256 также указаны в `build-info.json`. Работа с PS2 требует проверки на устройстве.

Для локальной сборки нужны JDK 17, Android SDK и Gradle. Workflow сначала создаёт wrapper Gradle 8.9; wrapper не хранится в репозитории. Сервер необходимо собрать и поместить в `app/src/main/jniLibs/arm64-v8a/libudpfsd.so` до сборки Android-части, как показано в workflow.

## Важные замечания

Приложение предназначено для использования с собственными резервными копиями игр и файлами, которыми вы имеете право пользоваться. Стабильность передачи зависит от Wi-Fi, роутера, сетевого адаптера PS2 и используемого PS2-клиента. Для наиболее стабильного соединения PS2 рекомендуется подключать к роутеру по Ethernet.

## Благодарности

Основная серверная часть — [`pcm720/udpfsd`](https://github.com/pcm720/udpfsd). Спасибо разработчикам UDPFS, Neutrino/NHDDL и сообществу PS2 homebrew.

---

# English

## Overview

PS2 UDPFS Server for Android turns an Android phone or tablet into a UDPFS server for PlayStation 2. It uses [`udpfsd`](https://github.com/pcm720/udpfsd) and is primarily intended for loading games over a local network with compatible PS2 clients such as Neutrino/NHDDL.

## Stall recovery fix in 1.0.4

The pinned `udpfsd` receives the `android-nack1` patch: find a requested packet inside the retransmit buffer, and advance the window on a valid NACK when a window ACK was lost. Regression tests cover packet loss, ACK loss, 12-bit sequence wrap and stale/invalid NACKs. CI first reproduces the failures on the original transport, then runs the patched tests with the Go race detector. Physical PS2 validation is still required.

## Features

- Run a UDPFS server directly on Android without root.
- Select the games directory from the app UI.
- ISO, CSO and ZSO support.
- Read/write server mode so Neutrino/NHDDL can create cache and service files.
- Displays the Android device's local network IP address.
- Monitors PS2/client activity and displays the active peer IP.
- Built-in `udpfsd` diagnostic log.
- Foreground Service, WakeLock and a Wi-Fi HIGH_PERF lock for reliability while the screen is off.
- A button to request an Android battery-optimization exemption.
- GitHub-based update checking.
- The native ARM64 `udpfsd` component is built automatically from a pinned upstream commit when the APK is built.

## Requirements

- Android 8.0 (API 26) or newer.
- ARM64 Android device.
- A network-enabled PlayStation 2 running UDPFS-compatible software.
- Android device and PS2 connected to the same local network.

Typical setup:

```text
Android phone ── Wi-Fi ── Router ── Ethernet ── PS2
```

## Quick start

1. Install the APK on your Android device.
2. If requested, grant **All files access**. The native `udpfsd` process needs a direct filesystem path to the selected directory.
3. Tap **Select folder** and choose the directory containing your games.
4. Tap **Start server**.
5. Check that the app reports the server as running and displays the phone's LAN IP address.
6. Start Neutrino/NHDDL or another UDPFS-compatible client on your PS2.
7. When the console contacts the server, the app will show PS2 activity and the peer IP address.

UDPFS discovery uses UDP port **62966**. The data port is assigned dynamically by `udpfsd`.

## Supported formats

| Format | Support |
| --- | --- |
| ISO | ✅ |
| CSO | ✅ |
| ZSO | ✅ |
| CHD | ❌ in the current Android build |

CHD is currently unavailable because the Android `udpfsd` binary is built with CGO disabled.

## Android permissions

The app uses network access, WakeLock, a Foreground Service and `MANAGE_EXTERNAL_STORAGE` (All files access). All-files access is required because the native server needs a regular filesystem path rather than only an Android Storage Access Framework URI.

## Building

The APK is built automatically with GitHub Actions. The workflow:

- sets up JDK 17 and Go 1.25;
- fetches the pinned [`pcm720/udpfsd`](https://github.com/pcm720/udpfsd) commit from `UDPFSD_COMMIT`;
- builds the ARM64 Android PIE executable as the native `libudpfsd.so` library;
- builds the debug APK;
- uploads the APK as a GitHub Actions artifact.

Version 1.0.4 uses fresh Go and Gradle cache directories for every run and executes `clean assembleDebug --no-build-cache --rerun-tasks --refresh-dependencies`. Verification checks the APK signature, version, launcher activity and byte-for-byte inclusion of the rebuilt ARM64 PIE executable.

The `PS2-UDPFS-1.0.4-arm64-clean` artifact contains the APK, SHA-256 and `build-info.json` recording both source commits. The patch identifier and SHA-256 are also recorded. PS2 connectivity still requires an on-device test.

Local builds require JDK 17, Android SDK and Gradle. The workflow generates the Gradle 8.9 wrapper; it is not checked into this repository. Build the server into `app/src/main/jniLibs/arm64-v8a/libudpfsd.so` before building the Android app, following the workflow.

## Notes

This project is intended for use with your own game backups and files you are legally entitled to use. Network performance depends on Wi-Fi conditions, the router, the PS2 network connection and the client software. Connecting the PS2 to the router over Ethernet is recommended for the most reliable setup.

## Credits

Server functionality is powered by [`pcm720/udpfsd`](https://github.com/pcm720/udpfsd). Thanks to the UDPFS, Neutrino/NHDDL and wider PS2 homebrew communities.

---

PS2 UDPFS Server for Android is an independent homebrew project and is not affiliated with or endorsed by Sony Interactive Entertainment.
