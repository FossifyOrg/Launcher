# Preserve the line number information for debugging stack traces.
-keepattributes SourceFile,LineNumberTable
# Preserve class names.
-dontobfuscate
# keepattributes only applies to classes matched by keep rules.
# Put a very weak keep rule here to ensure it applies everywhere.
-keep,allowshrinking,allowoptimization class * {
    *;
}
