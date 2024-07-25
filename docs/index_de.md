---
title: Paginierung zurücksetzen
identifier: intranda_administration_reset_pagination
description: Goobi Administration Plugin für das Zurücksetzen der Paginierung für mehrere Vorgänge
published: true
---
## Einführung
Die vorliegende Dokumentation beschreibt die Installation, die Konfiguration und den Einsatz des Administration Plugins für das automatisierte Zurücksetzen der Paginierung innerhalb einer großen Anzahl an Vorgängen innerhalb von Goobi workflow.


## Installation
Das Plugin besteht insgesamt aus den folgenden zu installierenden Dateien:

```bash
plugin-intranda-administration-reset-pagination-base.jar
plugin-intranda-administration-reset-pagination-gui.jar
plugin_intranda_administration_reset_pagination.xml
```

Diese Dateien müssen in den richtigen Verzeichnissen installiert werden, so dass diese nach der Installation in folgenden Pfaden vorliegen:

```bash
/opt/digiverso/goobi/plugins/administration/plugin-intranda-administration-reset-pagination-base.jar
/opt/digiverso/goobi/plugins/GUI/plugin-intranda-administration-reset-pagination-gui.jar
/opt/digiverso/goobi/config/plugin_intranda_administration_reset_pagination.xml
```


## Überblick und Funktionsweise
Wenn das Plugin korrekt installiert und konfiguriert wurde, ist es innerhalb des Menüpunkts `Administration` zu finden. Nach dem Betreten können in der Oberfläche die oben beschriebenen Parameter noch einmal individuell angepasst werden.

![Nutzeroberfläche des Plugins](screen2_de.png)

Nach dem Klick auf den Button `Plugin ausführen` startet die Aktualisierung der METS-Dateien. Ein Fortschrittsbalken informiert über den Fortschritt. Innerhalb der Tabelle werden die bereits verarbeiteten Vorgänge aufgelistet und der jeweilige Status über den Erfolg der Durchführung angezeigt.


## Konfiguration
Die Konfiguration des Plugins erfolgt über die Konfigurationsdatei `plugin_intranda_administration_reset_pagination.xml` und kann im laufenden Betrieb angepasst werden. Im folgenden ist eine beispielhafte Konfigurationsdatei aufgeführt:

{{CONFIG_CONTENT}}

| Parameter | Erläuterung |
| :--- | :--- |
| `filter` | Mit diesem Parameter kann ein Filter als Standard festgelegt werden. Dieser wird beim Betreten des Plugins automatisch vorausgefüllt, kann dann aber je nach Wunsch bei jeder Verwendung des Plugins innerhalb der Nutzeroberfläche angepasst werden. |

Für eine Nutzung dieses Plugins muss der Nutzer über die korrekte Rollenberechtigung verfügen. Bitte weisen Sie daher der Benutzergruppe die Rolle `Plugin_administration_reset_pagination` zu.

![Korrekt zugewiesene Rolle für die Nutzer](screen1_de.png)
