# PS2 UDPFS Server for Android

[Русский](#русский) · [English](#english)

Android-приложение, превращающее телефон или планшет в UDPFS-сервер для PlayStation 2. Проект использует [`udpfsd`](https://github.com/pcm720/udpfsd) и предназначен прежде всего для запуска игр по локальной сети через совместимые PS2-клиенты, включая Neutrino/NHDDL.

> Проект не связан с Sony Interactive Entertainment. PlayStation и PS2 являются товарными знаками их правообладателей.

---

# Русский

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
- Нативный ARM64 `udpfsd` собирается автоматически из актуального исходного кода upstream при сборке APK.

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
- загружает актуальный [`pcm720/udpfsd`](https://github.com/pcm720/udpfsd);
- собирает ARM64 Android PIE-бинарник как нативную библиотеку `libudpfsd.so`;
- собирает debug APK;
- публикует APK как GitHub Actions artifact.

Локальная сборка Android-части выполняется стандартным Gradle wrapper проекта.

## Важные замечания

Приложение предназначено для использования с собственными резервными копиями игр и файлами, которыми вы имеете право пользоваться. Стабильность передачи зависит от Wi-Fi, роутера, сетевого адаптера PS2 и используемого PS2-клиента. Для наиболее стабильного соединения PS2 рекомендуется подключать к роутеру по Ethernet.

## Благодарности

Основная серверная часть — [`pcm720/udpfsd`](https://github.com/pcm720/udpfsd). Спасибо разработчикам UDPFS, Neutrino/NHDDL и сообществу PS2 homebrew.

---

# English

## Overview

PS2 UDPFS Server for Android turns an Android phone or tablet into a UDPFS server for PlayStation 2. It uses [`udpfsd`](https://github.com/pcm720/udpfsd) and is primarily intended for loading games over a local network with compatible PS2 clients such as Neutrino/NHDDL.

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
- The native ARM64 `udpfsd` component is built automatically from current upstream source when the APK is built.

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
- fetches the latest [`pcm720/udpfsd`](https://github.com/pcm720/udpfsd) source;
- builds the ARM64 Android PIE executable as the native `libudpfsd.so` library;
- builds the debug APK;
- uploads the APK as a GitHub Actions artifact.

The Android project can also be built locally using the included Gradle wrapper.

## Notes

This project is intended for use with your own game backups and files you are legally entitled to use. Network performance depends on Wi-Fi conditions, the router, the PS2 network connection and the client software. Connecting the PS2 to the router over Ethernet is recommended for the most reliable setup.

## Credits

Server functionality is powered by [`pcm720/udpfsd`](https://github.com/pcm720/udpfsd). Thanks to the UDPFS, Neutrino/NHDDL and wider PS2 homebrew communities.

---

PS2 UDPFS Server for Android is an independent homebrew project and is not affiliated with or endorsed by Sony Interactive Entertainment.
