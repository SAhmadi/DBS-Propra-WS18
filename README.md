# DBS Programmierpraktikum - Template für API

Dies ist das auf Jersey beruhende Template für die API.

## Vorbereitung

In der IDE muss dieses Template als Gradle-Projekt importiert werden.

## Allgemein

Die Mainklasse ist ```de.hhu.cs.dbs.propra.Application```. Nachdem das Programm gestartet wurde, kann mit cURL der Server getestet werden.

Die Datenbank muss in ```data``` liegen und den Namen ```database.db``` besitzen.

Änderungen müssen hauptsächlich nur im Package ```de.hhu.cs.dbs.propra.controllers``` vorgenommen werden. Dies umfasst auch das Anlegen von Controllern. Die darin enthaltene Klasse ```ExampleController``` dient als Beispiel dafür und muss für die Abgabe gelöscht werden. Zusätzlich müssen in der Klasse ```de.hhu.cs.dbs.propra.repositories.SQLiteUserRepository``` die mit ```TODO``` kommentierten SQL-Anweisungen entsprechend angepasst werden, um eine korrekte Authentifizierung und Authorisierung zu ermöglichen.

## Nützliche Links

- http://jdk.java.net[OpenJDK]
- https://gradle.org[Gradle]
- https://www.docker.com[Docker]
- https://eclipse-ee4j.github.io/jersey/[Jersey]
- https://curl.haxx.se[cURL]