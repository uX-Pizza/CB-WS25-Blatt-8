---
title: Student Support Code Template
---

<!-- pandoc -s -f markdown -t markdown --columns=94  README.md -->

# Compiler Sample Project

Dies ist ein Starter-Projekt für die Übungsaufgaben in "Compilerbau" (Bachelor). Es existiert
eine [Gradle-Konfiguration](build.gradle) (Java-Projekt), [JUnit](https://junit.org/junit5/)
und [ANTLR](https://www.antlr.org/) sind auch bereits eingebunden. Das Projekt zeigt die
Einbindung der ANTLR-Grammatiken. Zusätzlich existieren [Beispielgrammatiken](src/main/antlr/)
für einige Übungsblätter.

## Installation

Öffnen Sie den Ordner `sample_project` als neues Java-Projekt "mit existierenden Quellen" in
[IntelliJ](https://www.jetbrains.com/idea/). Achten Sie dabei darauf, dass Sie als "Build
Model" entsprechend "Gradle" auswählen, damit die Konfiguration übernommen wird.

Sie benötigen ein installiertes [Java SE Development Kit **25 LTS**](https://jdk.java.net/25/).
Achten Sie darauf, dass dieses auch wirklich von IntelliJ verwendet wird (zu finden unter
*Projekt-Einstellungen*).

Weitere Software ist nicht notwendig. ANTLR und JUnit werden über das Build-Skript automatisch
als Dependency heruntergeladen und eingebunden. Es empfiehlt sich dennoch, zusätzlich das
[ANTLR-Plugin für IntelliJ](https://plugins.jetbrains.com/plugin/7358-antlr-v4) zu
installieren - damit können Sie in der IDE interaktiv mit den Grammatiken experimentieren und
müssen nicht immer das gesamte Programm kompilieren und laufen lassen.

Sie können natürlich auch eine beliebige andere IDE oder sogar einen einfachen Editor
verwenden.

## Gradle-Tasks

### Aufräumen

`./gradlew clean`

### Starten des Programms

Konfigurieren Sie Ihr Programm im [`build.gradle`](build.gradle) in der Variablen `mainClass`.

Danach können Sie das Programm kompilieren und starten über `./gradlew run`.

### Formatieren

Ihre Java-Sourcen können Sie mit `./gradlew spotlessApply` formatieren.

### Testen

`./gradlew check`

### Grammatik neu übersetzen

Die ANTLR-Grammatiken werden im Ordner [`src/main/antlr`](src/main/antlr/) erwartet. Sie
werden standardmäßig beim Bauen der Applikation übersetzt, also beispielsweise beim Ausführen
von `./gradlew run` oder `./gradlew build`.

Die dabei generierten Dateien werden im Build-Ordner
[`build/generated-src/antlr/main/`](build/generated-src/antlr/main/) abgelegt und sind über
die Gradle-Konfiguration automatisch im Classpath verfügbar.

Falls Ihre Grammatik in einem Package liegt (beispielsweise
[`HelloPackage.g4`](src/main/antlr/my/pkg/HelloPackage.g4) im Package `my.pkg`), dann wird für
die generierten Sourcen im Build-Ordner automatisch dieses Package mit angelegt. Damit später
die Einbindung in Ihr Programm funktioniert, sollten Sie entsprechend in der Grammatik über
die Direktive `@header` die entsprechende Package-Deklaration mit in die generierten Sourcen
hineingenerieren lassen.

Wenn Sie die Grammatik einzeln übersetzen wollen, können Sie dies mit
`./gradlew generateGrammarSource` tun.

> [!TIP]
> **Hinweis**: Sie werden in Ihrem eigenen Code Abhängigkeiten zu (bzw. Importe von)
> generierten Klassen haben. IntelliJ wird deshalb beim Start entsprechende Fehler anzeigen -
> die generierten Klassen existieren ja in einem frischen Projekt noch nicht! Diese werden
> erst beim Build (`./gradlew build`) bzw. beim expliziten Aufruf von
> `./gradlew generateGrammarSource` erzeugt. Danach sind dann auch die Fehlermeldungen weg ...
> Bei einem `./gradlew clean` werden auch die generierten Sourcen mit entfernt, weshalb es
> danach bis zum ersten Build oder Run wieder Fehlermeldung bzgl. der ANTLR-Dateien gibt.

> [!IMPORTANT]
> **Hinweis**: Sorgen Sie dafür, dass Ihre IDE tatsächlich auch die Projekteinstellungen von
> Gradle übernommen hat und auch mit Gradle baut!
>
> Check, ob die **Projekteinstellungen** in IntelliJ passen:
> 1. Menü `File > Project Structure > Project Settings > Project` sollte für Ihr Projekt als
> SDK ein "Java 25" zeigen: ![](img/ij-projectsettings-sdk.png)
> 2. Menü `File > Project Structure > Project Settings > Libraries` sollte für Ihr Projekt
> Jar-Files für ANTLR4 zeigen: ![](img/ij-projectsettings-libs.png)
>
> Check, ob **IntelliJ mit Gradle baut**:
> Menü `File > Settings > Build, Execution, Deployment > Build Tools > Gradle` sollte auf
> Gradle umgestellt sein: ![](img/ij-setting-gradlebuild.png)
> Unter "Build & Run" sollte "Gradle" ausgewählt sein, die "Distribution" sollte auf "Wrapper"
> stehen, und als "Gradle JVM" sollte die für das Projekt verwendete JVM eingestellt sein,
> d.h. aktuell Java 25.

## License

This [work](https://github.com/Compiler-CampusMinden/student-support-code-template) by
[Carsten Gips](https://github.com/cagix) and
[contributors](https://github.com/Compiler-CampusMinden/student-support-code-template/graphs/contributors)
is licensed under [MIT](LICENSE.md).
