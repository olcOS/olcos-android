<p align="center">
  <img src="docs/assets/icon.png" width="96" height="96" alt="olcOS">
</p>

<h1 align="center">olcOS для Android</h1>

<p align="center">
  VPN и SOCKS5-прокси через ваш сервер внутри видеозвонка. Android 8.0+, Kotlin и Jetpack Compose.
</p>

<p align="center">
  <a href="https://github.com/olcOS/olcos/releases/latest"><img src="https://img.shields.io/github/v/release/olcOS/olcos?include_prereleases&label=%D1%80%D0%B5%D0%BB%D0%B8%D0%B7&color=3A57D9" alt="Последний релиз"></a>
  <img src="https://img.shields.io/badge/Android-8%2B-3A57D9" alt="Android 8+">
  <a href="LICENSE"><img src="https://img.shields.io/badge/%D0%BB%D0%B8%D1%86%D0%B5%D0%BD%D0%B7%D0%B8%D1%8F-MIT-3A57D9" alt="Лицензия MIT"></a>
</p>

<p align="center">
  <a href="https://olcos.github.io">Сайт</a> ·
  <a href="https://github.com/olcOS/olcos/releases/latest">Скачать</a> ·
  <a href="https://github.com/olcOS/olcos">olcOS</a> ·
  <a href="https://github.com/olcOS/olcos-ios">iOS</a> ·
  <a href="README.en.md">English</a>
</p>

olcOS ведёт трафик телефона через ваш собственный сервер. Данные идут внутри обычного видеозвонка (Яндекс Телемост,
VK Звонки, SaluteJazz, Jitsi, МТС Link) или напрямую на сервер по Xray (VLESS + REALITY). Общих серверов и аккаунтов
нет: сервер ваш, управляете им с телефона.

## Возможности

- VPN для всего телефона или SOCKS5-прокси на `127.0.0.1` для приложений, которые умеют с ним работать.
- Подключение по ссылке, QR-коду, подписке или приглашению с другого устройства. Ссылку можно вставить на вкладке
  «Подключения» или открыть из другого приложения.
- Несколько комнат на одном сервере склеиваются в один канал. Если комната отвалится, приложение подключит её снова.
- Установка своего сервера по SSH прямо из приложения.
- Управление сервером: статус, запуск и остановка комнат, журналы, новые протоколы, смена ключа и приглашения для
  других устройств.
- Комнаты Телемоста, VK Звонков и SaluteJazz создаются с телефона. Для МТС Link нужна постоянная встреча из вашего
  аккаунта.
- Интерфейс на русском и английском.

## Установка

1. Скачайте файл `.apk` из [последнего релиза](https://github.com/olcOS/olcos/releases/latest). Контрольная сумма
   SHA-256 указана в описании релиза.
2. Откройте файл на телефоне. Если Android спросит, разрешите установку из этого источника.
3. Запустите olcOS, добавьте подключение и нажмите «Подключиться». В режиме VPN система один раз попросит разрешение.

Чтобы новые версии приходили сами, добавьте olcOS в [Obtainium](https://apps.obtainium.imranr.dev/redirect?r=obtainium://app/%7B%22id%22%3A%22io.olcos.app%22%2C%22url%22%3A%22https%3A%2F%2Fgithub.com%2FolcOS%2Folcos%22%2C%22author%22%3A%22olcOS%22%2C%22name%22%3A%22olcOS%22%7D): он следит за релизами на GitHub и
предлагает обновление, когда оно выходит.

## Свой сервер

Нужен VPS на Linux (Debian, Ubuntu, Fedora или CentOS) с root-доступом. В приложении откройте «Подключения» → «+» →
«Свой сервер (VPS)», введите адрес и пароль или ключ SSH, и olcOS всё установит сам. Второй вариант — одна команда на
сервере:

```bash
curl -fsSL https://raw.githubusercontent.com/olcOS/olcos/main/install.sh | sudo bash
```

В конце команда покажет QR-код. Отсканируйте его в olcOS, и телефон станет владельцем сервера. Дальше комнаты,
протоколы и приглашения настраиваются в приложении.

## Ограничения

- Через VPN идёт TCP поверх IPv4. Приложениям, которым нужны UDP или IPv6, туннель может не подойти.
- Скорость зависит от сети и от сервиса звонков. Сервис может закрыть комнату или ограничить гостей, поэтому лучше
  держать несколько протоколов.
- Это бета, поведение и совместимость ещё меняются.
- Пользуйтесь приложением в рамках закона своей страны и правил сервисов, через которые идёт трафик.

## Ошибки и вопросы

Пишите в [Issues](https://github.com/olcOS/olcos/issues) и укажите, что речь об Android, а также версию приложения и
системы. Не публикуйте ссылки подключения, ключи и адреса своих серверов. Об уязвимостях сообщайте приватно, как
описано в [SECURITY.md](https://github.com/olcOS/olcos-ios/blob/main/SECURITY.md).

## Сборка

Нужны JDK 17, Android SDK с NDK и Go (версия указана в `gomobile/go.mod` ядра). Ядро на Go живёт в репозитории
[olcos-ios](https://github.com/olcOS/olcos-ios) и собирается в `app/libs/olcoscore.aar` скриптом
`scripts/build-core.sh`. Как устроено приложение, как CI собирает ядро и APK и какие форматы ссылок поддерживаются,
описано в [docs/development.md](docs/development.md).

## Лицензия

olcOS распространяется под лицензией [MIT](LICENSE). Туннель построен на
[olcrtc](https://github.com/ghostlane-project/olcrtc) (Apache-2.0), прямой режим использует
[Xray-core](https://github.com/XTLS/Xray-core) (MPL-2.0).
