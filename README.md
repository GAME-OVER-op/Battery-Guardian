# BatteryGuardianLSPosed

Companion Android app + LSPosed/Xposed module that **blocks forced shutdown at 0%** and adds **smart shutdown control**.

> ⚠️ High‑risk software: this module changes system power/battery behavior. Improper settings may cause **data loss** or **file‑system corruption** if power drops unexpectedly.

---

## Features
- **Dark theme only** (no light theme)
- **Automatic language**: Russian if system language is Russian, otherwise English
- **First-run disclaimer** (must accept; decline closes the app)
- **LSPosed recommended scope** enabled (legacy-compatible)
- Settings are shared from the app to the hook via a **ContentProvider**

### Operating modes (only one at a time)
1) **Voltage**
   - Blocks shutdown while battery voltage is above your threshold.
   - When voltage is **≤ threshold** for the configured *stability time*, the module **stops blocking** and the system can shut down normally.

2) **Timer**
   - When the device reaches **0%** and the module starts blocking shutdown, a timer starts.
   - After **N minutes**, the module **stops blocking** and the system can shut down normally.

3) **Permanent**
   - Blocks shutdown indefinitely.
   - The device will only turn off when you power it off manually (if possible) or when the battery physically drops power.
   - **Highest risk** of sudden power loss → potential data loss.

### Safety floor
- The module includes a hard safety floor at **-20%**. If the system ever reports a level **≤ -20**, the module will not keep blocking shutdown.

---

## LSPosed setup
1) Install the APK.
2) Open **LSPosed Manager** → Modules → enable this module.
3) In module scope selection, enable **System framework**.
   - Depending on LSPosed/ROM language, it may appear as **“Android system / Система Android”**.
4) Reboot.

> Note: LSPosed does not allow third-party apps to silently tick scope checkboxes. The “Recommended” label only helps you select the correct scope.

---

## How it works (technical)
- Hooks `com.android.server.BatteryService.shouldShutdownLocked()` and blocks the forced critical shutdown when conditions are met.
- App → hook settings exchange is done via `ContentProvider` (more reliable than world‑readable prefs on modern Android).

---

## Usage guidance
- **Find a safe shutdown point first.**
  - On your device, determine a voltage or time where the phone can still shut down cleanly.
  - Too low voltage can cause a hard power cut.
- Prefer **Voltage** or **Timer** for daily use.
- Use **Permanent** only if you fully understand the risks.

---

## Build
- Android Studio / Gradle wrapper.

Typical commands:
```bash
./gradlew :app:assembleDebug
```

---

## License
This project is **proprietary**. Copying, modification, and redistribution are prohibited.
See `LICENSE.txt`.


---

# BatteryGuardianLSPosed (Русский)

Приложение + модуль LSPosed/Xposed, который **блокирует принудительное выключение на 0%** и добавляет **умное управление выключением**.

> ⚠️ ПО повышенного риска: модуль меняет поведение питания/батареи. Неправильные настройки могут привести к **потере данных** и **повреждению файловой системы**.

## Возможности
- **Только тёмная тема**
- **Автовыбор языка**: русский при русском языке системы, иначе английский
- **Окно-предупреждение при первом запуске** (нужно согласие; при отказе приложение закрывается)
- **Рекомендуемый scope для LSPosed** (совместимость с legacy)
- Передача настроек в модуль через **ContentProvider**

### Режимы работы (выбирается один)
1) **По вольтажу**
   - Блокируем выключение, пока напряжение выше порога.
   - Когда напряжение **≤ порога** (в течение заданного времени подтверждения), модуль **перестаёт блокировать** и система может выключиться штатно.

2) **По таймеру**
   - При достижении **0%** и начале блокировки запускается таймер.
   - Через **N минут** модуль **перестаёт блокировать** и система может выключиться штатно.

3) **Постоянный**
   - Блокировка выключения без ограничений.
   - Устройство выключится только при ручном выключении (если возможно) или при физическом падении питания.
   - **Максимальный риск** внезапного отключения питания.

### Страховка
- Встроен “пол” **-20%**: если система когда-либо отдаст уровень **≤ -20**, модуль не будет продолжать блокировку.

## Настройка LSPosed
1) Установи APK.
2) Открой **LSPosed Manager** → Modules → включи модуль.
3) В scope выбери **Системный фреймворк**.
   - В некоторых версиях/переводах может отображаться как **«Система Android»**.
4) Перезагрузи устройство.

## Лицензия
Проект **закрытый**: копирование, редактирование и распространение запрещены.
См. `LICENSE.txt`.
