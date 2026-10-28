# Разработка под Android TV: особенности и грабли

Практическая страница для того, кто пишет приложение для телевизора. Всё ниже либо проверено на
Philips 50PUS8505/60 (TPM191E, Android 12, прошивка `STT2.230526.001`), либо помечено иначе.
Источник опыта — разработка этого форка.

> **Главный вывод одной строкой**: телевизор — это Android без сенсорного экрана, без системного
> выбора файлов и с агрессивным менеджером памяти. Почти всё, что «просто работает» на телефоне,
> здесь требует отдельного решения.

---

## 1. Пульт вместо тача: что работает, а что нет

| Элемент UI | Работает с пультом | Метка |
|---|---|---|
| `Button`, `TextView`, `SwitchPreference` | да | **ПОДТВЕРЖДЕНО** |
| `ListView` / `RecyclerView` с фокусом | да | **ПОДТВЕРЖДЕНО** |
| `EditText` **однострочный** в диалоге | да | **ПОДТВЕРЖДЕНО** |
| `EditText` **многострочный** | **нет**: курсор уходит в конец, D-pad не двигает его по тексту | **ПОДТВЕРЖДЕНО** |
| `AlertDialog` стандартной ширины | да, но текст не влезает | **ПОДТВЕРЖДЕНО** |
| Свайпы, жесты, `RecyclerView` со скроллом пальцем | **нет** | — |

### Проверенный обход для редактирования длинного текста

Не давайте многострочное поле. Делайте **список строк**: каждая строка — отдельный пункт `ListView`,
ОК открывает диалог правки **одной** строки. Внутри диалога поле однострочное — и с ним пульт
работает нормально.

```kotlin
listView.setOnItemClickListener { _, _, position, _ -> editLine(position) }
```

**ПОДТВЕРЖДЕНО**: этот вариант — единственный из испробованных, который устроил владельца после того,
как многострочное поле и «полноэкранный редактор» не сработали.

### Ловушка с курсором в `EditText`

```kotlin
// Так НЕ работает: при получении фокуса Android ставит курсор в конец,
// а setSelection до показа IME перезаписывается
textView.clearFocus()
// ... позже
textView.requestFocus()      // ← курсор в конце
textView.setSelection(pos)   // ← уже поздно
```

Если поле уже в фокусе, а вы хотите только показать клавиатуру — **не трогайте фокус**:

```kotlin
private fun showKeyboard() {
    imm.showSoftInput(textView, InputMethodManager.SHOW_IMPLICIT)   // курсор на месте
}

private fun hideKeyboard() {
    imm.hideSoftInputFromWindow(textView.windowToken, 0)            // фокус сохраняется
}
```

**ПОДТВЕРЖДЕНО**: на многострочном поле это всё равно не спасает от прыжка курсора, но для
однострочного — работает.

---

## 2. Системного выбора файлов может не быть вообще

**ПОДТВЕРЖДЕНО на Philips TPM191E**: пакета `com.android.documentsui` нет в прошивке. Следствия:

| Вызов | Результат |
|---|---|
| `ACTION_GET_CONTENT` | `No activities found` |
| `ACTION_OPEN_DOCUMENT` | `No activities found` |
| `ACTION_CREATE_DOCUMENT` | `No activities found` |
| `ACTION_OPEN_DOCUMENT_TREE` | `No activities found` |

Приложение, которое полагается на системный диалог, просто не сможет ни импортировать, ни сохранить
файл. Проверять заранее:

```kotlin
val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
    type = "text/plain"
    addCategory(Intent.CATEGORY_OPENABLE)
}
val available = packageManager.queryIntentActivities(intent, 0).isNotEmpty()
```

### Решение: свой файловый браузер

Пишите встроенный просмотрщик папок (`ListView` + `File`), а результат отдавайте вызывающему через
`setResult` и `EXTRA_PATH`. Тогда:

- работает на любой прошивке;
- можно ограничить расширение (передав его через `EXTRA_EXTENSION`);
- можно предложить сразу нужную папку.

```kotlin
intent.putExtra(FileActivity.EXTRA_MODE, FileActivity.MODE_OPEN)
intent.putExtra(FileActivity.EXTRA_TYPE, FileActivity.TYPE_STRATEGIES)
intent.putExtra(FileActivity.EXTRA_EXTENSION, "txt")
```

**ПОДТВЕРЖДЕНО**: этот подход заработал там, где системный диалог не открывался вовсе.

---

## 3. Диалоги разрешений врут

**ПОДТВЕРЖДЕНО**: пользователь нажимает «Разрешить», система показывает согласие, но права **не
выдаются**. Проверка:

```powershell
adb shell "appops get <pkg>"     # смотрим строки "ignore"
```

Причины на этом ТВ:

1. `targetSdk` приложения выше 30, а ОС — Android 12: `READ/WRITE_EXTERNAL_STORAGE` для такой пары
   не предусмотрены, диалог показывается «для совместимости», но фреймворк отказывает.
2. `DocumentsUI` вырезан, поэтому scoped-путь тоже закрыт.

### Обход

```powershell
adb shell "appops set <pkg> MANAGE_EXTERNAL_STORAGE allow"
adb shell "appops set --uid <pkg> MANAGE_EXTERNAL_STORAGE allow"   # UID-режим перекрывает обычный
adb shell "am force-stop <pkg>"                                    # приложение должно перезапуститься
```

**Грабли**:
- `pm grant <pkg> android.permission.MANAGE_EXTERNAL_STORAGE` →
  `SecurityException: not a changeable permission type`. Только `appops`.
- В выводе `appops get` две секции: `Uid mode:` и обычная. Значение в `Uid mode` важнее.

### Как проектировать приложение

Не полагайтесь на диалог разрешений. Считайте, что доступ к файлам может отсутствовать, и:

- храните данные во внутренней папке;
- предлагайте экспорт через **свой** браузер (см. §2);
- если нужен доступ к чужим файлам, документируйте команду `appops`.

---

## 4. Клавиатура закрывает полэкрана

**ПОДТВЕРЖДЕНО**: экранная клавиатура в диалоге перекрывает значительную часть содержимого, и
пульт начинает ходить по её кнопкам вместо вашего интерфейса.

Правила:
- `android:windowSoftInputMode="adjustResize"` в манифесте для экранов с вводом;
- **не показывайте клавиатуру автоматически** — только по явному действию (ОК на поле);
- в диалоге поднимайте его выше клавиатуры: `window.setSoftInputMode(SOFT_INPUT_ADJUST_RESIZE)`.

---

## 5. Обратная кнопка и `OnBackPressedCallback`

**ПОДТВЕРЖДЕНО (ловушка)**: после добавления `OnBackPressedCallback` перестала работать стрелка
«назад» в заголовке:

```kotlin
// Так ломается: handleBack() перехватывает ВСЁ, включая android.R.id.home
supportActionBar?.setDisplayHomeAsUpEnabled(true)
onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
    override fun handleOnBackPressed() = handleBack()
})
```

Правило: если нужна разная реакция на системный back и на стрелку в заголовке — обрабатывайте их
**раздельно**, а `handleOnBackPressed()` не должен вызывать ту же ветку, что и `onOptionsItemSelected`.

```kotlin
override fun onOptionsItemSelected(item: MenuItem) = when (item.itemId) {
    android.R.id.home -> { save(); finish(); true }
    else -> super.onOptionsItemSelected(item)
}
```

### Обязательный `toolbar` в layout

**ПОДТВЕРЖДЕНО на опыте проекта**: если базовый `Activity` вызывает `setupToolbar()` с
`findViewById(R.id.toolbar)`, а в layout нет `@+id/toolbar`, приложение падает с
`NullPointerException: findViewById(...) must not be null`. В каждом layout с заголовком
обязателен:

```xml
<include layout="@layout/app_toolbar" />
```

---

## 6. Ширина диалогов

Стандартный `AlertDialog` на телевизоре занимает около половины ширины, а команды бывают по 150+
символов. Растягивайте после `show()`:

```kotlin
val dialog = builder.create()
dialog.show()
val w = resources.displayMetrics.widthPixels
dialog.window?.setLayout((w * 0.96).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT)
```

**ПОДТВЕРЖДЕНО**: это единственный способ заставить диалог занять почти весь экран; тема приложения
ширину не меняет.

---

## 7. Манифест: обязательные объявления

```xml
<!-- Приложение должно появляться в списке приложений Android TV -->
<intent-filter>
    <action android:name="android.intent.action.MAIN" />
    <category android:name="android.intent.category.LAUNCHER" />
    <category android:name="android.intent.category.LEANBACK_LAUNCHER" />
</intent-filter>

<!-- Без этого телевизор может не показать приложение в Play -->
<uses-feature android:name="android.software.leanback" android:required="false" />
<uses-feature android:name="android.hardware.touchscreen" android:required="false" />
```

`LEANBACK_LAUNCHER` — **обязателен** для Android TV, иначе приложения не будет в списке.
`touchscreen required="false"` — иначе часть магазинов отфильтрует устройство.

**ПОДТВЕРЖДЕНО**: у ByeByeDPI объявлены обе категории, и оно видно на телевизоре.

---

## 8. Память: 2 ГБ и агрессивный менеджер

Телевизоры этого класса имеют ~2 ГБ RAM, из которых свободно обычно 60–150 МБ, а своп занят на 85–90%.
`lowmemorykiller` убивает процессы охотно.

| Правило | Почему |
|---|---|
| Минимум фоновых сервисов | каждый сервис = отдельный процесс |
| Не держите большие `Bitmap` | 4K-обои в памяти лаунчера — десятки МБ |
| Ограничивайте изображения до 1920 px | проверено: 13,7 МБ JPEG грузится заметно дольше |
| Никаких «прогревающих» библиотек | DI-фреймворки и аналитика стоят мегабайт на старте |
| Не запускайте `dumpsys meminfo` на медленном ТВ | на этом устройстве он не уложился в 10 секунд |

Замеры, которые стоит делать до и после изменений:

```powershell
adb shell "free -m"
adb shell "cat /proc/meminfo | grep -E 'MemFree|MemAvailable|SwapFree'"
adb shell "ps -A -o RSS,NAME --sort=-RSS | head -15"
adb shell "netstat -tn | grep -c CLOSE_WAIT"    # индикатор сетевых проблем
```

**ПОДТВЕРЖДЕНО**: на этом ТВ при свободных 62 МБ приложения падали на старте, после освобождения
~105 МБ — перестали.

---

## 9. Проверка на устройстве через ADB

Самое полезное для разработки:

```powershell
adb connect 192.168.1.149:5555

# краши и ANR
adb logcat -d -b crash
adb logcat -s AndroidRuntime:E

# отладка конкретного приложения
adb logcat --pid=$(adb shell pidof <pkg>)

# что на экране
adb shell "dumpsys activity | grep -m1 mResumedActivity"

# скриншот (не работает через `>`, нужен exec-out)
adb exec-out screencap -p > screen.png

# установка/обновление
adb install -r app.apk
```

**Грабли**:
- на телевизоре `adb` может быть защищён паролем отладки; авторизация сбрасывается после
  перезагрузки, галочка «Всегда разрешать» на эту модель не сохраняется;
- `adb shell 'grep ...'` с кавычками ломается в PowerShell — используйте одинарные кавычки
  для всей команды;
- `dumpsys package ... | grep versionName` отвечает `inaccessible or not found`: `grep` получает
  кривой аргумент из-за экранирования. Проверяйте `dumpsys package` без конвейера.

### Определение телевизора

```powershell
adb shell "getprop ro.product.model"
adb shell "getprop ro.build.display.id"
adb shell "getprop ro.board.platform"
adb shell "cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME"
```

**ПОДТВЕРЖДЕНО**: `resolve-activity` **без** `-a android.intent.action.MAIN` на Philips возвращает
`No activities found`, хотя Home назначен.

---

## 10. Сборка и подпись

| Проблема | Решение |
|---|---|
| debug-подпись меняется на каждом runner в CI | хранить keystore в секретах и подписывать **явно** через `signingConfigs` |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | подписи не совпали — удалить приложение на ТВ и поставить заново |
| `secrets` нельзя использовать в `if` на уровне шага | читать секрет в `env` шага и проверять в `run` |
| `SYSTEM_FREE`/NDK нужен только для C-кода | если проект нативный, в CI ставится `ndk;27.x` и `cmake` |

**ПОДТВЕРЖДЕНО на своём опыте**: пока ключ подписи не зафиксирован, каждая сборка требует
переустановки с потерей настроек. Как только `signingConfigs` берёт keystore из секретов —
обновление ставится поверх.

### Стабильный ключ в `build.gradle.kts`

```kotlin
val hasReleaseSigning = System.getenv("RELEASE_KEYSTORE_PATH")?.let { File(it).exists() } == true

signingConfigs {
    if (hasReleaseSigning) {
        create("release") {
            storeFile = File(System.getenv("RELEASE_KEYSTORE_PATH"))
            storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("RELEASE_KEY_ALIAS")
            keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
        }
    }
}
buildTypes {
    release { if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release") }
    debug   { if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release") }
}
```

---

## 11. Что проверять перед релизом на ТВ

- [ ] Приложение видно в списке приложений (`LEANBACK_LAUNCHER`).
- [ ] Любой диалог управляется пультом: фокус виден, кнопки доступны.
- [ ] Нет многострочных полей ввода.
- [ ] Все тексты влезают в экран 1280×720 (даже если ТВ 4K — интерфейс масштабируется).
- [ ] Приложение работает без системного выбора файлов.
- [ ] Холодный старт укладывается в 2–3 секунды.
- [ ] После перезагрузки телевизора настройки на месте.
- [ ] Потребление в покое — не больше 60–80 МБ.
- [ ] Подпись зафиксирована, обновление ставится поверх.
- [ ] `android:windowSoftInputMode="adjustResize"` на экранах ввода.

---

## 12. Чего в этом проекте достигли (как ориентир)

| Метрика | Значение |
|---|---|
| Холодный старт лаунчера | ~1,3 с (по данным CleanYourTV на этом шасси) |
| Потребление лаунчера FLauncher | 160–177 МБ (Flutter) |
| Потребление легкого Java-лаунчера | 54 КБ APK, 60–70 МБ RAM (dumbtv) |
| Свободная RAM после чистки | ~105 МБ вместо 62 МБ |
| `org.droidtv.*` процессов | 37 из 43 после деблоата |
| Покрытие стратегий обхода DPI | 71–75% адресов (предел провайдера) |

---

## 13. Полезные ссылки

| Что | Где |
|---|---|
| Android TV: рекомендации по UI | https://developer.android.com/design/ui/tv |
| Leanback и баннеры | https://developer.android.com/training/tv/start |
| `appops` (справка) | `adb shell appops help` |
| Исходники | этот репозиторий |
| Экспорт результатов и стратегии в файле | [README](../README.md) |
