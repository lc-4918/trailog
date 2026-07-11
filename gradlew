#!/bin/sh
# Lanceur Gradle simplifié. Si gradle-wrapper.jar est absent, ouvrez le projet
# dans Android Studio (il le régénère) ou lancez `gradle wrapper`.
DIR=$(cd "$(dirname "$0")" && pwd)
exec java -jar "$DIR/gradle/wrapper/gradle-wrapper.jar" "$@"
