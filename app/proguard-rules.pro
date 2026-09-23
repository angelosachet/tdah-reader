# PdfBox-Android: dependências opcionais que não incluímos.
-dontwarn org.bouncycastle.**
-dontwarn com.gemalto.jp2.**
# O PdfBox carrega algumas classes por reflexão (fontes, filtros, handlers).
-keep class com.tom_roush.pdfbox.** { *; }
-keep class com.tom_roush.fontbox.** { *; }
-keep class com.tom_roush.harmony.** { *; }
