# Se reduce solo el código de las librerías. El de Vigía se conserva entero para que
# las alarmas guardadas (JSON, Room, enums y clases selladas) se lean igual que antes.
-keep class app.vigia.alarmas.** { *; }
-keep enum app.vigia.alarmas.** { *; }
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod, SourceFile, LineNumberTable

# kotlinx.serialization
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-dontnote kotlinx.serialization.**

# Lector de códigos QR
-keep class com.journeyapps.barcodescanner.** { *; }
-keep class com.google.zxing.** { *; }
