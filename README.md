<div align="center">
  <p>
    <img src="https://github.com/romanvht/ByeDPIAndroid/raw/master/.github/images/app.svg" alt="Логотип ByeDPI" width="200" />
  </p>
  <h1>ByeByeDPI Android</h1>
  <p>
    Форк для Android TV
  </p>
  <p>
    <a href="https://github.com/romanvht/ByeByeDPI/releases/latest"><img src="https://img.shields.io/github/v/release/romanvht/ByeByeDPI" alt="Latest Release" /></a>
    <a href="https://github.com/romanvht/ByeByeDPI/blob/master/LICENSE"><img src="https://img.shields.io/github/license/romanvht/ByeByeDPI" alt="License" /></a>
    <a href="https://github.com/romanvht/ByeByeDPI"><img src="https://img.shields.io/github/languages/code-size/romanvht/ByeByeDPI" alt="GitHub code size in bytes"/></a>
  </p>
</div>

Приложение для Android, которое локально запускает ByeDPI и перенаправляет весь трафик через него.

Для стабильной работы может потребоваться изменить настройки. Подробнее о различных настройках можно прочитать в [документации ByeDPI](https://github.com/hufrea/byedpi/blob/main/README.md).

Приложение не является VPN. Оно использует VPN-режим на Android для перенаправления трафика, но не передает ничего на удаленный сервер. Оно не шифрует трафик и не скрывает ваш IP-адрес.

Оригинальный проект: https://github.com/romanvht/ByeByeDPI

---

### Что изменено в этом форке

Форк создан, чтобы удобно подбирать стратегии обхода DPI на телевизоре: без ручного ввода команд с
пульта и с полным отчётом о результатах теста.

#### 1. Экспорт полного отчёта по стратегиям

В экране тестирования появился пункт меню **«Экспорт результатов»**.

Сохраняется JSON со всеми проверенными стратегиями: команда, общий счёт запросов, доля успеха и
**разбивка по каждому проверенному сайту**. Это то, что раньше было видно только при ручном
раскрытии «Показать детали» у каждой стратегии.

Формат файла `bbd_results_ГГГГММДД_ЧЧММ.json`:

```json
{
  "app": "io.github.romanvht.byedpi",
  "export": "proxy_test_results",
  "timestamp": 1793664000000,
  "strategies": [
    {
      "command": "-o1 -r-5+se -a1 -At,r,s -d1 -n \"google.com\" -Qr -f-1 -a1",
      "successCount": 12,
      "totalRequests": 20,
      "successPercentage": 60,
      "sites": [
        { "site": "youtube.com",             "successCount": 3, "totalCount": 3, "percentage": 100 },
        { "site": "youtubei.googleapis.com", "successCount": 2, "totalCount": 3, "percentage": 66 },
        { "site": "discord.com",             "successCount": 0, "totalCount": 3, "percentage": 0 }
      ]
    }
  ]
}
```

Такой файл удобно отдать любому анализатору или ИИ, чтобы найти общие флаги у стратегий, прошедших
максимум сайтов, и собрать из них собственные комбинации.

Если запись в выбранное место не удалась, отчёт дополнительно сохраняется во внешнюю папку
приложения: `Android/data/io.github.romanvht.byedpi/files/results_export.json`.

#### 2. Свои стратегии из файла — без ввода с пульта

В настройках теста, как и раньше, включается переключатель **«Использовать свои команды»**.
Отличие в том, что теперь команды можно держать в файле:

```
Android/data/io.github.romanvht.byedpi/files/strategies.txt
```

Путь не требует root: файл кладётся любым файловым менеджером или через ADB:

```bash
adb push strategies.txt /sdcard/Android/data/io.github.romanvht.byedpi/files/strategies.txt
```

Правила:

* одна стратегия — одна строка;
* пустые строки пропускаются;
* строки, начинающиеся с `#`, игнорируются (удобно для комментариев);
* если файла нет или в нём нет рабочих строк, приложение берёт команды из поля в настройках.

Порядок подбора стратегии на телевизоре:

1. Прогнать стандартный набор стратегий.
2. Экспортировать результаты («Экспорт результатов»).
3. Отдать файл анализатору, получить список кандидатов.
4. Записать кандидатов в `strategies.txt`, включить «Использовать свои команды».
5. Перезапустить приложение, прогнать тест заново.
6. Повторять до нужного результата.

### Возможности
* Автозапуск сервиса при старте устройства
* Сохранение списков параметров командной строки
* Улучшена совместимость с Android TV/BOX
* Раздельное туннелирование приложений
* Импорт/экспорт настроек
* **Экспорт полного отчёта по стратегиям теста**
* **Свои стратегии из файла `strategies.txt`**

### Использование
* Для работы автозапуска активируйте пункт в настройках.
* Рекомендуется подключится один раз к VPN, чтобы принять запрос.
* После этого, при загрузке устройства, приложение автоматически запустит сервис в зависимости от настроек (VPN/Proxy)
* Комплексная инструкция от комьюнити [ByeByeDPI-Manual](https://byebyedpi.xyz)

### Сборка
1. Клонируйте репозиторий с сабмодулями:
```bash
git clone --recurse-submodules
```
2. Запустите скрипт сборки из корня репозитория:
```bash
./gradlew assembleRelease
```
3. APK будет в `app/build/outputs/apk/release/`

> P.S.: hev_socks5_tunnel не соберется под Windows, вам нужно будет использовать WSL.
> Для сборки без локального окружения используйте workflow GitHub Actions
> (`.github/workflows/build.yml`).

### Хеш подписи
SHA-256:
`77:45:10:75:AC:EA:40:64:06:47:5D:74:D4:59:88:3A:49:A6:40:51:FA:F3:2E:42:F7:18:F3:F9:77:7A:8D:FB`

> При сборке в GitHub Actions подпись будет другой — приложение придётся переустановить,
> а не обновить поверх существующего.

### Зависимости
- [ByeDPI](https://github.com/hufrea/byedpi)
- [hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)

### Благодарность
- [hufrea](https://github.com/hufrea) - за [ByeDPI](https://github.com/hufrea/byedpi)
- [dovecoteescapee](https://github.com/dovecoteescapee) - за изначальную реализацию [ByeDPIAndroid](https://github.com/dovecoteescapee/ByeDPIAndroid)
- [romanvht](https://github.com/romanvht) - за [ByeByeDPI](https://github.com/romanvht/ByeByeDPI)
